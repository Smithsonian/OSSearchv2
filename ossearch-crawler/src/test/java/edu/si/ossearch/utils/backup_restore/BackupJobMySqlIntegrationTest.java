package edu.si.ossearch.utils.backup_restore;

import edu.si.ossearch.utils.backup_restore.entity.BackupJobLease;
import edu.si.ossearch.utils.backup_restore.entity.BackupJobRunStatus;
import edu.si.ossearch.utils.backup_restore.repository.BackupJobLeaseRepository;
import edu.si.ossearch.utils.backup_restore.repository.BackupJobRunStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the hand-written native SQL in the backup lease and run-status repositories
 * against a REAL MySQL, which is the only thing that can verify it.
 * <p>
 * The rest of the backup test suite mocks these repositories, so it proves the services'
 * logic but says nothing about whether the SQL is accepted. Three things in particular
 * cannot be covered any other way:
 * <ul>
 *   <li>{@code INSERT IGNORE} - MySQL-specific syntax with no JPA equivalent.</li>
 *   <li>{@code DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL :seconds SECOND)} - Hibernate's
 *       native-query parameter parser rejects a bind parameter in the shorthand
 *       {@code + INTERVAL :seconds SECOND} position, so the function form is required.
 *       Only executing it proves the parser accepts this one.</li>
 *   <li>{@code findFirstByOrderByStartedAtDescIdDesc} - Spring Data validates the method
 *       NAME when it creates the repository proxy, but only a query against real rows
 *       shows the ordering is the intended one.</li>
 * </ul>
 * <p>
 * <strong>Opt-in.</strong> Requires a MySQL 8 reachable at the URL below and is skipped
 * unless {@code -Dossearch.it.mysql=true} is passed, so it never breaks a normal
 * {@code mvn test} on a machine with no database. To run it against the docker-compose
 * database:
 * <pre>
 * ./mvnw -pl ossearch-crawler test -Dossearch.skipTests=false \
 *     -Dossearch.it.mysql=true -Dtest=BackupJobMySqlIntegrationTest
 * </pre>
 * <p>
 * Safe against a populated database: {@code @DataJpaTest} is transactional and rolls
 * every test back, and {@code ddl-auto=none} means it can never alter the schema. It
 * reads whatever rows are already present rather than assuming an empty table.
 *
 * @author jbirkhimer
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfSystemProperty(named = "ossearch.it.mysql", matches = "true")
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.datasource.url=jdbc:mysql://localhost:3309/si_search_db_dmz",
        "spring.datasource.username=root",
        "spring.datasource.password=root",
        // Never let a verification run mutate the schema of the database it points at.
        "spring.jpa.hibernate.ddl-auto=none"
})
class BackupJobMySqlIntegrationTest {

    private static final String LOCK = "scheduled_collection_backup";
    private static final String NODE_A = "it-node-a";
    private static final String NODE_B = "it-node-b";
    private static final long SIX_HOURS_SECONDS = 6 * 60 * 60L;

    @Autowired
    private BackupJobLeaseRepository leaseRepository;

    @Autowired
    private BackupJobRunStatusRepository statusRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * The seed runs on every node at every startup, so being a no-op against an existing
     * row is the normal case, not the exception. {@code existsById()} + {@code save()}
     * would have been a check-then-act race between two nodes booting together.
     */
    @Test
    @DisplayName("INSERT IGNORE seeds the mutex row and is idempotent")
    void seedIsIdempotent() {
        leaseRepository.seed(LOCK);
        entityManager.flush();
        entityManager.clear();

        assertThat(leaseRepository.findById(LOCK))
                .as("the mutex row must exist after seeding")
                .isPresent();

        // The whole point: a second seed must not throw on the primary key.
        int inserted = leaseRepository.seed(LOCK);
        assertThat(inserted)
                .as("a repeat seed inserts nothing")
                .isZero();
    }

    /**
     * This is the DATE_ADD test. If Hibernate rejected the interval form, or MySQL
     * computed it wrongly, expires_at would be null or not ~6h out.
     * <p>
     * The offset is measured <em>in the database</em>, with {@code TIMESTAMPDIFF} against
     * {@code CURRENT_TIMESTAMP(3)}, rather than by reading expires_at into Java and
     * comparing it to {@link Instant#now()}. That is not test pedantry - it is the
     * invariant under test. Expiry is deliberately computed and compared entirely by the
     * database clock so that two app servers with skewed clocks still agree on when a
     * lease ends, and a MySQL server in a different zone from the JVM (the
     * docker-compose database runs UTC while a developer's JVM usually does not) makes a
     * Java-side comparison wrong by exactly that offset. Nothing in the production code
     * reads expires_at into Java, and this assertion must not be the first thing to.
     */
    @Test
    @DisplayName("acquire takes a free lease and sets expires_at by the DB clock")
    void acquireSetsExpiryFromTheDatabaseClock() {
        freeTheLease();

        int acquired = leaseRepository.acquire(NODE_A, SIX_HOURS_SECONDS, LOCK);
        assertThat(acquired).as("a free lease must be acquirable").isEqualTo(1);

        BackupJobLease lease = reloadLease();
        assertThat(lease.getOwner()).isEqualTo(NODE_A);
        assertThat(lease.getExpiresAt())
                .as("DATE_ADD must have populated expires_at")
                .isNotNull();

        Number secondsOut = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT TIMESTAMPDIFF(SECOND, CURRENT_TIMESTAMP(3), expires_at) "
                                   + "FROM backup_job_lease WHERE lock_name = :lockName")
                .setParameter("lockName", LOCK)
                .getSingleResult();

        // Generous window: the point is that the interval arithmetic happened at all and
        // landed where it should, not that it is accurate to the second.
        assertThat(secondsOut.longValue())
                .as("expires_at must sit ~6h ahead of the database's own clock")
                .isBetween(SIX_HOURS_SECONDS - 300, SIX_HOURS_SECONDS + 300);
    }

    @Test
    @DisplayName("a second node cannot acquire a lease that is still held")
    void acquireIsExclusiveWhileHeld() {
        freeTheLease();
        assertThat(leaseRepository.acquire(NODE_A, SIX_HOURS_SECONDS, LOCK)).isEqualTo(1);

        int stolen = leaseRepository.acquire(NODE_B, SIX_HOURS_SECONDS, LOCK);

        assertThat(stolen)
                .as("the conditional UPDATE must match no rows while another node holds the lease")
                .isZero();
        assertThat(reloadLease().getOwner())
                .as("the original owner must be untouched")
                .isEqualTo(NODE_A);
    }

    @Test
    @DisplayName("extend pushes expires_at out for the node that still holds the lease")
    void extendPushesExpiryOut() {
        freeTheLease();
        // A short lease first, so the extension is unambiguously larger.
        assertThat(leaseRepository.acquire(NODE_A, 60L, LOCK)).isEqualTo(1);
        Instant shortExpiry = reloadLease().getExpiresAt().toInstant();

        int extended = leaseRepository.extend(NODE_A, SIX_HOURS_SECONDS, LOCK);

        assertThat(extended).as("the holder must be able to extend").isEqualTo(1);
        assertThat(reloadLease().getExpiresAt().toInstant())
                .as("extend must move expires_at later")
                .isAfter(shortExpiry);
    }

    @Test
    @DisplayName("extend fails for a node that does not hold the lease")
    void extendRejectsANonHolder() {
        freeTheLease();
        assertThat(leaseRepository.acquire(NODE_A, SIX_HOURS_SECONDS, LOCK)).isEqualTo(1);

        assertThat(leaseRepository.extend(NODE_B, SIX_HOURS_SECONDS, LOCK))
                .as("a node that never held the lease must not be able to extend it")
                .isZero();
    }

    /**
     * Guards the owner predicate on release: a late release from a previous, expired
     * attempt must not free a lease another node has since taken.
     */
    @Test
    @DisplayName("release only clears the lease for its own owner")
    void releaseIsOwnerScoped() {
        freeTheLease();
        assertThat(leaseRepository.acquire(NODE_A, SIX_HOURS_SECONDS, LOCK)).isEqualTo(1);

        assertThat(leaseRepository.release(NODE_B, LOCK))
                .as("another node's release must not match")
                .isZero();
        assertThat(reloadLease().getOwner()).isEqualTo(NODE_A);

        assertThat(leaseRepository.release(NODE_A, LOCK))
                .as("the holder's release must match")
                .isEqualTo(1);

        BackupJobLease released = reloadLease();
        assertThat(released.getOwner()).isNull();
        assertThat(released.getExpiresAt()).isNull();
    }

    /**
     * Covers the full cycle the scheduled job actually performs, in order.
     */
    @Test
    @DisplayName("acquire -> extend -> release -> acquire round trips")
    void fullLeaseCycle() {
        freeTheLease();

        assertThat(leaseRepository.acquire(NODE_A, SIX_HOURS_SECONDS, LOCK)).isEqualTo(1);
        assertThat(leaseRepository.extend(NODE_A, SIX_HOURS_SECONDS, LOCK)).isEqualTo(1);
        assertThat(leaseRepository.release(NODE_A, LOCK)).isEqualTo(1);

        assertThat(leaseRepository.acquire(NODE_B, SIX_HOURS_SECONDS, LOCK))
                .as("a released lease must be acquirable by the other node")
                .isEqualTo(1);
    }

    /**
     * The derived query behind {@code getLastRun()}. Spring Data validated the method
     * name at startup; this checks the ordering against rows, including the id
     * tie-breaker that exists because TIMESTAMP(3) is coarse enough for two runs to
     * share a started_at.
     */
    @Test
    @DisplayName("findFirstByOrderByStartedAtDescIdDesc returns the newest run")
    void derivedQueryReturnsTheNewestRun() {
        Timestamp shared = Timestamp.from(Instant.now().plus(3650, ChronoUnit.DAYS));

        BackupJobRunStatus older = persistRun(shared, "PARTIAL_FAILURE");
        BackupJobRunStatus newer = persistRun(shared, "SUCCESS");
        entityManager.flush();
        entityManager.clear();

        Optional<BackupJobRunStatus> found = statusRepository.findFirstByOrderByStartedAtDescIdDesc();

        assertThat(found).isPresent();
        assertThat(found.get().getId())
                .as("with an identical started_at the higher id must win, not the lower")
                .isEqualTo(newer.getId())
                .isNotEqualTo(older.getId());
        assertThat(found.get().getStatus()).isEqualTo("SUCCESS");
    }

    /**
     * Round-trips every column the UI reads, so a mapping drift between the entity and
     * the table surfaces here rather than as silently missing status in production.
     */
    @Test
    @DisplayName("a run status row round trips through every mapped column")
    void runStatusRoundTripsAllColumns() {
        BackupJobRunStatus saved = new BackupJobRunStatus();
        saved.setHostname("it-host");
        saved.setStartedAt(Timestamp.from(Instant.now()));
        saved.setFinishedAt(Timestamp.from(Instant.now()));
        saved.setStatus("SUCCESS");
        saved.setCollectionsTotal(7);
        saved.setCollectionsSucceeded(6);
        saved.setCollectionsFailed(1);
        saved.setRetentionFilesDeleted(3);
        saved.setRetentionFilesFailedDelete(2);
        saved.setRetentionDryRun(false);
        saved.setRetentionStatus("COMPLETED");
        saved.setRetentionError(null);
        saved.setErrorMessage("boom");

        Long id = statusRepository.save(saved).getId();
        entityManager.flush();
        entityManager.clear();

        BackupJobRunStatus reloaded = statusRepository.findById(id).orElseThrow();
        assertThat(reloaded.getHostname()).isEqualTo("it-host");
        assertThat(reloaded.getStatus()).isEqualTo("SUCCESS");
        assertThat(reloaded.getCollectionsTotal()).isEqualTo(7);
        assertThat(reloaded.getCollectionsSucceeded()).isEqualTo(6);
        assertThat(reloaded.getCollectionsFailed()).isEqualTo(1);
        assertThat(reloaded.getRetentionFilesDeleted()).isEqualTo(3);
        assertThat(reloaded.getRetentionFilesFailedDelete()).isEqualTo(2);
        assertThat(reloaded.getRetentionDryRun()).isFalse();
        assertThat(reloaded.getRetentionStatus()).isEqualTo("COMPLETED");
        assertThat(reloaded.getErrorMessage()).isEqualTo("boom");
    }

    /**
     * Nullable columns must come back as null, not 0/false. The JDBC code this replaced
     * used {@code rs.getObject} rather than {@code getInt}/{@code getBoolean} for exactly
     * this reason, and boxed entity fields are what preserve it - a primitive {@code int}
     * would turn "this run never reported a count" into "it reported zero".
     */
    @Test
    @DisplayName("unset numeric and boolean columns come back null, not 0/false")
    void nullableColumnsStayNull() {
        BackupJobRunStatus running = new BackupJobRunStatus();
        running.setHostname("it-host");
        running.setStartedAt(Timestamp.from(Instant.now()));
        running.setStatus("RUNNING");

        Long id = statusRepository.save(running).getId();
        entityManager.flush();
        entityManager.clear();

        BackupJobRunStatus reloaded = statusRepository.findById(id).orElseThrow();
        assertThat(reloaded.getFinishedAt()).isNull();
        assertThat(reloaded.getCollectionsTotal()).isNull();
        assertThat(reloaded.getCollectionsSucceeded()).isNull();
        assertThat(reloaded.getCollectionsFailed()).isNull();
        assertThat(reloaded.getRetentionFilesDeleted()).isNull();
        assertThat(reloaded.getRetentionFilesFailedDelete()).isNull();
        assertThat(reloaded.getRetentionDryRun()).isNull();
        assertThat(reloaded.getRetentionStatus()).isNull();
        assertThat(reloaded.getRetentionError()).isNull();
        assertThat(reloaded.getErrorMessage()).isNull();
    }

    /**
     * Puts the lease into a known-free state without assuming what the database already
     * held - this runs against a populated dev database, and the transaction rolls back
     * afterwards either way.
     */
    private void freeTheLease() {
        leaseRepository.seed(LOCK);
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE backup_job_lease SET owner = NULL, expires_at = NULL "
                                   + "WHERE lock_name = :lockName")
                .setParameter("lockName", LOCK)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private BackupJobLease reloadLease() {
        entityManager.flush();
        entityManager.clear();
        return leaseRepository.findById(LOCK).orElseThrow();
    }

    /**
     * started_at is pushed far into the future so these rows sort above anything the
     * database already contains, which lets the ordering assertion be exact rather than
     * dependent on what is already there.
     */
    private BackupJobRunStatus persistRun(Timestamp startedAt, String status) {
        BackupJobRunStatus run = new BackupJobRunStatus();
        run.setHostname("it-host");
        run.setStartedAt(startedAt);
        run.setStatus(status);
        return statusRepository.save(run);
    }
}

package edu.si.ossearch.utils.backup_restore.repository;

import edu.si.ossearch.utils.backup_restore.entity.BackupJobLease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic conditional UPDATEs against the single-row {@code backup_job_lease} mutex.
 * <p>
 * {@code exported = false} is a security control, not tidiness. Spring Data REST
 * auto-exposes every public repository interface it finds under {@code /api}, and
 * {@code WebSecurityConfig} guards {@code /api/**} with only {@code .authenticated()} -
 * so a default-exported lease repository would let ANY logged-in user PATCH the mutex
 * row and either seize the backup lock or free it out from under the node holding it,
 * enabling exactly the concurrent-retention double-deletion this lease prevents.
 * <p>
 * Every statement here is native and hand-written rather than derived, for two reasons
 * that must both survive any future edit:
 * <ul>
 *   <li>They are single conditional UPDATEs, never read-modify-write. Two concurrent
 *       UPDATEs against the same primary-key row serialize on the InnoDB row lock: the
 *       loser only re-evaluates its WHERE clause against the winner's committed row, by
 *       which point it matches nothing and affects 0 rows. Loading the entity and saving
 *       it back would turn that into a check-then-act race.</li>
 *   <li>Expiry is computed by the DATABASE clock ({@code CURRENT_TIMESTAMP(3)}), never
 *       the JVM's. Two app servers with skewed clocks must agree on when a lease
 *       expires. {@code DATE_ADD(..., INTERVAL :seconds SECOND)} is used rather than the
 *       {@code + INTERVAL :seconds SECOND} shorthand because Hibernate's native-query
 *       parameter parser does not accept a bind parameter in that position.</li>
 * </ul>
 * Intentionally SpEL-free: these are called from the {@code @Scheduled} backup thread,
 * which runs with an EMPTY SecurityContext. Same constraint documented on
 * {@code CollectionRepository#findAllCollectionIds()}.
 *
 * @author jbirkhimer
 */
@Repository
@RepositoryRestResource(exported = false)
public interface BackupJobLeaseRepository extends JpaRepository<BackupJobLease, String> {

    /**
     * Seeds the single mutex row. {@code INSERT IGNORE} rather than
     * {@code existsById()} + {@code save()}, which is racy across two nodes booting at
     * the same time: both would see "absent" and one would fail on the primary key.
     *
     * @return 1 if the row was inserted, 0 if it already existed
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT IGNORE INTO backup_job_lease (lock_name) VALUES (:lockName)", nativeQuery = true)
    int seed(@Param("lockName") String lockName);

    /**
     * Takes the lease only if it is currently free or already expired.
     *
     * @return 1 if this caller now holds the lease, 0 if another node holds it
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE backup_job_lease " +
                   "   SET owner = :owner, " +
                   "       expires_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL :seconds SECOND) " +
                   " WHERE lock_name = :lockName " +
                   "   AND (owner IS NULL OR expires_at < CURRENT_TIMESTAMP(3))", nativeQuery = true)
    int acquire(@Param("owner") String owner, @Param("seconds") long seconds,
                @Param("lockName") String lockName);

    /**
     * Pushes {@code expires_at} further out for a lease this caller still holds.
     * <p>
     * The {@code expires_at > CURRENT_TIMESTAMP(3)} predicate is required and is not
     * redundant with the owner check: if the lease has already lapsed, another node may
     * legitimately have taken and released it, so an owner-only match would silently
     * RE-ACQUIRE a lapsed lease instead of reporting that it was lost.
     *
     * @return 1 if the lease was still held and has been extended, 0 if it was lost
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE backup_job_lease " +
                   "   SET expires_at = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL :seconds SECOND) " +
                   " WHERE lock_name = :lockName " +
                   "   AND owner = :owner " +
                   "   AND expires_at > CURRENT_TIMESTAMP(3)", nativeQuery = true)
    int extend(@Param("owner") String owner, @Param("seconds") long seconds,
               @Param("lockName") String lockName);

    /**
     * Releases the lease, but only if {@code owner} still holds it, so a node can never
     * release someone else's lease (e.g. a slow/late release from a previous attempt
     * that has since expired and been re-acquired by another node).
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE backup_job_lease SET owner = NULL, expires_at = NULL " +
                   " WHERE lock_name = :lockName AND owner = :owner", nativeQuery = true)
    int release(@Param("owner") String owner, @Param("lockName") String lockName);
}

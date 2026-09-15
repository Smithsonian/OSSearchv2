package edu.si.ossearch.utils.backup_restore.lease;

import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.repository.BackupJobLeaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit + Mockito tests for {@link BackupJobLeaseService}. The repository is
 * mocked and backed by an in-memory model of the single mutex row that applies the same
 * WHERE-clause predicates the native statements do.
 * <p>
 * IMPORTANT COVERAGE LIMIT: this exercises the SERVICE's contract (fail-safe on error,
 * owner scoping, extend-only-if-still-held), NOT the SQL. The native statements use
 * MySQL-only syntax ({@code INSERT IGNORE}, {@code DATE_ADD(..., INTERVAL :seconds
 * SECOND)}, {@code CURRENT_TIMESTAMP(3)}) and there is no H2 or Testcontainers
 * dependency in the POM, so nothing here proves the queries parse or run. That needs a
 * real MySQL and is untested.
 */
@ExtendWith(MockitoExtension.class)
class BackupJobLeaseServiceTest {

    private static final Duration LEASE = Duration.ofHours(6);

    @Mock
    private BackupJobLeaseRepository repository;

    private BackupJobLeaseService service;

    /** In-memory stand-in for the single backup_job_lease row. */
    private String owner;
    private Instant expiresAt;

    @BeforeEach
    void setUp() {
        service = new BackupJobLeaseService(repository, new ScheduledBackupConfig());
        owner = null;
        expiresAt = null;
    }

    private void wireRow() {
        when(repository.acquire(anyString(), anyLong(), anyString())).thenAnswer(inv -> {
            String candidate = inv.getArgument(0);
            long seconds = inv.getArgument(1);
            if (owner != null && expiresAt != null && expiresAt.isAfter(Instant.now())) {
                return 0;
            }
            owner = candidate;
            expiresAt = Instant.now().plusSeconds(seconds);
            return 1;
        });
    }

    @Test
    void acquireThenSecondAcquireFailsThenReleaseThenAcquireSucceeds() {
        wireRow();
        when(repository.release(anyString(), anyString())).thenAnswer(inv -> {
            String candidate = inv.getArgument(0);
            if (!candidate.equals(owner)) {
                return 0;
            }
            owner = null;
            expiresAt = null;
            return 1;
        });

        assertThat(service.tryAcquire("node-a", LEASE)).isTrue();
        assertThat(service.tryAcquire("node-b", LEASE)).isFalse();

        // A node can never release someone else's lease.
        service.release("node-b");
        assertThat(service.tryAcquire("node-b", LEASE)).isFalse();

        service.release("node-a");
        assertThat(service.tryAcquire("node-b", LEASE)).isTrue();
    }

    @Test
    void extendSucceedsForTheHolderAndFailsForEveryoneElse() {
        wireRow();
        when(repository.extend(anyString(), anyLong(), anyString())).thenAnswer(inv -> {
            String candidate = inv.getArgument(0);
            long seconds = inv.getArgument(1);
            if (!candidate.equals(owner) || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
                return 0;
            }
            expiresAt = Instant.now().plusSeconds(seconds);
            return 1;
        });

        assertThat(service.tryAcquire("node-a", LEASE)).isTrue();
        Instant before = expiresAt;

        assertThat(service.extend("node-a", LEASE)).isTrue();
        assertThat(expiresAt).isAfterOrEqualTo(before);

        // Not the holder.
        assertThat(service.extend("node-b", LEASE)).isFalse();

        // Already lapsed: must report "lost", not silently re-acquire.
        expiresAt = Instant.now().minusSeconds(1);
        assertThat(service.extend("node-a", LEASE)).isFalse();
    }

    @Test
    void everyOperationFailsSafeWhenTheDatabaseIsUnreachable() {
        when(repository.acquire(anyString(), anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        when(repository.extend(anyString(), anyLong(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThat(service.tryAcquire("node-a", LEASE)).isFalse();
        assertThat(service.extend("node-a", LEASE)).isFalse();
    }

    @Test
    void theSeedRowIsOnlyWrittenOnANodeWhereTheJobIsEnabled() {
        ScheduledBackupConfig disabled = new ScheduledBackupConfig();
        disabled.setEnabled(false);
        new BackupJobLeaseService(repository, disabled).seedLeaseRow();
        verify(repository, never()).seed(anyString());

        ScheduledBackupConfig enabled = new ScheduledBackupConfig();
        enabled.setEnabled(true);
        new BackupJobLeaseService(repository, enabled).seedLeaseRow();
        verify(repository).seed(eq("scheduled_collection_backup"));
    }

    @Test
    void aFailedSeedIsSwallowedSoItCanNeverAbortStartup() {
        when(repository.seed(anyString())).thenThrow(new DataAccessResourceFailureException("db down"));
        ScheduledBackupConfig enabled = new ScheduledBackupConfig();
        enabled.setEnabled(true);
        new BackupJobLeaseService(repository, enabled).seedLeaseRow();
    }
}

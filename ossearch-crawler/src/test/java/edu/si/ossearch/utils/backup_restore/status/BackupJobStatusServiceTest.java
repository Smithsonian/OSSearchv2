package edu.si.ossearch.utils.backup_restore.status;

import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.entity.BackupJobRunStatus;
import edu.si.ossearch.utils.backup_restore.repository.BackupJobRunStatusRepository;
import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit + Mockito tests for {@link BackupJobStatusService}. No Spring context and
 * no database: there is no H2 dependency in the POM and the only DB-backed profile
 * points at a real MySQL instance, so the repository is mocked and backed by a tiny
 * in-memory store that mimics save/findById/findFirst.
 * <p>
 * The key-name assertions are the point of this class. Every method of the service
 * swallows {@code DataAccessException} into a log line, so column/key drift would stop
 * all status recording SILENTLY; these tests are what makes that loud.
 */
@ExtendWith(MockitoExtension.class)
class BackupJobStatusServiceTest {

    /**
     * The exact keys, in the exact order, that {@code BackUpRestoreController}
     * serializes to JSON and the Vue {@code ScheduledBackupStatus} component reads by
     * name. Changing this list is a breaking UI change, not a test fix.
     */
    private static final List<String> CONTRACT_KEYS = List.of(
            "hostname", "startedAt", "finishedAt", "status", "collectionsTotal",
            "collectionsSucceeded", "collectionsFailed", "retentionFilesDeleted",
            "retentionFilesFailedDelete", "retentionDryRun", "retentionStatus",
            "retentionError", "errorMessage");

    @Mock
    private BackupJobRunStatusRepository repository;

    private ScheduledBackupConfig config;
    private BackupJobStatusService service;

    /** Stand-in for the table; the last row saved wins for findFirst. */
    private BackupJobRunStatus stored;

    @BeforeEach
    void setUp() {
        config = new ScheduledBackupConfig();
        service = new BackupJobStatusService(repository, config);
        stored = null;
    }

    /** Wires the mock repository up as a one-row in-memory table. */
    private void wireStore() {
        AtomicLong ids = new AtomicLong(0);
        when(repository.save(any(BackupJobRunStatus.class))).thenAnswer(inv -> {
            BackupJobRunStatus run = inv.getArgument(0);
            if (run.getId() == null) {
                run.setId(ids.incrementAndGet());
            }
            stored = run;
            return run;
        });
    }

    @Test
    void roundTripRecordsStartAndFinishAndReadsThemBackUnderTheContractKeys() {
        wireStore();
        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored));
        when(repository.findFirstByOrderByStartedAtDescIdDesc())
                .thenAnswer(inv -> Optional.ofNullable(stored));

        // Must be recent: a RUNNING row older than the lease duration is deliberately
        // reported as STALE (see aRunningRowOlderThanTheLeaseDurationIsReportedStale).
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Long runId = service.recordStart("node-a", startedAt);
        assertThat(runId).isNotNull();

        // Mid-run the row must read back as RUNNING with a null finishedAt.
        Map<String, Object> running = service.getLastRun().orElseThrow();
        assertThat(running).containsEntry("status", "RUNNING");
        assertThat(running.get("finishedAt")).isNull();
        assertThat(running.get("collectionsTotal")).isNull();

        service.recordFinished(runId, 7, 6, 1, 3, 0, false,
                RetentionResult.Outcome.COMPLETED, null);

        Map<String, Object> last = service.getLastRun().orElseThrow();

        // Contract: exactly these keys, in exactly this order.
        assertThat(last.keySet()).containsExactlyElementsOf(CONTRACT_KEYS);

        assertThat(last).containsEntry("hostname", "node-a")
                .containsEntry("startedAt", Timestamp.from(startedAt))
                // failed > 0 must be PARTIAL_FAILURE, not FAILED.
                .containsEntry("status", "PARTIAL_FAILURE")
                .containsEntry("collectionsTotal", 7)
                .containsEntry("collectionsSucceeded", 6)
                .containsEntry("collectionsFailed", 1)
                .containsEntry("retentionFilesDeleted", 3)
                .containsEntry("retentionFilesFailedDelete", 0)
                .containsEntry("retentionDryRun", false)
                // retention_status is its own column; a COMPLETED retention under a
                // PARTIAL_FAILURE run must not be folded into status.
                .containsEntry("retentionStatus", "COMPLETED");
        assertThat(last.get("finishedAt")).isNotNull();
        assertThat(last.get("retentionError")).isNull();
        assertThat(last.get("errorMessage")).isNull();
    }

    @Test
    void recordAbortedPersistsTheAbortStatusItIsGivenRatherThanAHardcodedOne() {
        wireStore();
        when(repository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(stored));
        when(repository.findFirstByOrderByStartedAtDescIdDesc())
                .thenAnswer(inv -> Optional.ofNullable(stored));

        Long runId = service.recordStart("node-a", Instant.now());
        service.recordAborted(runId, BackupJobStatusService.STATUS_ABORTED_CRAWL_DIR_UNAVAILABLE,
                "CRAWL_DIR_UNAVAILABLE");

        Map<String, Object> last = service.getLastRun().orElseThrow();
        assertThat(last).containsEntry("status", "ABORTED_CRAWL_DIR_UNAVAILABLE")
                .containsEntry("errorMessage", "CRAWL_DIR_UNAVAILABLE");
    }

    @Test
    void aRunningRowOlderThanTheLeaseDurationIsReportedStale() {
        config.getLease().setDurationHours(6);
        BackupJobRunStatus run = new BackupJobRunStatus();
        run.setId(1L);
        run.setHostname("node-a");
        run.setStatus("RUNNING");
        run.setStartedAt(Timestamp.from(Instant.now().minus(Duration.ofHours(7))));
        when(repository.findFirstByOrderByStartedAtDescIdDesc()).thenReturn(Optional.of(run));

        assertThat(service.getLastRun().orElseThrow()).containsEntry("status", "STALE");
    }

    @Test
    void aRunningRowInsideTheLeaseDurationIsStillReportedRunning() {
        config.getLease().setDurationHours(6);
        BackupJobRunStatus run = new BackupJobRunStatus();
        run.setId(1L);
        run.setHostname("node-a");
        run.setStatus("RUNNING");
        run.setStartedAt(Timestamp.from(Instant.now().minus(Duration.ofHours(1))));
        when(repository.findFirstByOrderByStartedAtDescIdDesc()).thenReturn(Optional.of(run));

        assertThat(service.getLastRun().orElseThrow()).containsEntry("status", "RUNNING");
    }

    @Test
    void writesAreNoOpsOnANullRunIdAndNeverTouchTheRepository() {
        service.recordFinished(null, 1, 1, 0, 0, 0, false, RetentionResult.Outcome.COMPLETED, null);
        service.recordAborted(null, BackupJobStatusService.STATUS_ABORTED_LOW_DISK, "x");
        service.recordFailed(null, new IllegalStateException("x"));
        // No stubbing was needed, so any repository call here would fail the test as an
        // unnecessary-stubbing/strict-stub violation; assert the store stayed empty too.
        assertThat(stored).isNull();
    }

    @Test
    void aFailedLookupIsIndistinguishableFromNoHistory() {
        when(repository.findFirstByOrderByStartedAtDescIdDesc())
                .thenThrow(new DataAccessResourceFailureException("db down"));
        assertThat(service.getLastRun()).isEmpty();
    }

    @Test
    void aFailedInsertYieldsANullRunIdRatherThanPropagating() {
        when(repository.save(any(BackupJobRunStatus.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));
        assertThat(service.recordStart("node-a", Instant.now())).isNull();
    }
}

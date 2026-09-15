package edu.si.ossearch.utils.backup_restore.service;

import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.lease.BackupJobLeaseService;
import edu.si.ossearch.utils.backup_restore.retention.BackupRetentionRunner;
import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import edu.si.ossearch.utils.backup_restore.status.BackupJobStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit + Mockito tests for {@link ScheduledCollectionBackupService}. No Spring
 * context is started: the service is built with {@code new} and constructor-injected
 * mocks, and {@code crawlDir} - injected via {@code @Value} in production - is set
 * directly since the test lives in the same package.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledCollectionBackupServiceTest {

    @Mock
    private BackupJobLeaseService leaseService;
    @Mock
    private BackupJobStatusService statusService;
    @Mock
    private BackupRetentionRunner retentionRunner;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private BackupRestoreService backupRestoreService;

    private ScheduledBackupConfig config;
    private ScheduledCollectionBackupService service;

    @TempDir
    File crawlDir;

    @BeforeEach
    void setUp() {
        config = new ScheduledBackupConfig();
        config.setEnabled(true);
        // Disk preflight off by default so the happy-path tests cannot flake on a
        // constrained sandbox. The two abort branches set it explicitly - see
        // abortsWithLowDiskStatusAndStillRunsRetention.
        config.setMinFreeSpaceMb(0);
        config.setWithCrawlSchedule(true);
        config.setIncludeUsers(false);

        service = new ScheduledCollectionBackupService(
                config, leaseService, statusService, retentionRunner, collectionRepository, backupRestoreService);
        service.crawlDir = crawlDir;

        // Common happy-path stubs; individual tests override as needed. lenient() since
        // not every test reaches every stubbed call (e.g. the "disabled" case never
        // touches the lease at all).
        lenient().when(leaseService.resolveHostname()).thenReturn("test-host");
        lenient().when(leaseService.tryAcquire(anyString(), any())).thenReturn(true);
        lenient().when(statusService.recordStart(anyString(), any(Instant.class))).thenReturn(1L);
        lenient().when(retentionRunner.run()).thenReturn(new RetentionResult(false));
        lenient().when(leaseService.extend(anyString(), any())).thenReturn(true);
    }

    private void stubBackupSucceeds(Long id) throws Exception {
        when(backupRestoreService.backupCollection(eq(id), anyBoolean(), anyBoolean()))
                .thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    void backsUpEveryCollectionId() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L, 2L, 3L));
        stubBackupSucceeds(1L);
        stubBackupSucceeds(2L);
        stubBackupSucceeds(3L);

        service.runScheduledBackup();

        verify(backupRestoreService).backupCollection(1L, config.isWithCrawlSchedule(), config.isIncludeUsers());
        verify(backupRestoreService).backupCollection(2L, config.isWithCrawlSchedule(), config.isIncludeUsers());
        verify(backupRestoreService).backupCollection(3L, config.isWithCrawlSchedule(), config.isIncludeUsers());
    }

    @Test
    void continuesRemainingCollectionsWhenOneFails() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L, 2L, 3L));
        stubBackupSucceeds(1L);
        when(backupRestoreService.backupCollection(eq(2L), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("boom"));
        stubBackupSucceeds(3L);

        service.runScheduledBackup();

        // Collection 1 and 3 must both have been attempted despite collection 2 blowing up
        // in between - a single failure must never short-circuit the loop.
        verify(backupRestoreService).backupCollection(eq(1L), anyBoolean(), anyBoolean());
        verify(backupRestoreService).backupCollection(eq(2L), anyBoolean(), anyBoolean());
        verify(backupRestoreService).backupCollection(eq(3L), anyBoolean(), anyBoolean());

        ArgumentCaptor<Integer> totalCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> succeededCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> failedCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(statusService).recordFinished(
                anyLong(), totalCaptor.capture(), succeededCaptor.capture(), failedCaptor.capture(),
                anyInt(), anyInt(), anyBoolean(), any(RetentionResult.Outcome.class), any());

        assertThat(totalCaptor.getValue()).isEqualTo(3);
        assertThat(succeededCaptor.getValue()).isEqualTo(2);
        assertThat(failedCaptor.getValue()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenDisabled() {
        config.setEnabled(false);

        service.runScheduledBackup();

        verifyNoInteractions(collectionRepository, backupRestoreService, leaseService, statusService, retentionRunner);
    }

    @Test
    void skipsRunWhenLeaseNotAcquired() {
        when(leaseService.tryAcquire(anyString(), any())).thenReturn(false);

        service.runScheduledBackup();

        verifyNoInteractions(backupRestoreService);
        verify(statusService, never()).recordStart(anyString(), any(Instant.class));
        // Lease was never actually held, so there is nothing for this run to release.
        verify(leaseService, never()).release(anyString());
    }

    @Test
    void releasesLeaseEvenWhenRunFails() {
        when(collectionRepository.findAllCollectionIds()).thenThrow(new RuntimeException("db is down"));

        service.runScheduledBackup();

        verify(leaseService).release("test-host");
        verify(statusService).recordFailed(eq(1L), any(Exception.class));
        verifyNoInteractions(backupRestoreService);
    }

    @Test
    void runsRetentionAfterBackups() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L));
        stubBackupSucceeds(1L);

        RetentionResult retentionResult = new RetentionResult(true);
        retentionResult.recordDeleted(new File("would-be-deleted-1.json"));
        retentionResult.recordDeleted(new File("would-be-deleted-2.json"));
        retentionResult.recordFailedDelete(new File("failed-to-delete.json"));
        when(retentionRunner.run()).thenReturn(retentionResult);

        service.runScheduledBackup();

        // "After" is the assertion, not just "both happened": retention deletes files on the
        // same volume the backups are being written to, so a retention pass that ran BEFORE
        // the backup loop would prune against a stale listing that is missing the very
        // backups this run just produced.
        InOrder ordered = inOrder(backupRestoreService, leaseService, retentionRunner, statusService);
        ordered.verify(backupRestoreService).backupCollection(eq(1L), anyBoolean(), anyBoolean());
        ordered.verify(leaseService).extend(eq("test-host"), any());
        ordered.verify(retentionRunner).run();
        ordered.verify(statusService).recordFinished(
                eq(1L), eq(1), eq(1), eq(0), eq(2), eq(1), eq(true),
                eq(RetentionResult.Outcome.COMPLETED), isNull());

        verify(retentionRunner, times(1)).run();
    }

    /**
     * The defect this guards: a retention step that CRASHED used to be reported downstream
     * exactly like one that was switched off (both {@code deleted=0, failedDelete=0}), so
     * the persisted run status - and the status panel reading it - looked like a normal run.
     * The FAILED outcome and its message must reach the audit row.
     */
    @Test
    void recordsFailedRetentionOutcomeAndMessage() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L));
        stubBackupSucceeds(1L);
        when(retentionRunner.run()).thenReturn(RetentionResult.failed("crawlDir listing blew up"));

        service.runScheduledBackup();

        ArgumentCaptor<RetentionResult.Outcome> outcomeCaptor =
                ArgumentCaptor.forClass(RetentionResult.Outcome.class);
        ArgumentCaptor<String> retentionErrorCaptor = ArgumentCaptor.forClass(String.class);
        verify(statusService).recordFinished(
                eq(1L), eq(1), eq(1), eq(0), anyInt(), anyInt(), anyBoolean(),
                outcomeCaptor.capture(), retentionErrorCaptor.capture());

        assertThat(outcomeCaptor.getValue()).isEqualTo(RetentionResult.Outcome.FAILED);
        assertThat(retentionErrorCaptor.getValue()).isEqualTo("crawlDir listing blew up");
    }

    /**
     * A crashed retention step must NOT be laundered into the overall run status: the
     * collections all succeeded, so {@code recordFinished} still reports failed=0 and the
     * SUCCESS/PARTIAL_FAILURE decision (which means "some collections failed") is untouched.
     * Retention state rides in its own field.
     */
    @Test
    void failedRetentionDoesNotChangeCollectionFailureCounts() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L, 2L));
        stubBackupSucceeds(1L);
        stubBackupSucceeds(2L);
        when(retentionRunner.run()).thenReturn(RetentionResult.failed("boom"));

        service.runScheduledBackup();

        verify(statusService).recordFinished(
                eq(1L), eq(2), eq(2), eq(0), eq(0), eq(0), eq(false),
                eq(RetentionResult.Outcome.FAILED), eq("boom"));
    }

    @Test
    void skipsSecondInvocationWhileStillRunning() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L));

        // Re-enter the scheduled entry point synchronously while the first (and only)
        // collection is being backed up. NOTE this is NOT what the cron trigger does:
        // Spring's ReschedulingRunnable only computes a cron task's next fire time after
        // the current execution returns, so a cron @Scheduled method cannot overlap itself
        // and this guard is unreachable from that path. What is covered here is a direct
        // re-entrant call - the only way the guard can actually fire.
        when(backupRestoreService.backupCollection(eq(1L), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    service.runScheduledBackup();
                    return new ByteArrayInputStream(new byte[0]);
                });

        service.runScheduledBackup();

        // The re-entrant call must have been rejected by the running-guard before it
        // could acquire the lease or touch the repository a second time.
        verify(collectionRepository, times(1)).findAllCollectionIds();
        verify(leaseService, times(1)).tryAcquire(anyString(), any());
    }

    /**
     * The low-disk abort's load-bearing invariant: retention MUST still run. Pruning is the
     * only thing that can free space on the volume, so a version of this branch that
     * returned early would make every subsequent nightly run find the same condition and
     * abort again - a deadlock that never recovers on its own.
     */
    @Test
    void abortsWithLowDiskStatusAndStillRunsRetention() {
        // A threshold no filesystem can satisfy, so the preflight is guaranteed to trip.
        config.setMinFreeSpaceMb(Long.MAX_VALUE / (1024L * 1024L));

        RetentionResult retentionResult = new RetentionResult(false);
        retentionResult.recordDeleted(new File("pruned-to-free-space.json"));
        when(retentionRunner.run()).thenReturn(retentionResult);

        service.runScheduledBackup();

        verify(retentionRunner, times(1)).run();
        verify(statusService).recordAborted(eq(1L),
                eq(BackupJobStatusService.STATUS_ABORTED_LOW_DISK), anyString(),
                eq(1), eq(0), eq(false), eq(RetentionResult.Outcome.COMPLETED), isNull());
        verifyNoInteractions(backupRestoreService, collectionRepository);
        // The lease is deliberately held rather than released on an abort: an abort takes
        // milliseconds, and releasing lets a second node with enabled=true mistakenly set
        // pick the window straight back up and run the whole job again.
        verify(leaseService, never()).release(anyString());
    }

    /**
     * A missing/unmounted crawlDir must abort with its OWN status - the UI badge otherwise
     * says "low disk" while the real problem is a dead NFS mount - and must NOT run
     * retention: there is nothing safely enumerable under an unmounted path.
     */
    @Test
    void abortsWithCrawlDirUnavailableStatusAndSkipsRetention() {
        service.crawlDir = new File(crawlDir, "definitely-not-mounted");

        service.runScheduledBackup();

        verify(statusService).recordAborted(eq(1L),
                eq(BackupJobStatusService.STATUS_ABORTED_CRAWL_DIR_UNAVAILABLE), anyString());
        verifyNoInteractions(retentionRunner, backupRestoreService, collectionRepository);
        verify(leaseService, never()).release(anyString());
    }

    /**
     * A night on which every collection failed (read-only remount, or an export regression)
     * has produced nothing, so pruning 90-day-old backups buys nothing and is irreversible.
     * Retention must be skipped - the opposite call to the low-disk abort, where pruning is
     * the only thing that can clear the condition.
     */
    @Test
    void skipsRetentionWhenEveryCollectionFailed() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L, 2L));
        when(backupRestoreService.backupCollection(anyLong(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("read-only filesystem"));

        service.runScheduledBackup();

        verifyNoInteractions(retentionRunner);
        verify(statusService).recordFinished(eq(1L), eq(2), eq(0), eq(2),
                eq(0), eq(0), eq(false), eq(RetentionResult.Outcome.SKIPPED),
                eq("all 2 collections failed this run"));
        verify(leaseService).release("test-host");
    }

    /** A run with zero collections is not a systemic failure, so retention still runs. */
    @Test
    void runsRetentionWhenThereAreNoCollectionsAtAll() {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of());

        service.runScheduledBackup();

        verify(retentionRunner, times(1)).run();
    }

    /**
     * A run that outlives lease.duration-hours has lost the mutex; another node may already
     * be pruning the same NFS directories. The destructive step must not proceed on an
     * unproven lease.
     */
    @Test
    void skipsRetentionWhenTheLeaseWasLostDuringTheRun() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L));
        stubBackupSucceeds(1L);
        when(leaseService.extend(anyString(), any())).thenReturn(false);

        service.runScheduledBackup();

        verifyNoInteractions(retentionRunner);
        // SKIPPED, not FAILED and not NULL: retention did not break, and it did not quietly
        // do nothing either - it was deliberately not run, and the audit row must say so.
        verify(statusService).recordFinished(eq(1L), eq(1), eq(1), eq(0),
                eq(0), eq(0), eq(false), eq(RetentionResult.Outcome.SKIPPED),
                eq("backup job lease was lost before the retention step"));
    }
}

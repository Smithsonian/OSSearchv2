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
import static org.mockito.Mockito.doThrow;
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
        config.setMinFreeSpaceMb(0); // disk preflight must never flake this test on a constrained sandbox
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
    }

    private void stubBackupSucceeds(Long id) throws Exception {
        when(backupRestoreService.backupCollection(eq(id), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(new ByteArrayInputStream(new byte[0]));
    }

    @Test
    void backsUpEveryCollectionId() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L, 2L, 3L));
        stubBackupSucceeds(1L);
        stubBackupSucceeds(2L);
        stubBackupSucceeds(3L);

        service.runScheduledBackup();

        // The 4th argument must be `true`: the automatic marker it puts in the backup
        // filename is the only thing that makes retention able to prune the scheduled
        // job's own output (see BackupRetentionPolicy.BACKUP_FILE).
        verify(backupRestoreService).backupCollection(1L, config.isWithCrawlSchedule(), config.isIncludeUsers(), true);
        verify(backupRestoreService).backupCollection(2L, config.isWithCrawlSchedule(), config.isIncludeUsers(), true);
        verify(backupRestoreService).backupCollection(3L, config.isWithCrawlSchedule(), config.isIncludeUsers(), true);
    }

    @Test
    void continuesRemainingCollectionsWhenOneFails() throws Exception {
        when(collectionRepository.findAllCollectionIds()).thenReturn(List.of(1L, 2L, 3L));
        stubBackupSucceeds(1L);
        when(backupRestoreService.backupCollection(eq(2L), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("boom"));
        stubBackupSucceeds(3L);

        service.runScheduledBackup();

        // Collection 1 and 3 must both have been attempted despite collection 2 blowing up
        // in between - a single failure must never short-circuit the loop.
        verify(backupRestoreService).backupCollection(eq(1L), anyBoolean(), anyBoolean(), anyBoolean());
        verify(backupRestoreService).backupCollection(eq(2L), anyBoolean(), anyBoolean(), anyBoolean());
        verify(backupRestoreService).backupCollection(eq(3L), anyBoolean(), anyBoolean(), anyBoolean());

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

        verify(retentionRunner, times(1)).run();
        verify(statusService).recordFinished(
                eq(1L), eq(1), eq(1), eq(0), eq(2), eq(1), eq(true),
                eq(RetentionResult.Outcome.COMPLETED), isNull());
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

        // While the first (and only) collection is being backed up, re-enter the
        // scheduled entry point synchronously - exactly like a second cron trigger
        // firing while the first run is still in progress, but deterministic since
        // everything here runs on the calling thread (no real @Async/@Scheduled
        // proxying happens when the bean is built with `new`).
        when(backupRestoreService.backupCollection(eq(1L), anyBoolean(), anyBoolean(), anyBoolean()))
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
}

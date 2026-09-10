package edu.si.ossearch.utils.backup_restore.service;

import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.lease.BackupJobLeaseService;
import edu.si.ossearch.utils.backup_restore.retention.BackupRetentionRunner;
import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import edu.si.ossearch.utils.backup_restore.status.BackupJobStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nightly job that backs up every collection and then prunes old backups
 * according to the configured retention policy.
 * <p>
 * This job is inert unless {@code ossearch.backup.scheduled.enabled=true}. It
 * must run on exactly one app server in a cluster: that flag is the first line
 * of defense, and the DB-backed {@link BackupJobLeaseService} lease is the
 * authoritative second line of defense in case the flag is (incorrectly) set
 * on more than one host.
 * <p>
 * The method runs on the dedicated {@code backupTaskScheduler} single-thread
 * {@code ThreadPoolTaskScheduler} (see the {@code scheduler} attribute on
 * {@code @Scheduled} below, backed by
 * {@code ScheduledBackupExecutorConfig#backupTaskScheduler()}) rather than the
 * default scheduler thread, so a long-running backup/retention pass cannot
 * starve or delay {@code SchedulerHeartbeatService.beat()} or any other
 * {@code @Scheduled} task sharing the default scheduler. This deterministically
 * moves the job off the shared thread - unlike the {@code @Async} approach this
 * replaced, there is no bean-post-processor registration-order race to lose.
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledCollectionBackupService {

    private final ScheduledBackupConfig config;
    private final BackupJobLeaseService leaseService;
    private final BackupJobStatusService statusService;
    private final BackupRetentionRunner retentionRunner;
    private final CollectionRepository collectionRepository;
    private final BackupRestoreService backupRestoreService;

    /**
     * Not final: assigned via {@code @Value} rather than constructor injection so
     * it can still be overridden directly (package-private setter/field access)
     * by a unit test built with plain {@code new} + Mockito, matching the pattern
     * already used at BackupRestoreServiceImpl.java:65-66.
     */
    @Value(value = "${ossearch.nutch.crawlDir}")
    File crawlDir;

    /**
     * Re-entrancy guard against overlapping runs. See {@link #runScheduledBackup()}
     * for why this is required in addition to the lease and the executor config.
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Scheduled entry point, fired according to the configured cron expression.
     * <p>
     * The {@code ${property:default}} form is REQUIRED here even though
     * {@link ScheduledBackupConfig#getCron()} already has a default of its own:
     * Spring resolves the {@code @Scheduled} cron placeholder at bean-creation
     * time, before any {@code @ConfigurationProperties} binding is consulted, so
     * a bare {@code ${ossearch.backup.scheduled.cron}} with no property present
     * would fail context startup with a placeholder resolution error. The
     * in-annotation default is what actually back-stops a missing property.
     */
    @Scheduled(cron = "${ossearch.backup.scheduled.cron:0 30 3 * * ?}", scheduler = "backupTaskScheduler")
    public void runScheduledBackup() {
        // Confirms, in production logs, which thread actually ran this: it must read
        // "backup-job-*" (the dedicated backupTaskScheduler), never the shared scheduler
        // thread that SchedulerHeartbeatService.beat() and the other @Scheduled jobs rely on.
        log.info("Scheduled collection backup: triggered on thread {}", Thread.currentThread().getName());

        if (!config.isEnabled()) {
            // Silent: this fires nightly on every node, and on most nodes the
            // job is intentionally disabled. Logging here would just be noise.
            return;
        }

        // backupTaskScheduler is a dedicated single-thread ThreadPoolTaskScheduler, so Spring
        // serializes fires of this method the same way it does for any @Scheduled method on
        // the default scheduler: a second cron fire while a run is in progress simply queues
        // behind the first rather than running concurrently or being silently discarded (as
        // the old @Async + queueCapacity(0) + DiscardPolicy executor could do). This guard is
        // therefore no longer a race against a possibly-losing @Async proxy - it is a genuine,
        // reachable re-entrancy check that also documents and logs the skip.
        if (!running.compareAndSet(false, true)) {
            log.warn("Scheduled collection backup: previous run is still in progress, skipping this trigger");
            return;
        }
        try {
            executeRun();
        } finally {
            running.set(false);
        }
    }

    private void executeRun() {
        String hostname = leaseService.resolveHostname();

        // The lease must cover backup AND retention as a single unit, not just
        // retention: two nodes pruning the same NFS backup directories from
        // slightly different point-in-time listings can each individually
        // satisfy the keep-N floor, while their combined deletions defeat it.
        if (!leaseService.tryAcquire(hostname, Duration.ofHours(config.getLease().getDurationHours()))) {
            log.info("Scheduled collection backup: lease held by another node, skipping this run on {}", hostname);
            return;
        }

        log.info("Scheduled collection backup: starting run on {}", hostname);
        Long runId = statusService.recordStart(hostname, Instant.now());
        try {
            // crawlDir.getUsableSpace() returns 0 for a path that does not exist or is not
            // mounted (rather than throwing), which would otherwise misreport "the NFS mount
            // is gone" as ordinary low disk space. Check this FIRST, with its own distinct
            // log message and status reason, and do NOT run retention in this case - there is
            // nothing safely enumerable under a missing/unmounted crawlDir.
            if (!crawlDir.isDirectory()) {
                log.error("Scheduled collection backup: aborting run on {}, crawlDir {} does not exist or is not a directory (NFS mount likely unavailable)",
                        hostname, crawlDir);
                statusService.recordAborted(runId, "CRAWL_DIR_UNAVAILABLE");
                return;
            }

            // Disk preflight: one check for the whole run, since every collection
            // backup writes to the same crawlDir filesystem. This fails fast
            // rather than burning N doomed NFS writes and leaving partial files
            // behind when the volume is already low on space.
            long usableSpaceBytes = crawlDir.getUsableSpace();
            long minFreeSpaceBytes = config.getMinFreeSpaceMb() * 1024L * 1024L;
            if (usableSpaceBytes < minFreeSpaceBytes) {
                log.error("Scheduled collection backup: aborting run on {}, usable space {} bytes is below configured minimum {} bytes",
                        hostname, usableSpaceBytes, minFreeSpaceBytes);
                // Retention MUST still run here even though the backup loop is skipped:
                // pruning old backups is the only thing that can free space on this volume.
                // If we returned without running it, every subsequent nightly run would find
                // the same low-disk condition and abort again forever - a self-sustaining
                // deadlock that never recovers without someone manually intervening.
                // Retention only deletes existing files, so it needs no free space itself.
                RetentionResult retention = retentionRunner.run();
                logIfRetentionFailed(hostname, retention);
                statusService.recordAborted(runId, "LOW_DISK_SPACE",
                        retention.getDeletedCount(), retention.getFailedDeleteCount(), retention.isDryRun(),
                        retention.getOutcome(), retention.getErrorMessage());
                return;
            }

            List<Long> collectionIds = collectionRepository.findAllCollectionIds();
            int total = collectionIds.size();
            int succeeded = 0;
            int failed = 0;
            List<Long> failedIds = new ArrayList<>();

            for (Long id : collectionIds) {
                try (ByteArrayInputStream ignored = backupRestoreService.backupCollection(
                        id, config.isWithCrawlSchedule(), config.isIncludeUsers(), true)) {
                    // The returned stream is discarded: backupCollection() already
                    // wrote the backup file to disk before constructing this
                    // stream, which exists only to serve the HTTP download path.
                    // It is still closed via try-with-resources for hygiene.
                    succeeded++;
                } catch (Exception e) {
                    // One collection failing must never stop the loop - every
                    // other collection still deserves its own backup attempt.
                    failed++;
                    failedIds.add(id);
                    log.error("Scheduled backup failed for collection id {}", id, e);
                }
            }

            log.info("Scheduled collection backup: run on {} finished collections - total={}, succeeded={}, failed={}, failedIds={}",
                    hostname, total, succeeded, failed, failedIds);

            if (total > 0 && succeeded == 0) {
                // Distinguish "every single collection failed" from ordinary
                // per-collection noise: this pattern usually means the NFS mount
                // is gone, unmounted, or has gone read-only, not that N unrelated
                // collections independently broke at the same time.
                log.error("Scheduled collection backup: ALL {} collections failed on {} - this looks like a systemic failure (e.g. crawlDir/NFS mount unavailable or read-only), not per-collection errors",
                        total, hostname);
            }

            RetentionResult retention = retentionRunner.run();
            logIfRetentionFailed(hostname, retention);
            statusService.recordFinished(runId, total, succeeded, failed,
                    retention.getDeletedCount(), retention.getFailedDeleteCount(), retention.isDryRun(),
                    retention.getOutcome(), retention.getErrorMessage());

            log.info("Scheduled collection backup: completed run on {}", hostname);
        } catch (Exception e) {
            log.error("Scheduled collection backup: run on {} failed unexpectedly", hostname, e);
            statusService.recordFailed(runId, e);
        } finally {
            leaseService.release(hostname);
        }
    }

    /**
     * Surfaces a broken retention step in THIS job's own logs. {@link BackupRetentionRunner}
     * swallows the exception by contract (retention must not fail the backup run), so
     * without this the job had no idea retention had broken - and a retention step that
     * silently stopped pruning can quietly fill the backup volume for months.
     * <p>
     * Deliberately does not alter the run's overall status: {@code PARTIAL_FAILURE} keeps
     * meaning "some collections failed". Retention state travels in its own persisted field.
     */
    private void logIfRetentionFailed(String hostname, RetentionResult retention) {
        if (retention.getOutcome() == RetentionResult.Outcome.FAILED) {
            log.error("Scheduled collection backup: retention FAILED on {} - no backups were pruned this run: {}",
                    hostname, retention.getErrorMessage());
        }
    }
}

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
 * {@code @Scheduled} task sharing the default scheduler.
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
     * Re-entrancy guard against overlapping runs. Not the primary defence and not
     * reachable from the cron trigger - see {@link #runScheduledBackup()}.
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
        // Thread name is logged for operator context only. It is deliberately NOT treated
        // as a self-check: "backup-job-*" here proves nothing about isolation, since a
        // mis-wired context in which backupTaskScheduler became the app's ONLY TaskScheduler
        // would print exactly the same thing while every other @Scheduled job also ran on it.
        // ScheduledBackupSchedulerWiringTest is what actually pins the isolation.
        log.debug("Scheduled collection backup: triggered on thread {}", Thread.currentThread().getName());

        if (!config.isEnabled()) {
            // Silent: this fires nightly on every node, and on most nodes the
            // job is intentionally disabled. Logging here would just be noise.
            return;
        }

        // Belt-and-braces only: this guard is UNREACHABLE for the cron trigger. Spring's
        // ReschedulingRunnable computes a cron task's next fire time only after the current
        // execution returns, so a cron @Scheduled method cannot overlap itself no matter how
        // long a run takes - there is no queued second fire waiting behind this one either.
        // The guard is kept because it costs nothing and would catch any future caller that
        // invokes this method directly (as ScheduledCollectionBackupServiceTest does), but do
        // not rely on it as the defence against concurrent runs: the lease is that defence.
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

        // Set to false by the abort paths. An abort decides nothing about the backup
        // window itself - it records that this window has been CLAIMED and could not be
        // served - so the lease is deliberately held until it expires on its own rather
        // than released here. Releasing it immediately (an abort takes milliseconds) means
        // that with `enabled=true` mistakenly set on two nodes and a little cron skew, node
        // B walks straight into the lease this node just dropped and runs the whole job a
        // second time, which is exactly what the mutex exists to prevent.
        boolean releaseLease = true;
        try {
            // crawlDir.getUsableSpace() returns 0 for a path that does not exist or is not
            // mounted (rather than throwing), which would otherwise misreport "the NFS mount
            // is gone" as ordinary low disk space. Check this FIRST, with its own distinct
            // log message and status reason, and do NOT run retention in this case - there is
            // nothing safely enumerable under a missing/unmounted crawlDir.
            if (!crawlDir.isDirectory()) {
                log.error("Scheduled collection backup: aborting run on {}, crawlDir {} does not exist or is not a directory (NFS mount likely unavailable)",
                        hostname, crawlDir);
                statusService.recordAborted(runId, BackupJobStatusService.STATUS_ABORTED_CRAWL_DIR_UNAVAILABLE,
                        "crawlDir " + crawlDir + " does not exist or is not a directory");
                releaseLease = false;
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
                statusService.recordAborted(runId, BackupJobStatusService.STATUS_ABORTED_LOW_DISK,
                        "usable space " + usableSpaceBytes + " bytes is below configured minimum "
                                + minFreeSpaceBytes + " bytes",
                        retention.getDeletedCount(), retention.getFailedDeleteCount(), retention.isDryRun(),
                        retention.getOutcome(), retention.getErrorMessage());
                releaseLease = false;
                return;
            }

            List<Long> collectionIds = collectionRepository.findAllCollectionIds();
            int total = collectionIds.size();
            int succeeded = 0;
            int failed = 0;
            List<Long> failedIds = new ArrayList<>();

            for (Long id : collectionIds) {
                try (ByteArrayInputStream ignored = backupRestoreService.backupCollection(
                        id, config.isWithCrawlSchedule(), config.isIncludeUsers())) {
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
                //
                // Retention is SKIPPED in this case, and the skip is the point: pruning is
                // irreversible, and a night that produced zero new backups has bought
                // nothing to justify deleting 90-day-old ones. Left to run, a read-only
                // remount (or any regression that breaks every export) would quietly eat
                // the backup history one night at a time while every run still reported
                // retention COMPLETED. Note this is the OPPOSITE call to the low-disk abort
                // above, which must still prune: there, pruning is the only thing that can
                // clear the condition, so skipping it would deadlock the job forever. Here
                // nothing is unblocked by deleting, so the safe default is to keep the files.
                log.error("Scheduled collection backup: ALL {} collections failed on {} - this looks like a systemic failure (e.g. crawlDir/NFS mount unavailable or read-only), not per-collection errors; SKIPPING retention so old backups are not pruned on a run that produced none",
                        total, hostname);
                RetentionResult retention = RetentionResult.skipped(
                        "all " + total + " collections failed this run");
                statusService.recordFinished(runId, total, succeeded, failed,
                        retention.getDeletedCount(), retention.getFailedDeleteCount(), retention.isDryRun(),
                        retention.getOutcome(), retention.getErrorMessage());
                return;
            }

            // The lease must still be ours before the one destructive step in this job.
            // A run that outlasts lease.duration-hours has already lost the mutex, and
            // another node may by now be backing up and pruning the same NFS directories;
            // two concurrent prunes each satisfying the keep-newest floor from their own
            // stale listing can delete past it between them. extend() re-proves ownership
            // and pushes the expiry out for the retention pass itself.
            if (!leaseService.extend(hostname, Duration.ofHours(config.getLease().getDurationHours()))) {
                log.error("Scheduled collection backup: lease lost during the run on {} (it outlived lease.duration-hours) - SKIPPING retention; another node may already own this backup window",
                        hostname);
                // recordFinished, not recordAborted: the collection backups genuinely
                // finished and their counts are real. Only the destructive step was
                // skipped, and the SKIPPED retention outcome is what carries that.
                RetentionResult retention = RetentionResult.skipped(
                        "backup job lease was lost before the retention step");
                statusService.recordFinished(runId, total, succeeded, failed,
                        retention.getDeletedCount(), retention.getFailedDeleteCount(), retention.isDryRun(),
                        retention.getOutcome(), retention.getErrorMessage());
                return;
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
            if (releaseLease) {
                // release() is owner-scoped, so this is a no-op if the lease was lost
                // mid-run and has since been taken by another node.
                leaseService.release(hostname);
            }
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

package edu.si.ossearch.utils.backup_restore.status;

import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.entity.BackupJobRunStatus;
import edu.si.ossearch.utils.backup_restore.repository.BackupJobRunStatusRepository;
import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Writes and reads the append-only audit log of scheduled collection backup job runs
 * ({@link BackupJobRunStatus}).
 * <p>
 * Every write method is a no-op on a null {@code runId} and swallows
 * {@link DataAccessException} into a log line: status bookkeeping must never be able to
 * break the backup run itself, and it must never be able to break application startup.
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupJobStatusService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /** A run that has started and not yet reported an outcome. */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * Reported (never persisted) in place of {@link #STATUS_RUNNING} for a run that can
     * no longer possibly be live - see {@link #getLastRun()}.
     */
    public static final String STATUS_STALE = "STALE";

    /**
     * Abort status for the low-disk-space preflight. The exact string is a compatibility
     * constraint, not a preference: historical rows carry it and the UI badge matches on
     * it.
     */
    public static final String STATUS_ABORTED_LOW_DISK = "ABORTED_LOW_DISK";

    /**
     * Abort status for a missing/unmounted {@code crawlDir}. A distinct value from
     * {@link #STATUS_ABORTED_LOW_DISK} because the two need different operator
     * responses: one is "prune the volume", the other is "the NFS mount is gone".
     */
    public static final String STATUS_ABORTED_CRAWL_DIR_UNAVAILABLE = "ABORTED_CRAWL_DIR_UNAVAILABLE";

    private final BackupJobRunStatusRepository runStatusRepository;
    private final ScheduledBackupConfig config;

    /**
     * Records the start of a run and returns its generated id, or {@code null}
     * if the insert failed. Callers must tolerate a {@code null} runId — every
     * other method here is a no-op when given one, since status bookkeeping
     * must never break the backup run itself.
     */
    public Long recordStart(String hostname, Instant startedAt) {
        try {
            BackupJobRunStatus run = new BackupJobRunStatus();
            run.setHostname(hostname);
            run.setStartedAt(Timestamp.from(startedAt));
            run.setStatus(STATUS_RUNNING);
            return runStatusRepository.save(run).getId();
        } catch (DataAccessException e) {
            log.error("failed to record backup job run start for host {}: {}", hostname, e.getMessage(), e);
            return null;
        }
    }

    /**
     * @param retentionOutcome how the retention step ended. Persisted to its OWN
     *                         {@code retention_status} column and deliberately NOT folded
     *                         into {@code status}: {@code PARTIAL_FAILURE} means "some
     *                         collections failed" and nothing else. A run can be
     *                         {@code SUCCESS} with {@code retention_status = 'FAILED'}.
     * @param retentionError   failure detail for a {@code FAILED} outcome, else null
     */
    public void recordFinished(Long runId, int total, int succeeded, int failed, int retentionDeleted,
                                int retentionFailedDelete, boolean retentionDryRun,
                                RetentionResult.Outcome retentionOutcome, String retentionError) {
        update(runId, "finished", run -> {
            run.setStatus(failed == 0 ? "SUCCESS" : "PARTIAL_FAILURE");
            run.setCollectionsTotal(total);
            run.setCollectionsSucceeded(succeeded);
            run.setCollectionsFailed(failed);
            applyRetention(run, retentionDeleted, retentionFailedDelete, retentionDryRun,
                    retentionOutcome, retentionError);
        });
    }

    /**
     * @param abortStatus the status to persist - one of {@link #STATUS_ABORTED_LOW_DISK}
     *                    or {@link #STATUS_ABORTED_CRAWL_DIR_UNAVAILABLE}. A parameter
     *                    rather than a hardcoded literal because both abort paths reach
     *                    this method, and hardcoding one of them made the {@code status}
     *                    column contradict the {@code error_message} beside it.
     */
    public void recordAborted(Long runId, String abortStatus, String reason) {
        update(runId, "aborted", run -> {
            run.setStatus(abortStatus);
            run.setErrorMessage(truncate(reason));
        });
    }

    /**
     * Same as {@link #recordAborted(Long, String, String)} but also records retention
     * counts. Used when the low-disk-space preflight aborts the backup loop but retention
     * still ran (it is the only thing that can free space on the volume for the next run),
     * so the audit row should reflect what retention actually did rather than leaving
     * those columns null.
     */
    public void recordAborted(Long runId, String abortStatus, String reason, int retentionDeleted,
                              int retentionFailedDelete, boolean retentionDryRun,
                              RetentionResult.Outcome retentionOutcome, String retentionError) {
        update(runId, "aborted", run -> {
            run.setStatus(abortStatus);
            run.setErrorMessage(truncate(reason));
            applyRetention(run, retentionDeleted, retentionFailedDelete, retentionDryRun,
                    retentionOutcome, retentionError);
        });
    }

    public void recordFailed(Long runId, Exception e) {
        update(runId, "failed", run -> {
            run.setStatus("FAILED");
            run.setErrorMessage(truncate(e != null ? e.getMessage() : null));
        });
    }

    /**
     * Most recent run, mapped into a {@link LinkedHashMap} with camelCase keys for direct
     * JSON serialization. The key names and their order are a PUBLIC API CONTRACT: the
     * controller serializes this map straight to JSON and the Vue status component reads
     * the fields by name. Renaming or reordering a key is a breaking UI change.
     * <p>
     * Returns {@link Optional#empty()} both when the table has no rows and when the query
     * itself fails, so callers never need to distinguish "no history" from "status lookup
     * broke".
     */
    public Optional<Map<String, Object>> getLastRun() {
        try {
            return runStatusRepository.findFirstByOrderByStartedAtDescIdDesc().map(this::toMap);
        } catch (DataAccessException e) {
            log.error("failed to fetch last backup job run status: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private Map<String, Object> toMap(BackupJobRunStatus run) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hostname", run.getHostname());
        row.put("startedAt", run.getStartedAt());
        row.put("finishedAt", run.getFinishedAt());
        row.put("status", reportedStatus(run));
        row.put("collectionsTotal", run.getCollectionsTotal());
        row.put("collectionsSucceeded", run.getCollectionsSucceeded());
        row.put("collectionsFailed", run.getCollectionsFailed());
        row.put("retentionFilesDeleted", run.getRetentionFilesDeleted());
        row.put("retentionFilesFailedDelete", run.getRetentionFilesFailedDelete());
        row.put("retentionDryRun", run.getRetentionDryRun());
        // Both are NULL for rows written before these columns existed, which
        // serializes as JSON null.
        row.put("retentionStatus", run.getRetentionStatus());
        row.put("retentionError", run.getRetentionError());
        row.put("errorMessage", run.getErrorMessage());
        return row;
    }

    /**
     * A JVM killed mid-run leaves {@code status = 'RUNNING'} with no {@code finished_at}
     * and nothing to ever reconcile it, so the UI would report a run that died months ago
     * as live forever.
     * <p>
     * The staleness threshold is the configured LEASE DURATION rather than a new magic
     * number, and that is the only defensible choice available: the lease is exactly the
     * window during which this run holds the mutex. Once it has elapsed another node is
     * entitled to acquire the lease and start its own run, so a still-{@code RUNNING} row
     * older than that is - by the system's own concurrency contract - no longer the
     * authoritative live run, whether or not the process is still breathing.
     * <p>
     * Reported only, never written back: {@code getLastRun()} is a read path reached from
     * an HTTP GET, and having it issue an UPDATE would make a status query able to
     * corrupt the audit log it is reporting on.
     */
    private String reportedStatus(BackupJobRunStatus run) {
        if (!STATUS_RUNNING.equals(run.getStatus()) || run.getFinishedAt() != null
                || run.getStartedAt() == null) {
            return run.getStatus();
        }
        Duration leaseDuration = Duration.ofHours(config.getLease().getDurationHours());
        Instant staleAfter = run.getStartedAt().toInstant().plus(leaseDuration);
        return Instant.now().isAfter(staleAfter) ? STATUS_STALE : run.getStatus();
    }

    /**
     * Loads the row, applies {@code mutation} and saves it back. Read-modify-write is
     * safe here in a way it is NOT for the lease: each row is written only by the single
     * node that owns that run, one step at a time, so there is no concurrent writer to
     * lose an update to.
     * <p>
     * {@code finished_at} is stamped from the JVM clock rather than
     * {@code CURRENT_TIMESTAMP(3)} so it shares a clock with {@code started_at}, which
     * {@link #recordStart} also writes from Java: the DB session timezone may differ from
     * the JVM's, which would skew computed durations. This is also why there is no
     * {@code @UpdateTimestamp} on the column.
     */
    private void update(Long runId, String what, java.util.function.Consumer<BackupJobRunStatus> mutation) {
        if (runId == null) {
            return;
        }
        try {
            Optional<BackupJobRunStatus> found = runStatusRepository.findById(runId);
            if (found.isEmpty()) {
                log.error("failed to record backup job run {} {}: row not found", runId, what);
                return;
            }
            BackupJobRunStatus run = found.get();
            run.setFinishedAt(Timestamp.from(Instant.now()));
            mutation.accept(run);
            runStatusRepository.save(run);
        } catch (DataAccessException e) {
            log.error("failed to record backup job run {} {}: {}", runId, what, e.getMessage(), e);
        }
    }

    private void applyRetention(BackupJobRunStatus run, int retentionDeleted, int retentionFailedDelete,
                                boolean retentionDryRun, RetentionResult.Outcome retentionOutcome,
                                String retentionError) {
        run.setRetentionFilesDeleted(retentionDeleted);
        run.setRetentionFilesFailedDelete(retentionFailedDelete);
        run.setRetentionDryRun(retentionDryRun);
        run.setRetentionStatus(name(retentionOutcome));
        run.setRetentionError(truncate(retentionError));
    }

    /** Null-safe enum-to-column-value mapping; a null outcome writes SQL NULL. */
    private static String name(RetentionResult.Outcome outcome) {
        return outcome != null ? outcome.name() : null;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }
}

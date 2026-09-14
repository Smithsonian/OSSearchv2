package edu.si.ossearch.utils.backup_restore.retention;

import lombok.Getter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable accumulator for the outcome of a {@link BackupRetentionPolicy#apply()} run.
 * <p>
 * {@code deletedFiles} is bounded to avoid unbounded memory growth on a first run over
 * years of accumulated backups (potentially many thousands of files) - the counters
 * ({@link #getCandidateCount()}, {@link #getDeletedCount()}, {@link #getFailedDeleteCount()})
 * keep counting past the cap even after the list itself stops growing.
 */
@Getter
public class RetentionResult {

    /**
     * Maximum number of file paths retained in {@link #deletedFiles}. Counts are not capped,
     * only the list of paths kept around for logging/inspection.
     */
    private static final int MAX_RETAINED_FILES = 200;

    /**
     * Maximum stored length of {@link #errorMessage}. Matches the width of the
     * {@code retention_error} column written by
     * {@code BackupJobStatusService} - truncation happens here, at the point the
     * message is captured, so an over-long exception message can never make the
     * status UPDATE fail (a failing status write is exactly the silent-failure
     * class this whole {@link Outcome} distinction exists to eliminate).
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /**
     * The three states a retention step can end in. All three leave
     * {@code deleted=0, failedDelete=0} possible, so a {@code boolean ran} flag alone would
     * leave a crashed or deliberately-skipped sweep byte-for-byte indistinguishable from one
     * that genuinely had nothing to prune, making broken retention look like a normal run in
     * the persisted status and in the UI. Each therefore gets its own constant AND its own
     * {@link #summary()} wording.
     * <p>
     * Persisted into the {@code retention_status VARCHAR(32)} column, so a constant name must
     * stay within 32 characters.
     */
    public enum Outcome {
        /** Retention ran to completion (possibly deleting nothing, possibly dry-run). */
        COMPLETED,
        /** Retention started but blew up; {@link #getErrorMessage()} says why. */
        FAILED,
        /**
         * Retention was deliberately not run, so nothing was even examined. Distinct from
         * {@link #FAILED}, which would be a lie: the sweep did not break, the caller chose
         * not to start it (e.g. every collection backup failed, or the cluster lease was
         * lost mid-run and this node no longer owns the volume).
         * {@link #getErrorMessage()} carries the reason.
         */
        SKIPPED
    }

    private final boolean dryRun;
    private int candidateCount;
    private int deletedCount;
    private int failedDeleteCount;
    private final List<String> deletedFiles = new ArrayList<>();
    private final List<String> candidateFiles = new ArrayList<>();

    /**
     * How this retention step ended. Defaults to {@link Outcome#COMPLETED}: an instance
     * built through the public constructor is one the policy is actively accumulating
     * into, and the abnormal ending is reachable only via the {@link #failed(String)}
     * factory.
     */
    private Outcome outcome = Outcome.COMPLETED;

    /**
     * The reason for an abnormal outcome. Non-null only for {@link Outcome#FAILED} (the
     * exception detail) and {@link Outcome#SKIPPED} (why the caller chose not to run).
     * Truncated to 2000 chars.
     */
    private String errorMessage;

    public RetentionResult(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * A backup file identified as beyond the retention floor and eligible for deletion
     * (whether or not it is actually deleted, e.g. in dry-run mode). This is the only record
     * of a candidate in dry-run mode, since {@link #recordDeleted(File)} is never called
     * there - so the path is captured here, bounded by the same {@link #MAX_RETAINED_FILES}
     * cap used for {@link #deletedFiles}. The count keeps incrementing past the cap even
     * after the list itself stops growing.
     */
    public void recordCandidate(File file) {
        candidateCount++;
        if (candidateFiles.size() < MAX_RETAINED_FILES) {
            candidateFiles.add(file.getAbsolutePath());
        }
    }

    public List<String> getCandidateFiles() {
        return Collections.unmodifiableList(candidateFiles);
    }

    /**
     * A candidate file that was successfully deleted (or, in dry-run mode, would have been).
     */
    public void recordDeleted(File file) {
        deletedCount++;
        if (deletedFiles.size() < MAX_RETAINED_FILES) {
            deletedFiles.add(file.getAbsolutePath());
        }
    }

    /**
     * A candidate file whose deletion was attempted but failed (delete() returned false or
     * threw). Never aborts the run.
     */
    public void recordFailedDelete(File file) {
        failedDeleteCount++;
    }

    public List<String> getDeletedFiles() {
        return Collections.unmodifiableList(deletedFiles);
    }

    /**
     * Factory for a result representing a retention step that started and then threw.
     * Deliberately NOT the same shape as a clean, empty {@link Outcome#COMPLETED} result:
     * both leave {@code deleted=0, failedDelete=0}, so without this distinction a
     * retention step that has been broken for months is indistinguishable from one that
     * simply had nothing to prune.
     *
     * @param errorMessage the failure detail, truncated to
     *                     {@value #MAX_ERROR_MESSAGE_LENGTH} chars; may be null (some
     *                     exceptions carry no message)
     */
    public static RetentionResult failed(String errorMessage) {
        RetentionResult result = new RetentionResult(false);
        result.outcome = Outcome.FAILED;
        result.errorMessage = truncate(errorMessage);
        return result;
    }

    /**
     * Factory for a result representing a retention step the caller deliberately did not
     * start - not a failure and not a clean sweep. Mirrors {@link #failed(String)}: the
     * whole point of {@link Outcome} is that "nothing was pruned" must never be ambiguous
     * about WHY, and "we chose not to look" is a third answer that deserves the same
     * treatment as the other two.
     * <p>
     * Callers today: the scheduled backup job, when every collection's backup failed (so
     * pruning against a broken run would be reckless) and when the cluster lease was lost
     * mid-run (so this node no longer owns the volume).
     *
     * @param reason why retention was skipped, truncated to
     *               {@value #MAX_ERROR_MESSAGE_LENGTH} chars; may be null
     */
    public static RetentionResult skipped(String reason) {
        RetentionResult result = new RetentionResult(false);
        result.outcome = Outcome.SKIPPED;
        result.errorMessage = truncate(reason);
        return result;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH
                ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : message;
    }

    /**
     * One-line human-readable summary suitable for logging. Deliberately phrases dry-run and
     * real runs differently ("would delete N" vs "deleted N") rather than a shared
     * "candidates=N, deleted=N" line for both - dry-run's whole purpose is to let an operator
     * see what WOULD be deleted before enabling real deletion, and a summary that looks the
     * same in both modes defeats that at the one place most likely to actually be read.
     */
    public String summary() {
        if (outcome == Outcome.FAILED) {
            return String.format("Backup retention: FAILED - no backups were pruned: %s", errorMessage);
        }
        if (outcome == Outcome.SKIPPED) {
            return String.format("Backup retention: SKIPPED - retention was not run: %s", errorMessage);
        }
        if (dryRun) {
            return String.format("Backup retention (dry-run): would delete %d", candidateCount);
        }
        return String.format(
                "Backup retention: deleted=%d, failed=%d (candidates=%d)",
                deletedCount,
                failedDeleteCount,
                candidateCount);
    }
}

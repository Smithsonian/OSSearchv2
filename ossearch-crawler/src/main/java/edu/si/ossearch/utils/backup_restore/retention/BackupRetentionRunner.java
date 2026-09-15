package edu.si.ossearch.utils.backup_restore.retention;

import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Thin Spring wrapper around {@link BackupRetentionPolicy}. Keeps all the actual pruning
 * logic in a plain-Java, unit-testable class and only handles wiring here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupRetentionRunner {

    private final ScheduledBackupConfig config;

    @Value(value = "${ossearch.nutch.crawlDir}")
    private File crawlDir;

    /**
     * Runs backup retention. Always executes - there is no configuration switch to skip
     * it. Never throws - a retention failure must not fail the overall scheduled backup
     * run.
     *
     * @return a result whose {@link RetentionResult#getOutcome()} distinguishes the two
     *         endings: {@code COMPLETED} (the sweep ran, counters are meaningful) and
     *         {@code FAILED} (the sweep threw; counters are meaningless and nothing was
     *         pruned)
     */
    public RetentionResult run() {
        try {
            BackupRetentionPolicy policy = new BackupRetentionPolicy(
                    crawlDir,
                    config.getRetention().getDays(),
                    config.getRetention().getCount(),
                    false,
                    // logCandidates: the nightly run is the one place an operator wants the
                    // per-file record of what was pruned, and it happens once a day.
                    true);

            RetentionResult result = policy.apply();
            log.info(result.summary());
            return result;
        } catch (Exception e) {
            log.error("Backup retention: run failed unexpectedly", e);
            return RetentionResult.failed(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    /**
     * Read-only "what would be pruned next" computation, for the scheduled backup status
     * endpoint. Never deletes anything.
     * <p>
     * Difference from {@link #run()}: this call logs nothing at INFO - neither the summary
     * here nor, via {@code logCandidates = false}, the policy's per-candidate lines and
     * per-collection summaries. That matters because this is served from an HTTP GET on
     * every admin-UI refresh and those lines carry absolute NFS paths; at DEBUG they are
     * still available to an operator who asks for them.
     *
     * @return the candidate set, or {@code null} meaning "preview unavailable" (the
     *         computation failed). This is deliberately distinct from a result with an
     *         empty candidate list, which means "nothing would be pruned". The caller
     *         distinguishes the two.
     */
    public RetentionResult preview() {
        try {
            BackupRetentionPolicy policy = new BackupRetentionPolicy(
                    crawlDir,
                    config.getRetention().getDays(),
                    config.getRetention().getCount(),
                    // dryRun is a hard-coded literal true, never read from configuration:
                    // preview() is called from an HTTP GET, and a GET must never delete files.
                    true,
                    // logCandidates false - see this method's javadoc.
                    false);

            RetentionResult result = policy.apply();
            log.debug(result.summary());
            return result;
        } catch (Exception e) {
            log.error("Backup retention: preview failed unexpectedly", e);
            return null;
        }
    }
}

package edu.si.ossearch.utils.backup_restore.retention;

import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.service.BackupRestoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the safety contract of {@link BackupRetentionRunner#preview()}: it is reachable
 * from an HTTP GET, so it must never delete a file - unlike {@link BackupRetentionRunner#run()},
 * which always deletes.
 */
class BackupRetentionRunnerTest {

    private static final String COLLECTION_DIR = "americanhistory_1";

    @Test
    void previewNeverDeletesEvenThoughARealRunAlwaysWould(@TempDir File crawlDir) throws IOException {
        ScheduledBackupConfig config = config(90);
        BackupRetentionRunner runner = runner(config, crawlDir);
        List<File> files = createOldAutomaticBackups(crawlDir, 5);

        RetentionResult result = runner.preview();

        assertThat(result).isNotNull();
        assertThat(result.getCandidateCount()).isGreaterThan(0);
        assertThat(result.getDeletedCount()).isZero();
        assertThat(files).allSatisfy(f -> assertThat(f).exists());
    }

    /**
     * The core defect this suite now pins down: a crashed sweep must report FAILED, and
     * must never look identical to a clean, empty COMPLETED sweep - both leave
     * {@code deleted=0, failedDelete=0}, so without a distinct outcome a retention step
     * that has been broken for months would look like one that simply had nothing to do.
     * <p>
     * The throw is forced by leaving {@code crawlDir} unset (null), exactly as it would be
     * if the {@code ossearch.nutch.crawlDir} property were missing: the policy dereferences
     * it at {@code crawlDir.listFiles(File::isDirectory)}, which is the first statement of
     * {@code apply()} and sits OUTSIDE the per-collection {@code try/catch} that otherwise
     * swallows everything, so the NPE genuinely unwinds into {@code run()}'s handler.
     * <p>
     * Note a regular-file crawlDir does NOT work as a trigger here: {@code listFiles()}
     * returns null rather than throwing for a non-directory, and the policy null-checks it
     * and returns an empty COMPLETED result.
     */
    @Test
    void runReportsFailedWhenTheSweepThrows() {
        ScheduledBackupConfig config = config(90);
        BackupRetentionRunner runner = new BackupRetentionRunner(config);
        // crawlDir deliberately left null.

        RetentionResult result = runner.run();

        assertThat(result).isNotNull();
        assertThat(result.getOutcome()).isEqualTo(RetentionResult.Outcome.FAILED);
        assertThat(result.getErrorMessage()).isNotBlank();
        assertThat(result.summary()).contains("FAILED");
        assertThat(result.getDeletedCount()).isZero();
    }

    /**
     * A regular-file crawlDir is the null-listFiles path, not the throwing path: it must
     * still be reported as COMPLETED (nothing to prune), never as FAILED. Documents why the
     * test above uses a null crawlDir instead.
     */
    @Test
    void runReportsCompletedWhenCrawlDirIsNotADirectory(@TempDir File tempDir) throws IOException {
        File notADirectory = new File(tempDir, "crawlDir-is-a-file");
        assertThat(notADirectory.createNewFile()).isTrue();

        ScheduledBackupConfig config = config(90);
        BackupRetentionRunner runner = runner(config, notADirectory);

        RetentionResult result = runner.run();

        assertThat(result.getOutcome()).isEqualTo(RetentionResult.Outcome.COMPLETED);
        assertThat(result.getErrorMessage()).isNull();
    }

    /**
     * A successful sweep must report COMPLETED - the default - so that COMPLETED remains a
     * positive assertion about the run and not merely "not one of the other two".
     */
    @Test
    void runReportsCompletedOnASuccessfulSweep(@TempDir File crawlDir) throws IOException {
        ScheduledBackupConfig config = config(90);
        BackupRetentionRunner runner = runner(config, crawlDir);
        createOldAutomaticBackups(crawlDir, 5);

        RetentionResult result = runner.run();

        assertThat(result.getOutcome()).isEqualTo(RetentionResult.Outcome.COMPLETED);
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void runActuallyDeletesUnlikePreview(@TempDir File crawlDir) throws IOException {
        ScheduledBackupConfig config = config(90);
        BackupRetentionRunner runner = runner(config, crawlDir);
        List<File> files = createOldAutomaticBackups(crawlDir, 5);

        RetentionResult result = runner.run();

        assertThat(result).isNotNull();
        assertThat(result.getDeletedCount()).isGreaterThan(0);
        // Only the single newest backup is kept by the hardcoded floor; the rest are gone.
        assertThat(files.stream().filter(File::exists)).hasSize(1);
    }

    private BackupRetentionRunner runner(ScheduledBackupConfig config, File crawlDir) {
        BackupRetentionRunner runner = new BackupRetentionRunner(config);
        ReflectionTestUtils.setField(runner, "crawlDir", crawlDir);
        return runner;
    }

    private ScheduledBackupConfig config(int days) {
        ScheduledBackupConfig config = new ScheduledBackupConfig();
        config.getRetention().setDays(days);
        return config;
    }

    /**
     * Lays out real files as {@code <crawlDir>/<coll>_<id>/backup/<file>} using the exact
     * automatic-backup filename the writer produces
     * ({@link BackupRestoreService#backupFileName}), dated well past the retention window
     * so they are genuinely surplus. Newest first in the returned list.
     */
    private List<File> createOldAutomaticBackups(File crawlDir, int howMany) throws IOException {
        File backupDir = new File(new File(crawlDir, COLLECTION_DIR), "backup");
        assertThat(backupDir.mkdirs()).isTrue();

        List<File> files = new ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            Date when = Date.from(Instant.now().minus(200L + i, ChronoUnit.DAYS));
            File f = new File(backupDir, BackupRestoreService.backupFileName(COLLECTION_DIR, true, when));
            assertThat(f.createNewFile()).isTrue();
            files.add(f);
        }
        return files;
    }
}

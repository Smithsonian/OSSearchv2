package edu.si.ossearch.utils.backup_restore.retention;

import edu.si.ossearch.utils.backup_restore.service.BackupRestoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit + AssertJ tests for {@link BackupRetentionPolicy}. Deliberately no Spring:
 * this policy is pure Java, so {@code @TempDir} is enough to exercise it against a real
 * filesystem layout.
 */
class BackupRetentionPolicyTest {

    /**
     * Creates an <em>automatic</em> (scheduled) backup file at
     * {@code <crawlDir>/<name>_<id>/backup/<name>_<id>_backup_auto_<ts>.json}, matching the
     * layout written by {@code BackupRestoreServiceImpl#saveLocalBackup} for a scheduled
     * run. Only files carrying the
     * {@link BackupRestoreService#AUTOMATIC_BACKUP_MARKER} segment are visible to
     * retention, so this is the helper every test about pruning behaviour uses.
     */
    private File createBackupFile(File crawlDir, String name, long id, LocalDateTime timestamp) throws IOException {
        return writeBackupFile(crawlDir, name, id, timestamp, true);
    }

    /**
     * Creates a <em>manual</em> (UI-initiated) backup file at
     * {@code <crawlDir>/<name>_<id>/backup/<name>_<id>_backup_<ts>.json} - no
     * {@code auto} marker, and therefore completely invisible to retention: never a
     * deletion candidate and never counted toward the keep-newest-N floor.
     */
    private File createManualBackupFile(File crawlDir, String name, long id, LocalDateTime timestamp)
            throws IOException {
        return writeBackupFile(crawlDir, name, id, timestamp, false);
    }

    private File writeBackupFile(File crawlDir, String name, long id, LocalDateTime timestamp, boolean automatic)
            throws IOException {
        File collectionDir = new File(crawlDir, name + "_" + id);
        File backupDir = new File(collectionDir, "backup");
        if (!backupDir.isDirectory() && !backupDir.mkdirs()) {
            throw new IOException("Failed to create backup dir " + backupDir);
        }
        // The filename comes from the production writer's single source of truth rather than
        // being re-implemented here, so a change to the format cannot silently desynchronize
        // these tests from BackupRetentionPolicy.BACKUP_FILE.
        Date when = Date.from(timestamp.atZone(ZoneId.systemDefault()).toInstant());
        File backupFile = new File(backupDir,
                BackupRestoreService.backupFileName(name + "_" + id, automatic, when));
        Files.writeString(backupFile.toPath(), "{}");
        return backupFile;
    }

    @Test
    void keepsOnlyTheSingleNewestBackupRegardlessOfAge(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File newest = createBackupFile(crawlDir, "coll", 1, now);
        File old0 = createBackupFile(crawlDir, "coll", 1, now.minusDays(140));
        File old1 = createBackupFile(crawlDir, "coll", 1, now.minusDays(280));
        File old2 = createBackupFile(crawlDir, "coll", 1, now.minusDays(420));
        File old3 = createBackupFile(crawlDir, "coll", 1, now.minusDays(560));
        File old4 = createBackupFile(crawlDir, "coll", 1, now.minusDays(700));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(5);
        assertThat(newest).exists();
        assertThat(old0).doesNotExist();
        assertThat(old1).doesNotExist();
        assertThat(old2).doesNotExist();
        assertThat(old3).doesNotExist();
        assertThat(old4).doesNotExist();
    }

    @Test
    void keepsRecentFilesBeyondFloorWhenYoungerThanRetentionDays(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File f0 = createBackupFile(crawlDir, "coll", 1, now);
        File f1 = createBackupFile(crawlDir, "coll", 1, now.minusDays(1));
        File f2 = createBackupFile(crawlDir, "coll", 1, now.minusDays(2));
        File f3 = createBackupFile(crawlDir, "coll", 1, now.minusDays(3));
        File f4 = createBackupFile(crawlDir, "coll", 1, now.minusDays(4));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getDeletedCount()).isZero();
        assertThat(result.getCandidateCount()).isZero();
        assertThat(f0).exists();
        assertThat(f1).exists();
        assertThat(f2).exists();
        assertThat(f3).exists();
        assertThat(f4).exists();
    }

    @Test
    void neverDeletesLastRemainingBackupHoweverOld(@TempDir File crawlDir) throws IOException {
        File onlyBackup = createBackupFile(crawlDir, "coll", 1, LocalDateTime.now().minusYears(2));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getDeletedCount()).isZero();
        assertThat(onlyBackup).exists();
    }

    @Test
    void ignoresFilesNotMatchingTheBackupNamePattern(@TempDir File crawlDir) throws IOException {
        File collectionDir = new File(crawlDir, "coll_1");
        File backupDir = new File(collectionDir, "backup");
        assertThat(backupDir.mkdirs()).isTrue();

        File notes = new File(backupDir, "notes.txt");
        Files.writeString(notes.toPath(), "hello");

        // An otherwise perfectly valid automatic backup name with ".bak" appended: this
        // isolates the extension check specifically, since everything up to ".json" would
        // match on its own.
        File dotBak = new File(backupDir,
                "foo_1_backup_" + BackupRestoreService.AUTOMATIC_BACKUP_MARKER + "_2020-01-01T00-00-00.json.bak");
        Files.writeString(dotBak.toPath(), "{}");

        File randomJson = new File(backupDir, "random.json");
        Files.writeString(randomJson.toPath(), "{}");

        // A directory whose name looks exactly like a valid automatic backup file name -
        // never a candidate, since the policy only considers plain files (f.isFile()).
        File fakeDir = new File(backupDir,
                "coll_1_backup_" + BackupRestoreService.AUTOMATIC_BACKUP_MARKER + "_2019-01-01T00-00-00.json");
        assertThat(fakeDir.mkdirs()).isTrue();

        // A second real backup is required alongside the old one: the floor is always 1,
        // so a single matching backup could never be deleted (see
        // neverDeletesLastRemainingBackupHoweverOld) and this test would be unable to
        // distinguish "decoys correctly ignored" from "nothing was ever eligible for
        // deletion".
        File recentReal = createBackupFile(crawlDir, "coll", 1, LocalDateTime.now());
        File oldReal = createBackupFile(crawlDir, "coll", 1, LocalDateTime.now().minusYears(2));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(notes).exists();
        assertThat(dotBak).exists();
        assertThat(randomJson).exists();
        assertThat(fakeDir).exists();
        assertThat(recentReal).exists();
        assertThat(oldReal).doesNotExist();
    }

    @Test
    void neverTouchesCollectionDirsWithoutABackupSubdirectory(@TempDir File crawlDir) throws IOException {
        File collectionDir = new File(crawlDir, "mycoll_9");
        File crawldb = new File(collectionDir, "crawldb");
        File segments = new File(collectionDir, "segments");
        assertThat(crawldb.mkdirs()).isTrue();
        assertThat(segments.mkdirs()).isTrue();
        File looseFile = new File(collectionDir, "readme.txt");
        Files.writeString(looseFile.toPath(), "live crawl data");

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, false).apply();

        assertThat(result.getDeletedCount()).isZero();
        assertThat(crawldb).exists();
        assertThat(segments).exists();
        assertThat(looseFile).exists();
    }

    @Test
    void dryRunReportsCandidatesWithoutDeleting(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File newest = createBackupFile(crawlDir, "coll", 1, now);
        File old0 = createBackupFile(crawlDir, "coll", 1, now.minusDays(400));
        File old1 = createBackupFile(crawlDir, "coll", 1, now.minusDays(500));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, true).apply();

        assertThat(result.getDeletedCount()).isZero();
        assertThat(result.getCandidateCount()).isGreaterThan(0);
        assertThat(newest).exists();
        assertThat(old0).exists();
        assertThat(old1).exists();
    }

    @Test
    void usesFilenameTimestampNotLastModified(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File recent = createBackupFile(crawlDir, "coll", 1, now);

        File oldByName = createBackupFile(crawlDir, "coll", 1, now.minusYears(2));
        // Touch mtime to "now" - if the policy used lastModified() instead of the
        // filename timestamp, this file would look brand new and survive.
        Files.setLastModifiedTime(oldByName.toPath(), FileTime.from(Instant.now()));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(recent).exists();
        assertThat(oldByName).doesNotExist();
    }

    @Test
    void neverDeletesManualBackupsHoweverOldOrNumerous(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<File> manual = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            manual.add(createManualBackupFile(crawlDir, "coll", 1, now.minusDays(100L + i * 100L)));
        }

        // Aggressive settings - keep only the single newest, expire anything over a day
        // old - which would wipe out 19 of these files if they were automatic.
        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, false).apply();

        assertThat(result.getCandidateCount()).isZero();
        assertThat(result.getDeletedCount()).isZero();
        assertThat(manual).allSatisfy(f -> assertThat(f).exists());
    }

    @Test
    void manualBackupsDoNotCountTowardTheFloor(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        // A single, old automatic backup: with the floor fixed at 1 it is the only entry
        // in the automatic backup list and must survive purely because backups.size() (1)
        // never exceeds the floor - regardless of how many manual files surround it, and
        // regardless of whether some of those manual files are newer than it.
        File automatic = createBackupFile(crawlDir, "coll", 1, now.minusDays(400));
        List<File> manual = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            manual.add(createManualBackupFile(crawlDir, "coll", 1, now.minusDays(i * 10L)));
        }

        // If the manual files were visible to retention as automatic-list entries, the
        // newer ones would push the sole automatic backup out of the newest-1 floor and
        // it would be deleted as expired.
        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getCandidateCount()).isZero();
        assertThat(result.getDeletedCount()).isZero();
        assertThat(automatic).exists();
        assertThat(manual).allSatisfy(f -> assertThat(f).exists());
    }

    @Test
    void deletesAutomaticBackupsWhileKeepingManualOnesInTheSameDirectory(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<File> automatic = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // Newest first: index 0 is the newest automatic backup.
            automatic.add(createBackupFile(crawlDir, "coll", 1, now.minusDays(60L + i * 10L)));
        }
        List<File> manual = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            manual.add(createManualBackupFile(crawlDir, "coll", 1, now.minusDays(65L + i * 10L)));
        }

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(9);

        // Only the single newest automatic file is inside the floor and survives...
        assertThat(automatic.get(0)).exists();
        // ...and the 9 surplus automatic files are gone, by name.
        assertThat(automatic.subList(1, 10)).allSatisfy(f -> assertThat(f).doesNotExist());
        // Every manual file survives, by name, despite being just as old.
        assertThat(manual).allSatisfy(f -> assertThat(f).exists());
    }

    /**
     * Regression guard tying the writer's filename format to the retention pattern. If
     * either drifts, retention stops matching (prunes nothing forever) or starts matching
     * manual backups (deletes files it must never touch), and no other test would notice.
     */
    @Test
    void writerOutputMatchesRetentionPatternAndManualDoesNot() {
        Date when = Date.from(LocalDateTime.of(2024, 5, 17, 3, 4, 5)
                .atZone(ZoneId.systemDefault()).toInstant());
        assertThat(BackupRetentionPolicy.BACKUP_FILE
                .matcher(BackupRestoreService.backupFileName("coll_1", true, when)).matches()).isTrue();
        assertThat(BackupRetentionPolicy.BACKUP_FILE
                .matcher(BackupRestoreService.backupFileName("coll_1", false, when)).matches()).isFalse();
    }

    @Test
    void ignoresShapeValidButUnparseableTimestampAndDoesNotCountItTowardTheFloor(@TempDir File crawlDir)
            throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File recentReal = createBackupFile(crawlDir, "coll", 1, now);
        File oldReal = createBackupFile(crawlDir, "coll", 1, now.minusYears(2));

        // Matches the digit-shape regex but is not a real calendar date/time. Its mtime is
        // set to "now" to mimic the NFS case that made the old lastModified() fallback
        // dangerous: it would have sorted as the newest file, taken the only floor slot, and
        // let the genuine recent backup be deleted instead.
        File garbage = new File(new File(new File(crawlDir, "coll_1"), "backup"),
                "coll_1_backup_" + BackupRestoreService.AUTOMATIC_BACKUP_MARKER + "_2026-13-45T99-99-99.json");
        Files.writeString(garbage.toPath(), "{}");
        Files.setLastModifiedTime(garbage.toPath(), FileTime.from(Instant.now()));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, false).apply();

        // Only the genuinely old backup goes; the malformed file is neither deleted nor
        // allowed to occupy the keep-newest-1 floor.
        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(garbage).exists();
        assertThat(recentReal).exists();
        assertThat(oldReal).doesNotExist();
    }

    @Test
    void emptyCrawlDirIsNoOp(@TempDir File crawlDir) {
        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, false).apply();

        assertThat(result.getDeletedCount()).isZero();
        assertThat(result.getCandidateCount()).isZero();
        assertThat(result.getFailedDeleteCount()).isZero();
        // An empty crawl dir is a run that COMPLETED and had nothing to do - explicitly not
        // the same thing as retention being disabled or having crashed.
        assertThat(result.getOutcome()).isEqualTo(RetentionResult.Outcome.COMPLETED);
    }

    @Test
    void failedResultCarriesTheOutcomeAndMessage() {
        RetentionResult result = RetentionResult.failed("boom");

        assertThat(result.getOutcome()).isEqualTo(RetentionResult.Outcome.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("boom");
        // The summary is the line an operator actually reads in the log, so the failure
        // must be visible there and must carry the cause.
        assertThat(result.summary()).contains("boom");
        assertThat(result.summary()).isNotEqualTo(
                "Backup retention: deleted=0, failed=0 (candidates=0)");
    }

    @Test
    void plainResultDefaultsToCompletedAndKeepsItsExistingSummaryWording() {
        RetentionResult dryRun = new RetentionResult(true);
        assertThat(dryRun.getOutcome()).isEqualTo(RetentionResult.Outcome.COMPLETED);
        assertThat(dryRun.summary()).isEqualTo("Backup retention (dry-run): would delete 0");

        RetentionResult real = new RetentionResult(false);
        assertThat(real.getOutcome()).isEqualTo(RetentionResult.Outcome.COMPLETED);
        assertThat(real.summary()).isEqualTo("Backup retention: deleted=0, failed=0 (candidates=0)");
    }

    @Test
    void failedResultTruncatesAnOverlongMessageToTheColumnWidth() {
        RetentionResult result = RetentionResult.failed("x".repeat(5000));

        // Truncation happens at capture time so an over-long exception message can never
        // make the retention_error VARCHAR(2000) write fail (and then get swallowed).
        assertThat(result.getErrorMessage()).hasSize(2000);
        assertThat(RetentionResult.failed(null).getErrorMessage()).isNull();
    }
}

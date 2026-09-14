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
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain JUnit + AssertJ tests for {@link BackupRetentionPolicy}. Deliberately no Spring:
 * this policy is pure Java, so {@code @TempDir} is enough to exercise it against a real
 * filesystem layout.
 */
class BackupRetentionPolicyTest {

    /**
     * Creates a backup file at
     * {@code <crawlDir>/<name>_<id>/backup/<name>_<id>_backup_<ts>.json}, matching the
     * layout written by {@code BackupRestoreServiceImpl#saveLocalBackup}. Scheduled and
     * manual backups share this one convention, so this is the helper every test about
     * pruning behaviour uses.
     */
    private File createBackupFile(File crawlDir, String name, long id, LocalDateTime timestamp) throws IOException {
        File collectionDir = new File(crawlDir, name + "_" + id);
        File backupDir = new File(collectionDir, "backup");
        if (!backupDir.isDirectory() && !backupDir.mkdirs()) {
            throw new IOException("Failed to create backup dir " + backupDir);
        }
        // The filename comes from the production writer's single source of truth rather than
        // being re-implemented here, so a change to the format cannot silently desynchronize
        // these tests from BackupRetentionPolicy's pattern.
        Date when = Date.from(timestamp.atZone(ZoneId.systemDefault()).toInstant());
        File backupFile = new File(backupDir,
                BackupRestoreService.backupFileName(name + "_" + id, when));
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

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, false).apply();

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

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, false).apply();

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

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, false).apply();

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

        // An otherwise perfectly valid backup name for this collection with ".bak"
        // appended: this isolates the extension check specifically, since everything up to
        // ".json" would match on its own.
        File dotBak = new File(backupDir, "coll_1_backup_2020-01-01T00-00-00.json.bak");
        Files.writeString(dotBak.toPath(), "{}");

        File randomJson = new File(backupDir, "random.json");
        Files.writeString(randomJson.toPath(), "{}");

        // A directory whose name looks exactly like a valid backup file name - never a
        // candidate, since the policy only considers plain files (f.isFile()).
        File fakeDir = new File(backupDir, "coll_1_backup_2019-01-01T00-00-00.json");
        assertThat(fakeDir.mkdirs()).isTrue();

        // A second real backup is required alongside the old one: the floor is always 1,
        // so a single matching backup could never be deleted (see
        // neverDeletesLastRemainingBackupHoweverOld) and this test would be unable to
        // distinguish "decoys correctly ignored" from "nothing was ever eligible for
        // deletion".
        File recentReal = createBackupFile(crawlDir, "coll", 1, LocalDateTime.now());
        File oldReal = createBackupFile(crawlDir, "coll", 1, LocalDateTime.now().minusYears(2));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, 1, false).apply();

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

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, 1, false).apply();

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

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, true).apply();

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

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(recent).exists();
        assertThat(oldByName).doesNotExist();
    }

    /**
     * Regression guard tying the writer's filename format to the retention pattern. If
     * either drifts, retention stops matching and prunes nothing forever, and no other test
     * would notice.
     */
    @Test
    void writerOutputMatchesRetentionPattern() {
        Date when = Date.from(LocalDateTime.of(2024, 5, 17, 3, 4, 5)
                .atZone(ZoneId.systemDefault()).toInstant());
        assertThat(BackupRetentionPolicy.backupFilePattern("coll_1")
                .matcher(BackupRestoreService.backupFileName("coll_1", when)).matches()).isTrue();
    }

    /**
     * The pattern is anchored to the enclosing collection directory's name, so a backup
     * file naming a DIFFERENT collection that happens to sit in this collection's backup
     * dir is not a deletion candidate. Against an unanchored "^.+_backup_<ts>\.json$" the
     * stray file would be pruned along with this collection's own history.
     */
    @Test
    void ignoresBackupFileNamedForADifferentCollection(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File recentOwn = createBackupFile(crawlDir, "coll", 1, now);
        File oldOwn = createBackupFile(crawlDir, "coll", 1, now.minusYears(2));

        // Same directory, same name shape, same age - but it names collection "other_2".
        File foreign = new File(new File(new File(crawlDir, "coll_1"), "backup"),
                "other_2_backup_2019-01-01T00-00-00.json");
        Files.writeString(foreign.toPath(), "{}");

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, 1, false).apply();

        // Exactly one deletion: this collection's own expired backup. The unanchored
        // pattern would report 2 and take the foreign file with it.
        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(foreign).exists();
        assertThat(recentOwn).exists();
        assertThat(oldOwn).doesNotExist();
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
                "coll_1_backup_2026-13-45T99-99-99.json");
        Files.writeString(garbage.toPath(), "{}");
        Files.setLastModifiedTime(garbage.toPath(), FileTime.from(Instant.now()));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 1, 1, false).apply();

        // Only the genuinely old backup goes; the malformed file is neither deleted nor
        // allowed to occupy the keep-newest-1 floor.
        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(garbage).exists();
        assertThat(recentReal).exists();
        assertThat(oldReal).doesNotExist();
    }

    @Test
    void emptyCrawlDirIsNoOp(@TempDir File crawlDir) {
        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, false).apply();

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

    /**
     * The count half of issue #16's "by count and/or age" retention. Every one of these six
     * backups is far older than the 30-day window, so age alone would prune all but one -
     * the count floor is the only thing keeping the other two. Against the previous
     * hardcoded {@code floor = 1} this asserts 3 deletions where the old code performs 5.
     */
    @Test
    void keepsTheNewestCountBackupsRegardlessOfAge(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File n0 = createBackupFile(crawlDir, "coll", 1, now.minusDays(100));
        File n1 = createBackupFile(crawlDir, "coll", 1, now.minusDays(200));
        File n2 = createBackupFile(crawlDir, "coll", 1, now.minusDays(300));
        File old0 = createBackupFile(crawlDir, "coll", 1, now.minusDays(400));
        File old1 = createBackupFile(crawlDir, "coll", 1, now.minusDays(500));
        File old2 = createBackupFile(crawlDir, "coll", 1, now.minusDays(600));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 3, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(3);
        assertThat(n0).exists();
        assertThat(n1).exists();
        assertThat(n2).exists();
        assertThat(old0).doesNotExist();
        assertThat(old1).doesNotExist();
        assertThat(old2).doesNotExist();
    }

    /**
     * Concretely: the reason the count floor matters now. Since the automatic-vs-manual
     * filename distinction was dropped, a human's "before I change something risky" manual
     * backup is an ordinary retention candidate the moment it ages out of the window. With
     * {@code count} at its configured default of 30 it survives anyway.
     */
    @Test
    void countFloorProtectsAnOldBackupThatTheAgeRuleAloneWouldPrune(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File recent = createBackupFile(crawlDir, "coll", 1, now);
        File oldButWithinTheCountFloor = createBackupFile(crawlDir, "coll", 1, now.minusYears(2));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 30, false).apply();

        assertThat(result.getCandidateCount()).isZero();
        assertThat(result.getDeletedCount()).isZero();
        assertThat(recent).exists();
        // The assertion that would fail if the floor regressed to a hardcoded 1: this file
        // is two years past a 30-day window and is kept purely by count.
        assertThat(oldButWithinTheCountFloor).exists();
    }

    /**
     * A count of 0 or less can never reach the policy through configuration ({@code @Min(1)}),
     * but the policy is plain Java and constructible directly, and it deletes files - so the
     * clamp is asserted rather than assumed. Without it the floor would be 0 and this
     * collection's entire history would go.
     */
    @Test
    void countBelowOneIsClampedSoAHistoryIsNeverFullyDeleted(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File newest = createBackupFile(crawlDir, "coll", 1, now.minusYears(1));
        File older = createBackupFile(crawlDir, "coll", 1, now.minusYears(2));

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 0, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(newest).exists();
        assertThat(older).doesNotExist();
    }

    /**
     * The truncated-file trap. A backup killed part-way through a write (crash, lost NFS
     * mount, ENOSPC) leaves a zero-length file whose FILENAME says "now" - and retention
     * ranks on the filename by design. Un-dropped, it sorts newest, takes the floor slot, and
     * every genuinely-good backup behind it becomes an age candidate.
     * <p>
     * Numbers chosen so the two behaviours cannot coincide: the old code deletes 2 (including
     * {@code lastGoodBackup}, the whole point) and keeps only the empty file; the fixed code
     * deletes 1 and keeps the real backup.
     */
    @Test
    void zeroLengthBackupIsDroppedAndCannotOccupyTheKeepNewestFloor(@TempDir File crawlDir) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        File lastGoodBackup = createBackupFile(crawlDir, "coll", 1, now.minusDays(100));
        File expired = createBackupFile(crawlDir, "coll", 1, now.minusDays(200));

        // Same helper, then truncated to nothing - a real, correctly-named backup file of
        // zero bytes, exactly what a partial write leaves behind.
        File truncated = createBackupFile(crawlDir, "coll", 1, now);
        Files.write(truncated.toPath(), new byte[0]);
        assertThat(truncated).isEmpty();

        RetentionResult result = new BackupRetentionPolicy(crawlDir, 30, 1, false).apply();

        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(lastGoodBackup).exists();
        assertThat(expired).doesNotExist();
        // Dropped, not deleted: the policy ignores it entirely rather than pruning it.
        assertThat(truncated).exists();
    }

    @Test
    void skippedResultIsDistinctFromBothCompletedAndFailed() {
        RetentionResult result = RetentionResult.skipped("all collection backups failed");

        assertThat(result.getOutcome()).isEqualTo(RetentionResult.Outcome.SKIPPED);
        assertThat(result.getErrorMessage()).isEqualTo("all collection backups failed");
        assertThat(result.summary()).contains("SKIPPED");
        assertThat(result.summary()).contains("all collection backups failed");
        // The point of the enum: three endings that all leave deleted=0 must not produce the
        // same line. Assert against both of the others explicitly.
        assertThat(result.summary()).isNotEqualTo(
                "Backup retention: deleted=0, failed=0 (candidates=0)");
        assertThat(result.summary()).isNotEqualTo(
                RetentionResult.failed("all collection backups failed").summary());
    }

    @Test
    void skippedResultTruncatesAnOverlongReasonToTheColumnWidth() {
        assertThat(RetentionResult.skipped("x".repeat(5000)).getErrorMessage()).hasSize(2000);
        assertThat(RetentionResult.skipped(null).getErrorMessage()).isNull();
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

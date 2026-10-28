package edu.si.ossearch.utils.backup_restore.service;

import edu.si.ossearch.utils.backup_restore.retention.BackupRetentionPolicy;
import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests the defense-in-depth containment check that {@code saveLocalBackup} performs before
 * writing. Plain JUnit + AssertJ, no Spring: the service is instantiated directly and its
 * package-private {@code crawlDir} field is set to a {@link TempDir}, which is all the check
 * depends on.
 * <p>
 * This exists because bean validation on {@code Collection#name} can be bypassed - a direct DB
 * edit, a data migration, or a write path that skips Hibernate's validation listener - and a name
 * containing {@code ..} would otherwise put a backup outside {@code crawlDir} entirely.
 */
class BackupRestoreServiceImplContainmentTest {

    @TempDir
    Path tempDir;

    private BackupRestoreServiceImpl service;
    private File crawlDir;

    @BeforeEach
    void setUp() throws IOException {
        crawlDir = Files.createDirectories(tempDir.resolve("crawls")).toFile();
        service = new BackupRestoreServiceImpl();
        service.crawlDir = crawlDir;
    }

    @Test
    @DisplayName("accepts a target inside crawlDir")
    void acceptsTargetInsideCrawlDir() {
        File target = new File(crawlDir, "americanhistory_1/backup/americanhistory_1_backup_auto_x.json");
        assertThatCode(() -> service.assertInsideCrawlDir(target)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects a target escaping crawlDir via ..")
    void rejectsTraversalTarget() {
        File target = new File(crawlDir, "../evil_1/backup/evil_1_backup_auto_x.json");
        assertThatThrownBy(() -> service.assertInsideCrawlDir(target))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("refusing to write a backup outside crawlDir");
    }

    @Test
    @DisplayName("rejects a sibling directory that merely shares the crawlDir name prefix")
    void rejectsSiblingWithSharedPrefix() {
        // The trailing File.separator in the containment check is what makes this fail: a naive
        // startsWith("/.../crawls") would accept "/.../crawlsevil".
        File target = new File(crawlDir.getPath() + "evil", "backup/x.json");
        assertThatThrownBy(() -> service.assertInsideCrawlDir(target))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("refusing to write a backup outside crawlDir");
    }

    @Test
    @DisplayName("rejects crawlDir itself as a write target")
    void rejectsCrawlDirItself() {
        assertThatThrownBy(() -> service.assertInsideCrawlDir(crawlDir))
                .isInstanceOf(IOException.class);
    }

    /**
     * End-to-end proof through the real (private) write path. {@code saveLocalBackup} is private
     * and there is no seam to reach it, so it is invoked reflectively rather than being made
     * package-private purely for the test.
     */
    @Test
    @DisplayName("saveLocalBackup refuses a traversal collection directory and writes nothing")
    void saveLocalBackupRefusesTraversal() throws Exception {
        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);

        assertThatThrownBy(() -> saveLocalBackup.invoke(service, "../evil_1", new JSONObject().put("a", "b")))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IOException.class)
                .hasMessageContaining("refusing to write a backup outside crawlDir");

        assertThat(tempDir.resolve("evil_1")).doesNotExist();
    }

    @Test
    @DisplayName("saveLocalBackup still writes a well-formed backup for a legitimate name")
    void saveLocalBackupWritesInsideCrawlDir() throws Exception {
        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);

        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"));

        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        assertThat(backupDir).isDirectory();
        // Exactly one entry, and it is the backup: the ".part" staging sibling the atomic
        // write uses is renamed onto the final name, so a successful write leaves nothing
        // else behind - no residue and no staging subdirectory.
        assertThat(backupDir.listFiles()).hasSize(1);
        assertThat(backupDir.listFiles()[0].getName()).matches("americanhistory_1_backup_.+\\.json");
    }

    /**
     * Finding #7: the write must go through a staging file plus a rename, not straight onto
     * the final path. On NFS a plain {@code Files.write} onto the destination can leave a
     * truncated {@code *_backup_<ts>.json} behind, and retention ranks on the filename, so
     * that partial file sorts newest and pushes the last good backup into the deletable set.
     * <p>
     * "It never writes in place" is an absence, and the deterministic way to assert it is to
     * make the staged path unusable and check that the write then produces NOTHING: a
     * directory is planted where the writer wants its staging file, so the staged write
     * fails before any byte reaches the final name. The old in-place writer does not touch
     * that path at all, so it completes normally and leaves a backup file - which is exactly
     * the assertion below failing.
     * <p>
     * The companion positive case (a normal write really does produce the backup, with no
     * residue) is {@link #saveLocalBackupWritesInsideCrawlDir}; without it this test could be
     * satisfied by a writer that never writes anything.
     */
    @Test
    @DisplayName("saveLocalBackup stages the write and produces no file when staging fails")
    void saveLocalBackupWritesViaAStagingFile() throws Exception {
        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        assertThat(backupDir.mkdirs()).isTrue();

        // The staged name is derived from the target name, which is derived from the clock,
        // so block every possible one: the backup directory itself is made read-only, which
        // fails the staged create and nothing else.
        assertThat(backupDir.setWritable(false)).isTrue();
        try {
            Method saveLocalBackup = BackupRestoreServiceImpl.class
                    .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
            saveLocalBackup.setAccessible(true);

            assertThatThrownBy(() -> saveLocalBackup.invoke(service, "americanhistory_1",
                    new JSONObject().put("a", "b")))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause()
                    .isInstanceOf(IOException.class);

            // Nothing under the final name: not a complete backup, and not a partial one.
            assertThat(backupDir.listFiles()).isEmpty();
        } finally {
            assertThat(backupDir.setWritable(true)).isTrue();
        }
    }

    /**
     * The staged file is a sibling in the backup directory, not a file inside a ".tmp"
     * subdirectory. The subdirectory version had to create and then tear that directory down
     * on every write, which orphaned staged files invisibly on a hard kill and raced a
     * concurrent writer whose directory could be removed between its create and its use.
     * <p>
     * Asserted by planting a plain file named ".tmp" in the backup directory: the staging-
     * directory writer calls {@code Files.createDirectories} on exactly that path and fails,
     * writing no backup at all, so this test fails against it. The sibling writer never looks
     * at the name.
     */
    @Test
    @DisplayName("the write uses no .tmp staging subdirectory")
    void saveLocalBackupUsesNoStagingSubdirectory() throws Exception {
        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        assertThat(backupDir.mkdirs()).isTrue();
        File dotTmp = new File(backupDir, ".tmp");
        Files.writeString(dotTmp.toPath(), "an unrelated file that is not a directory");

        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);
        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"));

        assertThat(backupDir.listFiles()).hasSize(2);
        // The backup landed, and the write created no directory of its own.
        assertThat(backupDir.listFiles(File::isDirectory)).isEmpty();
        assertThat(backupDir.listFiles((d, name) -> name.matches("americanhistory_1_backup_.+\\.json")))
                .hasSize(1);
        assertThat(dotTmp).content().isEqualTo("an unrelated file that is not a directory");
    }

    /**
     * The written backup is {@code rw-r--r--}, as a plain {@code Files.write} under the usual
     * umask produced before the write was staged. {@code Files.createTempFile} - the previous
     * staging mechanism - creates {@code rw-------} regardless of umask on POSIX and
     * {@code ATOMIC_MOVE} carries that mode onto the final name, so every backup written
     * after that change became unreadable to any account other than the service's own. This
     * fails against that version with "0600".
     */
    @Test
    @DisplayName("a written backup is mode 0644")
    void saveLocalBackupWritesAWorldReadableFile() throws Exception {
        assumeTrue(Files.getFileAttributeView(tempDir, PosixFileAttributeView.class) != null,
                "POSIX permissions are not supported on this filesystem");

        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);
        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"));

        File[] written = new File(crawlDir, "americanhistory_1/backup").listFiles();
        assertThat(written).hasSize(1);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(written[0].toPath())))
                .isEqualTo("rw-r--r--");
    }

    /**
     * The atomicity guarantee that matters to retention, asserted on content: the file that
     * appears under the final name is always the complete document. A large payload is used
     * so that an in-place write would need many write syscalls to land it.
     */
    @Test
    @DisplayName("the file under the final name is always the complete document")
    void saveLocalBackupLandsTheCompleteDocument() throws Exception {
        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);

        JSONObject payload = new JSONObject().put("a", "b".repeat(200_000));
        saveLocalBackup.invoke(service, "americanhistory_1", payload);

        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        File[] written = backupDir.listFiles();
        assertThat(written).hasSize(1);
        assertThat(written[0]).content().isEqualTo(payload.toString(4));
    }

    /**
     * A ".part" staged file left behind by a hard kill is inert: neither reader of the backup
     * directory sees it. {@code collectionListBackupsAvailable} used to list every
     * non-directory entry, so it would have offered this half-written file to an admin as a
     * restorable backup - that half of this test fails against that version. Retention
     * ignores it for the same reason both now do: the name does not match
     * {@link edu.si.ossearch.utils.backup_restore.service.BackupRestoreService#backupFilePattern}.
     */
    @Test
    @DisplayName("a leftover .part sibling is neither listed nor a retention candidate")
    void leftoverStagedFileIsNeitherListedNorARetentionCandidate() throws Exception {
        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);
        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"));

        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        File backup = backupDir.listFiles()[0];
        // The residue of a hard kill between "staged" and "renamed": the writer's own staged
        // name for a later write of the same collection.
        File staged = new File(backupDir, "americanhistory_1_backup_2019-01-01T00-00-00.json"
                + BackupRestoreService.STAGED_FILE_SUFFIX);
        Files.writeString(staged.toPath(), "half a backup");

        List<Map<String, String>> listed = service.collectionListBackupsAvailable("americanhistory_1");
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("file")).isEqualTo(backup.getName());

        // Retention with a floor of 1 and a 0-day window: everything it recognises beyond the
        // newest file is a candidate, so a candidate count of 0 is a real statement that the
        // ".part" file was never recognised at all.
        RetentionResult result = new BackupRetentionPolicy(crawlDir, 0, 1, true).apply();
        assertThat(result.getCandidateCount()).isZero();
        assertThat(staged).exists();
    }

    /**
     * The lister offers only this collection's own non-empty backups. A zero-length file is a
     * failed write that can only fail again on restore, and a file naming another collection
     * (or anything else hand-copied into the directory) is not this collection's history.
     * Against the old "every non-directory entry" lister all four decoys below are offered as
     * restorable backups.
     */
    @Test
    @DisplayName("the lister rejects zero-length and foreign-named files")
    void listerRejectsZeroLengthAndForeignFiles() throws Exception {
        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);
        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"));

        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        File real = backupDir.listFiles()[0];

        // Correctly named for this collection, but empty: a truncated or failed write.
        Files.write(new File(backupDir, "americanhistory_1_backup_2020-01-01T00-00-00.json").toPath(),
                new byte[0]);
        // Named for a different collection.
        Files.writeString(new File(backupDir, "other_2_backup_2020-01-01T00-00-00.json").toPath(), "{}");
        // Hand-copied notes, and a backup name with an extra extension.
        Files.writeString(new File(backupDir, "notes.txt").toPath(), "hello");
        Files.writeString(new File(backupDir, "americanhistory_1_backup_2020-01-01T00-00-00.json.bak").toPath(),
                "{}");

        List<Map<String, String>> listed = service.collectionListBackupsAvailable("americanhistory_1");

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("file")).isEqualTo(real.getName());
    }
}

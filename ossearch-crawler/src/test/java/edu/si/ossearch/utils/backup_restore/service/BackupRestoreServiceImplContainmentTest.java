package edu.si.ossearch.utils.backup_restore.service;

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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        // Exactly one entry, and it is the backup: the staging file the atomic write uses is
        // moved into place and its staging directory removed, so nothing else is left behind.
        // A leftover temp would break this deliberately - collectionListBackupsAvailable
        // lists every non-directory entry here and would offer it as a restorable backup.
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
     * make the staged path unusable and check that the write then produces NOTHING: a regular
     * file is planted where the writer wants its staging directory, so
     * {@code Files.createDirectories} fails before any byte is written. The old in-place
     * writer does not touch that path at all, so it completes normally and leaves a backup
     * file - which is exactly the assertion below failing.
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

        // A regular file occupying the staging directory's path.
        Files.writeString(new File(backupDir, stagingDirName()).toPath(), "not a directory");

        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);

        assertThatThrownBy(() -> saveLocalBackup.invoke(service, "americanhistory_1",
                new JSONObject().put("a", "b")))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IOException.class);

        // Nothing under the final name: not a complete backup, and not a partial one either.
        assertThat(backupDir.listFiles()).hasSize(1);
        assertThat(backupDir.listFiles()[0].getName()).isEqualTo(stagingDirName());
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
     * The staging file must be invisible to both readers of the backup directory even while
     * it exists: retention filters by name, but {@code collectionListBackupsAvailable} lists
     * every non-directory entry and would otherwise offer a half-written file to a user as a
     * restorable backup. Staging inside a subdirectory is what satisfies the second one.
     */
    @Test
    @DisplayName("a staged file left behind by a crash is not listed as an available backup")
    void leftoverStagingFileIsNotListedAsAnAvailableBackup() throws Exception {
        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        assertThat(backupDir.mkdirs()).isTrue();

        Method saveLocalBackup = BackupRestoreServiceImpl.class
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class);
        saveLocalBackup.setAccessible(true);
        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"));

        // Simulate the residue of a hard kill between "staged" and "renamed", in whatever
        // location the writer stages into.
        File stagingDir = new File(backupDir, stagingDirName());
        assertThat(stagingDir.mkdirs()).isTrue();
        Files.writeString(new File(stagingDir, "staged.123.part").toPath(), "half a backup");

        List<Map<String, String>> listed = service.collectionListBackupsAvailable("americanhistory_1");

        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).get("file")).matches("americanhistory_1_backup_.+\\.json");
    }

    /** Reads the writer's own staging directory name so this test cannot drift from it. */
    private String stagingDirName() throws Exception {
        java.lang.reflect.Field f = BackupRestoreServiceImpl.class.getDeclaredField("STAGING_DIR_NAME");
        f.setAccessible(true);
        return (String) f.get(null);
    }
}

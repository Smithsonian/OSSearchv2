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
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class, boolean.class);
        saveLocalBackup.setAccessible(true);

        assertThatThrownBy(() -> saveLocalBackup.invoke(service, "../evil_1", new JSONObject().put("a", "b"), true))
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
                .getDeclaredMethod("saveLocalBackup", String.class, JSONObject.class, boolean.class);
        saveLocalBackup.setAccessible(true);

        saveLocalBackup.invoke(service, "americanhistory_1", new JSONObject().put("a", "b"), true);

        File backupDir = new File(crawlDir, "americanhistory_1/backup");
        assertThat(backupDir).isDirectory();
        assertThat(backupDir.listFiles()).hasSize(1);
    }
}

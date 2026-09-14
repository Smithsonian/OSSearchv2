package edu.si.ossearch.utils.backup_restore.service;

import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author jbirkhimer
 */
public interface BackupRestoreService {

    /**
     * Single source of truth for the on-disk backup filename:
     * {@code <collectionDirName>_backup_<yyyy-MM-dd'T'HH-mm-ss>.json}.
     * <p>
     * The per-collection pattern built by
     * {@code BackupRetentionPolicy#backupFilePattern} must match exactly what this
     * produces. Any change to this format is a change to what retention can find and
     * delete, so the two must be kept in step (the test
     * {@code writerOutputMatchesRetentionPattern} guards this).
     */
    static String backupFileName(String collectionDirName, java.util.Date when) {
        // SimpleDateFormat is not thread-safe, so it is created per call rather than hoisted
        // to a static field.
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(when);
        return collectionDirName + "_backup_" + ts + ".json";
    }

    ByteArrayInputStream backupCollection(Long id, boolean withCrawlSchedule, boolean includeUsers) throws Exception;

    List<Map<String, String>> collectionListBackupsAvailable(String collectionDir);

    String localBackup(String collectionDir, String fileName, boolean delete) throws IOException;

    ResponseEntity<?> restoreCollectionAndCrawlSchedule(List<Map<String, String>> files, boolean restoreCollection, boolean restoreCrawlSchedule, boolean restoreUsers);

    ResponseEntity<?> bulkBackupCollection(List<Long> ids, boolean withCrawlSchedule, boolean includeUsers);

    ResponseEntity<?> bulkListAvailableBackupsCollection();

    Map<String, String> getLocalFileData(String file) throws IOException;
}

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
     * Filename marker identifying a backup written by the scheduled backup job rather
     * than by hand from the UI. It is the ONLY thing that makes an automatic backup
     * distinguishable, and the automatic retention sweep deletes only files carrying it.
     */
    String AUTOMATIC_BACKUP_MARKER = "auto";

    /**
     * Single source of truth for the on-disk backup filename:
     * {@code <collectionDirName>_backup_[auto_]<yyyy-MM-dd'T'HH-mm-ss>.json}.
     * <p>
     * {@code BackupRetentionPolicy.BACKUP_FILE} must match exactly what this produces for
     * automatic backups ({@code automatic == true}) and must NOT match what it produces for
     * manual ones - manual backups are exempt from retention. Any change to this format is
     * a change to what retention can find and delete, so the two must be kept in step (the
     * test {@code writerOutputMatchesRetentionPatternAndManualDoesNot} guards this).
     */
    static String backupFileName(String collectionDirName, boolean automatic, java.util.Date when) {
        // SimpleDateFormat is not thread-safe, so it is created per call rather than hoisted
        // to a static field.
        String marker = automatic ? AUTOMATIC_BACKUP_MARKER + "_" : "";
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(when);
        return collectionDirName + "_backup_" + marker + ts + ".json";
    }

    ByteArrayInputStream backupCollection(Long id, boolean withCrawlSchedule, boolean includeUsers, boolean automatic) throws Exception;

    /**
     * Manual (UI-initiated) backup - the written file carries no
     * {@link #AUTOMATIC_BACKUP_MARKER} and is therefore never deleted by retention.
     */
    default ByteArrayInputStream backupCollection(Long id, boolean withCrawlSchedule, boolean includeUsers) throws Exception {
        return backupCollection(id, withCrawlSchedule, includeUsers, false);
    }

    List<Map<String, String>> collectionListBackupsAvailable(String collectionDir);

    String localBackup(String collectionDir, String fileName, boolean delete) throws IOException;

    ResponseEntity<?> restoreCollectionAndCrawlSchedule(List<Map<String, String>> files, boolean restoreCollection, boolean restoreCrawlSchedule, boolean restoreUsers);

    ResponseEntity<?> bulkBackupCollection(List<Long> ids, boolean withCrawlSchedule, boolean includeUsers);

    ResponseEntity<?> bulkListAvailableBackupsCollection();

    Map<String, String> getLocalFileData(String file) throws IOException;
}

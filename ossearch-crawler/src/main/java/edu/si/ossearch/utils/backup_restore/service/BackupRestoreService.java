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
     * The per-collection pattern built by {@link #backupFilePattern} must match exactly what
     * this produces. Any change to this format is a change to what retention can find and
     * delete, and to what an admin is offered for restore, so the two must be kept in step
     * (the test {@code writerOutputMatchesRetentionPattern} guards this).
     */
    static String backupFileName(String collectionDirName, java.util.Date when) {
        // SimpleDateFormat is not thread-safe, so it is created per call rather than hoisted
        // to a static field.
        String ts = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(when);
        return collectionDirName + "_backup_" + ts + ".json";
    }

    /**
     * Suffix of the staging file {@code BackupRestoreServiceImpl} writes before renaming it
     * onto its final name. It is a sibling of the backup in the same directory - that is what
     * guarantees the rename is same-filesystem and therefore atomic - so it must be a name
     * that {@link #backupFilePattern} rejects, which it is: the pattern ends at
     * {@code .json$}.
     */
    String STAGED_FILE_SUFFIX = ".part";

    /**
     * The backup filename pattern for one collection directory, anchored to that directory's
     * own name: under {@code <collectionDirName>/backup/} only
     * {@code <collectionDirName>_backup_<yyyy-MM-dd'T'HH-mm-ss>.json} matches, and group 1 is
     * the timestamp. An unanchored leading {@code .+} would make every file ending
     * {@code _backup_<ts>.json} match no matter which collection it names.
     * <p>
     * The counterpart of {@link #backupFileName}: the writer produces the name, this
     * recognises it. Both readers of a {@code backup} directory use this one pattern -
     * {@code BackupRetentionPolicy}, which deletes what matches, and
     * {@link #collectionListBackupsAvailable}, which offers what matches to an admin for
     * restore - so there is a single definition of what a backup file is. The test
     * {@code writerOutputMatchesRetentionPattern} guards the writer/reader pairing.
     * <p>
     * {@code Pattern.quote} on the directory name: this pattern gates a deletion path and the
     * name is data, derived from {@code Collection.name}. Unquoted, a name containing regex
     * metacharacters would either WIDEN what matches (a '.' becoming a wildcard) or make
     * {@code Pattern.compile} throw. Quoting makes the name always a literal.
     * <p>
     * DOTALL is inert here and kept only as zero-risk defence: with the whole prefix inside
     * {@code \Q...\E} and the timestamp group written as explicit digit classes, the pattern
     * contains no '.' metacharacter for the flag to affect. A collection name containing a
     * line terminator - writable by rows created before {@code @Pattern}/{@code @Size}
     * validation was added to {@code Collection#name} - matches literally either way.
     */
    static java.util.regex.Pattern backupFilePattern(String collectionDirName) {
        return java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(collectionDirName)
                + "_backup_(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})\\.json$",
                java.util.regex.Pattern.DOTALL);
    }

    ByteArrayInputStream backupCollection(Long id, boolean withCrawlSchedule, boolean includeUsers) throws Exception;

    List<Map<String, String>> collectionListBackupsAvailable(String collectionDir);

    String localBackup(String collectionDir, String fileName, boolean delete) throws IOException;

    ResponseEntity<?> restoreCollectionAndCrawlSchedule(List<Map<String, String>> files, boolean restoreCollection, boolean restoreCrawlSchedule, boolean restoreUsers);

    ResponseEntity<?> bulkBackupCollection(List<Long> ids, boolean withCrawlSchedule, boolean includeUsers);

    ResponseEntity<?> bulkListAvailableBackupsCollection();

    Map<String, String> getLocalFileData(String file) throws IOException;
}

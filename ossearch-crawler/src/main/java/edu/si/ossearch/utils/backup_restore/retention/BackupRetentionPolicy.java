package edu.si.ossearch.utils.backup_restore.retention;

import edu.si.ossearch.utils.backup_restore.service.BackupRestoreService;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prunes old collection backup files written by
 * {@code BackupRestoreServiceImpl#saveLocalBackup}.
 * <p>
 * Only <em>automatic</em> (scheduled) backups are ever considered - those whose filename
 * carries the {@link BackupRestoreService#AUTOMATIC_BACKUP_MARKER} segment. Manually
 * created backups are exempt: they are not deletion candidates, and they do not count
 * toward the keep-newest-1 floor either, so the floor protects the single newest automatic
 * backup and every manual backup is kept regardless of age.
 * <p>
 * Deliberately plain Java with no Spring dependencies (no {@code @Component}, no injected
 * beans) so it can be exercised directly in a JUnit test with {@code @TempDir} and zero
 * Spring context. Spring wiring lives in {@link BackupRetentionRunner}.
 * <p>
 * This class deletes files on a shared production NFS volume, so every operation is
 * deliberately conservative:
 * <ul>
 *   <li>It only ever descends into a {@code backup} subdirectory directly under a
 *       top-level entry of {@code crawlDir} - it never touches crawldb/segments/linkdb or
 *       any other live Nutch crawl data sitting alongside {@code backup}.</li>
 *   <li>It never cross-references directory names against the database - a collection
 *       rename can orphan a {@code <oldName>_<id>/backup} directory forever, and that
 *       directory is pruned exactly like any other.</li>
 *   <li>Every {@code listFiles()} result is null-checked - it returns null (not an empty
 *       array) on I/O error, permission problems, or when the path is not a directory.</li>
 * </ul>
 */
@Slf4j
public class BackupRetentionPolicy {

    // Package-private so the test in this package can assert it against the filename the
    // writer actually produces (BackupRestoreService#backupFileName).
    //
    // Pattern.quote on the interpolated marker: this pattern gates a deletion path, and the
    // marker is a plain constant that could be edited to contain regex metacharacters. A
    // marker of "auto.v2" would turn the '.' into a wildcard and WIDEN what gets deleted;
    // "auto+" would make Pattern.compile throw during static init as an
    // ExceptionInInitializerError, which BackupRetentionRunner's catch (Exception) does not
    // catch. Quoting makes the marker always a literal.
    //
    // DOTALL: the leading ".+" is the collection dir name, and Collection.name is not
    // validated anywhere, so a name containing \n or \r is writable. Without DOTALL, '.'
    // excludes line terminators and such a collection's automatic backups would never match
    // and never be pruned. DOTALL only affects '.'; the timestamp group is explicit digits,
    // the ".json" tail is anchored, and matches() still anchors the whole string, so nothing
    // else widens.
    static final Pattern BACKUP_FILE =
            Pattern.compile("^.+_backup_" + Pattern.quote(BackupRestoreService.AUTOMATIC_BACKUP_MARKER)
                    + "_(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})\\.json$", Pattern.DOTALL);
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private final File crawlDir;
    private final int retentionDays;
    // No longer a user-facing configuration option: retention always runs. This now only
    // distinguishes an internal preview() call (true, never deletes) from a real run()
    // call (false, actually deletes).
    private final boolean dryRun;

    public BackupRetentionPolicy(File crawlDir, int retentionDays, boolean dryRun) {
        this.crawlDir = crawlDir;
        this.retentionDays = retentionDays;
        this.dryRun = dryRun;
    }

    public RetentionResult apply() {
        RetentionResult result = new RetentionResult(dryRun);

        File[] collectionDirs = crawlDir.listFiles(File::isDirectory);
        if (collectionDirs == null) {
            log.warn("Backup retention: listFiles() returned null for crawl dir {} " +
                    "(missing, not a directory, or I/O/permission error); skipping this run", crawlDir);
            return result;
        }

        for (File collectionDir : collectionDirs) {
            // One bad collection directory must never abort the sweep for every remaining
            // one. This is reachable today: a large retention.days makes
            // Instant.now().minus(days, ChronoUnit.DAYS) throw ArithmeticException /
            // DateTimeException, which would otherwise unwind out of this loop and skip
            // every collection dir ordered after the bad one for the rest of the night.
            // Mirrors the per-collection isolation the backup loop already has.
            try {
                applyToCollection(collectionDir, result);
            } catch (Exception e) {
                log.warn("Retention failed for backup dir {}, continuing", collectionDir, e);
            }
        }

        return result;
    }

    private void applyToCollection(File collectionDir, RetentionResult result) {
        File backupDir = new File(collectionDir, "backup");

        // Skip WITHOUT listing anything else under collectionDir - this is what keeps
        // live crawl data (crawldb/segments/linkdb) untouched. An orphaned collection dir
        // (left behind by a rename) is handled the same as any other; we never look at the
        // database to decide whether a directory "still belongs" to a collection.
        if (!backupDir.isDirectory()) {
            return;
        }

        File[] entries = backupDir.listFiles();
        if (entries == null) {
            log.warn("Backup retention: listFiles() returned null for backup dir {} " +
                    "(I/O or permission error); skipping this collection", backupDir);
            return;
        }

        List<File> backups = new ArrayList<>();
        for (File f : entries) {
            // Only plain files matching the exact automatic-backup filename pattern are
            // ever candidates - manual backups, stray files, subdirectories, ".bak" files,
            // anything else is left alone.
            if (f.isFile() && BACKUP_FILE.matcher(f.getName()).matches()) {
                // A name-shape match whose timestamp is not a real calendar date/time (e.g.
                // 2026-13-45T99-99-99) is dropped entirely: not a deletion candidate, and
                // not counted toward the keep-newest-1 floor either. Counting it would let a
                // garbage file consume the floor's only slot and displace a genuine backup
                // into the deletable set.
                if (timestampOf(f) == null) {
                    log.warn("Backup retention: ignoring backup file with unparseable timestamp {} " +
                            "(matches the backup name shape but is not a valid date/time)", f.getAbsolutePath());
                    continue;
                }
                backups.add(f);
            }
        }

        if (backups.isEmpty()) {
            return;
        }

        // Newest first, by timestamp parsed from the filename (see timestampOf()).
        backups.sort(Comparator.comparing(this::timestampOf).reversed());

        // The single newest backup is always kept, unconditionally, regardless of age.
        // This is the code's own invariant, not configurable: there is no knob that can
        // ever widen or shrink it.
        int floor = 1;

        if (backups.size() <= floor) {
            return;
        }

        List<File> beyondFloor = backups.subList(floor, backups.size());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int candidatesInCollection = 0;
        int deletedInCollection = 0;
        int failedInCollection = 0;

        for (File f : beyondFloor) {
            boolean expired = timestampOf(f).isBefore(cutoff);
            if (!expired) {
                continue;
            }

            result.recordCandidate(f);
            candidatesInCollection++;

            if (dryRun) {
                // INFO, not DEBUG: this path is only reachable from BackupRetentionRunner's
                // read-only preview() (never from a real run() - see its javadoc), so an
                // "operator can see what WOULD be deleted" is exactly what this line is for.
                // At the effective INFO log level, a DEBUG line here would be invisible,
                // which would silently defeat that purpose - the operator would see nothing.
                log.info("Backup retention: would delete {}", f.getAbsolutePath());
                continue;
            }

            boolean deleted;
            try {
                deleted = f.delete();
            } catch (Exception e) {
                deleted = false;
                log.warn("Backup retention: exception deleting {}: {}", f.getAbsolutePath(), e.toString());
            }

            if (deleted) {
                log.debug("Backup retention: deleted {}", f.getAbsolutePath());
                result.recordDeleted(f);
                deletedInCollection++;
            } else {
                log.warn("Backup retention: failed to delete {}", f.getAbsolutePath());
                result.recordFailedDelete(f);
                failedInCollection++;
            }
        }

        if (candidatesInCollection > 0) {
            log.info("Backup retention: collection dir '{}': candidates={}, deleted={}, failed={}{}",
                    collectionDir.getName(), candidatesInCollection, deletedInCollection, failedInCollection,
                    dryRun ? " (dry-run, nothing actually deleted)" : "");
        }
    }

    /**
     * Parses the backup timestamp out of the filename (regex group 1) rather than using
     * {@link File#lastModified()} as the primary source.
     * <p>
     * The filename's timestamp is assigned once, at write time, and is immutable
     * thereafter. mtime, in contrast, lives on a shared NFS mount written by more than one
     * host and can be disturbed by rsync/copy, metadata refreshes, or clock skew between
     * hosts. Since this class deletes files, an mtime error skews in the destructive
     * direction (a backup could look older than it really is and get pruned early). The
     * filename is therefore treated as the source of truth, and there is deliberately no
     * mtime fallback: if the timestamp does not parse as a real calendar date/time (e.g. an
     * out-of-range value that still matches the digit-shape regex) this returns
     * {@code null} and the caller drops the file from consideration altogether. Falling back
     * to mtime would let a malformed file sort as the newest, consume the keep-newest-1
     * floor's only slot, and displace a genuine backup into the deletable set.
     *
     * @return the write-time instant taken from the filename, or {@code null} if the name
     *         does not match or its timestamp is not a valid date/time
     */
    private Instant timestampOf(File f) {
        Matcher m = BACKUP_FILE.matcher(f.getName());
        if (m.matches()) {
            try {
                return LocalDateTime.parse(m.group(1), TS_FORMAT).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }
}

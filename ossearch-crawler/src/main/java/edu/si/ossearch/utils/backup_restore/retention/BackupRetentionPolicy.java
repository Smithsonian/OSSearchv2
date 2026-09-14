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
 * Every backup in a collection's {@code backup} directory is a retention candidate -
 * scheduled and manually created ones alike, since they share one filename convention and
 * nothing on disk distinguishes them. What protects a collection from losing everything is
 * the keep-newest-N floor: the newest {@code keepNewestCount} backups are always kept,
 * whatever their age, and only files behind that floor that are ALSO older than the
 * retention window are deleted. Both conditions must hold, so the floor is what protects a
 * human's "before I change something risky" manual backup now that manual backups are no
 * longer distinguishable on disk.
 * <p>
 * A candidate must be named for the collection directory that encloses it: under
 * {@code <crawlDir>/<collectionDirName>/backup/} only {@code <collectionDirName>_backup_<ts>.json}
 * matches. A file naming some other collection - copied in by hand, or left by a rename -
 * is therefore left alone rather than being pruned against this collection's history.
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

    /**
     * The backup filename pattern for one collection directory, anchored to that
     * directory's own name: under {@code <collectionDirName>/backup/} only
     * {@code <collectionDirName>_backup_<yyyy-MM-dd'T'HH-mm-ss>.json} is a candidate. An
     * unanchored leading {@code .+} would make every file ending {@code _backup_<ts>.json}
     * deletable no matter which collection it names.
     * <p>
     * Package-private so the test in this package can assert it against the filename the
     * writer actually produces ({@link BackupRestoreService#backupFileName}).
     * <p>
     * {@code Pattern.quote} on the directory name: this pattern gates a deletion path and
     * the name is data, derived from {@code Collection.name}. Unquoted, a name containing
     * regex metacharacters would either WIDEN what gets deleted (a '.' becoming a wildcard)
     * or make {@code Pattern.compile} throw. Quoting makes the name always a literal.
     * <p>
     * DOTALL is inert here and kept only as zero-risk defence: with the whole prefix inside
     * {@code \Q...\E} and the timestamp group written as explicit digit classes, the
     * pattern contains no '.' metacharacter for the flag to affect. A collection name
     * containing a line terminator - writable by rows created before this change added
     * {@code @Pattern}/{@code @Size} validation to {@code Collection#name} - matches
     * literally either way.
     */
    static Pattern backupFilePattern(String collectionDirName) {
        return Pattern.compile("^" + Pattern.quote(collectionDirName)
                + "_backup_(\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2})\\.json$", Pattern.DOTALL);
    }

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

    private final File crawlDir;
    private final int retentionDays;
    /**
     * Keep-newest floor: how many of a collection's most recent backups are kept regardless
     * of age. Bound by {@code ossearch.backup.scheduled.retention.count}, which bean
     * validation already constrains to {@code [1, 10000]}; the {@code Math.max(1, ...)} in
     * the constructor is a second belt on a deletion path, because this class is plain Java
     * and can also be constructed directly (tests, future callers) with no validation in
     * front of it.
     */
    private final int keepNewestCount;
    // No longer a user-facing configuration option: retention always runs. This now only
    // distinguishes an internal preview() call (true, never deletes) from a real run()
    // call (false, actually deletes).
    private final boolean dryRun;
    /**
     * Whether each individual candidate, and the per-collection summary, are logged at INFO.
     * False for {@link BackupRetentionRunner#preview()}, which is reachable from an HTTP GET
     * on every admin-UI refresh: with a backlog that would write hundreds of INFO lines
     * containing absolute NFS paths per page view. Those lines still go out at DEBUG, so
     * nothing is lost when an operator actually wants them.
     */
    private final boolean logCandidates;

    /**
     * Convenience constructor for callers that want candidate logging (the nightly run and
     * the tests). Equivalent to the five-argument form with {@code logCandidates = true}.
     */
    public BackupRetentionPolicy(File crawlDir, int retentionDays, int keepNewestCount, boolean dryRun) {
        this(crawlDir, retentionDays, keepNewestCount, dryRun, true);
    }

    public BackupRetentionPolicy(File crawlDir, int retentionDays, int keepNewestCount,
                                 boolean dryRun, boolean logCandidates) {
        this.crawlDir = crawlDir;
        this.retentionDays = retentionDays;
        this.keepNewestCount = Math.max(1, keepNewestCount);
        this.dryRun = dryRun;
        this.logCandidates = logCandidates;
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
            // one: an unhandled throw here would skip every collection dir ordered after
            // the bad one for the rest of the night. No specific throw is claimed - this is
            // ordinary defensive isolation against filesystem faults on a shared NFS mount
            // (a SecurityException from the security manager, a RuntimeIOException surfacing
            // from a stale mount, a name that somehow breaks Pattern.compile), mirroring the
            // per-collection isolation the backup loop already has.
            //
            // Explicitly NOT justified by arithmetic overflow in the cutoff computation
            // below: retention.days is an int, and Instant.now().minus(Integer.MAX_VALUE,
            // ChronoUnit.DAYS) is about year -5,877,584, comfortably inside Instant's
            // +/-1e9-year range. An earlier revision of this comment asserted otherwise and
            // was simply wrong.
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

        // Anchored to this collection directory's own name, so a backup file naming a
        // different collection that happens to sit here is not a candidate.
        Pattern backupFile = backupFilePattern(collectionDir.getName());

        List<File> backups = new ArrayList<>();
        for (File f : entries) {
            // Only plain files matching this collection's exact backup filename pattern are
            // ever candidates - stray files, subdirectories, ".bak" files, files named for
            // another collection, anything else is left alone.
            if (f.isFile() && backupFile.matcher(f.getName()).matches()) {
                // A name-shape match whose timestamp is not a real calendar date/time (e.g.
                // 2026-13-45T99-99-99) is dropped entirely: not a deletion candidate, and
                // not counted toward the keep-newest floor either. Counting it would let a
                // garbage file consume a floor slot and displace a genuine backup into the
                // deletable set.
                if (timestampOf(f, backupFile) == null) {
                    log.warn("Backup retention: ignoring backup file with unparseable timestamp {} " +
                            "(matches the backup name shape but is not a valid date/time)", f.getAbsolutePath());
                    continue;
                }
                // Same treatment, for the same reason, for a zero-length file. Retention
                // ranks purely on the filename (see timestampOf()), so a backup truncated to
                // nothing by a crash or ENOSPC mid-write would still sort as the newest,
                // occupy a floor slot, and push the last genuinely-good backup into the
                // deletable set. saveLocalBackup now writes via a temp file + atomic move so
                // this should not arise; this is the second half of that fix, covering files
                // already on disk from before it and any write path that bypasses it.
                if (f.length() == 0L) {
                    log.warn("Backup retention: ignoring zero-length backup file {} " +
                            "(truncated or failed write; it must not occupy a keep-newest slot)",
                            f.getAbsolutePath());
                    continue;
                }
                backups.add(f);
            }
        }

        if (backups.isEmpty()) {
            return;
        }

        // Newest first, by timestamp parsed from the filename (see timestampOf()).
        backups.sort(Comparator.comparing((File f) -> timestampOf(f, backupFile)).reversed());

        // The newest `keepNewestCount` backups are always kept, unconditionally, regardless
        // of age; only files behind that floor are even looked at by the age rule below.
        // This is the count half of the "by count and/or age" retention in issue #16, and
        // it is configurable via ossearch.backup.scheduled.retention.count (default 30). It
        // can be widened or narrowed by that knob, but never below 1 (bean validation's
        // @Min(1), plus the Math.max in the constructor), so a collection can never lose its
        // entire backup history to retention.
        int floor = keepNewestCount;

        if (backups.size() <= floor) {
            return;
        }

        List<File> beyondFloor = backups.subList(floor, backups.size());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        int candidatesInCollection = 0;
        int deletedInCollection = 0;
        int failedInCollection = 0;

        for (File f : beyondFloor) {
            boolean expired = timestampOf(f, backupFile).isBefore(cutoff);
            if (!expired) {
                continue;
            }

            result.recordCandidate(f);
            candidatesInCollection++;

            if (dryRun) {
                // Level is chosen by logCandidates, not by dryRun. preview() is served from
                // an HTTP GET on every admin-UI refresh, and this line carries an absolute
                // /data/... NFS path; with a backlog that is hundreds of INFO lines of
                // infrastructure detail per page view.
                if (logCandidates) {
                    log.info("Backup retention: would delete {}", f.getAbsolutePath());
                } else {
                    log.debug("Backup retention: would delete {}", f.getAbsolutePath());
                }
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
            // Also gated on logCandidates: this fires in BOTH modes, so leaving it at INFO
            // would still put one line per collection with a backlog into the log on every
            // preview() (i.e. every admin-UI refresh).
            String suffix = dryRun ? " (dry-run, nothing actually deleted)" : "";
            if (logCandidates) {
                log.info("Backup retention: collection dir '{}': candidates={}, deleted={}, failed={}{}",
                        collectionDir.getName(), candidatesInCollection, deletedInCollection,
                        failedInCollection, suffix);
            } else {
                log.debug("Backup retention: collection dir '{}': candidates={}, deleted={}, failed={}{}",
                        collectionDir.getName(), candidatesInCollection, deletedInCollection,
                        failedInCollection, suffix);
            }
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
     * to mtime would let a malformed file sort as the newest, consume a keep-newest
     * floor slot, and displace a genuine backup into the deletable set.
     *
     * @param f          the backup file
     * @param backupFile  this collection directory's anchored filename pattern, as built by
     *                    {@link #backupFilePattern(String)}
     * @return the write-time instant taken from the filename, or {@code null} if the name
     *         does not match or its timestamp is not a valid date/time
     */
    private Instant timestampOf(File f, Pattern backupFile) {
        Matcher m = backupFile.matcher(f.getName());
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

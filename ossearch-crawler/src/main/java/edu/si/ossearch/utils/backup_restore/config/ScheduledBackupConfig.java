package edu.si.ossearch.utils.backup_restore.config;

import edu.si.ossearch.utils.backup_restore.retention.BackupRetentionPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the scheduled backup feature.
 * <p>
 * {@code @Validated} is not decoration. These properties drive irreversible deletes on a
 * shared NFS volume at 03:30, unattended: {@code retention.days <= 0} puts the age cutoff
 * at or after "now", so every backup past the keep-newest-1 floor would be deleted for
 * every collection on the next run, and {@code lease.duration-hours <= 0} hands out a
 * lease that is already expired, defeating the cluster mutex. A bad value must fail loudly
 * at startup, not quietly at 03:30, so the bounds below are enforced at binding time and
 * abort context refresh.
 */
@Getter
@Setter
@Validated
@Configuration
@ConfigurationProperties(prefix = "ossearch.backup.scheduled")
public class ScheduledBackupConfig {

    /**
     * Enable/disable the scheduled backup job. Should be enabled on exactly
     * ONE app server when running a cluster.
     */
    private boolean enabled = false;

    /**
     * Cron expression for when the scheduled backup job runs (default: 3:30 AM every day)
     */
    @NotBlank
    private String cron = "0 30 3 * * ?";

    /**
     * Whether to include the crawl schedule information in each collection backup
     */
    private boolean withCrawlSchedule = true;

    /**
     * Whether to include user/owner information in each collection backup
     */
    private boolean includeUsers = false;

    /**
     * Minimum free space, in megabytes, required on the crawlDir filesystem for the
     * backup job to run. The run is aborted if free space is below this threshold.
     * Zero disables the preflight; negative is meaningless.
     */
    @Min(0)
    private long minFreeSpaceMb = 500;

    /**
     * Lease settings used to prevent overlapping/duplicate backup runs across a cluster
     */
    @Valid
    @NotNull
    private Lease lease = new Lease();

    /**
     * Retention settings controlling cleanup of old backups
     */
    @Valid
    @NotNull
    private Retention retention = new Retention();

    @Getter
    @Setter
    public static class Lease {
        /**
         * How long, in hours, a backup job lease is held before it is considered stale.
         * Must be at least 1: a zero or negative lease expires the instant it is taken,
         * so every node would consider the mutex free and run concurrently. The upper
         * bound (one week) is a guard against a typo wedging the mutex for a node that
         * has since died - this value also doubles as the RUNNING-row staleness threshold
         * in {@code BackupJobStatusService}.
         */
        @Min(1)
        @Max(168)
        private int durationHours = 6;
    }

    @Getter
    @Setter
    public static class Retention {
        /**
         * Backups older than this many days are always deleted on the next scheduled
         * run, subject to the keep-newest floor. There is no on/off switch - retention
         * always runs (see {@link BackupRetentionPolicy}).
         * <p>
         * Must be at least 1: a zero or negative window puts the cutoff at or after now,
         * which deletes every backup past the keep-newest floor for every collection on
         * the very next run. The 100-year upper bound catches the mirror-image typo: any
         * window longer than the volume's history silently disables pruning altogether
         * while every run still reports retention COMPLETED with deleted=0, so nothing
         * ever surfaces that the backup volume has stopped being cleaned. (Note it is NOT
         * needed to keep {@code Instant#minus} in range: for an int-typed property even
         * {@code Integer.MAX_VALUE} days is ~5.8M years, well inside {@code Instant}'s
         * +/-1e9-year range.)
         */
        @Min(1)
        @Max(36500)
        private int days = 90;

        /**
         * Keep-newest floor: the newest {@code count} backups of a collection are to be
         * kept regardless of age, and only files behind that floor are eligible for
         * age-based deletion. Must be at least 1 - a value of 0 would allow a collection's
         * entire backup history to be deleted.
         * <p>
         * Enforced by {@link BackupRetentionPolicy}, which keeps the newest {@code count}
         * backups of a collection and applies the {@link #days} rule only to the files
         * behind them - the count half of issue #16's "by count and/or age" retention.
         * Manual and scheduled backups are not distinguishable on disk, so this floor is
         * also what keeps an operator's ad-hoc "before I change something risky" backup
         * from aging out.
         */
        @Min(1)
        @Max(10000)
        private int count = 30;
    }
}

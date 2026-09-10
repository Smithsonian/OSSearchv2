package edu.si.ossearch.utils.backup_restore.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the scheduled backup feature
 */
@Getter
@Setter
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
     */
    private long minFreeSpaceMb = 500;

    /**
     * Lease settings used to prevent overlapping/duplicate backup runs across a cluster
     */
    private Lease lease = new Lease();

    /**
     * Retention settings controlling cleanup of old backups
     */
    private Retention retention = new Retention();

    @Getter
    @Setter
    public static class Lease {
        /**
         * How long, in hours, a backup job lease is held before it is considered stale
         */
        private int durationHours = 6;
    }

    @Getter
    @Setter
    public static class Retention {
        /**
         * Automatic backups older than this many days are always deleted on the next
         * scheduled run. There is no on/off switch - retention always runs - and no
         * configurable keep-newest floor beyond the single most-recent automatic backup
         * per collection, which is always protected (see {@link BackupRetentionPolicy}).
         */
        private int days = 90;
    }
}

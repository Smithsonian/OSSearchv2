package edu.si.ossearch.utils.backup_restore.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These properties drive irreversible deletes on a shared NFS volume at 03:30, unattended.
 * A single YAML typo is the whole failure mode, so the contract under test is not "the
 * value is rejected somewhere" but "the application refuses to start" - a run that boots
 * with {@code retention.days: 0} and discovers it eight hours later has already deleted
 * every backup past the keep-newest floor for every collection.
 */
class ScheduledBackupConfigValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProps.class);

    @Test
    void bindsTheDocumentedDefaults() {
        runner.run(context -> {
            ScheduledBackupConfig config = context.getBean(ScheduledBackupConfig.class);
            assertThat(config.getRetention().getDays()).isEqualTo(90);
            assertThat(config.getRetention().getCount()).isEqualTo(30);
            assertThat(config.getLease().getDurationHours()).isEqualTo(6);
        });
    }

    @Test
    void bindsOverriddenValues() {
        runner.withPropertyValues(
                "ossearch.backup.scheduled.retention.days=30",
                "ossearch.backup.scheduled.retention.count=5",
                "ossearch.backup.scheduled.lease.duration-hours=12").run(context -> {
            ScheduledBackupConfig config = context.getBean(ScheduledBackupConfig.class);
            assertThat(config.getRetention().getDays()).isEqualTo(30);
            assertThat(config.getRetention().getCount()).isEqualTo(5);
            assertThat(config.getLease().getDurationHours()).isEqualTo(12);
        });
    }

    /** Cutoff at or after "now" - everything past the keep-newest floor would be deleted. */
    @Test
    void refusesToStartWithANonPositiveRetentionWindow() {
        runner.withPropertyValues("ossearch.backup.scheduled.retention.days=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("ossearch.backup.scheduled.retention.days=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * The mirror-image typo: a window longer than the volume's history silently disables
     * pruning while every run still reports retention COMPLETED with deleted=0.
     */
    @Test
    void refusesToStartWithAnAbsurdlyLargeRetentionWindow() {
        runner.withPropertyValues("ossearch.backup.scheduled.retention.days=999999999")
                .run(context -> assertThat(context).hasFailed());
    }

    /** A zero keep-newest floor would allow a collection's whole history to be deleted. */
    @Test
    void refusesToStartWithANonPositiveRetentionCount() {
        runner.withPropertyValues("ossearch.backup.scheduled.retention.count=0")
                .run(context -> assertThat(context).hasFailed());
    }

    /** A lease that is already expired when granted is not a mutex at all. */
    @Test
    void refusesToStartWithANonPositiveLeaseDuration() {
        runner.withPropertyValues("ossearch.backup.scheduled.lease.duration-hours=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWithNegativeMinFreeSpace() {
        runner.withPropertyValues("ossearch.backup.scheduled.min-free-space-mb=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWithABlankCronExpression() {
        runner.withPropertyValues("ossearch.backup.scheduled.cron=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ScheduledBackupConfig.class)
    static class EnableProps {
    }
}

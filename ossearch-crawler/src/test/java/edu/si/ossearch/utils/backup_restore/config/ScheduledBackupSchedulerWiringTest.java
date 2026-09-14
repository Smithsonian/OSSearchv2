package edu.si.ossearch.utils.backup_restore.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the scheduler wiring in {@link ScheduledBackupExecutorConfig}.
 * <p>
 * The defect this guards is silent and total: declaring only {@code backupTaskScheduler}
 * makes it the sole {@link TaskScheduler} bean in the context, which suppresses Boot's
 * auto-configured {@code taskScheduler} (its {@code @ConditionalOnMissingBean} is on the
 * TYPE) and makes {@code TaskSchedulerRouter} - which also resolves the default by type
 * first - hand the backup job's pool-of-one to EVERY other {@code @Scheduled} method in the
 * application. Nothing fails, no warning is logged, and the backup job still reports
 * running on a {@code backup-job-*} thread; the isolation is simply gone, and
 * {@code SchedulerHeartbeatService.beat()} would stall behind a nightly backup.
 * <p>
 * <b>Scope of the proof.</b> This is not a {@code @SpringBootTest}: this module has no
 * bootable test context (the only DB profile points at a real MySQL on localhost:3309), so
 * the real {@code SchedulerHeartbeatService} bean is not exercised here. What IS exercised
 * is the mechanism the reviewer's concern actually turns on: a real Spring context, real
 * {@code @EnableScheduling}, Boot's real {@code TaskSchedulingAutoConfiguration}, and two
 * real {@code @Scheduled} methods - one plain (standing in for {@code beat()}) and one
 * naming {@code backupTaskScheduler} - whose ACTUAL EXECUTION THREADS are captured and
 * asserted to be different pools. A plain {@code @Scheduled} method resolves its scheduler
 * through exactly the same code path here as {@code beat()} does in production, so the
 * routing is genuinely verified; what is not verified is anything specific to the
 * heartbeat bean itself or to the full application context.
 */
class ScheduledBackupSchedulerWiringTest {

    private static final long AWAIT_SECONDS = 10;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingEnabled.class, ScheduledBackupExecutorConfig.class)
            .withBean(ThreadNameProbe.class);

    @Test
    void plainScheduledMethodsDoNotRunOnTheBackupScheduler() {
        runner.run(context -> {
            ThreadNameProbe probe = context.getBean(ThreadNameProbe.class);

            assertThat(probe.defaultSchedulerLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
                    .as("plain @Scheduled method should have fired").isTrue();
            assertThat(probe.backupSchedulerLatch.await(AWAIT_SECONDS, TimeUnit.SECONDS))
                    .as("backupTaskScheduler-targeted @Scheduled method should have fired").isTrue();

            // The whole point: an ordinary @Scheduled method - which is what
            // SchedulerHeartbeatService.beat() is - must not land on the backup pool.
            assertThat(probe.defaultSchedulerThread)
                    .as("an ordinary @Scheduled method must not run on the backup job's pool-of-one")
                    .doesNotStartWith("backup-job-");
            assertThat(probe.backupSchedulerThread)
                    .as("scheduler = \"backupTaskScheduler\" must still resolve to the dedicated pool")
                    .startsWith("backup-job-");
        });
    }

    /**
     * Guards the fix directly rather than only its effect: if someone deletes the explicit
     * {@code taskScheduler} bean, the context drops to a single TaskScheduler and the
     * by-type default resolution silently reintroduces the defect.
     */
    @Test
    void bothSchedulerBeansArePresentSoTheDefaultResolvesByName() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(TaskScheduler.class)).containsOnlyKeys(
                    "backupTaskScheduler", "taskScheduler");
            assertThat(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class))
                    .isNotSameAs(context.getBean("backupTaskScheduler", ThreadPoolTaskScheduler.class));
        });
    }

    @Test
    void generalPurposeSchedulerHonoursTheSpringTaskSchedulingPoolSizeProperty() {
        runner.withPropertyValues("spring.task.scheduling.pool.size=7").run(context -> {
            // getCorePoolSize(), not getPoolSize(): the latter reports live threads, which a
            // ScheduledThreadPoolExecutor only creates on demand.
            assertThat(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class)
                    .getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(7);
            // The backup pool is fixed at 1 and must not follow that property.
            assertThat(context.getBean("backupTaskScheduler", ThreadPoolTaskScheduler.class)
                    .getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingEnabled {
    }

    /**
     * Two scheduled methods that do nothing but record the thread they were invoked on.
     * {@code fixedDelay} with a zero initial delay fires once immediately; the delay is long
     * enough that no second invocation happens inside the test.
     */
    static class ThreadNameProbe {

        final CountDownLatch defaultSchedulerLatch = new CountDownLatch(1);
        final CountDownLatch backupSchedulerLatch = new CountDownLatch(1);

        volatile String defaultSchedulerThread;
        volatile String backupSchedulerThread;

        /** Stands in for {@code SchedulerHeartbeatService.beat()} and every other plain job. */
        @Scheduled(fixedDelay = 600_000, initialDelay = 0)
        void onDefaultScheduler() {
            defaultSchedulerThread = Thread.currentThread().getName();
            defaultSchedulerLatch.countDown();
        }

        /** Stands in for {@code ScheduledCollectionBackupService.runScheduledBackup()}. */
        @Scheduled(fixedDelay = 600_000, initialDelay = 0, scheduler = "backupTaskScheduler")
        void onBackupScheduler() {
            backupSchedulerThread = Thread.currentThread().getName();
            backupSchedulerLatch.countDown();
        }
    }
}

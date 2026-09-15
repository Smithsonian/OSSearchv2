package edu.si.ossearch.utils.backup_restore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Task schedulers for this application's {@code @Scheduled} methods.
 * <p>
 * The backup job gets its own single-thread scheduler so that a long-running backup
 * cannot delay {@code SchedulerHeartbeatService.beat()} (fixedRate 30s, 90s stale
 * threshold), which would make the UI incorrectly report the Quartz cluster as dead
 * for the duration of the run. {@code ScheduledCollectionBackupService} targets it by
 * name via the {@code scheduler} attribute of {@code @Scheduled} (Spring Framework 6.1+).
 * <p>
 * <b>Both beans below are required.</b> Spring Boot's
 * {@code TaskSchedulingConfigurations$TaskSchedulerConfiguration} auto-configures its
 * {@code taskScheduler} bean under {@code @ConditionalOnMissingBean(TaskScheduler.class)},
 * so declaring {@link #backupTaskScheduler()} alone SUPPRESSES it. With a single
 * {@link TaskScheduler} in the context,
 * {@code TaskSchedulerRouter#determineDefaultScheduler} - which resolves by TYPE first and
 * only falls back to the {@code "taskScheduler"} bean NAME on
 * {@code NoUniqueBeanDefinitionException} - would hand the backup job's pool-of-one to
 * every other {@code @Scheduled} method in the app, achieving the exact opposite of the
 * isolation intended here. Declaring a second {@link TaskScheduler} restores the by-name
 * fallback for the default, while {@code scheduler = "backupTaskScheduler"} still resolves
 * through {@code BeanFactoryAnnotationUtils#qualifiedBeanOfType} (a bean-name match).
 * {@code ScheduledBackupSchedulerWiringTest} pins this behaviour.
 */
@Configuration
public class ScheduledBackupExecutorConfig {

    /**
     * Default pool size for the general-purpose scheduler. Boot's own default is 1, which is
     * too small here: five {@code @Scheduled} methods share this scheduler
     * ({@code SchedulerHeartbeatService#beat}, {@code TokensPurgeTask}, {@code SearchMetaTagServiceTask}
     * and both {@code SearchLogCompressionService} crons), and four of them are daily jobs
     * that can overlap - the two purge/refresh tasks are both configured for 05:00. The
     * heartbeat is the latency-sensitive one, so the pool is sized to leave it a free thread
     * while the other daily jobs run. Overridable with {@code spring.task.scheduling.pool.size}.
     */
    private static final String DEFAULT_POOL_SIZE = "4";

    @Bean(name = "backupTaskScheduler")
    public ThreadPoolTaskScheduler backupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("backup-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * General-purpose scheduler used by every {@code @Scheduled} method that does not name
     * a scheduler. The bean name {@code taskScheduler} is load-bearing: it is the name
     * {@code TaskSchedulerRouter} looks up once two {@link TaskScheduler} beans make the
     * by-type lookup ambiguous.
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:" + DEFAULT_POOL_SIZE + "}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}

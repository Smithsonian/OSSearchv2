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
 * <p>
 * <b>The declared return type of both methods is {@link TaskScheduler}, not the
 * {@code ThreadPoolTaskScheduler} that is actually constructed, and that narrowing is
 * load-bearing.</b> {@code ThreadPoolTaskScheduler} is also a
 * {@link java.util.concurrent.Executor}, and Boot's
 * {@code TaskExecutorConfigurations$TaskExecutorConfiguration} guards its
 * {@code applicationTaskExecutor} bean with {@code @ConditionalOnMissingBean(Executor.class)}.
 * {@code @ConditionalOnMissingBean} resolves a bean's type from the factory method's
 * declared return type, before instantiation, so declaring these beans as
 * {@code ThreadPoolTaskScheduler} would suppress {@code applicationTaskExecutor} exactly the
 * way a lone {@code TaskScheduler} suppresses {@code taskScheduler}. This application uses
 * {@code @EnableAsync} ({@code CrawlUtilsController}, {@code EDANFieldMappingUtilController}),
 * and with no {@code applicationTaskExecutor} (and so no {@code taskExecutor} alias)
 * {@code AsyncExecutionAspectSupport} falls back to a {@code SimpleAsyncTaskExecutor} - a new
 * unbounded thread per {@code @Async} call. Declaring {@link TaskScheduler} keeps these beans
 * invisible to the {@code Executor} condition while still satisfying the {@code TaskScheduler}
 * one.
 * <p>
 * <b>Hand-rolling the {@code taskScheduler} bean bypasses Boot's own scheduler
 * configuration.</b> {@code spring.task.scheduling.thread-name-prefix}, the
 * {@code spring.task.scheduling.shutdown.*} properties, any
 * {@code ThreadPoolTaskSchedulerCustomizer} beans and Boot's virtual-threads scheduler
 * variant have no effect on it; only {@code spring.task.scheduling.pool.size} is honoured,
 * and only because it is read explicitly below. None of those are set in this repository;
 * anyone setting one will have to wire it here by hand.
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
    public TaskScheduler backupTaskScheduler() {
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
    public TaskScheduler taskScheduler(
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

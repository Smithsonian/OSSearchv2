package edu.si.ossearch.utils.backup_restore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Dedicated single-thread {@link ThreadPoolTaskScheduler} for the scheduled backup job.
 * <p>
 * All five existing {@code @Scheduled} methods in this application share Spring's
 * default single-threaded scheduler. A long-running backup on that same thread would
 * starve {@code SchedulerHeartbeatService.beat()} (fixedRate 30s, 90s stale threshold),
 * which would make the UI incorrectly report the scheduler cluster as dead while a
 * backup is running.
 * <p>
 * This job used to rely on {@code @Async("backupExecutor")} (a {@code ThreadPoolTaskExecutor}
 * with {@code queueCapacity(0)} + a {@code DiscardPolicy}) to move itself off the shared
 * scheduler thread, but that had two latent problems:
 * <ol>
 *   <li>Whether the job actually ran off the shared scheduler thread depended on
 *       {@code AsyncAnnotationBeanPostProcessor} having proxied the bean before
 *       {@code ScheduledAnnotationBeanPostProcessor} registered it - both default to
 *       {@code LOWEST_PRECEDENCE}, so it was registration-order-dependent and unasserted.
 *       If it lost that race, the job ran on the shared scheduler thread - exactly the
 *       failure this design exists to prevent.</li>
 *   <li>{@code queueCapacity(0)} + {@code DiscardPolicy} meant a second cron fire while a
 *       run was in progress was discarded at submission, so the method body never ran and
 *       the re-entrancy guard's {@code log.warn} was dead code - an overlap produced ZERO
 *       log output.</li>
 * </ol>
 * Spring Framework 6.1+ supports naming a scheduler directly on {@code @Scheduled} via its
 * {@code scheduler} attribute, so {@code runScheduledBackup()} now targets this bean
 * ({@code scheduler = "backupTaskScheduler"}) instead of using {@code @Async}. A dedicated
 * single-thread {@code ThreadPoolTaskScheduler} both keeps the job off the shared scheduler
 * thread deterministically (no proxy-ordering race) and serializes overlapping fires through
 * Spring's normal {@code @Scheduled} re-entrancy behavior, so the guard in
 * {@code ScheduledCollectionBackupService} is now genuinely reachable and its warning always
 * logs on an overlap.
 */
@Configuration
public class ScheduledBackupExecutorConfig {

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
}

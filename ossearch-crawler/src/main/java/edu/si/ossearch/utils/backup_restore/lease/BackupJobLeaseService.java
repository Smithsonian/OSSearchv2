package edu.si.ossearch.utils.backup_restore.lease;

import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.repository.BackupJobLeaseRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/**
 * DB-backed mutual-exclusion lease used to guarantee that the scheduled
 * collection backup job runs on at most one app server at a time, even when
 * the {@code ossearch.backup.scheduled.enabled} config flag is (incorrectly)
 * set to true on more than one host. This matters because the backup job's
 * retention step DELETES files on a shared NFS volume: double-execution
 * would mean two nodes concurrently pruning/writing the same backup
 * directories.
 * <p>
 * The table itself is a JPA entity ({@code BackupJobLease}) created by
 * {@code ddl-auto: update} like every other table in the application; the atomicity
 * argument lives in {@link BackupJobLeaseRepository}, which is where the actual
 * statements are.
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupJobLeaseService {

    private static final String LOCK_NAME = "scheduled_collection_backup";

    private final BackupJobLeaseRepository leaseRepository;
    private final ScheduledBackupConfig config;

    /**
     * Seeds the single mutex row, and ONLY on a node where the scheduled backup job is
     * actually enabled - a node that will never take the lease has no reason to write
     * to the table on every boot.
     * <p>
     * The bean itself is deliberately NOT {@code @ConditionalOnProperty}: it is injected
     * as a mandatory dependency by {@code ScheduledCollectionBackupService} (a final
     * constructor field), so making the bean conditional would abort context startup on
     * every node with the feature off. Conditioning the startup WORK gets the same
     * saving without that.
     * <p>
     * Never throws. Status/lease bookkeeping must not be able to break application
     * startup; a failure here is logged at ERROR and {@link #tryAcquire} will then fail
     * closed (0 rows updated, no lease, no run) rather than run unprotected.
     */
    @PostConstruct
    void seedLeaseRow() {
        if (!config.isEnabled()) {
            return;
        }
        try {
            leaseRepository.seed(LOCK_NAME);
        } catch (DataAccessException e) {
            log.error("failed to seed backup job lease row {}: {}", LOCK_NAME, e.getMessage(), e);
        }
    }

    /**
     * Attempts to acquire the lease for {@code owner} for {@code leaseDuration}.
     *
     * @return true only if this node now provably holds the lease
     */
    public boolean tryAcquire(String owner, Duration leaseDuration) {
        try {
            return leaseRepository.acquire(owner, leaseDuration.getSeconds(), LOCK_NAME) == 1;
        } catch (DataAccessException e) {
            // Fail safe: if we can't prove we hold the lease, never run.
            log.error("backup job lease acquisition failed for owner {}: {}", owner, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Pushes the lease expiry out by a further {@code leaseDuration} for a lease this
     * node still holds. Callers must re-check this before any destructive step (the
     * retention delete pass) of a long run: a run that outlives its lease has already
     * lost the mutex, and continuing to delete files on the shared NFS volume would race
     * whichever node has since taken over.
     *
     * @return true if the lease was still held and has been extended; false if it was
     *         lost (expired, taken by another node, or the UPDATE failed), in which case
     *         the caller must stop.
     */
    public boolean extend(String owner, Duration leaseDuration) {
        try {
            return leaseRepository.extend(owner, leaseDuration.getSeconds(), LOCK_NAME) == 1;
        } catch (DataAccessException e) {
            // Same fail-safe rule as tryAcquire: unproven means not held.
            log.error("backup job lease extension failed for owner {}: {}", owner, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Releases the lease, but only if it is currently held by {@code owner}.
     * The owner scoping means a node can never release someone else's lease
     * (e.g. a slow/late release from a previous attempt that has since expired
     * and been re-acquired by another node).
     */
    public void release(String owner) {
        try {
            leaseRepository.release(owner, LOCK_NAME);
        } catch (DataAccessException e) {
            log.error("backup job lease release failed for owner {}: {}", owner, e.getMessage(), e);
        }
    }

    /**
     * Same hostname-resolution logic as {@code SchedulerHeartbeatService}, exposed
     * here so callers that need a lease owner identity don't have to duplicate it.
     */
    public String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}

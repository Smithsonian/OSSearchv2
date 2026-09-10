package edu.si.ossearch.utils.backup_restore.lease;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
public class BackupJobLeaseService {

    private static final String LOCK_NAME = "scheduled_collection_backup";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    void createTableIfMissing() {
        // CREATE TABLE IF NOT EXISTS instead of a JPA entity so behavior does
        // not depend on the hibernate ddl-auto setting of the environment.
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS backup_job_lease (" +
                "lock_name VARCHAR(64) NOT NULL PRIMARY KEY, " +
                "owner VARCHAR(255) NULL, " +
                "expires_at TIMESTAMP(3) NULL)");
        jdbcTemplate.update("INSERT IGNORE INTO backup_job_lease (lock_name) VALUES (?)", LOCK_NAME);
    }

    /**
     * Attempts to acquire the lease for {@code owner} for {@code leaseDuration}.
     * <p>
     * This is deliberately an atomic conditional UPDATE against the single
     * primary-key row, NOT a read-then-write upsert. Two concurrent UPDATEs
     * against the same primary-key row serialize on the InnoDB row lock: one
     * transaction wins and commits first, and the loser's UPDATE only then
     * re-evaluates the WHERE clause (against the now-committed row) — by
     * which point {@code owner} is no longer NULL and {@code expires_at} is
     * in the future, so the WHERE clause matches nothing and the loser's
     * UPDATE affects 0 rows. This is what makes double-execution across two
     * app servers impossible even if the {@code enabled} config flag is
     * wrongly set on both hosts.
     */
    public boolean tryAcquire(String owner, Duration leaseDuration) {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE backup_job_lease " +
                    "   SET owner = ?, expires_at = CURRENT_TIMESTAMP(3) + INTERVAL ? SECOND " +
                    " WHERE lock_name = ? " +
                    "   AND (owner IS NULL OR expires_at < CURRENT_TIMESTAMP(3))",
                    owner, leaseDuration.getSeconds(), LOCK_NAME);
            return updated == 1;
        } catch (DataAccessException e) {
            // Fail safe: if we can't prove we hold the lease, never run.
            log.error("backup job lease acquisition failed for owner {}: {}", owner, e.getMessage(), e);
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
            jdbcTemplate.update(
                    "UPDATE backup_job_lease SET owner = NULL, expires_at = NULL WHERE lock_name = ? AND owner = ?",
                    LOCK_NAME, owner);
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

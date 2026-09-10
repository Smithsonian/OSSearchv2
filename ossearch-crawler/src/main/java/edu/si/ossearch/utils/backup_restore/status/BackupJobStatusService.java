package edu.si.ossearch.utils.backup_restore.status;

import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Append-only audit log of scheduled collection backup job runs. Deliberately
 * a SEPARATE table from {@code backup_job_lease}: the lease is a mutex whose
 * {@code expires_at} churns on every acquire/release attempt and only ever
 * holds one row's worth of current state, while this table accumulates one
 * row per run so the history of past runs (and their outcomes) is preserved.
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
public class BackupJobStatusService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private static final String TABLE = "backup_job_run_status";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    void createTableIfMissing() {
        // CREATE TABLE IF NOT EXISTS instead of a JPA entity so behavior does
        // not depend on the hibernate ddl-auto setting of the environment.
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
                "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                "hostname VARCHAR(255) NOT NULL, " +
                "started_at TIMESTAMP(3) NOT NULL, " +
                "finished_at TIMESTAMP(3) NULL, " +
                "status VARCHAR(32) NOT NULL, " +
                "collections_total INT NULL, " +
                "collections_succeeded INT NULL, " +
                "collections_failed INT NULL, " +
                "retention_files_deleted INT NULL, " +
                "retention_files_failed_delete INT NULL, " +
                "retention_dry_run BOOLEAN NULL, " +
                "retention_status VARCHAR(32) NULL, " +
                "retention_error VARCHAR(2000) NULL, " +
                "error_message TEXT NULL)");

        // The CREATE above only shapes a FRESH install. Every environment where this table
        // already exists (with rows in it) would otherwise be left without the two retention
        // status columns, the UPDATEs below would fail with "unknown column", the existing
        // catch (DataAccessException) would swallow it, and status recording would silently
        // stop - reintroducing exactly the silent-failure class these columns exist to fix.
        addColumnIfMissing("retention_status", "VARCHAR(32) NULL");
        addColumnIfMissing("retention_error", "VARCHAR(2000) NULL");
    }

    /**
     * Idempotent, additive column migration. MySQL 8 has no
     * {@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS} (that is MariaDB), so existence is
     * probed through {@code information_schema} first and the ALTER is issued only when the
     * column is genuinely absent - making this safe to run on every single startup.
     * <p>
     * Never throws: like every other method in this class, status bookkeeping must never be
     * able to break application startup. A failure here is logged at ERROR and startup
     * continues (the run-status writes will then log their own failures too, which is the
     * loudest this class is allowed to be).
     * <p>
     * {@code columnName} and {@code definition} are compile-time constants supplied by
     * {@link #createTableIfMissing()}; no user-controlled value is ever interpolated into
     * DDL here.
     */
    private void addColumnIfMissing(String columnName, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                    " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, TABLE, columnName);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + columnName + " " + definition);
            log.info("added column {}.{} to bring an existing table up to date", TABLE, columnName);
        } catch (DataAccessException e) {
            log.error("failed to add column {}.{}; retention run status may not be recorded: {}",
                    TABLE, columnName, e.getMessage(), e);
        }
    }

    /**
     * Records the start of a run and returns its generated id, or {@code null}
     * if the insert failed. Callers must tolerate a {@code null} runId — every
     * other method here is a no-op when given one, since status bookkeeping
     * must never break the backup run itself.
     */
    public Long recordStart(String hostname, Instant startedAt) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO backup_job_run_status (hostname, started_at, status) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, hostname);
                ps.setTimestamp(2, Timestamp.from(startedAt));
                ps.setString(3, "RUNNING");
                return ps;
            }, keyHolder);
            Number key = keyHolder.getKey();
            return key != null ? key.longValue() : null;
        } catch (DataAccessException e) {
            log.error("failed to record backup job run start for host {}: {}", hostname, e.getMessage(), e);
            return null;
        }
    }

    /**
     * @param retentionOutcome how the retention step ended. Persisted to its OWN
     *                         {@code retention_status} column and deliberately NOT folded
     *                         into {@code status}: {@code PARTIAL_FAILURE} means "some
     *                         collections failed" and nothing else. A run can be
     *                         {@code SUCCESS} with {@code retention_status = 'FAILED'}.
     * @param retentionError   failure detail for a {@code FAILED} outcome, else null
     */
    public void recordFinished(Long runId, int total, int succeeded, int failed, int retentionDeleted,
                                int retentionFailedDelete, boolean retentionDryRun,
                                RetentionResult.Outcome retentionOutcome, String retentionError) {
        if (runId == null) {
            return;
        }
        try {
            String status = failed == 0 ? "SUCCESS" : "PARTIAL_FAILURE";
            // finished_at is bound from the JVM clock rather than CURRENT_TIMESTAMP(3) so it
            // shares a clock with started_at, which recordStart also writes from Java: the DB
            // session timezone may differ from the JVM's, which would skew computed durations.
            jdbcTemplate.update(
                    "UPDATE backup_job_run_status " +
                    "   SET finished_at = ?, status = ?, collections_total = ?, " +
                    "       collections_succeeded = ?, collections_failed = ?, retention_files_deleted = ?, " +
                    "       retention_files_failed_delete = ?, retention_dry_run = ?, " +
                    "       retention_status = ?, retention_error = ? " +
                    " WHERE id = ?",
                    Timestamp.from(Instant.now()), status, total, succeeded, failed, retentionDeleted,
                    retentionFailedDelete, retentionDryRun, name(retentionOutcome), truncate(retentionError), runId);
        } catch (DataAccessException e) {
            log.error("failed to record backup job run {} finished: {}", runId, e.getMessage(), e);
        }
    }

    public void recordAborted(Long runId, String reason) {
        if (runId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE backup_job_run_status SET finished_at = ?, status = ?, error_message = ? WHERE id = ?",
                    Timestamp.from(Instant.now()), "ABORTED_LOW_DISK", truncate(reason), runId);
        } catch (DataAccessException e) {
            log.error("failed to record backup job run {} aborted: {}", runId, e.getMessage(), e);
        }
    }

    /**
     * Same as {@link #recordAborted(Long, String)} but also records retention counts. Used
     * when the low-disk-space preflight aborts the backup loop but retention still ran (it
     * is the only thing that can free space on the volume for the next run), so the audit
     * row should reflect what retention actually did rather than leaving those columns null.
     */
    public void recordAborted(Long runId, String reason, int retentionDeleted, int retentionFailedDelete,
                              boolean retentionDryRun, RetentionResult.Outcome retentionOutcome,
                              String retentionError) {
        if (runId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE backup_job_run_status " +
                    "   SET finished_at = ?, status = ?, error_message = ?, " +
                    "       retention_files_deleted = ?, retention_files_failed_delete = ?, retention_dry_run = ?, " +
                    "       retention_status = ?, retention_error = ? " +
                    " WHERE id = ?",
                    Timestamp.from(Instant.now()), "ABORTED_LOW_DISK", truncate(reason), retentionDeleted,
                    retentionFailedDelete, retentionDryRun, name(retentionOutcome), truncate(retentionError), runId);
        } catch (DataAccessException e) {
            log.error("failed to record backup job run {} aborted: {}", runId, e.getMessage(), e);
        }
    }

    public void recordFailed(Long runId, Exception e) {
        if (runId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "UPDATE backup_job_run_status SET finished_at = ?, status = ?, error_message = ? WHERE id = ?",
                    Timestamp.from(Instant.now()), "FAILED", truncate(e != null ? e.getMessage() : null), runId);
        } catch (DataAccessException ex) {
            log.error("failed to record backup job run {} failed: {}", runId, ex.getMessage(), ex);
        }
    }

    /**
     * Most recent run, mapped into a {@link LinkedHashMap} with camelCase keys
     * for direct JSON serialization. Returns {@link Optional#empty()} both
     * when the table has no rows and when the query itself fails, so callers
     * never need to distinguish "no history" from "status lookup broke".
     */
    public Optional<Map<String, Object>> getLastRun() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT hostname, started_at, finished_at, status, collections_total, collections_succeeded, " +
                    "       collections_failed, retention_files_deleted, retention_files_failed_delete, " +
                    "       retention_dry_run, retention_status, retention_error, error_message " +
                    "  FROM backup_job_run_status " +
                    " ORDER BY started_at DESC, id DESC LIMIT 1",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("hostname", rs.getString("hostname"));
                        row.put("startedAt", rs.getTimestamp("started_at"));
                        row.put("finishedAt", rs.getTimestamp("finished_at"));
                        row.put("status", rs.getString("status"));
                        row.put("collectionsTotal", (Integer) rs.getObject("collections_total"));
                        row.put("collectionsSucceeded", (Integer) rs.getObject("collections_succeeded"));
                        row.put("collectionsFailed", (Integer) rs.getObject("collections_failed"));
                        row.put("retentionFilesDeleted", (Integer) rs.getObject("retention_files_deleted"));
                        row.put("retentionFilesFailedDelete", (Integer) rs.getObject("retention_files_failed_delete"));
                        row.put("retentionDryRun", (Boolean) rs.getObject("retention_dry_run"));
                        // Both are NULL for rows written before these columns existed;
                        // getString() yields null for those, which serializes as JSON null.
                        row.put("retentionStatus", rs.getString("retention_status"));
                        row.put("retentionError", rs.getString("retention_error"));
                        row.put("errorMessage", rs.getString("error_message"));
                        return row;
                    });
            return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
        } catch (DataAccessException e) {
            log.error("failed to fetch last backup job run status: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** Null-safe enum-to-column-value mapping; a null outcome writes SQL NULL. */
    private static String name(RetentionResult.Outcome outcome) {
        return outcome != null ? outcome.name() : null;
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_MESSAGE_LENGTH ? message.substring(0, MAX_ERROR_MESSAGE_LENGTH) : message;
    }
}

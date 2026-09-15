package edu.si.ossearch.utils.backup_restore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Append-only audit log of scheduled collection backup job runs. Deliberately a
 * SEPARATE table from {@code backup_job_lease}: the lease is a mutex whose
 * {@code expires_at} churns on every acquire/release attempt and only ever holds one
 * row's worth of current state, while this table accumulates one row per run so the
 * history of past runs (and their outcomes) is preserved.
 * <p>
 * Every nullable numeric/boolean column is a BOXED type on purpose. A run that
 * aborted before the backup loop ran never has a collection count at all, and
 * {@code 0}/{@code false} would be a lie the UI cannot distinguish from a real
 * zero-collection run; {@code null} round-trips as JSON {@code null}.
 * <p>
 * Column types are pinned explicitly rather than left to Hibernate's defaults.
 * {@code ddl-auto: update} only ever ADDS missing tables/columns - it never alters an
 * existing column - so an unpinned type would silently give fresh installs a different
 * schema from every database that already has this table.
 *
 * @author jbirkhimer
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "backup_job_run_status")
public class BackupJobRunStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "hostname", nullable = false, length = 255)
    private String hostname;

    /**
     * Written from the JVM clock (not {@code CURRENT_TIMESTAMP(3)}) so it shares a
     * clock with {@code finished_at}: the DB session timezone may differ from the
     * JVM's, which would skew any duration computed from the pair.
     */
    @Column(name = "started_at", nullable = false, columnDefinition = "TIMESTAMP(3) NOT NULL")
    private Timestamp startedAt;

    /** Same JVM clock as {@code started_at}; null while the run is still in progress. */
    @Column(name = "finished_at", columnDefinition = "TIMESTAMP(3) NULL")
    private Timestamp finishedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "collections_total")
    private Integer collectionsTotal;

    @Column(name = "collections_succeeded")
    private Integer collectionsSucceeded;

    @Column(name = "collections_failed")
    private Integer collectionsFailed;

    @Column(name = "retention_files_deleted")
    private Integer retentionFilesDeleted;

    @Column(name = "retention_files_failed_delete")
    private Integer retentionFilesFailedDelete;

    @Column(name = "retention_dry_run")
    private Boolean retentionDryRun;

    /**
     * How the retention step ended. Its OWN column, deliberately NOT folded into
     * {@code status}: {@code PARTIAL_FAILURE} there means "some collections failed"
     * and nothing else. A run can be {@code SUCCESS} with {@code retention_status =
     * 'FAILED'}. Null on rows written before this column existed.
     */
    @Column(name = "retention_status", length = 32)
    private String retentionStatus;

    /** Failure detail for a {@code FAILED} retention outcome, else null. */
    @Column(name = "retention_error", length = 2000)
    private String retentionError;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}

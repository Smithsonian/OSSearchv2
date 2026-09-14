package edu.si.ossearch.utils.backup_restore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Single-row DB-backed mutex guaranteeing that the scheduled collection backup job
 * runs on at most one app server at a time.
 * <p>
 * This entity exists to define the TABLE. It is never loaded, mutated and saved:
 * every state change goes through an atomic conditional UPDATE in
 * {@code BackupJobLeaseRepository}, because a read-modify-write through the
 * persistence context would reintroduce exactly the check-then-act race the mutex
 * exists to prevent.
 *
 * @author jbirkhimer
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "backup_job_lease")
public class BackupJobLease implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "lock_name", nullable = false, length = 64)
    private String lockName;

    /** Null when the lease is free. */
    @Column(name = "owner", length = 255)
    private String owner;

    /**
     * Set from the DATABASE clock, never the JVM's: two app servers with skewed
     * clocks must agree on when a lease expires or the mutex is not a mutex.
     */
    @Column(name = "expires_at", columnDefinition = "TIMESTAMP(3) NULL")
    private Timestamp expiresAt;
}

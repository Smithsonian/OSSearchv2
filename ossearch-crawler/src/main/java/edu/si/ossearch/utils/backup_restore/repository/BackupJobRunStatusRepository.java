package edu.si.ossearch.utils.backup_restore.repository;

import edu.si.ossearch.utils.backup_restore.entity.BackupJobRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code exported = false} is load bearing, not tidiness. Spring Data REST
 * auto-exposes every public repository interface it finds under {@code /api}, and
 * {@code WebSecurityConfig} guards {@code /api/**} with only {@code .authenticated()} -
 * so a default-exported repository would let any logged-in user read (and PATCH) the
 * backup audit log, whose {@code error_message} can contain raw exception text.
 * <p>
 * Intentionally SpEL-free: every method here is reached from the {@code @Scheduled}
 * backup thread, which runs with an EMPTY SecurityContext, so an auth predicate like
 * {@code ?#{authentication.name}} or {@code hasRole(...)} would fail or silently return
 * nothing. Same constraint documented on
 * {@code CollectionRepository#findAllCollectionIds()}.
 *
 * @author jbirkhimer
 */
@Repository
@RepositoryRestResource(exported = false)
public interface BackupJobRunStatusRepository extends JpaRepository<BackupJobRunStatus, Long> {

    /**
     * Most recent run. Ordered by {@code startedAt} first with {@code id} as the
     * tie-breaker, because {@code TIMESTAMP(3)} is coarse enough for two runs to share
     * a value and the auto-increment id is the only total order available.
     */
    Optional<BackupJobRunStatus> findFirstByOrderByStartedAtDescIdDesc();
}

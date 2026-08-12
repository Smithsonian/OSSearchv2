package edu.si.ossearch.scheduler.repository;

import edu.si.ossearch.scheduler.entity.CrawlSchedulerJobInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.Optional;

/**
 * @author jbirkhimer
 */

@Tag(name = "CrawlSchedulerJobInfo", description = "CrawlSchedulerJobInfoRepository")
//@Repository
@RepositoryRestResource(collectionResourceRel = "crawlschedulerjobinfo", path = "crawlschedulerjobinfo")
@SecurityRequirement(name = "bearerAuth")
public interface CrawlSchedulerJobInfoRepository extends JpaRepository<CrawlSchedulerJobInfo, Long> {

    @Override
//    @Query("select s from CrawlSchedulerJobInfo s join Collection c where s.collectionName = c.name and c.owner.username = ?#{authentication.name} or u.username = ?#{authentication.name}")
    @Query("select s from CrawlSchedulerJobInfo s join Collection c on c.name = s.collectionName join c.users cu on cu.username = ?#{authentication.name} or c.owner.username = ?#{authentication.name}")
    Page<CrawlSchedulerJobInfo> findAll(Pageable pageable);

    //Page<CrawlSchedulerJobInfo> findByCollectionName(String collectionName, Pageable pageable);

    List<CrawlSchedulerJobInfo> findByCollectionName(String collectionName);

    CrawlSchedulerJobInfo findByJobName(String jobName);

    CrawlSchedulerJobInfo findByJobNameAndJobGroup(String jobName, String jobGroup);

    @Operation(summary = "Find CrawlSchedulerJobInfo id by jobName and jobGroup")
    @Query("select j.id from CrawlSchedulerJobInfo j where j.jobName = :jobName and j.jobGroup = :jobGroup")
    Optional<Long> findIdByJobNameAndJobGroup(String jobName, String jobGroup);
}

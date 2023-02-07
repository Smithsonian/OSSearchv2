package edu.si.ossearch.scheduler.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.si.ossearch.scheduler.entity.CrawlSchedulerJobInfo.JobType;
import edu.si.ossearch.scheduler.entity.converter.JobTypeConverter;
import edu.si.ossearch.scheduler.entity.converter.StateConverter;
import edu.si.ossearch.scheduler.entity.converter.StepTypeConverter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static edu.si.ossearch.scheduler.entity.CrawlSchedulerJobInfo.JobType.SCHEDULED_CRAWL;

@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = CrawlLog.TABLE_NAME)
public class CrawlLog implements Serializable {
    private static final long serialVersionUID = 5446187926121781866L;
    public static final String TABLE_NAME = "crawl_log";

    public enum StepType {
        INJECT, SITEMAP, HOSTDB_UPDATE, GENERATE, FETCH, PARSE, CRAWLDB_UPDATE, LINKDB_UPDATE, DEDUP, INDEX, WEBGRAPH, REINDEX, RECRAWL, READDB, SITEMAPS_FROM_HOSTDB, CLEANUP, INITIALIZE, REPORTS
    }

    public enum State {
        IDLE, RUNNING, FINISHED, FAILED, STOPPED, KILLED, STOPPING, KILLING, ANY
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String jobKey;
    private String jobId;

    //@JsonSerialize(converter = JobTypeConverter.class)
    @Enumerated(EnumType.STRING)
    private JobType jobType = SCHEDULED_CRAWL;

    @JsonSerialize(converter = StateConverter.class)
    @Enumerated(EnumType.STRING)
    private State state;

    @Column(columnDefinition = "TEXT")
    private String crawlConfig;

    @Column(columnDefinition = "TEXT")
    private String jobConfig;

    private int rounds;
    private int currentRound;

    @JsonSerialize(converter = StepTypeConverter.class)
    @Enumerated(EnumType.STRING)
    private StepType currentStep;

    @OneToMany(mappedBy = "crawlLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private Set<CrawlStepLog> crawlStepLogs = new HashSet<>();

    public void setCrawlStepLogs(Set<CrawlStepLog> crawlStepLogs) {
        this.crawlStepLogs = crawlStepLogs;
        for (CrawlStepLog crawlStepLog : crawlStepLogs) {
            crawlStepLog.setCrawlLog(this);
        }
    }

    @Column(columnDefinition = "TEXT")
    private String dbStats;

    private long solrCount;

    @Column(columnDefinition = "TEXT")
    private String errors;

    @CreationTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdDate;

    @UpdateTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedDate;

}

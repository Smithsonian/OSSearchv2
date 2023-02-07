package edu.si.ossearch.scheduler.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ToString//(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "step_args")
public class NutchStepArgs implements Serializable {

    private static final long serialVersionUID = 8229085083041585797L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_inject_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "inject_option_name")
    private Map<String, String> inject = new HashMap<>();


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_sitemap_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "sitemap_option_name")
    private Map<String, String> sitemap = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_generate_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "generate_option_name")
    private Map<String, String> generate = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_parse_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "parse_option_name")
    private Map<String, String> parse = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_updatedb_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "updatedb_option_name")
    private Map<String, String> updatedb = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_updatehostdb_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "updatehostdb_option_name")
    private Map<String, String> updatehostdb = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_invertlinks_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "invertlinks_option_name")
    private Map<String, String> invertlinks = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_dedup_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "dedup_option_name")
    private Map<String, String> dedup = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_fetch_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "fetch_option_name")
    @Column(name = "_fetch")
    private Map<String, String> fetch = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_index_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "index_option_name")
    @Column(name = "_index")
    private Map<String, String> index = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "step_args_webgraph_mapping",
            joinColumns = {@JoinColumn(name = "step_args_id", referencedColumnName = "id")})
    @MapKeyColumn(name = "webgraph_option_name")
    @Column(name = "_webgraph")
    private Map<String, String> webgraph = new HashMap<>();

    @CreationTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdDate;

    @UpdateTimestamp
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedDate;
}

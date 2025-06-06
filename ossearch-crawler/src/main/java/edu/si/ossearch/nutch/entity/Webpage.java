package edu.si.ossearch.nutch.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
//@ToString//(onlyExplicitlyIncluded = true)
@Entity
@IdClass(WebpagePK.class)
@Table(name = "webpage")
public class Webpage implements Serializable {

    private static final long serialVersionUID = -6698654913702498445L;

//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long id;

    @Id
    private String id;

    @Id
    private Integer collectionId;

    @Column(columnDefinition = "TEXT", length = 4096)
    private String url;
    private Integer statusCode;
    private String statusName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date fetchTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifiedTime;
    private Integer retriesSinceFetch;
    private Integer retryIntervalSeconds;
    private Integer retryIntervalDays;
    private Float score;
    private String signature;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    /*@ElementCollection(fetch = FetchType.EAGER)
//    @CollectionTable(name = "webpage_metadata_mapping",
//            joinColumns = {@JoinColumn(name = "webpage_url", referencedColumnName = "url")})
//    @MapKeyColumn(name = "metaData_key")
//    @Column(name = "metaData_value")
    private Map<String, String> metadata = new HashMap<>();*/

//    @ElementCollection(fetch = FetchType.EAGER)
//    @CollectionTable(name = "crawl_datum_metadata_mapping",
//            joinColumns = {@JoinColumn(name = "crawl_datum_id", referencedColumnName = "id")})
//    @MapKeyColumn(name = "metadata_name")
////    @Column(name = "nutch_property")
//    private Map<String, Object> metadata = new HashMap<>();
}

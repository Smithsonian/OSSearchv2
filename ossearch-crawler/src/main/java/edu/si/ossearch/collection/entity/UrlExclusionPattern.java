package edu.si.ossearch.collection.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import edu.si.ossearch.collection.entity.converters.UrlExclusionPatternScopeConverter;
import edu.si.ossearch.collection.entity.converters.UrlExclusionPatternTypeConverter;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * @author jbirkhimer
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
//@Entity
//@Table(name= "url_exclusion_pattern")
@Embeddable
public class UrlExclusionPattern {

    public enum Type {
        contains,
        regex
    }

    public enum Scope {
        crawl,
        index,
        all
    }

//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private long id;
    @Column(nullable = false)
    private int orderId;

    @Column(columnDefinition = "TEXT")
    private String expression;

    @JsonSerialize(converter = UrlExclusionPatternTypeConverter.class)
    @Enumerated(EnumType.STRING)
    private UrlExclusionPattern.Type type;

    @JsonSerialize(converter = UrlExclusionPatternScopeConverter.class)
    @Enumerated(EnumType.STRING)
    private UrlExclusionPattern.Scope scope;

    private Boolean ignoreCase = false;

//    @ManyToOne(fetch = FetchType.LAZY, optional = false)
//    @JoinColumn(name = "collection_id", nullable = false)
//    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
//    private Collection collection;

//    @ManyToOne(fetch = FetchType.LAZY, optional = false)
//    @JoinColumn(name = "crawlConfig_id", nullable = false)
//    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
//    private CrawlConfig crawlConfig;

//    @CreationTimestamp
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private Date createdDate;
//
//    @UpdateTimestamp
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private Date updatedDate;
}

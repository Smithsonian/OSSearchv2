package edu.si.ossearch.reports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Response DTO for SD603 Report.
 * Contains the report results organized by collection and term, with summary statistics.
 *
 * @author jbirkhimer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SD603ReportResponse {

    /**
     * Timestamp when the report was generated.
     */
    private Date generatedAt;

    /**
     * Total number of URL matches across all collections and terms.
     */
    private long totalMatches;

    /**
     * Results organized by collection.
     */
    private List<CollectionResult> byCollection;

    /**
     * Summary of match counts per search term across all collections.
     */
    private List<TermSummary> summary;

    /**
     * Results for a single collection.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionResult {
        /**
         * The collection ID.
         */
        private Long collectionId;

        /**
         * The collection name.
         */
        private String collectionName;

        /**
         * List of term matches for this collection.
         */
        private List<TermMatch> matches;
    }

    /**
     * Matches for a single search term within a collection.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermMatch {
        /**
         * The search term that was matched.
         */
        private String searchTerm;

        /**
         * Number of URLs matching this term in this collection.
         */
        private int count;

        /**
         * List of URLs that matched this term.
         */
        private List<UrlResult> urls;
    }

    /**
     * A single URL result.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UrlResult {
        /**
         * The URL of the matching page.
         */
        private String url;

        /**
         * The title of the matching page.
         */
        private String title;
    }

    /**
     * Summary statistics for a single search term across all collections.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TermSummary {
        /**
         * The search term.
         */
        private String term;

        /**
         * Total count of matches for this term across all collections.
         */
        private int totalCount;
    }
}

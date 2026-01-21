package edu.si.ossearch.reports.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for Keywords and Phrases Report.
 * Contains the collection IDs to search and the search terms (words/phrases) to find.
 *
 * @author jbirkhimer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeywordsAndPhrasesReportRequest {

    /**
     * List of collection IDs to search across.
     */
    @NotEmpty(message = "At least one collection ID is required")
    private List<Long> collectionIds;

    /**
     * List of words or phrases to search for in the collections.
     */
    @NotEmpty(message = "At least one search term is required")
    private List<String> searchTerms;
}

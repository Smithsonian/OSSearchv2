package edu.si.ossearch.reports.service.impl;

import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.reports.dto.SD603ReportRequest;
import edu.si.ossearch.reports.dto.SD603ReportResponse;
import edu.si.ossearch.reports.dto.SD603ReportResponse.CollectionResult;
import edu.si.ossearch.reports.dto.SD603ReportResponse.TermMatch;
import edu.si.ossearch.reports.dto.SD603ReportResponse.TermSummary;
import edu.si.ossearch.reports.dto.SD603ReportResponse.UrlResult;
import edu.si.ossearch.reports.service.SD603ReportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SD603ReportService.
 * Searches Solr for specified terms across collections and organizes results.
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
public class SD603ReportServiceImpl implements SD603ReportService {

    @Value(value = "${spring.data.solr.collection}")
    @NonNull
    private String solrCollection;

    @Autowired
    @Qualifier("master")
    private SolrClient solrClient;

    @Autowired
    private CollectionRepository collectionRepository;

    private static final int MAX_ROWS = 10000;

    @Override
    public SD603ReportResponse generateReport(SD603ReportRequest request) throws SolrServerException, IOException {
        log.info("Generating SD603 report for collections: {} with terms: {}",
                request.getCollectionIds(), request.getSearchTerms());

        // Build a map of collection ID to collection name
        Map<Long, String> collectionNames = new HashMap<>();
        for (Long collectionId : request.getCollectionIds()) {
            Optional<String> name = collectionRepository.findCollectionById(collectionId);
            collectionNames.put(collectionId, name.orElse("Unknown Collection " + collectionId));
        }

        // Build filter query for collections
        String collectionFilterQuery = buildCollectionFilterQuery(request.getCollectionIds());

        // Map to store results: collectionId -> term -> list of URLs
        Map<Long, Map<String, List<UrlResult>>> resultsByCollection = new LinkedHashMap<>();
        // Map to track term counts across all collections
        Map<String, Integer> termTotalCounts = new LinkedHashMap<>();

        // Initialize maps for each collection
        for (Long collectionId : request.getCollectionIds()) {
            resultsByCollection.put(collectionId, new LinkedHashMap<>());
        }

        // Initialize term counts
        for (String term : request.getSearchTerms()) {
            termTotalCounts.put(term, 0);
        }

        // Search for each term
        for (String term : request.getSearchTerms()) {
            SolrQuery query = buildSolrQuery(term, collectionFilterQuery);
            QueryResponse response = solrClient.query(solrCollection, query);
            SolrDocumentList docs = response.getResults();

            log.debug("Term '{}' returned {} results", term, docs.getNumFound());

            // Group results by collection
            for (SolrDocument doc : docs) {
                Long collectionId = extractCollectionId(doc);
                if (collectionId != null && resultsByCollection.containsKey(collectionId)) {
                    Map<String, List<UrlResult>> termResults = resultsByCollection.get(collectionId);
                    if (!termResults.containsKey(term)) {
                        termResults.put(term, new ArrayList<>());
                    }

                    String url = getFieldAsString(doc, "url");
                    String title = getFieldAsString(doc, "title");

                    termResults.get(term).add(UrlResult.builder()
                            .url(url)
                            .title(title)
                            .build());

                    // Update total count for this term
                    termTotalCounts.merge(term, 1, Integer::sum);
                }
            }
        }

        // Build the response
        long totalMatches = termTotalCounts.values().stream().mapToInt(Integer::intValue).sum();

        List<CollectionResult> collectionResults = new ArrayList<>();
        for (Long collectionId : request.getCollectionIds()) {
            Map<String, List<UrlResult>> termResults = resultsByCollection.get(collectionId);

            List<TermMatch> matches = new ArrayList<>();
            for (String term : request.getSearchTerms()) {
                List<UrlResult> urls = termResults.getOrDefault(term, new ArrayList<>());
                matches.add(TermMatch.builder()
                        .searchTerm(term)
                        .count(urls.size())
                        .urls(urls)
                        .build());
            }

            collectionResults.add(CollectionResult.builder()
                    .collectionId(collectionId)
                    .collectionName(collectionNames.get(collectionId))
                    .matches(matches)
                    .build());
        }

        List<TermSummary> summaries = request.getSearchTerms().stream()
                .map(term -> TermSummary.builder()
                        .term(term)
                        .totalCount(termTotalCounts.getOrDefault(term, 0))
                        .build())
                .collect(Collectors.toList());

        return SD603ReportResponse.builder()
                .generatedAt(new Date())
                .totalMatches(totalMatches)
                .byCollection(collectionResults)
                .summary(summaries)
                .build();
    }

    @Override
    public ByteArrayInputStream exportToCsv(SD603ReportResponse report) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out)) {
            // Write CSV header
            writer.println("Collection ID,Collection Name,Search Term,URL,Title");

            // Write data rows
            for (CollectionResult collection : report.getByCollection()) {
                for (TermMatch match : collection.getMatches()) {
                    for (UrlResult url : match.getUrls()) {
                        writer.printf("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                                collection.getCollectionId(),
                                escapeCsvValue(collection.getCollectionName()),
                                escapeCsvValue(match.getSearchTerm()),
                                escapeCsvValue(url.getUrl() != null ? url.getUrl() : ""),
                                escapeCsvValue(url.getTitle() != null ? url.getTitle() : ""));
                    }
                }
            }

            writer.flush();
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    /**
     * Build a Solr query for the given search term.
     */
    private SolrQuery buildSolrQuery(String term, String collectionFilterQuery) {
        SolrQuery query = new SolrQuery();

        // Use edismax query parser
        query.setParam("defType", "edismax");

        // Set query fields with boosting
        query.setParam("qf", "content title _text_");

        // Wrap term in quotes for phrase search
        // Note: ClientUtils.escapeQueryChars() does NOT escape double quotes,
        // so we must escape them separately to prevent query injection
        String escapedTerm = ClientUtils.escapeQueryChars(term).replace("\"", "\\\"");
        query.setQuery("\"" + escapedTerm + "\"");

        // Add collection filter
        query.addFilterQuery(collectionFilterQuery);

        // Set fields to return
        query.setFields("url", "title", "collectionID");

        // Set maximum rows
        query.setRows(MAX_ROWS);

        return query;
    }

    /**
     * Build a filter query string for the given collection IDs.
     */
    private String buildCollectionFilterQuery(List<Long> collectionIds) {
        if (collectionIds.size() == 1) {
            return "collectionID:" + collectionIds.get(0);
        }
        String ids = collectionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(" OR "));
        return "collectionID:(" + ids + ")";
    }

    /**
     * Extract the collection ID from a Solr document.
     * Handles both single-valued and multi-valued collectionID fields.
     */
    private Long extractCollectionId(SolrDocument doc) {
        Object collectionIdObj = doc.getFieldValue("collectionID");

        // Handle multi-valued field (returns Collection)
        if (collectionIdObj instanceof Collection) {
            Collection<?> values = (Collection<?>) collectionIdObj;
            if (values.isEmpty()) {
                return null;
            }
            collectionIdObj = values.iterator().next();
        }

        // Now parse the actual value
        if (collectionIdObj instanceof Number) {
            return ((Number) collectionIdObj).longValue();
        } else if (collectionIdObj instanceof String) {
            try {
                return Long.parseLong((String) collectionIdObj);
            } catch (NumberFormatException e) {
                log.warn("Could not parse collectionID: {}", collectionIdObj);
                return null;
            }
        }
        return null;
    }

    /**
     * Get a field value as a String from a Solr document.
     */
    private String getFieldAsString(SolrDocument doc, String fieldName) {
        Object value = doc.getFieldValue(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Collection) {
            Collection<?> values = (Collection<?>) value;
            if (!values.isEmpty()) {
                return values.iterator().next().toString();
            }
            return null;
        }
        return value.toString();
    }

    /**
     * Escape a value for CSV output by replacing quotes with double quotes.
     */
    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }
}

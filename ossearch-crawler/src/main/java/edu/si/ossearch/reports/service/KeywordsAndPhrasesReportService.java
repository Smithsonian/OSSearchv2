package edu.si.ossearch.reports.service;

import edu.si.ossearch.reports.dto.KeywordsAndPhrasesReportRequest;
import edu.si.ossearch.reports.dto.KeywordsAndPhrasesReportResponse;
import org.apache.solr.client.solrj.SolrServerException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Service interface for generating Keywords and Phrases Reports.
 * Keywords and Phrases Reports search for specific words/phrases across selected collections
 * and return matching URLs organized by collection and search term.
 *
 * @author jbirkhimer
 */
public interface KeywordsAndPhrasesReportService {

    /**
     * Generate a Keywords and Phrases report by searching for the specified terms across the given collections.
     *
     * @param request The report request containing collection IDs and search terms
     * @return The report response with matches organized by collection and term
     * @throws SolrServerException if there is an error communicating with Solr
     * @throws IOException if there is an I/O error
     */
    KeywordsAndPhrasesReportResponse generateReport(KeywordsAndPhrasesReportRequest request) throws SolrServerException, IOException;

    /**
     * Export a Keywords and Phrases report to CSV format.
     *
     * @param report The report to export
     * @return A ByteArrayInputStream containing the CSV data
     */
    ByteArrayInputStream exportToCsv(KeywordsAndPhrasesReportResponse report);
}

package edu.si.ossearch.reports.service;

import edu.si.ossearch.reports.dto.SD603ReportRequest;
import edu.si.ossearch.reports.dto.SD603ReportResponse;
import org.apache.solr.client.solrj.SolrServerException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Service interface for generating SD603 Reports.
 * SD603 Reports search for specific words/phrases across selected collections
 * and return matching URLs organized by collection and search term.
 *
 * @author jbirkhimer
 */
public interface SD603ReportService {

    /**
     * Generate an SD603 report by searching for the specified terms across the given collections.
     *
     * @param request The report request containing collection IDs and search terms
     * @return The report response with matches organized by collection and term
     * @throws SolrServerException if there is an error communicating with Solr
     * @throws IOException if there is an I/O error
     */
    SD603ReportResponse generateReport(SD603ReportRequest request) throws SolrServerException, IOException;

    /**
     * Export an SD603 report to CSV format.
     *
     * @param report The report to export
     * @return A ByteArrayInputStream containing the CSV data
     * @throws IOException if there is an error generating the CSV
     */
    ByteArrayInputStream exportToCsv(SD603ReportResponse report) throws IOException;
}

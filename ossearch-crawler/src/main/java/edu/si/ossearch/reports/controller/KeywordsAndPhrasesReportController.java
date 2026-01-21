package edu.si.ossearch.reports.controller;

import edu.si.ossearch.reports.dto.KeywordsAndPhrasesReportRequest;
import edu.si.ossearch.reports.dto.KeywordsAndPhrasesReportResponse;
import edu.si.ossearch.reports.service.KeywordsAndPhrasesReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * REST controller for Keywords and Phrases Report endpoints.
 * Provides functionality to search for specific words/phrases across collections
 * and export results as CSV.
 *
 * @author jbirkhimer
 */
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/reports/keywords-and-phrases")
@Tag(description = "Keywords and Phrases Report - Search for words/phrases across collections", name = "Keywords and Phrases Report")
@SecurityRequirement(name = "bearerAuth")
public class KeywordsAndPhrasesReportController {

    @Autowired
    private KeywordsAndPhrasesReportService keywordsAndPhrasesReportService;

    /**
     * Generate a Keywords and Phrases report by searching for specified terms across selected collections.
     *
     * @param request The report request containing collection IDs and search terms
     * @return The report response with matches organized by collection and term
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Generate Keywords and Phrases Report - Search for words/phrases across collections",
            description = "Searches for the specified words or phrases across the selected collections " +
                    "and returns matching URLs organized by collection and search term.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Report generated successfully",
                            content = @Content(mediaType = "application/json")
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request - missing required fields"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error during report generation"
                    )
            }
    )
    public ResponseEntity<?> generateReport(@Valid @RequestBody KeywordsAndPhrasesReportRequest request) {
        log.info("Generating Keywords and Phrases report for {} collections and {} search terms",
                request.getCollectionIds().size(), request.getSearchTerms().size());
        try {
            KeywordsAndPhrasesReportResponse response = keywordsAndPhrasesReportService.generateReport(request);
            log.info("Keywords and Phrases report generated successfully with {} total matches", response.getTotalMatches());
            return ResponseEntity.ok(response);
        } catch (SolrServerException | IOException e) {
            log.error("Keywords and Phrases Report generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Report generation failed: " + e.getMessage()));
        }
    }

    /**
     * Export a Keywords and Phrases report as a CSV file.
     *
     * @param request The report request containing collection IDs and search terms
     * @return CSV file download response
     */
    @PostMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Export Keywords and Phrases Report as CSV",
            description = "Generates a Keywords and Phrases report and exports it as a downloadable CSV file.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "CSV export generated successfully",
                            content = @Content(mediaType = "text/csv")
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request - missing required fields"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error during export"
                    )
            }
    )
    public ResponseEntity<?> exportReport(@Valid @RequestBody KeywordsAndPhrasesReportRequest request) {
        log.info("Exporting Keywords and Phrases report to CSV for {} collections and {} search terms",
                request.getCollectionIds().size(), request.getSearchTerms().size());
        try {
            KeywordsAndPhrasesReportResponse report = keywordsAndPhrasesReportService.generateReport(request);
            ByteArrayInputStream csv = keywordsAndPhrasesReportService.exportToCsv(report);

            String filename = "Keywords_and_Phrases_Report_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv";

            log.info("Keywords and Phrases CSV export completed with {} total matches", report.getTotalMatches());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(new InputStreamResource(csv));
        } catch (SolrServerException | IOException e) {
            log.error("Keywords and Phrases Report export failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Export failed: " + e.getMessage()));
        }
    }
}

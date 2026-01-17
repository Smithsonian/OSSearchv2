package edu.si.ossearch.reports.controller;

import edu.si.ossearch.reports.dto.SD603ReportRequest;
import edu.si.ossearch.reports.dto.SD603ReportResponse;
import edu.si.ossearch.reports.service.SD603ReportService;
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
 * REST controller for SD603 Report endpoints.
 * Provides functionality to search for specific words/phrases across collections
 * and export results as CSV.
 *
 * @author jbirkhimer
 */
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/reports/sd603")
@Tag(description = "SD603 Report - Search for words/phrases across collections", name = "SD603 Report")
@SecurityRequirement(name = "bearerAuth")
public class SD603ReportController {

    @Autowired
    private SD603ReportService sd603ReportService;

    /**
     * Generate an SD603 report by searching for specified terms across selected collections.
     *
     * @param request The report request containing collection IDs and search terms
     * @return The report response with matches organized by collection and term
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Generate SD603 Report - Search for words/phrases across collections",
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
    public ResponseEntity<?> generateReport(@Valid @RequestBody SD603ReportRequest request) {
        log.info("Generating SD603 report for {} collections and {} search terms",
                request.getCollectionIds().size(), request.getSearchTerms().size());
        try {
            SD603ReportResponse response = sd603ReportService.generateReport(request);
            log.info("SD603 report generated successfully with {} total matches", response.getTotalMatches());
            return ResponseEntity.ok(response);
        } catch (SolrServerException | IOException e) {
            log.error("SD603 Report generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Report generation failed: " + e.getMessage()));
        }
    }

    /**
     * Export an SD603 report as a CSV file.
     *
     * @param request The report request containing collection IDs and search terms
     * @return CSV file download response
     */
    @PostMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(
            summary = "Export SD603 Report as CSV",
            description = "Generates an SD603 report and exports it as a downloadable CSV file.",
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
    public ResponseEntity<?> exportReport(@Valid @RequestBody SD603ReportRequest request) {
        log.info("Exporting SD603 report to CSV for {} collections and {} search terms",
                request.getCollectionIds().size(), request.getSearchTerms().size());
        try {
            SD603ReportResponse report = sd603ReportService.generateReport(request);
            ByteArrayInputStream csv = sd603ReportService.exportToCsv(report);

            String filename = "SD603_Report_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv";

            log.info("SD603 CSV export completed with {} total matches", report.getTotalMatches());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(new InputStreamResource(csv));
        } catch (SolrServerException | IOException e) {
            log.error("SD603 Report export failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "Export failed: " + e.getMessage()));
        }
    }
}

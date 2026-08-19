package edu.si.ossearch.utils.backup_restore.controller;

import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.utils.backup_restore.request.RestoreLocalBackupRequest;
import edu.si.ossearch.utils.backup_restore.service.BackupRestoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author jbirkhimer
 */
@Slf4j
@RestController
@RequestMapping("/api/utils")
@Tag(description = "Utils | Backup/Restore", name = "Utils | Backup/Restore")
@SecurityRequirement(name = "bearerAuth")
public class BackUpRestoreController {

    @Autowired
    CollectionRepository collectionRepository;

    @Autowired
    BackupRestoreService backupRestoreService;

    @GetMapping(value = "/backup/collection/{id:\\d+}")
    @Operation(summary = "backup collection by id", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public ResponseEntity<?> backupCollectionById(@PathVariable(name = "id") Long id,
                                                  @RequestParam(value = "withCrawlSchedule", required = false, defaultValue = "true") boolean withCrawlSchedule,
                                                  @RequestParam(value = "includeUsers", required = false, defaultValue = "false") boolean includeUsers,
                                                  @RequestParam(value = "listAvailableBackups", required = false, defaultValue = "false") boolean listAvailableBackups
    ) {

        Optional<String> collectionName = collectionRepository.findCollectionById(id);

        if (collectionName.isPresent()) {

            String filePrefix = collectionName.get() + "_" + id + "_backup";
            String filename = filePrefix + "_" + new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(new Date()) + ".json";

            try {

                if (!listAvailableBackups) {
                    ByteArrayInputStream byteArrayOutputStream = backupRestoreService.backupCollection(id, withCrawlSchedule, includeUsers);

                    InputStreamResource fileInputStream = new InputStreamResource(byteArrayOutputStream);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(fileInputStream);
                } else {
                    String collectionDir = collectionName.get() + "_" + id;
                    return ResponseEntity.ok(backupRestoreService.collectionListBackupsAvailable(collectionDir));
                }
            } catch (Exception e) {
                log.error("Problem with backups for collection id: {}, name: {}!", id, collectionName.get(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(backupError(id, collectionName.get(),
                                "Problem with backup for collection " + collectionName.get() + "!", e));
            }
        } else {
            log.error("Backup Error! Collection not found for id: {}!", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(backupError(id, null, "Backup Error! Collection not found!", null));
        }
    }

    /**
     * Structured error body so the client can surface the real reason a backup failed.
     * The backup endpoints stream binary/JSON attachments, so callers read the response as a
     * Blob and need a parseable body rather than a plain string.
     */
    private Map<String, Object> backupError(Long id, String collectionName, String message, Exception e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("collectionId", id);
        error.put("collectionName", collectionName);
        error.put("status", "failed");
        error.put("message", message);
        if (e != null) {
            error.put("error", String.valueOf(e.getMessage()));
            error.put("errorType", e.getClass().getName());
        }
        return error;
    }

    @GetMapping(value = "/backup/collection/{collectionDir}/{filename}")
    @Operation(summary = "get backup file", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public ResponseEntity<?> getBackupFile(@PathVariable(name = "collectionDir") String collectionDir, @PathVariable(value = "filename") String filename) {

        try {
            String backupJsonStr = backupRestoreService.localBackup(collectionDir, filename, false);
            if (backupJsonStr != null) {
                return ResponseEntity.ok(backupJsonStr);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Backup Error! Backup file not found!");
            }
        } catch (Exception e) {
            log.error("Problem getting backup file {}/{}!", collectionDir, filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Problem getting backup file " + collectionDir +"/"+filename+"! Error: "+e.getMessage());
        }
    }

    @DeleteMapping(value = "/backup/collection/{id:\\d+}/{filename}")
    @Operation(summary = "get backup files ", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    public ResponseEntity<?> deleteBackupFile(@PathVariable(name = "id") Long id, @PathVariable(value = "filename") String filename) {

        Optional<String> collectionName = collectionRepository.findCollectionById(id);

        if (collectionName.isPresent()) {
            try {
                String collectionDir = collectionName.get() + "_" + id;
                String backupJsonStr = backupRestoreService.localBackup(collectionDir, filename, true);
                if (backupJsonStr != null) {
                    return ResponseEntity.ok(backupJsonStr);
                } else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Delete Backup Error! Backup file not found!");
                }
            } catch (Exception e) {
                log.error("Problem with backups for collection {}!", collectionName, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Problem with delete backup for collection " + collectionName);
            }
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Delete Backup Error! Collection not found!");
        }
    }

    @GetMapping(value = "/backup/collection/bulk")
    @Operation(summary = "bulk backup collections by list of ids", responses = {@ApiResponse(content = {@Content(mediaType = "application/json"), @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)})})
    public ResponseEntity<?> bulkBackupCollectionByIds(@RequestParam(name = "ids", required = false) List<Long> ids,
                                                       @RequestParam(value = "withCrawlSchedule", required = false, defaultValue = "true") boolean withCrawlSchedule,
                                                       @RequestParam(value = "includeUsers", required = false, defaultValue = "false") boolean includeUsers,
                                                       @RequestParam(value = "listAvailableBackups", required = false, defaultValue = "false") boolean listAvailableBackups
    ) {
        if (listAvailableBackups) {
            return backupRestoreService.bulkListAvailableBackupsCollection();
        } else {
            return backupRestoreService.bulkBackupCollection(ids, withCrawlSchedule, includeUsers);
        }
    }

    @Operation(summary = "bulk restore collections from uploaded backup files", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    @PostMapping(value = "/restore/collection/bulk/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bulkUploadRestoreCollectionAndCrawlSchedule(
            @Parameter(
                    description = "Files to be uploaded",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "restoreCollection", defaultValue = "true") boolean restoreCollection,
            @RequestParam(value = "restoreCrawlSchedule", defaultValue = "true") boolean restoreCrawlSchedule,
            @RequestParam(value = "restoreUsers", defaultValue = "true") boolean restoreUsers
    ) {
        List<Map<String, String>> filesData = new ArrayList<>();

        for (MultipartFile file : Arrays.asList(files)) {
            try {
                Map<String, String> fileInfo = new HashMap<>();
                fileInfo.put("filename", file.getOriginalFilename());
                fileInfo.put("data", new String(file.getBytes(), StandardCharsets.UTF_8));
                filesData.add(fileInfo);
            } catch (IOException e) {
                log.error("Problem restoring collection from file: {}", file.getOriginalFilename(), e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Problem restoring collection from file: " + file.getOriginalFilename() + ". Error: " + e.getMessage());
            }
        }

        return backupRestoreService.restoreCollectionAndCrawlSchedule(filesData, restoreCollection, restoreCrawlSchedule, restoreUsers);
    }

    @Operation(summary = "bulk restore collections from local backup files", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    @PostMapping(value = "/restore/collection/bulk/local")
    public ResponseEntity<?> bulkLocalRestoreCollectionAndCrawlSchedule(@Valid @RequestBody RestoreLocalBackupRequest localBackupRequest) {
        List<Map<String, String>> filesData = new ArrayList<>();

        for (String file : localBackupRequest.getFiles()) {
            try {
                filesData.add(backupRestoreService.getLocalFileData(file));
            } catch (IOException e) {
                log.error("Problem restoring collection from file: {}", file, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Problem restoring collection from file: " + file + ". Error: " + e.getMessage());
            }
        }

        return backupRestoreService.restoreCollectionAndCrawlSchedule(filesData, localBackupRequest.getRestoreCollection(), localBackupRequest.getRestoreCrawlSchedule(), localBackupRequest.getRestoreUsers());
    }
}

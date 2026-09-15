package edu.si.ossearch.utils.backup_restore.controller;

import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.utils.backup_restore.config.ScheduledBackupConfig;
import edu.si.ossearch.utils.backup_restore.request.RestoreLocalBackupRequest;
import edu.si.ossearch.utils.backup_restore.retention.BackupRetentionRunner;
import edu.si.ossearch.utils.backup_restore.retention.RetentionResult;
import edu.si.ossearch.utils.backup_restore.service.BackupRestoreService;
import edu.si.ossearch.utils.backup_restore.status.BackupJobStatusService;
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
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Autowired
    ScheduledBackupConfig scheduledBackupConfig;

    @Autowired
    BackupJobStatusService backupJobStatusService;

    @Autowired
    BackupRetentionRunner backupRetentionRunner;

    @Operation(summary = "scheduled collection backup status: config + last run", responses = {@ApiResponse(content = @Content(mediaType = "application/json"))})
    @GetMapping(value = "/backup/scheduled/status")
    // Admin-gated: the response includes lastRun.errorMessage, which can be a raw exception
    // message (absolute NFS paths, JDBC/Hibernate internals). /api/** is only .authenticated()
    // by default (see WebSecurityConfig), so this @PreAuthorize is the only thing keeping the
    // endpoint away from other authenticated users. The UI matches this gate rather than
    // relying on it: both the /backupRestore route and the per-collection backupRestore child
    // route carry beforeEnter: isAdmin, and ScheduledBackupStatus is rendered under
    // v-if="isAdmin" in both views, so a non-admin never issues this request in the first
    // place. Keep the two in sync - relaxing either side alone is a regression.
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> scheduledBackupStatus(
            // The retention preview walks the whole crawlDir over NFS (one metadata round trip
            // per collection), so it is opt-in rather than a cost every caller of this status
            // endpoint pays. The cheap "days" value is always returned.
            @Parameter(description = "also compute which backup files the next retention sweep would prune (walks crawlDir)")
            @RequestParam(value = "includeRetentionPreview", required = false, defaultValue = "false") boolean includeRetentionPreview) {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            // "enabledOnThisNode" is honestly node-local: enabled is per-host by design (true on
            // exactly one node), and the load balancer can route this GET to either app server,
            // so this field only describes whichever node answered THIS request - it is NOT a
            // cluster-wide "are scheduled backups enabled" answer. If lastRun.hostname differs
            // from "node" below, that proves the job is enabled and running on the OTHER node,
            // even though this node reports enabledOnThisNode=false. Cluster-wide enablement
            // discovery is out of scope here.
            status.put("enabledOnThisNode", scheduledBackupConfig.isEnabled());
            // "node" is who answered THIS request; "lastRun.hostname" is who actually
            // executed the run being reported. The load balancer can route this GET to
            // either app server while only one of them runs the scheduled job, so the
            // UI needs both to tell "who answered" apart from "who executed".
            status.put("node", nodeName());
            status.put("cron", scheduledBackupConfig.getCron());

            // nextRun is pure arithmetic on the cron expression - it is computed
            // regardless of enabledOnThisNode because it does not claim a backup will
            // actually happen, only when the expression would next fire. Whether it
            // actually runs is the separate "enabledOnThisNode" fact above, and we keep
            // that honest here too: when enabled=false on this node the @Scheduled
            // trigger still fires on schedule but the job early-returns and does
            // nothing, so nextRunNote says so alongside the computed time instead of
            // suppressing the time.
            String nextRun = null;
            String nextRunNote = null;
            try {
                LocalDateTime next = CronExpression.parse(scheduledBackupConfig.getCron())
                        .next(LocalDateTime.now());
                if (next != null) {
                    // Emit an explicit offset. CronExpression yields a zoneless LocalDateTime
                    // whose toString() the browser would parse as browser-local time, while
                    // lastRun.startedAt/finishedAt are java.sql.Timestamps serialized as real
                    // UTC instants - two reference frames in one <dl>. Stamping the server's
                    // zone offset on here makes both values absolute, so new Date(...) in the
                    // UI renders them in the same (viewer-local) frame.
                    nextRun = next.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
                    if (!scheduledBackupConfig.isEnabled()) {
                        nextRunNote = "scheduled backups are not enabled on this node, so this time will not actually run";
                    }
                } else {
                    nextRunNote = "cron expression has no future fire time";
                }
            } catch (Exception e) {
                nextRunNote = "could not parse cron expression: " + e.getMessage();
            }
            status.put("nextRun", nextRun);
            status.put("nextRunNote", nextRunNote);

            status.put("lastRun", backupJobStatusService.getLastRun().orElse(null));
            status.put("retention", retentionStatus(includeRetentionPreview));
        } catch (Exception e) {
            log.error("Problem getting scheduled backup status!", e);
            status.put("error", e.getMessage());
        }
        return ResponseEntity.ok(status);
    }

    /**
     * Maximum number of pruning candidates included in the status response. RetentionResult
     * itself is internally bounded at 200; this is a further UI-facing cap.
     */
    private static final int MAX_REPORTED_CANDIDATES = 50;

    /**
     * Builds the "retention" block of the scheduled backup status response.
     * <p>
     * {@code preview()} walks the whole crawlDir on every call - one NFS metadata round trip
     * per collection, uncached. It is therefore only performed when the caller explicitly asks
     * for it via {@code includePreview}; otherwise this returns just the configured retention
     * window, which is a plain config read. When the preview is skipped the candidate keys are
     * omitted entirely rather than sent as zeros, so the UI can tell "not asked for" apart
     * from "nothing to prune".
     * <p>
     * Never throws: a retention preview problem must not fail the status endpoint.
     */
    private Map<String, Object> retentionStatus(boolean includePreview) {
        Map<String, Object> retention = new LinkedHashMap<>();
        retention.put("days", scheduledBackupConfig.getRetention().getDays());

        if (!includePreview) {
            return retention;
        }

        RetentionResult preview = null;
        try {
            preview = backupRetentionRunner.preview();
        } catch (Exception e) {
            log.error("Problem previewing backup retention candidates!", e);
        }

        if (preview == null) {
            // null means "preview unavailable", which is distinct from an empty candidate
            // list ("nothing would be pruned").
            retention.put("candidateCount", 0);
            retention.put("candidateTruncated", false);
            retention.put("candidates", new ArrayList<>());
            retention.put("error", "retention preview unavailable");
            return retention;
        }

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (String path : preview.getCandidateFiles()) {
            if (candidates.size() >= MAX_REPORTED_CANDIDATES) {
                break;
            }
            // Do not leak absolute filesystem paths to the UI. Given
            // <crawlDir>/<collDir>/backup/<file>, report <collDir> and the bare filename,
            // derived by File parent navigation rather than by splitting on a hardcoded
            // path separator.
            File file = new File(path);
            File backupDir = file.getParentFile();
            File collectionDir = backupDir != null ? backupDir.getParentFile() : null;
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("collection", collectionDir != null ? collectionDir.getName() : null);
            candidate.put("file", file.getName());
            candidates.add(candidate);
        }

        retention.put("candidateCount", preview.getCandidateCount());
        // True for both the 50-entry cap here and RetentionResult's own 200-path bound.
        retention.put("candidateTruncated", preview.getCandidateCount() > candidates.size());
        retention.put("candidates", candidates);
        retention.put("error", null);
        return retention;
    }

    private String nodeName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return "unknown";
        }
    }

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

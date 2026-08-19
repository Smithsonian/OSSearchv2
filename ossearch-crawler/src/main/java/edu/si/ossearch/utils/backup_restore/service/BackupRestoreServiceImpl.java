package edu.si.ossearch.utils.backup_restore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import edu.si.ossearch.OSSearchException;
import edu.si.ossearch.collection.entity.Collection;
import edu.si.ossearch.collection.entity.PageResult;
import edu.si.ossearch.collection.entity.projections.CollectionExport;
import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.scheduler.controller.CrawlSchedulerController;
import edu.si.ossearch.scheduler.entity.CrawlSchedulerJobInfo;
import edu.si.ossearch.scheduler.repository.CrawlSchedulerJobInfoRepository;
import edu.si.ossearch.scheduler.service.JobService;
import edu.si.ossearch.security.models.User;
import edu.si.ossearch.security.repository.UserRepository;
import edu.si.ossearch.utils.backup_restore.entity.projections.CrawlSchedulerJobInfoInfoExport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.EntityLinks;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author jbirkhimer
 */
@Slf4j
@Service
public class BackupRestoreServiceImpl implements BackupRestoreService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    CollectionRepository collectionRepository;

    @Autowired
    CrawlSchedulerJobInfoRepository crawlSchedulerJobInfoRepository;

    @Value(value = "${ossearch.nutch.crawlDir}")
    File crawlDir;

    @Autowired
    EntityLinks entityLinks;
    private ObjectMapper mapper = new ObjectMapper();

    @Autowired
    CrawlSchedulerController crawlSchedulerController;

    @Autowired
    private JobService jobService;

    @Override
    @Transactional
    public ByteArrayInputStream backupCollection(Long id, boolean withCrawlSchedule, boolean includeUsers) throws Exception {

        JSONObject json = new JSONObject();

        json.put("collection", exportCollection(id));

        String collectionName = json.getJSONObject("collection").getString("name");

        if (withCrawlSchedule) {
            /*JSONObject crawlSchedulerJobInfo = exportCrawlSchedulerJobInfo(collectionName);
            if (crawlSchedulerJobInfo.isEmpty()) {
                json.put("crawlSchedulerJobInfo", "no crawl schedule available");
            } else {
                json.put("crawlSchedulerJobInfo", crawlSchedulerJobInfo);
            }*/
            json.put("crawlSchedulerJobInfo", exportCrawlSchedulerJobInfo(collectionName));
        }

        if (!includeUsers) {
            json.getJSONObject("collection").remove("owner");
            json.getJSONObject("collection").remove("users");
        }

        saveLocalBackup(collectionName+"_"+ id, json);

        return new ByteArrayInputStream(json.toString(4).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public List<Map<String, String>> collectionListBackupsAvailable(String collectionDir) {

        Path crawlBaseDir = new Path(crawlDir.getAbsolutePath(), collectionDir);
        Path collectionBackupDir = new Path(crawlBaseDir, "backup");

        File collectionBackupDirPath = new File(collectionBackupDir.toString());

        if (collectionBackupDirPath.exists()) {

            return Stream.of(collectionBackupDirPath.listFiles())
                    .filter(file -> !file.isDirectory())
                    .map(file -> {
                        Map<String, String> row = new HashMap<>();
                        row.put("file", file.getName());
                        row.put("date", getLastModifiedDate(file.lastModified()));
                        return row;
                    })
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private String getLastModifiedDate(Long lastModified) {
        return new SimpleDateFormat("yyyy-MM-dd hh:mm:ss aa").format(new Date(lastModified));
    }

    private JSONObject exportCollection(Long id) throws JsonProcessingException {
        Collection collection = collectionRepository.getById(id);
        ProjectionFactory projectionFactory = new SpelAwareProxyProjectionFactory();
        CollectionExport collectionExport = projectionFactory.createProjection(CollectionExport.class, collection);
        return new JSONObject(getJsonString(collectionExport));
    }

    private JSONArray exportCrawlSchedulerJobInfo(String collectionName) throws JsonProcessingException {
        List<CrawlSchedulerJobInfo> jobs = crawlSchedulerJobInfoRepository.findByCollectionName(collectionName);
        JSONArray jobsArray = new JSONArray();
        if (jobs != null) {
            ProjectionFactory crawlProjectionFactory = new SpelAwareProxyProjectionFactory();
            for (CrawlSchedulerJobInfo job : jobs) {
                CrawlSchedulerJobInfoInfoExport export =
                    crawlProjectionFactory.createProjection(CrawlSchedulerJobInfoInfoExport.class, job);
                jobsArray.put(new JSONObject(getJsonString(export)));
            }
        }
        return jobsArray;
    }

    /**
     * Tomcat's default max response header size is ~8KB, and a large bulk backup (see issue #2,
     * ~80 collections) could fail that many collections at once, each carrying a full exception
     * message. Rather than put every failure's full detail in the header, cap it to a total count
     * plus the first few collection names/ids - the full detail for every failure is already
     * written to an "_ERROR.json" entry per collection inside the zip itself.
     */
    private static final int MAX_BACKUP_ERROR_HEADER_ENTRIES = 10;
    private static final int MAX_BACKUP_ERROR_HEADER_BYTES = 6000;

    private String buildBackupErrorsHeader(JSONArray backupErrors) {
        JSONObject summary = new JSONObject();
        summary.put("count", backupErrors.length());

        JSONArray names = new JSONArray();
        int limit = Math.min(backupErrors.length(), MAX_BACKUP_ERROR_HEADER_ENTRIES);
        for (int i = 0; i < limit; i++) {
            JSONObject failure = backupErrors.getJSONObject(i);
            names.put(new JSONObject()
                    .put("collectionId", failure.opt("collectionId"))
                    .put("collectionName", failure.opt("collectionName")));
        }
        summary.put("failed", names);
        summary.put("truncated", backupErrors.length() > limit);

        // Header values must be single-line ASCII, so encode the JSON payload.
        // URLEncoder implements application/x-www-form-urlencoded (space -> "+"), but the
        // frontend decodes with decodeURIComponent (RFC 3986, space -> "%20", "+" left as-is),
        // so "+" has to be swapped for "%20" to match what the client expects.
        String encoded = URLEncoder.encode(summary.toString(), StandardCharsets.UTF_8).replace("+", "%20");

        // Defensive hard cap in case collection names alone are unexpectedly long - drop entries
        // one at a time rather than fail the whole response over a header that's just a summary.
        while (encoded.getBytes(StandardCharsets.UTF_8).length > MAX_BACKUP_ERROR_HEADER_BYTES && names.length() > 0) {
            names.remove(names.length() - 1);
            summary.put("failed", names);
            summary.put("truncated", true);
            encoded = URLEncoder.encode(summary.toString(), StandardCharsets.UTF_8).replace("+", "%20");
        }

        return encoded;
    }

    private void writeZipEntry(ZipOutputStream zipOut, String filename, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(filename);
        entry.setSize(bytes.length);
        zipOut.putNextEntry(entry);
        zipOut.write(bytes);
        zipOut.closeEntry();
    }

    private void saveLocalBackup(String collectionDir, JSONObject json) throws IOException {
        Path crawlBaseDir = new Path(crawlDir.getAbsolutePath(), collectionDir);
        Path collectionBackupDir = new Path(crawlBaseDir, "backup");
        String filename = crawlBaseDir.getName()+ "_backup_" + new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(new Date()) + ".json";
        Files.createDirectories(Paths.get(collectionBackupDir.toString()));
        Files.write(Paths.get(collectionBackupDir.toString(), filename), json.toString(4).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String localBackup(String collectionDir, String fileName, boolean delete) throws IOException {
        Path crawlBaseDir = new Path(crawlDir.getAbsolutePath(), collectionDir);
        Path collectionBackupDir = new Path(crawlBaseDir, "backup");
        File file = new File(new File(collectionBackupDir.toString()), fileName);
        if (file.exists()) {
            if (delete) {
                file.delete();
                return new JSONArray(collectionListBackupsAvailable(collectionDir)).toString();
            } else {
                return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
            }
        } else {
            return null;
        }
    }

    private String getJsonString(Object entity) throws JsonProcessingException {
        ObjectMapper mapper = new JsonMapper();
        return mapper.writeValueAsString(entity);
    }

    @Override
    @Transactional
    public ResponseEntity<?> restoreCollectionAndCrawlSchedule(List<Map<String, String>> files, boolean restoreCollection, boolean restoreCrawlSchedule, boolean restoreUsers) {
        JSONArray message = new JSONArray();
        try {

            for (Map<String, String> file : files) {
                JSONObject restoreStatus = new JSONObject();
                restoreStatus.put("file", file.get("filename"));

                JSONObject collectionStatus = new JSONObject();
                restoreStatus.put("collection", collectionStatus);

                JSONArray crawlSchedulerJobInfoStatuses = new JSONArray();
                restoreStatus.put("crawlSchedulerJobInfo", crawlSchedulerJobInfoStatuses);

                message.put(restoreStatus);

                try {
                    JSONObject json = new JSONObject(file.get("data"));

                    try {

                        JSONObject collectionJson = json.getJSONObject("collection");

                        Collection collection = restoreCollection(collectionJson, restoreUsers, collectionStatus);
                        collectionStatus.put("id", collection.getId());
                        collectionStatus.put("name", collection.getName());

                        Link link = entityLinks.linkToItemResource(Collection.class, collection.getId()).expand();
                        URI location = URI.create(link.getHref());
                        collectionStatus.put("location", location);

                    } catch (Exception e) {
                        log.error("Fail to restore collection from file! {}", file.get("filename"), e);
                        collectionStatus.put("status", "failed");
                        collectionStatus.append("error", e.getMessage());
                        //throw new OSSearchException("Failed to restore collection from file " + file.get("filename"), e);
                    }

                    Object raw = json.opt("crawlSchedulerJobInfo");
                    List<JSONObject> jobJsons = new ArrayList<>();

                    if (raw instanceof JSONArray) {
                        JSONArray arr = (JSONArray) raw;
                        for (int i = 0; i< arr.length(); i++) {
                            jobJsons.add(arr.getJSONObject(i));
                        }
                    } else if (raw instanceof JSONObject && !((JSONObject) raw).isEmpty()) {
                        jobJsons.add((JSONObject) raw);
                    }

                    for (JSONObject jobJson : jobJsons) {
                        JSONObject jobStatus = new JSONObject();
                        jobStatus.put("jobName", jobJson.optString("jobName"));
                        jobStatus.put("jobGroup", jobJson.optString("jobGroup"));
                        crawlSchedulerJobInfoStatuses.put(jobStatus);

                        try {
                            restoreCrawlSchedulerJobInfo(collectionStatus.getLong("id"), jobJson, jobStatus);
                        } catch (Exception e) {
                            log.error("Fail to restore crawlSchedulerJobInfo from file! {}", file.get("filename"), e);
                            jobStatus.put("status", "failed");
                            jobStatus.append("error", e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    restoreStatus.put("status", "failed");
                    restoreStatus.append("error", e.getMessage());
                }
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(message.toString());
        } catch (Exception e) {
            log.error("Fail to upload files!", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(message.toString());
        }
    }

    @Override
    @Transactional
    public ResponseEntity<?> bulkBackupCollection(List<Long> ids, boolean withCrawlSchedule, boolean includeUsers) {

        String zipFilename = "ossearch_bulk_collections_backup_" + new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(new Date()) + ".zip";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Collections that failed to back up, reported to the client via the X-Backup-Errors response header.
        JSONArray backupErrors = new JSONArray();

            try(ZipOutputStream zipOut = new ZipOutputStream(baos)) {

                for (Long id : ids) {

                    String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(new Date());
                    Optional<String> collectionName = collectionRepository.findCollectionById(id);
                    String name = collectionName.orElse("unknown");

                    // Back up each collection independently so one failure does not abort the whole zip.
                    try {

                        if (collectionName.isEmpty()) {
                            throw new IllegalArgumentException("Collection not found for id " + id + "!");
                        }

                        JSONObject json = new JSONObject();

                        json.put("collection", exportCollection(id));

                        name = json.getJSONObject("collection").getString("name");

                        if (withCrawlSchedule) {
                            json.put("crawlSchedulerJobInfo", exportCrawlSchedulerJobInfo(name));
                        }

                        if (!includeUsers) {
                            json.getJSONObject("collection").remove("owner");
                            json.getJSONObject("collection").remove("users");
                        }

                        saveLocalBackup(name + "_" + id, json);

                        writeZipEntry(zipOut, name + "_" + id + "_backup_" + timestamp + ".json", json.toString(4));

                    } catch (Exception e) {
                        log.error("There was an error with the Backup of collection id: {}, name: {}", id, name, e);

                        JSONObject errorJson = new JSONObject();
                        errorJson.put("collectionId", id);
                        errorJson.put("collectionName", name);
                        errorJson.put("status", "failed");
                        errorJson.put("error", e.getMessage());
                        errorJson.put("errorType", e.getClass().getName());
                        errorJson.put("timestamp", timestamp);

                        writeZipEntry(zipOut, name + "_" + id + "_backup_" + timestamp + "_ERROR.json", errorJson.toString(4));

                        backupErrors.put(new JSONObject()
                                .put("collectionId", id)
                                .put("collectionName", name)
                                .put("error", String.valueOf(e.getMessage())));
                    }
                }

                zipOut.finish();

            } catch (Exception e) {
                log.error("Problem with bulk backup!", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Problem with bulk backup! Error: " + e.getMessage());
            }

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        if (!backupErrors.isEmpty()) {
            log.warn("Bulk backup completed with {} failed collection(s): {}", backupErrors.length(), backupErrors);
            response.header("X-Backup-Errors", buildBackupErrorsHeader(backupErrors));
        }

        return response.body(baos.toByteArray());
    }

    @Override
    public ResponseEntity<?> bulkListAvailableBackupsCollection() {

        /*List<CollectionRepository.CollectionIdNameInfoTest> userCollections = collectionRepository.findAllCollectionsByOwnerAndUsers();
        List<Long> collectionIds = userCollections.stream()
                .map(collection -> collection.getId())
                .collect(Collectors.toList());

        List<Map<String, List<Map<String, String>>>> result = collectionIds.stream().map(id -> {
            Optional<String> collectionName = collectionRepository.findCollectionById(id);

            Map<String, List<Map<String, String>>> answer = new HashMap<>();
            if (collectionName.isPresent()) {
                String collectionDir = collectionName.get() + "_" + id;
                answer.put(collectionName.get(), collectionListBackupsAvailable(collectionDir));
            } else {
                answer.put(collectionName.get(), new ArrayList<>());
            }
            return answer;
        }).collect(Collectors.toList());*/

        try {
            File[] files = crawlDir.listFiles();
            if (files == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Map<String, List<Map<String, String>>>> result = Stream.of(files)
                    .filter(file -> file.isDirectory())
                    .map(collectionDir -> {
                        Map<String, List<Map<String, String>>> answer = new HashMap<>();
                        List<Map<String, String>> collectionBackupsAvailable = collectionListBackupsAvailable(collectionDir.getName());
                        //String collectionName = StringUtils.substringBeforeLast(collectionDir.getName(), "_");
                        answer.put(collectionDir.getName(), collectionBackupsAvailable);
                        return answer;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Problem with loading backups!", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("message", "Problem with loading backups! Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(error);
        }
    }

    @Override
    public Map<String, String> getLocalFileData(String filename) throws IOException {
        String collectionDir = StringUtils.substringBefore(filename, "_backup");

        Path crawlBaseDir = new Path(crawlDir.getAbsolutePath(), collectionDir);
        Path collectionBackupDir = new Path(crawlBaseDir, "backup");
        File backupFile = new File(new File(collectionBackupDir.toString()), filename);
        String fileData = FileUtils.readFileToString(backupFile, StandardCharsets.UTF_8);

        Map<String, String> fileInfo = new HashMap<>();
        fileInfo.put("filename", filename);
        fileInfo.put("data", fileData);

        return fileInfo;
    }

    private Collection restoreCollection(JSONObject collectionJson, boolean restoreUsers, JSONObject collectionStatus) throws Exception {

        collectionStatus.put("status", "created");

        Collection collection = mapper.readValue(collectionJson.toString(), Collection.class);

        Optional<Collection> existingCollection = collectionRepository.getByName(collection.getName());
        if (existingCollection.isPresent()) {
            collection.setId(existingCollection.get().getId());
            collectionStatus.put("status", "updated");
        }

        Set<Collection> includedCollections = collection.getIncludedCollections().stream()
                .map(includedCollection -> collectionRepository.getByName(includedCollection.getName())
                        .orElseGet(() -> {
                            collectionStatus.append("error", "Cannot restore the included collection '" + includedCollection.getName() + "'. The collection does not exist yet. Restore collection '" + includedCollection.getName() + "' separately and manually set it as part of this collection");
                            return null;
                        })
                )
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.debug("includedCollections size: {}", includedCollections.size());

        collection.setIncludedCollections(includedCollections);

        // check for owner and create or set the owner
        if (collection.getOwner() != null && restoreUsers) {
            Optional<User> owner = userRepository.findByUsername(collection.getOwner().getUsername());
            if (owner.isPresent()) {
                collection.setOwner(owner.get());
            } else {
                User newOwner = userRepository.saveAndFlush(collection.getOwner());
                collection.setOwner(newOwner);
            }
        } else {
            User newAdminOwner = userRepository.findByUsername("admin").get();
            collection.setOwner(newAdminOwner);
        }

        //remove the users for now we will handle them later
        Set<User> userSet = new HashSet<>();
        if (collection.getUsers().size() > 0) {
            userSet.addAll(collection.getUsers());
            collection.getUsers().clear();
        }

        collection = collectionRepository.save(collection);

        if (restoreUsers) {
            for (User user : userSet) {
                Optional<User> optionalUser = userRepository.findByUsername(user.getUsername());
                if (!optionalUser.isPresent()) {
                    log.warn("User not found! Creating user { id: {}, name: {}}  not found!", user.getId(), user.getUsername());
                    optionalUser = Optional.of(userRepository.saveAndFlush(user));
                }
                collection.getUsers().add(optionalUser.get());
                optionalUser.get().getCollections().add(collection);
                userRepository.save(optionalUser.get());
            }
        }

        return collectionRepository.saveAndFlush(collection);
    }

    private void restoreCrawlSchedulerJobInfo(long collectionId, JSONObject crawlSchedulerJobInfoJson, JSONObject crawlSchedulerJobInfoStatus) throws Exception {

        CrawlSchedulerJobInfo crawlSchedulerJobInfo = mapper.readValue(crawlSchedulerJobInfoJson.toString(), CrawlSchedulerJobInfo.class);
        crawlSchedulerJobInfo.setCollectionId(String.valueOf(collectionId));

        String jobName = crawlSchedulerJobInfo.getJobName();
        String jobGroup = crawlSchedulerJobInfo.getJobGroup();

        // One-time/ad-hoc jobs (custom crawls, ADD_URLS, CRAWL_NOW, etc.) use a non-cron SimpleTrigger
        // with a fixed startTime. jobType isn't a reliable signal here - it gets reset to
        // SCHEDULED_CRAWL after every run - but a non-cron job whose startTime has already passed
        // will misfire and re-run immediately under Quartz's default misfire policy if re-scheduled.
        // Skip re-scheduling these; the job's own status/config is still visible in the backup file.
        if (!crawlSchedulerJobInfo.isCronJob()) {
            log.warn("Skipping restore of one-time/ad-hoc crawl schedule job: {} (group: {}) - not re-scheduled to avoid re-firing a stale crawl.", jobName, jobGroup);
            crawlSchedulerJobInfoStatus.put("status", "skipped");
            crawlSchedulerJobInfoStatus.put("reason", "One-time/ad-hoc job - not re-scheduled on restore to avoid re-firing a stale crawl.");
            return;
        }

        Optional<Long> schedulerJobInfoId = crawlSchedulerJobInfoRepository.findIdByJobNameAndJobGroup(jobName, jobGroup);

        if (schedulerJobInfoId.isPresent()) {
            crawlSchedulerJobInfo.setId(schedulerJobInfoId.get());
        }

        boolean status = false;
        if (!jobService.isJobWithNamePresent(jobName, jobGroup)) {
            status = jobService.scheduleNewJob(crawlSchedulerJobInfo);
            crawlSchedulerJobInfoStatus.put("status", status ? "created" : "failed");
        } else {
            status = jobService.updateScheduleJob(crawlSchedulerJobInfo);
            crawlSchedulerJobInfoStatus.put("status",  status ? "updated" : "failed");
        }

        String jobStatus = crawlSchedulerJobInfo.getJobStatus();
        if (jobStatus != null && jobStatus.contains("PAUSED")) {
            status = jobService.pauseJob(jobName, jobGroup);
            crawlSchedulerJobInfoStatus.put("state", status ? "paused crawl schedule" : "failed to pause crawl schedule");
        }
    }
}

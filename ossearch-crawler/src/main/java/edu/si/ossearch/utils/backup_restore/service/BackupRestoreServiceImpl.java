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

    private JSONObject exportCrawlSchedulerJobInfo(String collectionName) throws JsonProcessingException {
        CrawlSchedulerJobInfo crawlSchedulerJobInfo = crawlSchedulerJobInfoRepository.findByCollectionName(collectionName);
        if (crawlSchedulerJobInfo != null) {
            ProjectionFactory crawlProjectionFactory = new SpelAwareProxyProjectionFactory();
            CrawlSchedulerJobInfoInfoExport crawlSchedulerJobInfoExport = crawlProjectionFactory.createProjection(CrawlSchedulerJobInfoInfoExport.class, crawlSchedulerJobInfo);
            return new JSONObject(getJsonString(crawlSchedulerJobInfoExport));
        } else {
            return new JSONObject();
        }
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

                JSONObject crawlSchedulerJobInfoStatus = new JSONObject();
                restoreStatus.put("crawlSchedulerJobInfo", crawlSchedulerJobInfoStatus);

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

                    try {
                        if (json.has("crawlSchedulerJobInfo") && !json.getJSONObject("crawlSchedulerJobInfo").isEmpty()) {
                            JSONObject crawlSchedulerJobInfoJson = json.getJSONObject("crawlSchedulerJobInfo");
                            restoreCrawlSchedulerJobInfo(collectionStatus.getLong("id"), crawlSchedulerJobInfoJson, crawlSchedulerJobInfoStatus);
                        } else {
                            crawlSchedulerJobInfoStatus.put("status", "N/A");
                        }
                    } catch (Exception e) {
                        log.error("Fail to restore crawlSchedulerJobInfo from file! {}", file.get("filename"), e);
                        crawlSchedulerJobInfoStatus.put("status", "failed");
                        crawlSchedulerJobInfoStatus.append("error", e.getMessage());
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

            try(ZipOutputStream zipOut = new ZipOutputStream(baos)) {

                for (Long id : ids) {
                    Optional<String> collectionName = collectionRepository.findCollectionById(id);

                    if (collectionName.isPresent()) {

                        String filePrefix = collectionName.get() + "_" + id + "_backup";
                        String filename = filePrefix + "_" + new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss").format(new Date()) + ".json";

                        JSONObject json = new JSONObject();

                        json.put("collection", exportCollection(id));

                        collectionName = Optional.ofNullable(json.getJSONObject("collection").getString("name"));

                        if (withCrawlSchedule) {
                            /*JSONObject crawlSchedulerJobInfo = exportCrawlSchedulerJobInfo(collectionName.get());
                            if (crawlSchedulerJobInfo.isEmpty()) {
                                json.put("crawlSchedulerJobInfo", "no crawl schedule available");
                            } else {
                                json.put("crawlSchedulerJobInfo", crawlSchedulerJobInfo);
                            }*/
                            json.put("crawlSchedulerJobInfo", exportCrawlSchedulerJobInfo(collectionName.get()));
                        }

                        if (!includeUsers) {
                            json.getJSONObject("collection").remove("owner");
                            json.getJSONObject("collection").remove("users");
                        }

                        saveLocalBackup(collectionName.get() + "_" + id, json);

                        byte[] bytes = json.toString(4).getBytes(StandardCharsets.UTF_8);

                        ZipEntry entry = new ZipEntry(filename);
                        entry.setSize(bytes.length);
                        zipOut.putNextEntry(entry);
                        zipOut.write(bytes);
                        zipOut.closeEntry();

                    } else {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bulk Backup Error! Collection not found!");
                    }

                }

                zipOut.finish();
                zipOut.close();

            } catch (Exception e) {
                log.error("Problem with bulk backup!", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Problem with bulk backup! Error: " + e.getMessage());
            }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipFilename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
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

        if (crawlSchedulerJobInfo.getJobStatus().contains("PAUSED")) {
            status = jobService.pauseJob(jobName, jobGroup);
            crawlSchedulerJobInfoStatus.put("state", status ? "paused crawl schedule" : "failed to pause crawl schedule");
        }
    }
}

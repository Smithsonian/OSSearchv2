package edu.si.ossearch.nutch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import edu.si.ossearch.OSSearchException;
import edu.si.ossearch.collection.entity.Collection;
import edu.si.ossearch.collection.entity.CrawlConfig;
import edu.si.ossearch.collection.entity.UrlExclusionPattern;
import edu.si.ossearch.collection.repository.CollectionRepository;
import edu.si.ossearch.collection.repository.CrawlConfigRepository;
import edu.si.ossearch.nutch.entity.Webpage;
import edu.si.ossearch.nutch.repository.CrawlDbRepository;
import edu.si.ossearch.nutch.repository.WebpageRepository;
import edu.si.ossearch.scheduler.entity.CrawlLog;
import edu.si.ossearch.scheduler.entity.CrawlSchedulerJobInfo;
import edu.si.ossearch.scheduler.entity.CrawlSchedulerJobInfo.JobType;
import edu.si.ossearch.scheduler.entity.CrawlStepLog;
import edu.si.ossearch.scheduler.repository.CrawlLogRepository;
import edu.si.ossearch.scheduler.repository.CrawlSchedulerJobInfoRepository;
import edu.si.ossearch.scheduler.repository.CrawlStepLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.nutch.crawl.*;
import org.apache.nutch.fetcher.Fetcher;
import org.apache.nutch.hostdb.UpdateHostDb;
import org.apache.nutch.indexer.IndexingJob;
import org.apache.nutch.parse.ParseSegment;
import org.apache.nutch.scoring.webgraph.WebGraph;
import org.apache.nutch.tools.FileDumper;
import org.apache.nutch.util.HadoopFSUtil;
import org.apache.nutch.util.NutchConfiguration;
import org.apache.nutch.util.SitemapProcessor;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.si.ossearch.scheduler.entity.CrawlLog.State.*;
import static edu.si.ossearch.scheduler.entity.CrawlLog.StepType.*;
import static org.apache.nutch.crawl.CrawlDb.CRAWLDB_ADDITIONS_ALLOWED;
import static org.apache.nutch.crawl.CrawlDbFilter.URL_FILTERING;
import static org.apache.nutch.crawl.Injector.URL_FILTER_NORMALIZE_ALL;
import static org.apache.nutch.urlfilter.regex.RegexURLFilter.URLFILTER_REGEX_FILE;
import static org.apache.nutch.urlfilter.regex.RegexURLFilter.URLFILTER_REGEX_RULES;

/**
 * @author jbirkhimer
 */
@Slf4j
@Component
@Scope("prototype")
@ConfigurationProperties(prefix = "oss.nutch")
public class Crawler {

    @Value(value = "${ossearch.nutch.pluginsDir}")
    @NonNull
    File pluginDir;

    @Value(value = "${ossearch.nutch.confDir}")
    @NonNull
    File nutchConfDir;

    @Value(value = "${ossearch.nutch.crawlDir}")
    File crawlDir;

    @Value(value = "${spring.data.solr.collection}")
    String solrCollection;

    @Autowired
    private CrawlSchedulerJobInfoRepository schedulerRepository;

    @Autowired
    private CrawlConfigRepository crawlConfigRepository;

    @Autowired
    private CrawlLogRepository crawlLogRepository;

    @Autowired
    private CrawlStepLogRepository crawlStepLogRepository;

    @Autowired
    private CollectionRepository collectionRepository;

    @Autowired
    @Qualifier("master")
    private SolrClient solrClient;

    @Autowired
    private CrawlDbRepository crawlDbRepository;

    @Autowired
    private WebpageRepository webpageRepository;

    protected AtomicBoolean stopFlag = new AtomicBoolean(false);
    protected AtomicBoolean errorFlag = new AtomicBoolean(false);

    private CrawlLog crawlLog;

    private CrawlSchedulerJobInfo jobInfo;
    private CrawlConfig crawlConfig;

    private Configuration conf;

    private Path crawlBaseDir;
    private Path dbDir;

    private Path crawldbDir;
    private Path seedDir;
    private Path sitemapDir;
    private Path segmentsDir;
    private Path linkdb;
    private Path hostdb;

    private Path webGraphDb;
    private int currentRound = 0;

    private enum SitemapFromHostDb{
        NEVER, ALWAYS, ONCE
    }

    private enum DedupeGroup {
        NONE, HOST, DOMAIN
    }

    //Crawl Options
    private boolean index = true;
    private long wait = 0;
    private String s;
    private String sm;
    private boolean hostdbupdate;
    private boolean hostdbgenerate;
    private int num_fetchers = 1;
    private int num_tasks = 2;
    private long size_fetchlist = 50000;
    private int time_limit_fetch = 180;
    private int num_threads = 50;
    private SitemapFromHostDb sitemaps_from_hostdb = SitemapFromHostDb.NEVER;
    private DedupeGroup dedup_group = DedupeGroup.NONE;

    private int maxNumRounds = 50;

    Path regexUrlFilterFile;

    private String crawlUrlFilterRules = "";
    private String indexUrlFilterRules = "";

    private boolean dump = false;

    private Date startSegmentGenerateDate;

    /**
     * Nutch crawl script workflow
     * 1) inject
     * 2) loop # rounds of generate > fetch > parse > updatedb > invertlinks > dedup > index
     *
     * See for examples:
     * - https://www.yegor256.com/2019/04/17/nutch-from-java.html
     * - https://github.com/yegor256/nutch-in-java/blob/master/pom.xml
     * - https://github.com/apache/nutch/blob/branch-1.7/src/java/org/apache/nutch/crawl/Crawl.java
     *
     * @throws Exception
     */
    public void crawl() throws OSSearchException {
        try {
            currentRound++;

            // Next, we have to point Nutch to the directory with the
            // text file and let it "inject" our URLs into its database:
            inject();

            if (sitemaps_from_hostdb.equals(SitemapFromHostDb.ALWAYS) || sitemaps_from_hostdb.equals(SitemapFromHostDb.ONCE)) {
                // Performs Sitemap processing by fetching sitemap links, parsing the content and merging the urls from Sitemap (with the metadata) with the existing crawldb.
                if (crawlConfig.getUseSitemap() && sitemapDir != null && !crawlConfig.getSitemapUrls().isEmpty()) {
                    hostdbUpdate();
                    sitemap(hostdb, sitemapDir);
                } else if (crawlConfig.getUseSitemap()) {
                    hostdbUpdate();
                    sitemap(hostdb, null);
                }
            }

            // Now, it's time to do a few cycles of fetching, parsing, and
            // updating. This is how Nutch works, in increments. Each increment
            // will bring new web pages to the database. The more increments
            // you run, the deeper Nutch will go into the site.
            // If you really want to crawl deeper, you will need hundreds of increments.
            while (currentRound <= maxNumRounds) {
                log.info("Crawl Iteration {}", currentRound);

                Boolean endCrawlCycle = cycle();

                if (stopFlag.get()) {
                    log.warn("Stopping at depth={} - interrupted", currentRound);
                    break;
                } else if (endCrawlCycle) {
                    log.info("Stopping at depth={} - no more URLs to fetch.", currentRound);
                    break;
                }
                currentRound++;
            }

            currentRound--;

            webgraph();

            // if a recrawl delete existing solr records and index all segments from the recrawl
            if (jobInfo.isRecrawl() || jobInfo.getJobType() == JobType.RECRAWL) {
                solrClient.deleteByQuery(solrCollection, "collectionID:" + jobInfo.getCollectionId(), 30000);

                final List<Path> list = Files.list(Paths.get(segmentsDir.toString()))
                        .map(p -> new Path(p.toString()))
                        .sorted(Comparator.comparing(Path::toString))
                        .collect(Collectors.toList());

                index(list.toArray(new Path[list.size()]));
            }

            deleteSegmentDirsOlderThanFetchInterval();

            if (!stopFlag.get()) {
                log.info("Crawl loop finished with {} iterations", currentRound);

                CrawlStepLog crawlStepLog = createCrawlStepLog(REPORTS, RUNNING);

                try {
                    crawlStepLogRepository.saveAndFlush(crawlStepLog);

                    getStats();
                    updateCrawldbWebpagesDb();

                    //TODO: update database with linkDb anchors/inlinks/outlinks

                    crawlStepLog.setState(FINISHED);
                    crawlStepLogRepository.saveAndFlush(crawlStepLog);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    crawlLog.setState(FAILED);
                    crawlLog.setErrors(e.getMessage());
                    crawlLogRepository.saveAndFlush(crawlLog);
                    errorFlag.set(true);
                    throw new OSSearchException(e);
                }
                crawlLog.setState(CrawlLog.State.FINISHED);
            } else {
                crawlLog.setState(STOPPED);
            }

            crawlLogRepository.save(crawlLog);

            if (dump && !stopFlag.get()) {
                // where to dump
                Path dumpDir = new Path(crawlBaseDir, "dump_" + getDate());

                // Now it's time to dump what is fetched to a new directory,
                // which will contain HTML pages and all other files when
                // finished.
                Files.createDirectory(Paths.get(dumpDir.toString()));
                new FileDumper().dump(new File(dumpDir.toString()), new File(segmentsDir.toString()), null, true, false, true);
            }

        } catch (Exception | OSSearchException e) {
            log.error(e.getMessage(), e);
            crawlLog.setState(FAILED);
            crawlLog.setErrors(e.getMessage());
            crawlLogRepository.saveAndFlush(crawlLog);
            errorFlag.set(true);
            throw new OSSearchException(e);
        }
    }

    /**
     * One Crawl Cycle.
     *
     * @throws IOException
     * @throws InterruptedException
     * @throws ClassNotFoundException
     */
    private Boolean cycle() throws OSSearchException {
        Path[] sgmt = null;

        if (sitemaps_from_hostdb.equals(SitemapFromHostDb.ALWAYS) || (sitemaps_from_hostdb.equals(SitemapFromHostDb.ONCE) && currentRound == 1)) {
            sitemapsFromHostDb();
        }

        startSegmentGenerateDate = new Date(System.currentTimeMillis());

        if (hostdbgenerate) {
            sgmt = generate(hostdb);
        } else {
            sgmt = generate(null);
        }

        if (!stopFlag.get()) {

            //TODO: test maxNumSegments > 1
            if (sgmt != null && sgmt.length > 1) {
                log.debug("Do we ever get more than one segment back during normal crawl.");

                // Then, we get the path of the current segment:
                //final Path sgmt = segment(segmentsDir);
            }

            if (sgmt == null) {
                log.info("no more URLs to fetch.");
                return true;
            }
        }

        if (!stopFlag.get()) {
            // Then, we fetch
            fetcher(sgmt);
        }

        if (!stopFlag.get()) {
            // parse
            parse(sgmt);
        }

        if (!stopFlag.get()) {
            // updatedb
            updatedb(sgmt);
        }

        if (!stopFlag.get() && hostdbupdate) {
            hostdbUpdate();
        }

        if (!stopFlag.get()) {
            // invertlinks
            invertlinks(sgmt);
        }

        if (!stopFlag.get()) {
            // dedup
            dedup();
        }

        if (!stopFlag.get()) {
            /*List<Path> segments = new ArrayList<>();
            segments.add(sgmt);*/

            if (!jobInfo.isRecrawl() || jobInfo.getJobType() != JobType.RECRAWL) {
                // index
                index(sgmt);
            }
        }

        return false;
    }

    private void inject() throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(INJECT, RUNNING);

        if (!stopFlag.get()) {
            try {

                // clone configuration for injector
                Configuration injectConf = new Configuration(conf);
                injectConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                //cmd line: /opt/nutch/runtime/local/bin/nutch inject -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch/seeds/siarchives/crawl/crawldb /opt/nutch/seeds/siarchives/seed.txt

                // full args inject(Path crawlDb, Path urlDir, boolean overwrite, boolean update, boolean normalize, boolean filter, boolean filterNormalizeAll)

                Map<String, String> injectArgs = jobInfo.getNutchStepArgs().getInject();
                Boolean overwrite = Boolean.parseBoolean(injectArgs.getOrDefault("overwrite", "false"));
                Boolean update = Boolean.parseBoolean(injectArgs.getOrDefault("update", "false"));
                Boolean normalize = !Boolean.parseBoolean(injectArgs.getOrDefault("nonormalize", "false"));
                Boolean filter = !Boolean.parseBoolean(injectArgs.getOrDefault("nofilter", "false"));
                Boolean filterNormalizeAll = Boolean.parseBoolean(injectArgs.getOrDefault("filterNormalizeAll", "false"));

                StringJoiner sj = new StringJoiner(", ");
                sj.add("crawldbDir: " + crawldbDir);
                sj.add("seedDir: " + seedDir);
                sj.add("overwrite: " + overwrite);
                sj.add("update: " + update);
                sj.add("normalize: " + normalize);
                sj.add("filter: " + filter);
                sj.add("filterNormalizeAll: " + filterNormalizeAll);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter || filterNormalizeAll || injectConf.getBoolean(URL_FILTERING, false) || injectConf.getBoolean(URL_FILTER_NORMALIZE_ALL, false)) {
                    injectConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                new Injector(injectConf).inject(crawldbDir, seedDir, overwrite, update, normalize, filter, filterNormalizeAll);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", INJECT);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void sitemap(Path hostdb, Path sitemapDir) throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(SITEMAP, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch-1.18/bin/nutch sitemap -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch-1.18/seeds/aaa_v2/db/crawldb -sitemapUrls /opt/nutch-1.18/seeds/aaa_v2/sitemap_urls -threads 150

                // clone configuration for sitemap
                Configuration sitemapConf = new Configuration(conf);
                sitemapConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> sitemapArgs = jobInfo.getNutchStepArgs().getSitemap();

                Boolean strict = !Boolean.parseBoolean(sitemapArgs.getOrDefault("noStrict", "false"));
                Boolean filter = !Boolean.parseBoolean(sitemapArgs.getOrDefault("noFilter", "false"));
                Boolean normalize = !Boolean.parseBoolean(sitemapArgs.getOrDefault("noNormalize", "false"));
                Integer threads = num_threads;

                if (sitemapArgs.containsKey("threads")) {
                    threads = Integer.parseInt(sitemapArgs.getOrDefault("threads", "8"));
                }

                StringJoiner sj = new StringJoiner(", ");
                sj.add("crawldbDir: " + crawldbDir);
                sj.add("hostdb: " + hostdb);
                sj.add("sitemapDir: " + sitemapDir);
                sj.add("strict: " + strict);
                sj.add("filter: " + filter);
                sj.add("normalize: " + normalize);
                sj.add("threads: " + threads);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter || sitemapConf.getBoolean("sitemap.url.filter", false)) {
                    sitemapConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                SitemapProcessor sitemapProcessor = new SitemapProcessor();
                sitemapProcessor.setConf(sitemapConf);
                sitemapProcessor.sitemap(crawldbDir, hostdb, sitemapDir, strict, filter, normalize, threads);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", SITEMAP);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void sitemapsFromHostDb() throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(SITEMAPS_FROM_HOSTDB, RUNNING);
        if (!stopFlag.get()) {
            if (currentRound == 1) {
                hostdbUpdate();
            }
            sitemap(hostdb, null);
            crawlStepLog.setState(FINISHED);
            crawlStepLogRepository.saveAndFlush(crawlStepLog);
        } else {
            log.info("STOPPING {}", SITEMAPS_FROM_HOSTDB);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void hostdbUpdate() throws OSSearchException {

        CrawlStepLog crawlStepLog = createCrawlStepLog(HOSTDB_UPDATE, RUNNING);

        if (!stopFlag.get()) {
            try {
                // Usage: UpdateHostDb -hostdb <hostdb> [-tophosts <tophosts>] [-crawldb <crawldb>] [-checkAll] [-checkFailed] [-checkNew] [-checkKnown] [-force] [-filter] [-normalize]

                // clone configuration for updateHostDb
                Configuration updateHostDbConf = new Configuration(conf);
                updateHostDbConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> hostdbUpdateArgs = jobInfo.getNutchStepArgs().getUpdatehostdb();

                //Path hostDb = null;
                //Path crawlDb = null;
                Path topHosts = null;

                Boolean checkAll = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("checkAll", "false"));
                Boolean checkFailed = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("checkFailed", "false"));
                Boolean checkNew = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("checkNew", "false"));
                Boolean checkKnown = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("checkKnown", "false"));
                Boolean force = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("force", "false"));
                Boolean filter = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("filter", "false"));
                Boolean normalize = Boolean.parseBoolean(hostdbUpdateArgs.getOrDefault("normalize", "false"));

                List<String> updateHostDb_args = new ArrayList<>();
                updateHostDb_args.add("-crawldb");
                updateHostDb_args.add(crawldbDir.toString());
                updateHostDb_args.add("-hostdb");
                updateHostDb_args.add(hostdb.toString());

                if (topHosts != null) {
                    updateHostDb_args.add("-tophosts");
                    updateHostDb_args.add(topHosts.toString());
                }

                if (checkAll) updateHostDb_args.add("-checkAll");
                if (checkFailed) updateHostDb_args.add("-checkFailed");
                if (checkNew) updateHostDb_args.add("-checkNew");
                if (checkKnown) updateHostDb_args.add("-checkKnown");
                if (force) updateHostDb_args.add("-force");
                if (filter) updateHostDb_args.add("-filter");
                if (normalize) updateHostDb_args.add("-normalize");

                crawlStepLog.setArgs(updateHostDb_args.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter || updateHostDbConf.getBoolean("hostdb.url.filter", false)) {
                    updateHostDbConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                UpdateHostDb updateHostDb = new UpdateHostDb();
                updateHostDb.setConf(updateHostDbConf);
                int updateHostDbResult = updateHostDb.run(updateHostDb_args.toArray(new String[updateHostDb_args.size()]));

                if (updateHostDbResult == 0) {
                    crawlStepLog.setState(FINISHED);
                    crawlStepLogRepository.saveAndFlush(crawlStepLog);
                } else {
                    crawlStepLog.setState(FAILED);
                    crawlStepLog.setErrors("UpdateHostDb returned " + updateHostDbResult + " see logs for more info at " + new Date());
                    crawlStepLogRepository.saveAndFlush(crawlStepLog);
                    errorFlag.set(true);
                }

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", HOSTDB_UPDATE);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private Path[] generate(Path hostdb) throws OSSearchException {
        Path[] sgmt = null;

        CrawlStepLog crawlStepLog = createCrawlStepLog(GENERATE, RUNNING);

        if (!stopFlag.get()) {
            try {

                // First, we generate a list of target URLs to fetch from:
                // cmd line: /opt/nutch/runtime/local/bin/nutch generate -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch/seeds/siarchives/crawl/crawldb /opt/nutch/seeds/siarchives/crawl/segments -topN 50000 -numFetchers 1 -noFilter

                // clone configuration for generator
                Configuration generatorConf = new Configuration(conf);
                generatorConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> generateArgs = jobInfo.getNutchStepArgs().getGenerate();

                int numFetchers = 1;
                long topN = size_fetchlist;

                if (generateArgs.containsKey("topN")) {
                    if (Long.parseLong(generateArgs.get("topN")) == -1) {
                        topN = Long.MAX_VALUE;
                    } else {
                        topN = Long.parseLong(generateArgs.get("topN"));
                    }
                }

                Boolean filter = !Boolean.parseBoolean(generateArgs.getOrDefault("noFilter", "false"));
                Boolean norm = !Boolean.parseBoolean(generateArgs.getOrDefault("noNorm", "false"));
                Boolean force = Boolean.parseBoolean(generateArgs.getOrDefault("force", "false"));
                String expr = generateArgs.getOrDefault("expr", null);
                int maxNumSegments = Integer.parseInt(generateArgs.getOrDefault("maxNumSegments", "1"));

                long curTime = System.currentTimeMillis();
                int addDays = Integer.parseInt(generateArgs.getOrDefault("adddays", "0"));
                if (addDays > 0) {
                    curTime += addDays * 1000L * 60 * 60 * 24;
                }

                StringJoiner sj = new StringJoiner(", ");
                sj.add("crawldbDir: " + crawldbDir);
                sj.add("segmentsDir: " + segmentsDir);
                sj.add("numFetchers: " + numFetchers);
                sj.add("topN: " + topN);
                sj.add("curTime: " + curTime);
                sj.add("filter: " + filter);
                sj.add("norm: " + norm);
                sj.add("force: " + force);
                sj.add("maxNumSegments: " + maxNumSegments);
                sj.add("expr: " + expr);
                sj.add("hostdb: " + hostdb);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter) {
                    generatorConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                // generate(Path dbDir, Path segments, int numFetchers, long topN, long curTime, boolean filter, boolean norm, boolean force, int maxNumSegments, String expr, String hostdb)

                sgmt = new Generator(generatorConf).generate(crawldbDir, segmentsDir, numFetchers, topN, curTime, filter, norm, force, maxNumSegments, expr, hostdb != null ? hostdb.toString() : null);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                return sgmt;
            } catch (IOException | InterruptedException | ClassNotFoundException e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }

        } else {
            log.info("STOPPING {}", GENERATE);
            updateCrawlStepLogStopped(crawlStepLog);
        }

        return sgmt;
    }

    private void fetcher(Path[] sgmt) throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(FETCH, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch/runtime/local/bin/nutch fetch -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true -D fetcher.timelimit.mins=180 /opt/nutch/seeds/siarchives/crawl/segments/20210602150709 -threads 50

                // clone configuration for fetcher
                Configuration fetcherConf = new Configuration(conf);
                fetcherConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> fetchArgs = jobInfo.getNutchStepArgs().getFetch();

                int threads = num_threads;

                if (fetchArgs.containsKey("threads")) {
                    threads = Integer.parseInt(fetchArgs.get("threads"));
                    fetcherConf.setInt("fetcher.threads.fetch", threads);
                }

                StringJoiner sj = new StringJoiner(", ");
                sj.add("segments: " + Arrays.asList(sgmt));
                sj.add("threads: " + threads);
                sj.add("params: " + fetchArgs);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (fetcherConf.getBoolean("fetcher.filter.urls", false)) {
                    fetcherConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                new Fetcher(fetcherConf).fetch(sgmt[0], threads);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", FETCH);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void parse(Path[] sgmt) throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(PARSE, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch/runtime/local/bin/nutch parse -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true -D mapreduce.task.skip.start.attempts=2 -D mapreduce.map.skip.maxrecords=1 /opt/nutch/seeds/siarchives/crawl/segments/20210602150709

                // clone configuration for parser to allow for parser only skipRecordsOptions
                Configuration parseConf = new Configuration(conf);
                parseConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                // enable the skipping of records for the parsing so that a dodgy document
                // so that it does not fail the full task
                // skipRecordsOptions
                parseConf.set("mapreduce.task.skip.start.attempts","2");
                parseConf.set("mapreduce.map.skip.maxrecords","1");

                Map<String, String> parseArgs = jobInfo.getNutchStepArgs().getParse();

                if (parseArgs.containsKey("noFilter")) {
                    parseConf.setBoolean("parse.filter.urls", false);
                } else if (parseArgs.containsKey("noNormalize")) {
                    parseConf.setBoolean("parse.normalize.urls", false);
                }

                StringJoiner sj = new StringJoiner(", ");
                sj.add("segments: " + Arrays.asList(sgmt));
                sj.add("params: " + parseArgs);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (parseConf.getBoolean("parse.filter.urls", false)) {
                    parseConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                new ParseSegment(parseConf).parse(sgmt[0]);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", PARSE);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void updatedb(Path[] sgmt) throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(CRAWLDB_UPDATE, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch/runtime/local/bin/nutch updatedb -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch/seeds/siarchives/crawl/crawldb /opt/nutch/seeds/siarchives/crawl/segments/20210602150709

                // clone configuration for crawlDb
                Configuration crawlDbConf = new Configuration(conf);
                crawlDbConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> updatedbArgs = jobInfo.getNutchStepArgs().getUpdatedb();

                boolean normalize = crawlDbConf.getBoolean(CrawlDbFilter.URL_NORMALIZING, false);
                boolean filter = crawlDbConf.getBoolean(URL_FILTERING, false);
                boolean additionsAllowed = crawlDbConf.getBoolean(CRAWLDB_ADDITIONS_ALLOWED, true);
                boolean force = false;

                if (updatedbArgs.containsKey("normalize")) {
                    normalize = Boolean.parseBoolean(updatedbArgs.get("normalize"));
                }
                if (updatedbArgs.containsKey("filter")) {
                    filter = Boolean.parseBoolean(updatedbArgs.get("filter"));
                }
                if (updatedbArgs.containsKey("noAdditions")) {
                    additionsAllowed = !Boolean.parseBoolean(updatedbArgs.get("noAdditions"));
                }
                if (updatedbArgs.containsKey("force")) {
                    force = Boolean.parseBoolean(updatedbArgs.get("force"));
                }

                StringJoiner sj = new StringJoiner(", ");
                sj.add("crawldbDir:" + crawldbDir);
                sj.add("sgmt:" + Arrays.asList(sgmt).toString());
                sj.add("normalize:" + normalize);
                sj.add("filter:" + filter);
                sj.add("additionsAllowed:" + additionsAllowed);
                sj.add("force:" + force);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter || crawlDbConf.getBoolean("crawldb.url.filters", false)) {
                    crawlDbConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                //TODO: update to use -dir <segments> | <seg1>...<segN>
                new CrawlDb(crawlDbConf).update(crawldbDir, sgmt, normalize, filter, additionsAllowed, force);
                //new CrawlDb(conf).update(crawldbDir, Files.list(Paths.get(segmentsDir.toString())).map(p -> new Path(p.toString())).toArray(Path[]::new), false, false);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", CRAWLDB_UPDATE);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void invertlinks(Path[] sgmt) throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(LINKDB_UPDATE, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch/runtime/local/bin/nutch invertlinks -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch/seeds/siarchives/crawl/linkdb /opt/nutch/seeds/siarchives/crawl/segments/20210602150709 -noNormalize -nofilter

                // clone configuration for linksDb
                Configuration linksDbConf = new Configuration(conf);
                linksDbConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> invertlinksArgs = jobInfo.getNutchStepArgs().getInvertlinks();

                boolean filter = !Boolean.parseBoolean(invertlinksArgs.getOrDefault("noFilter", "false"));
                boolean normalize = !Boolean.parseBoolean(invertlinksArgs.getOrDefault("noNormalize", "false"));
                boolean force = Boolean.parseBoolean(invertlinksArgs.getOrDefault("force", "false"));

                StringJoiner sj = new StringJoiner(", ");
                sj.add("linkdb: " + linkdb);
                sj.add("segments: " + Arrays.asList(sgmt).toString());
                sj.add("normalize: " + normalize);
                sj.add("filter: " + filter);
                sj.add("force: " + force);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter) {
                    linksDbConf.set(URLFILTER_REGEX_RULES, crawlUrlFilterRules);
                }

                //TODO: update to use -dir <segments> | <seg1>...<segN>
                new LinkDb(linksDbConf).invert(linkdb, sgmt, normalize, filter, force);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", LINKDB_UPDATE);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void dedup() throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(DEDUP, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch/runtime/local/bin/nutch dedup -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch/seeds/siarchives/crawl/crawldb -group none

                // clone configuration for dedupJob
                Configuration dedupJobConf = new Configuration(conf);
                dedupJobConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> dedupArgs = jobInfo.getNutchStepArgs().getDedup();

                String group = dedup_group.toString().toLowerCase();
                if (dedupArgs.containsKey("group")) {
                        dedupArgs.get("group");
                }

                String compareOrder = dedupArgs.getOrDefault("compareOrder", "score,fetchTime,urlLength");

                List<String> dedup_args = new ArrayList<>();
                dedup_args.add(crawldbDir.toString());
                dedup_args.add("-group");
                dedup_args.add(group);

                if (compareOrder.indexOf("score") == -1 || compareOrder.indexOf("fetchTime") == -1 || compareOrder.indexOf("urlLength") == -1) {
                    log.error("DeduplicationJob: compareOrder must contain score, fetchTime and urlLength.");
                } else {
                    dedup_args.add("-compareOrder");
                    dedup_args.add(compareOrder);
                }

                StringJoiner sj = new StringJoiner(", ");
                sj.add("params: " + dedup_args);

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                DeduplicationJob deduplicationJob = new DeduplicationJob();
                deduplicationJob.setConf(dedupJobConf);
                deduplicationJob.run(dedup_args.toArray(new String[dedup_args.size()]));

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", DEDUP);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void index(Path[] sgmt) throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(INDEX, RUNNING);

        if (!stopFlag.get()) {
            try {
                //cmd line: /opt/nutch/runtime/local/bin/nutch index -Dmapreduce.job.reduces=2 -Dmapreduce.reduce.speculative=false -Dmapreduce.map.speculative=false -Dmapreduce.map.output.compress=true /opt/nutch/seeds/siarchives/crawl/crawldb -linkdb /opt/nutch/seeds/siarchives/crawl/linkdb /opt/nutch/seeds/siarchives/crawl/segments/20210602150709 -deleteGone

                Configuration indexConf = new Configuration(conf);
                indexConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                Map<String, String> indexArgs = jobInfo.getNutchStepArgs().getIndex();

                boolean noCrawlDb = false;
                String params = indexArgs.getOrDefault("params", null);
                boolean noCommit = Boolean.parseBoolean(indexArgs.getOrDefault("noCommit", "false"));
                boolean deleteGone = Boolean.parseBoolean(indexArgs.getOrDefault("deleteGone", "false"));
                boolean filter = Boolean.parseBoolean(indexArgs.getOrDefault("filter", "false"));
                boolean normalize = Boolean.parseBoolean(indexArgs.getOrDefault("normalize", "false"));
                boolean addBinaryContent = Boolean.parseBoolean(indexArgs.getOrDefault("addBinaryContent", "false"));
                boolean base64 = Boolean.parseBoolean(indexArgs.getOrDefault("base64", "false"));

                StringJoiner sj = new StringJoiner(", ");
                sj.add("crawldbDir: " + crawldbDir);
                sj.add("linkdb: " + linkdb);
                sj.add("segments: " + Arrays.asList(sgmt));
                sj.add("noCommit: " + noCommit);
                sj.add("deleteGone: " + deleteGone);
                sj.add("params: " + params);
                sj.add("filter: " + filter);
                sj.add("normalize: " + normalize);
                sj.add("addBinaryContent: " + addBinaryContent);
                sj.add("base64: " + base64);

                // Seems to cause issues when indexing to solr failing on commit
                //conf.set("mapreduce.local.map.tasks.maximum", "1");
                //conf.set("mapreduce.local.reduce.tasks.maximum", "1");

                List<Collection> partOfCollections = collectionRepository.getPartOfCollectionsByCollectionId(Long.parseLong(jobInfo.getCollectionId()));

                if (partOfCollections.size() > 0) {
                    //List<String> collectionIds = partOfCollections.stream().map(Collection::getId).map(String::valueOf).collect(Collectors.toList());
                    List<Long> collectionIds = partOfCollections.stream().map(Collection::getId).collect(Collectors.toList());
                    collectionIds.add(Long.parseLong(jobInfo.getCollectionId()));
                    Collections.sort(collectionIds);

                    //Set the collecitionIds for indexing
                    indexConf.set("moreIndexingFilter.collectionIDs", StringUtils.join(collectionIds, ","));
                }

                sj.add("moreIndexingFilter.collectionIDs: " + indexConf.get("moreIndexingFilter.collectionIDs"));

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                /*if (regexUrlFilterFile != null) {
                    indexConf.set("urlfilter.regex.file", "regexUrlFilters/"+regexUrlFilterFile.getName());
                    filter = true;
                }
                log.info(">>>>> index using urlfilter.regex.file: {}", indexConf.get("urlfilter.regex.file"));*/

                if (filter) {
                    indexConf.set(URLFILTER_REGEX_RULES, indexUrlFilterRules);
                }

                new IndexingJob(indexConf).index(crawldbDir, linkdb, Arrays.asList(sgmt), noCommit, deleteGone, params, filter, normalize, addBinaryContent, base64);

                getRoundStats(crawlStepLog);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", INDEX);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    private void webgraph() throws OSSearchException {

        CrawlStepLog crawlStepLog = createCrawlStepLog(WEBGRAPH, RUNNING);

        if (!stopFlag.get()) {
            try {

                Configuration webgraphConf = new Configuration(conf);
                webgraphConf.set("nutch.conf.uuid", UUID.randomUUID().toString());

                webgraphConf.setBoolean("db.ignore.external.links", false);
                webgraphConf.setBoolean("db.ignore.internal.links", false);

                webgraphConf.setBoolean("link.ignore.internal.host", false);
                webgraphConf.setBoolean("link.ignore.internal.domain", false);

                webgraphConf.setBoolean("link.ignore.limit.page", false);
                webgraphConf.setBoolean("link.ignore.limit.domain", false);

                Map<String, String> webgraphArgs = jobInfo.getNutchStepArgs().getWebgraph();

                boolean filter = Boolean.parseBoolean(webgraphArgs.getOrDefault("filter", "false"));
                boolean normalize = Boolean.parseBoolean(webgraphArgs.getOrDefault("normalize", "false"));

                //Path[] segPaths = Files.list(Paths.get(segmentsDir.toString())).map(p -> new Path(p.toString())).toArray(Path[]::new);

                FileSystem fs = segmentsDir.getFileSystem(webgraphConf);
                FileStatus[] fstats = fs.listStatus(segmentsDir, HadoopFSUtil.getPassDirectoriesFilter(fs));
                Path[] segPaths = HadoopFSUtil.getPaths(fstats);

                StringJoiner sj = new StringJoiner(", ");
                sj.add("webgraphDb: " + webGraphDb);
                sj.add("segments: " + Arrays.asList(segPaths));
                sj.add("filter: " + filter);
                sj.add("normalize: " + normalize);
                sj.add("db.ignore.external.links: " + webgraphConf.getBoolean("db.ignore.external.links", false));
                sj.add("db.ignore.internal.links: " + webgraphConf.getBoolean("db.ignore.internal.links", false));
                sj.add("link.ignore.internal.host: " + webgraphConf.getBoolean("link.ignore.internal.host", false));
                sj.add("link.ignore.internal.domain: " + webgraphConf.getBoolean("link.ignore.internal.domain", false));
                sj.add("link.ignore.limit.page: " + webgraphConf.getBoolean("link.ignore.limit.page", false));
                sj.add("link.ignore.limit.domain: " + webgraphConf.getBoolean("link.ignore.limit.domain", false));

                crawlStepLog.setArgs(sj.toString());
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                if (filter || webgraphConf.getBoolean(WebGraph.OutlinkDb.URL_FILTERING, false)) {
                    webgraphConf.set(URLFILTER_REGEX_RULES, indexUrlFilterRules);
                }

                WebGraph webGraph = new WebGraph();
                webGraph.setConf(webgraphConf);

                webGraph.createWebGraph(webGraphDb, segPaths, normalize, filter);

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", WEBGRAPH);
            updateCrawlStepLogStopped(crawlStepLog);
        }

    }

    public void reindex() throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(REINDEX, RUNNING);

        if (!stopFlag.get()) {
            try {
                solrClient.deleteByQuery(solrCollection, "collectionID:" + jobInfo.getCollectionId(), 30000);

                final List<Path> list = Files.list(Paths.get(segmentsDir.toString()))
                        .map(p -> new Path(p.toString()))
                        .sorted(Comparator.comparing(Path::toString))
                        .collect(Collectors.toList());

                index(list.toArray(new Path[list.size()]));

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

                getStats();
                //updateCrawldbWebpagesDb();

                crawlLog.setState(CrawlLog.State.FINISHED);
                crawlLogRepository.saveAndFlush(crawlLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
//                jobInfo.setReindex(false);
//                jobInfo.setJobType(SCHEDULED_CRAWL);
//                schedulerRepository.save(jobInfo);
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", REINDEX);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    public void reCrawl() throws OSSearchException {
        CrawlStepLog crawlStepLog = createCrawlStepLog(RECRAWL, RUNNING);

        if (!stopFlag.get()) {
            try {
                if (new File(dbDir.toString()).exists()) {
                    FileUtils.moveDirectory(new File(dbDir.toString()), new File(dbDir + "_backup_" + new Date().getTime()));
                }

                Optional<List<Webpage>> webpages = webpageRepository.findAllByCrawlDb_CollectionId(Integer.valueOf(jobInfo.getCollectionId()));

                webpageRepository.deleteAll(webpages.get());;

                crawl();

                crawlStepLog.setState(FINISHED);
                crawlStepLogRepository.saveAndFlush(crawlStepLog);

            } catch (Exception e) {
                log.error(e.getMessage(), e);
                updateCrawlStepLogError(crawlStepLog, e.getMessage());
//                jobInfo.setRecrawl(false);
//                jobInfo.setJobType(SCHEDULED_CRAWL);
//                schedulerRepository.save(jobInfo);
                throw new OSSearchException(e);
            }
        } else {
            log.info("STOPPING {}", RECRAWL);
            updateCrawlStepLogStopped(crawlStepLog);
        }
    }

    /**
     * Cleanup segments older than db.fetch.interval.default [default: 2592000 sec (30 days)] or db.fetch.interval.max [default: 7776000 sec (90 days)]
     * @throws OSSearchException
     */
    private void deleteSegmentDirsOlderThanFetchInterval() throws OSSearchException {
        Integer fetchInterval = conf.getInt("db.fetch.interval.default", 2592000);

        log.info("Crawl removing segments older than fetch interval = {} sec");

        CrawlStepLog crawlStepLog = createCrawlStepLog(CLEANUP, RUNNING);

        try {

            Date startDateMinusFetchInterval = DateUtils.addSeconds(startSegmentGenerateDate, -fetchInterval);

            File folder =  new File(segmentsDir.toString());
            String[] directories = folder.list((current, name) -> new File(current, name).isDirectory());

            JSONArray segmentsDeleted = new JSONArray();
            JSONArray errors = new JSONArray();

            for (String dir : directories) {
                log.debug("checking segment dir older than fetch interval: {}", dir);

                Date segmentDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(dir);

                if (segmentDate.compareTo(startDateMinusFetchInterval) < 0) {
                    File directory = new File(folder + "/" +dir);
                    log.debug("deleting old segment dir: {}", directory.getAbsolutePath());
                    try {
                        FileUtils.deleteDirectory(directory);
                        segmentsDeleted.put(directory.getName());
                    } catch (IOException e) {
                        errors.put(directory.getName() + " Error: "+e.getMessage());
                        log.error("Problem deleting old segment dir: {}", directory, e);
                    }
                }
            }

            StringJoiner sj = new StringJoiner(", ");
            sj.add("segmentDir: " + segmentsDir.toString());
            sj.add("segmentsDeleted: " + segmentsDeleted);
            crawlStepLog.setArgs(sj.toString());
            if (!errors.isEmpty()) {
                crawlStepLog.setErrors("Problem deleting old segment dir(s): " + errors);
            }
            crawlStepLog.setState(FINISHED);
            crawlStepLogRepository.saveAndFlush(crawlStepLog);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            updateCrawlStepLogError(crawlStepLog, e.getMessage());
            throw new OSSearchException(e);
        }
    }

    private void getStats() throws Exception, OSSearchException {

        Map<String, String> args = new HashMap<>();
        args.put("sort", "false");

        CrawlDbReader dbr = new CrawlDbReader();
        Map<String, Object> results = new HashMap<>();
        results = (Map<String, Object>) dbr.query(args, conf, "stats", dbDir.toString());
        crawlLog.setDbStats(getJsonString(results));


        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        query.setFilterQueries("collectionID:" + jobInfo.getCollectionId());
        query.setRows(0);
        QueryResponse rsp = solrClient.query(solrCollection, query);
        crawlLog.setSolrCount(rsp.getResults().getNumFound());
    }

    private void getRoundStats(CrawlStepLog crawlStepLog) throws Exception {
        Map<String, String> args = new HashMap<>();
        args.put("sort", "false");

        CrawlDbReader dbr = new CrawlDbReader();
        Map<String, Object> results = new HashMap<>();
        results = (Map<String, Object>) dbr.query(args, conf, "stats", dbDir.toString());
        crawlStepLog.setDbStats(getJsonString(results));


        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        query.setFilterQueries("collectionID:" + jobInfo.getCollectionId());
        query.setRows(0);
        QueryResponse rsp = solrClient.query(solrCollection, query);
        crawlStepLog.setSolrCount(rsp.getResults().getNumFound());
    }

    private void updateCrawldbWebpagesDb() {

        edu.si.ossearch.nutch.entity.CrawlDb savedCrawldb = crawlDbRepository.findCrawlDbByCollectionId(Integer.valueOf(jobInfo.getCollectionId()))
                .orElseGet(() -> {
                    edu.si.ossearch.nutch.entity.CrawlDb crawlDb = new edu.si.ossearch.nutch.entity.CrawlDb();
                    crawlDb.setCollectionId(Integer.valueOf(jobInfo.getCollectionId()));
                    edu.si.ossearch.nutch.entity.CrawlDb newCrawldb = crawlDbRepository.saveAndFlush(crawlDb);
                    return newCrawldb;
                });

        NutchCrawldbUtils crawldbUtils = new NutchCrawldbUtils();

        List<Webpage> webpageList = crawldbUtils.dumpCrawlDatumEntityList(crawldbDir, conf, savedCrawldb);

        List<String> newUUIDList = new ArrayList<>();
        webpageList.stream().forEach(webpage -> newUUIDList.add(webpage.getId()));

        Optional<List<String>> currentUUIDsInDb = webpageRepository.findAllUrlUuidsByCrawlDb_CollectionId(Integer.valueOf(jobInfo.getCollectionId()));

        List<String> webpageIdsForDelete = new ArrayList<>((CollectionUtils.removeAll(currentUUIDsInDb.get(), newUUIDList)));

        webpageRepository.deleteAllById(webpageIdsForDelete);

        webpageRepository.saveAll(webpageList);
    }

    /**
     * Get segment to work with.
     * @param segmentsDir
     * @return
     * @throws IOException
     */
    private static Path segment(final Path segmentsDir) throws IOException {
        // Get the path of the most recent segment in the list,
        // sorted by the date/time of their creation.
        final List<Path> list = Files.list(Paths.get(segmentsDir.toString()))
                .map(p -> new Path(p.toString()))
                .sorted(Comparator.comparing(Path::toString))
                .collect(Collectors.toList());
        return list.get(list.size() - 1);
    }

    private static String getDate() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format
                (new Date(System.currentTimeMillis()));
    }

    private void logConf(Configuration conf) {
//        String format3cols = "%-32s  %24s  %20s";

        List<Map.Entry<String, String>> list = new ArrayList<>();
        conf.iterator().forEachRemaining(list::add);
        list.sort(Map.Entry.comparingByKey());

        JSONArray configList = new JSONArray();
//        System.out.println(String.format(format3cols, "conf.name", "conf.value", "substituted.value"));
//        System.out.println("================================================================================");
        for (Map.Entry<String, String> e : list) {
            String key = e.getKey();
            String val = e.getValue();
            String substitutedVal = conf.get(key);
            JSONObject config = new JSONObject();
            config.put("name", key);
            if (val.equals(substitutedVal)) {
                config.put("value", val);
//                String format = String.format("%%-%ds  %%%ds", key.length(), (80 - 2 - key.length()));
//                System.out.println(String.format(format, key, val));
            } else {
                config.put("value", val);
                config.put("substituted.value", substitutedVal);
//                String format = String.format("%%-%ds  %%%ds  %%18s", key.length(), (60 - 2 - key.length()));
//                System.out.println(String.format(format, key, val, substitutedVal));
            }
            configList.put(config);
        }

        log.info("Nutch Properties:\n{}", configList.toString(4));
    }

    public void interrupt(boolean stop) {
        System.out.println("Stopping crawl... ");
        stopFlag.set(stop);
    }

    public void config(String jobId, JobKey jobKey, JobType jobType) throws OSSearchException {

        crawlLog = new CrawlLog();
        crawlLog.setJobKey(jobKey.toString());
        crawlLog.setJobId(jobId);
        crawlLog.setJobType(jobType);

        CrawlStepLog crawlStepLog = createCrawlStepLog(INITIALIZE, RUNNING);

        try {
            jobInfo = schedulerRepository.findByJobNameAndJobGroup(jobKey.getName(), jobKey.getGroup());
            crawlConfig = crawlConfigRepository.getCrawlConfigByCollectionName(jobInfo.getCollectionName());
            maxNumRounds = jobInfo.getNumberOfRounds();

            crawlLog.setRounds(maxNumRounds);
            crawlLog.setJobConfig(getJsonString(jobInfo));
            JSONObject crawlConfigJson = new JSONObject(getJsonString(crawlConfig));
            crawlConfigJson.remove("includeSiteUrls");
            crawlConfigJson.remove("excludeSiteUrls");
            crawlLog.setCrawlConfig(crawlConfigJson.toString());
            crawlLogRepository.saveAndFlush(crawlLog);

            setUpDirectories();

            setupCrawlOptions();

            setupConfigs();

//            logConf(conf);

            setupSeedFile();

            setupSitemapSeedFile();

            setupRegexUrlFilterRules(jobId, jobKey);

            crawlStepLog.setState(FINISHED);
            crawlStepLogRepository.saveAndFlush(crawlStepLog);
        } catch (OSSearchException | Exception e) {
            log.error("Error: jobKey, {}, jobId: {}, error :{}", jobKey, jobId, e.getMessage(), e);
            updateCrawlStepLogError(crawlStepLog, e.getMessage());
            throw new OSSearchException(e);
        }
    }

    private void setupConfigs() throws OSSearchException {
        try {
            Properties properties = new Properties();
            properties.putAll(jobInfo.getNutchProperties());

//            addConf(nutchConfDir);

            // Create a default configuration object, which will read
            // the content of nutch-default.xml file from the classpath,
            // parse it and take its entire content as the default
            // configuration. Funny, but this interface is from Hadoop:
            //        Configuration conf = NutchConfiguration.create();
            conf = NutchConfiguration.create(true, properties);

            log.info(">>>>> http.agent.name: {}", conf.get("http.agent.name"));

            // Now it's possible to reset some configuration parameters
            // by using this set() method. This one is mandatory, if you
            // don't set it the crawling won't work. The value is used
            // as User-Agent HTTP header.
            conf.set("hadoop.log.dir", "logs");
            conf.set("hadoop.tmp.dir", "hadoop_tmp");
            conf.set("hadoop.log.file", "logs/hadoop.log");

            // the directory for screenshot files
            Path screenshotDir = new Path("screenshots", "collectionID_"+jobInfo.getCollectionId());
            conf.set("screenshot.location", String.valueOf(screenshotDir));

            // note that some of the options listed here could be set in the
            // corresponding hadoop site xml param file
            //common options
            conf.set("mapreduce.job.reduces", String.valueOf(num_tasks));
            conf.set("mapreduce.reduce.speculative", "false");
            conf.set("mapreduce.map.speculative", "false");
            conf.set("mapreduce.map.output.compress", "true");

            conf.set("fetcher.timelimit.mins", String.valueOf(time_limit_fetch));

            //Set the collecitionId for indexing
            conf.set("moreIndexingFilter.collectionIDs", jobInfo.getCollectionId());

            // This one is also mandatory and we will discuss its
            // value below. You need to point Nutch to the directory
            // with compiled plugins and this collection is NOT in
            // its default JAR package, unfortunately.
            //File pluginDir = new File(System.getProperty("nutch.plugins.dir"));
            if (!Files.exists(pluginDir.toPath())) {
                throw new OSSearchException("Nutch Plugins directory " + pluginDir.getCanonicalPath() + " does not exist! Make sure to build nutch-plugins module first");
            }
            conf.set("plugin.folders", pluginDir.getCanonicalPath());

            // Seems to cause issues when indexing to solr failing on commit
            //conf.set("mapreduce.local.map.tasks.maximum", "2");
            //conf.set("mapreduce.local.reduce.tasks.maximum", "2");

            //Name of file on CLASSPATH containing regular expressions used by urlfilter-regex (RegexURLFilter) plugin. Can also use urlfilter.regex.file property
//            conf.set("urlfilter.regex.file", "conf/regex-urlfilter.txt");
//            conf.set("indexer.indexwriters.file", "conf/index-writers.xml");
//            conf.set("urlnormalizer.regex.file", "conf/regex-normalize.xml");

        } catch (Exception e) {
            e.printStackTrace();
            throw new OSSearchException(e);
        } catch (OSSearchException e) {
            e.printStackTrace();
            throw new OSSearchException(e.getMessage());
        }
    }

    private void setupCrawlOptions() throws OSSearchException {

        try {
            Map<String, String> crawlOptions = jobInfo.getCrawlOptions();

            index = Boolean.parseBoolean(crawlOptions.getOrDefault("index", "true"));

            if (crawlOptions.containsKey("wait")) {
                wait = Duration.parse("p" + crawlOptions.get("wait")).toMillis();
            } else {
                wait = -1;
            }

            if (crawlOptions.containsKey("s")) {
                log.warn("Ignoring seed dir. using default {}", seedDir);
            }

            if (crawlOptions.containsKey("sm")) {
                log.warn("Ignoring sitemap dir. using default {}", sitemapDir);
            }

            hostdbupdate = Boolean.parseBoolean(crawlOptions.getOrDefault("hostdbupdate", "false"));

            hostdbgenerate = Boolean.parseBoolean(crawlOptions.getOrDefault("hostdbgenerate", "false"));

            num_fetchers = Integer.parseInt(crawlOptions.getOrDefault("num_fetchers", "1"));

            num_tasks = Integer.parseInt(crawlOptions.getOrDefault("num_tasks", "2"));

            if (crawlOptions.containsKey("size_fetchlist")) {
                if (Integer.parseInt(crawlOptions.get("size_fetchlist")) == -1) {
                    size_fetchlist = Long.MAX_VALUE;
                } else {
                    size_fetchlist = Long.parseLong(crawlOptions.getOrDefault("size_fetchlist", "50000"));
                }
            }

            time_limit_fetch = Integer.parseInt(crawlOptions.getOrDefault("time_limit_fetch", "180"));

            num_threads = Integer.parseInt(crawlOptions.getOrDefault("num_threads", "50"));

            sitemaps_from_hostdb = SitemapFromHostDb.valueOf(crawlOptions.getOrDefault("sitemaps_from_hostdb", "never").toUpperCase());

            dedup_group = DedupeGroup.valueOf(crawlOptions.getOrDefault("dedup_group", "none").toUpperCase());
        } catch (DateTimeParseException e) {
            log.error("Problem parsing wait crawl option using default wait = {}!", wait, e);
            throw new OSSearchException("Problem parsing wait crawl option using default wait = "+wait+"!", e);
        } catch (Exception e) {
            throw new OSSearchException(e);
        }
    }

    private void setUpDirectories() throws OSSearchException {
        try {

            // First, we need to have a directory where everything will happen.
            String collectionCrawlDir = jobInfo.getCollectionName() + "_" + jobInfo.getCollectionId();
            if (!jobInfo.getCollectionName().equals(jobInfo.getJobName())) {
                collectionCrawlDir = jobInfo.getJobName() + "_" + jobInfo.getCollectionName() + "_" + jobInfo.getCollectionId();
            }
            crawlBaseDir = new Path(crawlDir.getAbsolutePath(), collectionCrawlDir);

            log.warn("crawl starting in {}", crawlBaseDir);

            dbDir = new Path(crawlBaseDir, "db");

            // the directory with its database
            crawldbDir = new Path(dbDir, "crawldb");

            // the directory with text files with URLs
            seedDir = new Path(crawlBaseDir, "seeds");

            // the directory with text files with URLs
            if (!crawlConfig.getSitemapUrls().isEmpty()) {
                sitemapDir = new Path(crawlBaseDir, "sitemaps");
            }

            // This is the directory with "segments". Each fetching cycle
            // will produce its own collection of files. Each collection
            // is called a segment.
            segmentsDir = new Path(dbDir, "segments");

            // the linkdb directory:
            linkdb = new Path(dbDir, "linkdb");

            // the hostdb directory
            hostdb = new Path(dbDir, "hostdb");

            // the webgraph directory
            webGraphDb = new Path(dbDir, "webgraph");
        } catch (Exception e) {
            throw new OSSearchException(e);
        }
    }

    private void setupSeedFile() throws OSSearchException {

        try {
            Set<String> seedUrls = crawlConfig.getSeedUrls();

            if (seedUrls.isEmpty()) {
                throw new OSSearchException("CrawlConfig seedsUrls is empty!");
            }

            // Next, we have to create a file with a list of URLs Nutch will
            // start crawling from:
            Files.createDirectories(Paths.get(seedDir.toString()));
            Files.write(Paths.get(seedDir.toString(), "seeds.txt"), String.join("\n", seedUrls).getBytes());
        } catch (OSSearchException | IOException e) {
            throw new OSSearchException(e);
        }
    }

    private void setupSitemapSeedFile() throws OSSearchException, IOException {
        Set<String> sitemapUrls = crawlConfig.getSitemapUrls();

        sitemapUrls.remove("./sitemap.xml (default)");

        if (!sitemapUrls.isEmpty()) {
            // we have to create a file with a list of sitemap URLs
            Files.createDirectories(Paths.get(sitemapDir.toString()));
            Files.write(Paths.get(sitemapDir.toString(), "sitemaps.txt"), String.join("\n", sitemapUrls).getBytes());
        }
    }

    private void setupRegexUrlFilterRules(String jobId, JobKey jobKey) throws OSSearchException, Exception {

        Set<String> rules = new LinkedHashSet<>();

        String defaultFileRules = "regex-urlfilter.txt";
        rules.addAll(readFileRules(conf.getConfResourceAsReader(defaultFileRules)));

        log.debug("default regex urlfilter rules: {}", defaultFileRules);

        String customFileRules = conf.get(URLFILTER_REGEX_FILE);
        rules.addAll(readFileRules(conf.getConfResourceAsReader(customFileRules)));

        log.debug("custom file regex urlfilter rules: {}", customFileRules);

        crawlConfig.getExcludeSiteUrls().forEach(rule -> {
            rules.add("-^"+rule.trim()+"$");
        });

        Set<String> crawlRules = new LinkedHashSet<>();
        Set<String> indexRules = new LinkedHashSet<>();

        crawlRules.addAll(rules);
        indexRules.addAll(rules);

        crawlConfig.getUrlExclusionPatterns().forEach(urlExclusionPattern -> {
            String rule = urlExclusionPattern.getExpression().trim();

            if (urlExclusionPattern.getType() == UrlExclusionPattern.Type.contains) {
             rule = "^.*"+Pattern.quote(rule)+".*$";
            }

            if (urlExclusionPattern.getIgnoreCase()) {
                rule = "(?i)"+rule+"(?-i)";
            }

            switch(urlExclusionPattern.getScope()) {
                case index:
                    indexRules.add("-"+rule);
                    break;
                case crawl:
                    crawlRules.add("-"+rule);
                    break;
                case all:
                    indexRules.add("-"+rule);
                    crawlRules.add("-"+rule);
                    break;
            }
        });

        //accept anything else
        indexRules.add("+.");
        crawlRules.add("+.");

        crawlUrlFilterRules = crawlRules.stream().collect(Collectors.joining("\n"));
        indexUrlFilterRules = indexRules.stream().collect(Collectors.joining("\n"));
    }

    /**
     * Read the specified file of rules.
     *
     * @param reader
     *          is a reader of regular expressions rules.
     * @return the corresponding {@RegexRule rules}.
     */
    private List<String> readFileRules(Reader reader) throws IOException,
            IllegalArgumentException {

        BufferedReader in = new BufferedReader(reader);
        List<String> rules = new ArrayList<>();
        String line;

        while ((line = in.readLine()) != null) {
            if (line.length() == 0) {
                continue;
            }
            char first = line.charAt(0);
            switch (first) {
                case ' ':
                case '\n':
                case '#': // skip blank & comment lines
                    continue;
            }

            //skip accept/reject anything else regex we will add that later
            if (line.equals("+.") || line.equals("-.")) {
                continue;
            }

            log.debug("Adding rule [" + line + "]");
            rules.add(line);
        }
        return rules;
    }

    private String getJsonString(Object entity) throws JsonProcessingException {
        ObjectMapper mapper = new JsonMapper();
        return mapper.writeValueAsString(entity);
    }

    private CrawlStepLog createCrawlStepLog(CrawlLog.StepType step, CrawlLog.State state) {
        crawlLog.setState(RUNNING);
        crawlLog.setCurrentStep(step);
        crawlLog.setCurrentRound(currentRound);
        crawlLogRepository.saveAndFlush(crawlLog);

        CrawlStepLog crawlStepLog = new CrawlStepLog();
        crawlStepLog.setCrawlLog(crawlLog);
        crawlStepLog.setJobId(crawlLog.getJobId());
        crawlStepLog.setJobKey(crawlLog.getJobKey());
        crawlStepLog.setRound(currentRound);
        crawlStepLog.setStepType(step);
        crawlStepLog.setState(RUNNING);
        crawlStepLogRepository.saveAndFlush(crawlStepLog);

        return crawlStepLog;
    }

    private void updateCrawlStepLogStopped(CrawlStepLog crawlStepLog) {
        crawlStepLog.setState(STOPPED);
        crawlLog.setState(STOPPED);
        crawlLogRepository.saveAndFlush(crawlLog);
    }

    private void updateCrawlStepLogError(CrawlStepLog crawlStepLog, String errorMessage) {
        crawlStepLog.setErrors(errorMessage);
        crawlStepLog.setState(FAILED);
        crawlStepLogRepository.saveAndFlush(crawlStepLog);

        crawlLog.setState(FAILED);
        crawlLog.setErrors(errorMessage);
        crawlLogRepository.saveAndFlush(crawlLog);
        errorFlag.set(true);
    }

    public void addConf(File nutchConfigPath) throws Exception {
        for (File confFile : nutchConfigPath.listFiles()) {
            log.info("Adding nutch conf: {}", confFile.getCanonicalPath());
            addPath(confFile.getCanonicalPath());
        }
    }

    //need to do add path to Classpath with reflection since the URLClassLoader.addURL(URL url) method is protected:
    public static void addPath(String s) throws Exception {
        File f = new File(s);
        URI u = f.toURI();
        URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<URLClassLoader> urlClass = URLClassLoader.class;
        Method method = urlClass.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        method.invoke(urlClassLoader, new Object[]{u.toURL()});
    }
}

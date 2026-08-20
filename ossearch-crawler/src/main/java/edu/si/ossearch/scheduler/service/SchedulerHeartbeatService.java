package edu.si.ossearch.scheduler.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes Quartz scheduler liveness through the shared database so any
 * application server behind the load balancer can report cluster-wide
 * scheduler status. Quartz runs NON_CLUSTERED on a single node by design
 * (two active schedulers on the shared QRTZ_* tables would double-fire
 * crawls), so a node's local scheduler state says nothing about whether the
 * system as a whole has a running scheduler — this heartbeat does.
 *
 * Freshness is judged with the database's own clock on both write and read,
 * so node clock skew cannot produce false positives/negatives.
 *
 * @author jbirkhimer
 */
@Slf4j
@Service
public class SchedulerHeartbeatService {

    /** Heartbeats older than this are considered dead (3x the beat interval). */
    private static final int STALE_AFTER_SECONDS = 90;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Scheduler scheduler;

    private final String hostname = resolveHostname();

    @PostConstruct
    void createTableIfMissing() {
        // CREATE TABLE IF NOT EXISTS instead of a JPA entity so behavior does
        // not depend on the hibernate ddl-auto setting of the environment.
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS scheduler_heartbeat (" +
                "hostname VARCHAR(255) NOT NULL PRIMARY KEY, " +
                "last_heartbeat TIMESTAMP(3) NOT NULL)");
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 15_000)
    public void beat() {
        try {
            if (scheduler.isStarted() && !scheduler.isInStandbyMode()) {
                jdbcTemplate.update(
                        "INSERT INTO scheduler_heartbeat (hostname, last_heartbeat) VALUES (?, CURRENT_TIMESTAMP(3)) " +
                        "ON DUPLICATE KEY UPDATE last_heartbeat = CURRENT_TIMESTAMP(3)", hostname);
            }
        } catch (Exception e) {
            log.warn("scheduler heartbeat failed: {}", e.getMessage());
        }
    }

    /**
     * Cluster-wide scheduler status derived from heartbeat freshness:
     * {@code active} (any live scheduler) and {@code activeNodes} (hostnames
     * with a fresh heartbeat — more than one means a misconfiguration where
     * multiple non-clustered schedulers share the same QRTZ_* tables).
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        try {
            List<String> activeNodes = jdbcTemplate.queryForList(
                    "SELECT hostname FROM scheduler_heartbeat " +
                    "WHERE last_heartbeat > (CURRENT_TIMESTAMP(3) - INTERVAL " + STALE_AFTER_SECONDS + " SECOND) " +
                    "ORDER BY hostname", String.class);
            status.put("active", !activeNodes.isEmpty());
            status.put("activeNodes", activeNodes);
        } catch (Exception e) {
            log.warn("scheduler heartbeat status query failed: {}", e.getMessage());
            status.put("active", false);
            status.put("activeNodes", List.of());
            status.put("error", e.getMessage());
        }
        return status;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}

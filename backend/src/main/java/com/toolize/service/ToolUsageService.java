package com.toolize.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks how often each generated MCP tool is actually called - by a real
 * MCP client via {@code /mcp} tools/call, or from the "Execute" button in
 * the UI - so users can see which tools their agents actually use.
 *
 * Counters live in memory and are only flushed to disk periodically: this
 * is observability data, not the source of truth, so it isn't worth paying
 * a full-file rewrite (like {@link ProjectPersistenceService} does) on every
 * single tool call.
 */
@Service
public class ToolUsageService {

    private static final Logger log = LoggerFactory.getLogger(ToolUsageService.class);
    private static final int MAX_RECENT_CALLS = 20;

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ConcurrentHashMap<String, LiveStats> stats = new ConcurrentHashMap<>();

    @Value("${toolize.data-dir:/data}")
    private String dataDir;

    public record CallRecord(Instant timestamp, int status, long latencyMs) {
    }

    public record ToolStatsView(long totalCalls, long errorCalls, double errorRate, Instant lastCalledAt,
                                 Integer lastStatus, Double avgLatencyMs, List<CallRecord> recentCalls) {
    }

    public record ProjectStatsSummary(long totalCalls, long errorCalls, Instant lastCalledAt) {
    }

    private static class LiveStats {
        final LongAdder totalCalls = new LongAdder();
        final LongAdder errorCalls = new LongAdder();
        final LongAdder totalLatencyMs = new LongAdder();
        final AtomicLong lastCalledAtMillis = new AtomicLong(0);
        final AtomicInteger lastStatus = new AtomicInteger(0);
        final Deque<CallRecord> recentCalls = new ArrayDeque<>();

        synchronized void record(int status, long latencyMs) {
            totalCalls.increment();
            if (status >= 400) {
                errorCalls.increment();
            }
            totalLatencyMs.add(latencyMs);
            lastCalledAtMillis.set(System.currentTimeMillis());
            lastStatus.set(status);

            recentCalls.addLast(new CallRecord(Instant.now(), status, latencyMs));
            while (recentCalls.size() > MAX_RECENT_CALLS) {
                recentCalls.pollFirst();
            }
        }

        synchronized ToolStatsView toView() {
            long total = totalCalls.sum();
            long errors = errorCalls.sum();
            double errorRate = total == 0 ? 0.0 : (double) errors / total;
            double avgLatency = total == 0 ? 0.0 : (double) totalLatencyMs.sum() / total;
            long lastCalled = lastCalledAtMillis.get();
            return new ToolStatsView(total, errors, errorRate,
                    lastCalled == 0 ? null : Instant.ofEpochMilli(lastCalled),
                    total == 0 ? null : lastStatus.get(),
                    total == 0 ? null : avgLatency,
                    List.copyOf(recentCalls));
        }
    }

    /**
     * Serializable snapshot of a single tool's stats, used only for the
     * periodic disk flush and startup reload - never for reads, which always
     * go through the live in-memory counters.
     */
    private record PersistedStats(long totalCalls, long errorCalls, long totalLatencyMs, long lastCalledAtMillis,
                                   int lastStatus, List<CallRecord> recentCalls) {
    }

    public void recordCall(String projectId, String operationId, int status, long latencyMs) {
        stats.computeIfAbsent(key(projectId, operationId), k -> new LiveStats()).record(status, latencyMs);
    }

    public ToolStatsView getStats(String projectId, String operationId) {
        LiveStats live = stats.get(key(projectId, operationId));
        return live != null ? live.toView() : new ToolStatsView(0, 0, 0.0, null, null, null, List.of());
    }

    public ProjectStatsSummary getProjectSummary(String projectId) {
        String prefix = projectId + ":";
        long total = 0;
        long errors = 0;
        long lastCalled = 0;
        for (Map.Entry<String, LiveStats> entry : stats.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            ToolStatsView view = entry.getValue().toView();
            total += view.totalCalls();
            errors += view.errorCalls();
            if (view.lastCalledAt() != null) {
                lastCalled = Math.max(lastCalled, view.lastCalledAt().toEpochMilli());
            }
        }
        return new ProjectStatsSummary(total, errors, lastCalled == 0 ? null : Instant.ofEpochMilli(lastCalled));
    }

    public void clearProject(String projectId) {
        String prefix = projectId + ":";
        stats.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Flushes current counters to disk every 30s, so a container restart
     * only ever loses a few seconds of stats instead of writing to disk on
     * every single tool call.
     */
    @Scheduled(fixedRate = 30_000)
    public void flush() {
        if (stats.isEmpty()) {
            return;
        }
        Map<String, PersistedStats> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, LiveStats> entry : stats.entrySet()) {
            LiveStats live = entry.getValue();
            synchronized (live) {
                snapshot.put(entry.getKey(), new PersistedStats(
                        live.totalCalls.sum(), live.errorCalls.sum(), live.totalLatencyMs.sum(),
                        live.lastCalledAtMillis.get(), live.lastStatus.get(), List.copyOf(live.recentCalls)));
            }
        }
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(statsFile().toFile(), snapshot);
        } catch (JacksonException e) {
            log.error("Failed to persist tool-stats.json: {}", e.getMessage());
        }
    }

    /**
     * Reloads counters from the last flush, on application startup.
     */
    public void load() {
        Path file = statsFile();
        if (!Files.exists(file)) {
            return;
        }
        try {
            Map<String, PersistedStats> loaded = mapper.readValue(file.toFile(),
                    mapper.getTypeFactory().constructMapType(Map.class, String.class, PersistedStats.class));
            for (Map.Entry<String, PersistedStats> entry : loaded.entrySet()) {
                PersistedStats p = entry.getValue();
                LiveStats live = new LiveStats();
                live.totalCalls.add(p.totalCalls());
                live.errorCalls.add(p.errorCalls());
                live.totalLatencyMs.add(p.totalLatencyMs());
                live.lastCalledAtMillis.set(p.lastCalledAtMillis());
                live.lastStatus.set(p.lastStatus());
                live.recentCalls.addAll(p.recentCalls());
                stats.put(entry.getKey(), live);
            }
            log.info("Reloaded tool usage stats for {} tools from disk", loaded.size());
        } catch (JacksonException e) {
            log.warn("Could not read tool-stats.json: {}", e.getMessage());
        }
    }

    private Path statsFile() {
        return Path.of(dataDir, "tool-stats.json");
    }

    private String key(String projectId, String operationId) {
        return projectId + ":" + operationId;
    }
}

package com.toolize.service;

import com.toolize.domain.ToolCallLogEntity;
import com.toolize.domain.ToolStatsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Tracks how often each generated MCP tool is actually called - by a real
 * MCP client via {@code /mcp} tools/call, or from the "Execute" button in
 * the UI - so users can see which tools their agents actually use.
 *
 * Backed by the shared database (ToolStatsEntity for aggregate counters,
 * ToolCallLogEntity for recent-call history) instead of in-memory counters
 * plus a local file, so stats stay consistent across replicas: a call routed
 * through one instance is immediately visible when another instance is
 * queried, since both read from the same rows.
 */
@Service
public class ToolUsageService {

    private static final Logger log = LoggerFactory.getLogger(ToolUsageService.class);
    private static final int CALL_LOG_RETENTION_DAYS = 30;

    private final ToolStatsJpaRepository statsRepository;
    private final ToolCallLogJpaRepository callLogRepository;

    public ToolUsageService(ToolStatsJpaRepository statsRepository, ToolCallLogJpaRepository callLogRepository) {
        this.statsRepository = statsRepository;
        this.callLogRepository = callLogRepository;
    }

    public record CallRecord(Instant timestamp, int status, long latencyMs) {
    }

    public record ToolStatsView(long totalCalls, long errorCalls, double errorRate, Instant lastCalledAt,
                                 Integer lastStatus, Double avgLatencyMs, List<CallRecord> recentCalls) {
    }

    public record ProjectStatsSummary(long totalCalls, long errorCalls, Instant lastCalledAt) {
    }

    /**
     * Tries an atomic UPDATE first (safe under concurrent calls from other
     * replicas); only falls back to inserting a new row the very first time a
     * given tool is called. That insert path isn't itself race-proof against
     * two replicas' very first call landing at the same instant, but that's a
     * one-time, self-healing edge case - every call after the row exists goes
     * through the atomic update.
     */
    @Transactional
    public void recordCall(String projectId, String operationId, int status, long latencyMs) {
        String key = key(projectId, operationId);
        Instant now = Instant.now();
        int errorIncrement = status >= 400 ? 1 : 0;

        int updated = statsRepository.incrementStats(key, errorIncrement, latencyMs, now, status);
        if (updated == 0) {
            statsRepository.save(new ToolStatsEntity(key, 1, errorIncrement, latencyMs, now, status));
        }

        callLogRepository.save(new ToolCallLogEntity(key, now, status, latencyMs));
    }

    public ToolStatsView getStats(String projectId, String operationId) {
        String key = key(projectId, operationId);
        Optional<ToolStatsEntity> statsOpt = statsRepository.findById(key);
        if (statsOpt.isEmpty()) {
            return new ToolStatsView(0, 0, 0.0, null, null, null, List.of());
        }

        ToolStatsEntity stats = statsOpt.get();
        double errorRate = stats.getTotalCalls() == 0 ? 0.0 : (double) stats.getErrorCalls() / stats.getTotalCalls();
        Double avgLatencyMs = stats.getTotalCalls() == 0 ? null : (double) stats.getTotalLatencyMs() / stats.getTotalCalls();

        List<CallRecord> recentCalls = new ArrayList<>(callLogRepository.findTop20ByToolKeyOrderByCalledAtDesc(key).stream()
                .map(e -> new CallRecord(e.getCalledAt(), e.getStatus(), e.getLatencyMs()))
                .toList());
        Collections.reverse(recentCalls); // oldest-first, matching the frontend's existing contract

        return new ToolStatsView(stats.getTotalCalls(), stats.getErrorCalls(), errorRate, stats.getLastCalledAt(),
                stats.getLastStatus(), avgLatencyMs, recentCalls);
    }

    public ProjectStatsSummary getProjectSummary(String projectId) {
        List<ToolStatsEntity> rows = statsRepository.findByIdStartingWith(projectId + ":");
        long totalCalls = 0;
        long errorCalls = 0;
        Instant lastCalledAt = null;
        for (ToolStatsEntity row : rows) {
            totalCalls += row.getTotalCalls();
            errorCalls += row.getErrorCalls();
            if (row.getLastCalledAt() != null && (lastCalledAt == null || row.getLastCalledAt().isAfter(lastCalledAt))) {
                lastCalledAt = row.getLastCalledAt();
            }
        }
        return new ProjectStatsSummary(totalCalls, errorCalls, lastCalledAt);
    }

    @Transactional
    public void clearProject(String projectId) {
        String prefix = projectId + ":";
        statsRepository.deleteByIdStartingWith(prefix);
        callLogRepository.deleteByToolKeyStartingWith(prefix);
    }

    /**
     * The stats row itself is O(1) forever, but the call-log table only
     * exists to serve "recent calls" - old rows are pure dead weight. Pruned
     * by age rather than a per-key windowed keep-last-20 delete so the same
     * query works unchanged across H2/Postgres/MySQL/Oracle.
     */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void pruneOldCallLogs() {
        Instant cutoff = Instant.now().minus(CALL_LOG_RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = callLogRepository.deleteByCalledAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Pruned {} tool call log entries older than {} days", deleted, CALL_LOG_RETENTION_DAYS);
        }
    }

    private String key(String projectId, String operationId) {
        return projectId + ":" + operationId;
    }
}

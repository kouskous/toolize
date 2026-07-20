package com.toolize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Aggregate call counters for a single tool, keyed by "{projectId}:{operationId}".
 * One row per tool, updated via an atomic UPDATE in ToolStatsJpaRepository so
 * concurrent calls across replicas don't lose increments to a read-modify-write race.
 */
@Entity
@Table(name = "tool_stats")
public class ToolStatsEntity {

    @Id
    @Column(length = 400)
    private String id;

    @Column(nullable = false)
    private long totalCalls;

    @Column(nullable = false)
    private long errorCalls;

    @Column(nullable = false)
    private long totalLatencyMs;

    private Instant lastCalledAt;

    private Integer lastStatus;

    protected ToolStatsEntity() {
        // JPA
    }

    public ToolStatsEntity(String id, long totalCalls, long errorCalls, long totalLatencyMs,
                            Instant lastCalledAt, Integer lastStatus) {
        this.id = id;
        this.totalCalls = totalCalls;
        this.errorCalls = errorCalls;
        this.totalLatencyMs = totalLatencyMs;
        this.lastCalledAt = lastCalledAt;
        this.lastStatus = lastStatus;
    }

    public String getId() {
        return id;
    }

    public long getTotalCalls() {
        return totalCalls;
    }

    public long getErrorCalls() {
        return errorCalls;
    }

    public long getTotalLatencyMs() {
        return totalLatencyMs;
    }

    public Instant getLastCalledAt() {
        return lastCalledAt;
    }

    public Integer getLastStatus() {
        return lastStatus;
    }
}

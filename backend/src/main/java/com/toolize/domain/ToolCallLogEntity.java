package com.toolize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per recorded tool call, so "recent calls" is a real query against
 * shared storage instead of an in-memory deque - consistent across replicas,
 * pruned after 30 days by ToolUsageService.
 */
@Entity
@Table(name = "tool_call_log", indexes = @Index(name = "idx_tool_call_log_tool_key", columnList = "toolKey"))
public class ToolCallLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 400)
    private String toolKey;

    @Column(nullable = false)
    private Instant calledAt;

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private long latencyMs;

    protected ToolCallLogEntity() {
        // JPA
    }

    public ToolCallLogEntity(String toolKey, Instant calledAt, int status, long latencyMs) {
        this.toolKey = toolKey;
        this.calledAt = calledAt;
        this.status = status;
        this.latencyMs = latencyMs;
    }

    public Long getId() {
        return id;
    }

    public String getToolKey() {
        return toolKey;
    }

    public Instant getCalledAt() {
        return calledAt;
    }

    public int getStatus() {
        return status;
    }

    public long getLatencyMs() {
        return latencyMs;
    }
}

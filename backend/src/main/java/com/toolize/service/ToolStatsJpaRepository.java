package com.toolize.service;

import com.toolize.domain.ToolStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ToolStatsJpaRepository extends JpaRepository<ToolStatsEntity, String> {

    @Modifying
    @Query("UPDATE ToolStatsEntity t SET t.totalCalls = t.totalCalls + 1, " +
            "t.errorCalls = t.errorCalls + :errorIncrement, " +
            "t.totalLatencyMs = t.totalLatencyMs + :latencyMs, " +
            "t.lastCalledAt = :calledAt, t.lastStatus = :status WHERE t.id = :id")
    int incrementStats(@Param("id") String id, @Param("errorIncrement") int errorIncrement,
                        @Param("latencyMs") long latencyMs, @Param("calledAt") Instant calledAt,
                        @Param("status") int status);

    List<ToolStatsEntity> findByIdStartingWith(String prefix);

    void deleteByIdStartingWith(String prefix);
}

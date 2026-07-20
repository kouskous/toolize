package com.toolize.service;

import com.toolize.domain.ToolCallLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ToolCallLogJpaRepository extends JpaRepository<ToolCallLogEntity, Long> {

    List<ToolCallLogEntity> findTop20ByToolKeyOrderByCalledAtDesc(String toolKey);

    void deleteByToolKeyStartingWith(String prefix);

    @Modifying
    @Query("DELETE FROM ToolCallLogEntity t WHERE t.calledAt < :cutoff")
    int deleteByCalledAtBefore(@Param("cutoff") Instant cutoff);
}

package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.MonitoringResult;

public interface MonitoringResultRepository extends JpaRepository<MonitoringResult, Long>
{

    List<MonitoringResult> findAllByExecutionId(Long executionId);

    List<MonitoringResult> findAllByOrderByCreatedAtDescIdDesc();

    @Query("""
            SELECT r
            FROM MonitoringResult r
            WHERE r.execution.id IN :executionIds
            ORDER BY r.execution.id, r.createdAt DESC, r.id DESC
            """)
    List<MonitoringResult> findByExecutionIds(@Param("executionIds") List<Long> executionIds);
}
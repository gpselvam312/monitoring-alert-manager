package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.MonitoringExecution;

public interface MonitoringExecutionRepository extends JpaRepository<MonitoringExecution, Long>
{
    List<MonitoringExecution> findByMonitoringJobIdOrderByStartedAtDesc(Long monitoringJobId);

    @Query("""
            SELECT e
            FROM MonitoringExecution e
            WHERE e.monitoringJob.id = :monitoringJobId
              AND (
                    LOWER(CAST(e.status AS string)) LIKE
                        LOWER(CONCAT('%', :search, '%'))
                    OR CAST(e.attemptNumber AS string) LIKE
                        CONCAT('%', :search, '%')
                    OR LOWER(COALESCE(e.errorMessage, '')) LIKE
                        LOWER(CONCAT('%', :search, '%'))
                  )
            ORDER BY e.startedAt DESC
            """)
    Page<MonitoringExecution> findByMonitoringJobId(@Param("monitoringJobId") Long monitoringJobId,
            @Param("search") String search, Pageable pageable);
}
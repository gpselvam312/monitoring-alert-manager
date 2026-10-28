package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.dxc.monitoring.entity.AlertRule;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    List<AlertRule> findByEnabledTrueOrderByIdAsc();
    @EntityGraph(attributePaths = {"monitoringJob", "monitoringJob.application", "monitoringJob.environment"})
    List<AlertRule> findByMonitoringJob_Application_IdInOrderByIdDesc(List<Long> applicationIds);

    @EntityGraph(attributePaths = {"monitoringJob", "monitoringJob.application", "monitoringJob.environment"})
    @Query("select r from AlertRule r where r.id = :id")
    java.util.Optional<AlertRule> findByIdWithJob(@Param("id") Long id);
    List<AlertRule> findByMonitoringJobIdAndEnabledTrueOrderByIdAsc(Long monitoringJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AlertRule r where r.monitoringJob.id = :jobId and r.enabled = true order by r.id")
    List<AlertRule> findEnabledForJobForUpdate(@Param("jobId") Long jobId);
}

package com.dxc.monitoring.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.dxc.monitoring.entity.Alert;

public interface AlertRepository extends JpaRepository<Alert, Long> {
    Page<Alert> findByStatusOrderByLastOccurredAtDescIdDesc(Alert.AlertStatus status, Pageable pageable);
    Page<Alert> findAllByOrderByLastOccurredAtDescIdDesc(Pageable pageable);
    Page<Alert> findByMonitoringJobIdAndStatusOrderByLastOccurredAtDescIdDesc(
            Long monitoringJobId, Alert.AlertStatus status, Pageable pageable);
    java.util.Optional<Alert> findByAlertRuleIdAndCorrelationKeyAndStatus(
            Long alertRuleId, String correlationKey, Alert.AlertStatus status);
    @EntityGraph(attributePaths = {"alertRule", "monitoringJob", "monitoringJob.application", "monitoringJob.environment", "lastExecution"})
    Page<Alert> findByMonitoringJob_Application_IdInAndStatusOrderByLastOccurredAtDescIdDesc(
            java.util.List<Long> applicationIds, Alert.AlertStatus status, Pageable pageable);
    @EntityGraph(attributePaths = {"alertRule", "monitoringJob", "monitoringJob.application", "monitoringJob.environment", "lastExecution"})
    java.util.Optional<Alert> findByIdAndMonitoringJob_Application_IdIn(Long id, java.util.List<Long> applicationIds);
    long countByStatus(Alert.AlertStatus status);
}

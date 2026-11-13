package com.dxc.monitoring.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertRule;

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

    @EntityGraph(attributePaths = {"alertRule", "monitoringJob", "monitoringJob.application", "monitoringJob.environment"})
    @Query(value = """
            select a from Alert a
            where a.monitoringJob.application.id in :applicationIds
              and (:applicationId is null or a.monitoringJob.application.id = :applicationId)
              and (:environmentId is null or a.monitoringJob.environment.id = :environmentId)
              and (:status is null or a.status = :status)
              and (:severity is null or a.severity = :severity)
              and (:search = '' or
                   lower(a.title) like lower(concat('%', :search, '%')) or
                   lower(coalesce(a.summary, '')) like lower(concat('%', :search, '%')) or
                   lower(a.monitoringJob.name) like lower(concat('%', :search, '%')) or
                   lower(a.monitoringJob.application.name) like lower(concat('%', :search, '%')) or
                   str(a.id) like concat('%', :search, '%'))
            """,
            countQuery = """
            select count(a) from Alert a
            where a.monitoringJob.application.id in :applicationIds
              and (:applicationId is null or a.monitoringJob.application.id = :applicationId)
              and (:environmentId is null or a.monitoringJob.environment.id = :environmentId)
              and (:status is null or a.status = :status)
              and (:severity is null or a.severity = :severity)
              and (:search = '' or
                   lower(a.title) like lower(concat('%', :search, '%')) or
                   lower(coalesce(a.summary, '')) like lower(concat('%', :search, '%')) or
                   lower(a.monitoringJob.name) like lower(concat('%', :search, '%')) or
                   lower(a.monitoringJob.application.name) like lower(concat('%', :search, '%')) or
                   str(a.id) like concat('%', :search, '%'))
            """)
    Page<Alert> searchHistory(@Param("applicationIds") java.util.List<Long> applicationIds,
            @Param("applicationId") Long applicationId, @Param("environmentId") Long environmentId,
            @Param("status") Alert.AlertStatus status, @Param("severity") AlertRule.Severity severity,
            @Param("search") String search, Pageable pageable);

    @Query("""
            select count(a) from Alert a
            where a.monitoringJob.application.id in :applicationIds
              and (:applicationId is null or a.monitoringJob.application.id = :applicationId)
              and (:environmentId is null or a.monitoringJob.environment.id = :environmentId)
              and (:severity is null or a.severity = :severity)
              and (:search = '' or
                   lower(a.title) like lower(concat('%', :search, '%')) or
                   lower(coalesce(a.summary, '')) like lower(concat('%', :search, '%')) or
                   lower(a.monitoringJob.name) like lower(concat('%', :search, '%')) or
                   lower(a.monitoringJob.application.name) like lower(concat('%', :search, '%')) or
                   str(a.id) like concat('%', :search, '%'))
              and (:status is null or a.status = :status)
            """)
    long countHistory(@Param("applicationIds") java.util.List<Long> applicationIds,
            @Param("applicationId") Long applicationId, @Param("environmentId") Long environmentId,
            @Param("status") Alert.AlertStatus status, @Param("severity") AlertRule.Severity severity,
            @Param("search") String search);

    boolean existsByAlertRuleId(Long alertRuleId);
    long countByStatus(Alert.AlertStatus status);
}

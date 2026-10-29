package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @EntityGraph(attributePaths = {"monitoringJob", "monitoringJob.application", "monitoringJob.environment"})
    @Query(value = """
            select r from AlertRule r left join r.monitoringJob.environment env
            where r.monitoringJob.application.id in :applicationIds
              and (:search = '' or
                   lower(r.name) like lower(concat('%', :search, '%')) or
                   lower(coalesce(r.description, '')) like lower(concat('%', :search, '%')) or
                   lower(r.monitoringJob.name) like lower(concat('%', :search, '%')) or
                   lower(r.monitoringJob.application.name) like lower(concat('%', :search, '%')) or
                   lower(coalesce(env.name, '')) like lower(concat('%', :search, '%')))
            """,
            countQuery = """
            select count(r) from AlertRule r left join r.monitoringJob.environment env
            where r.monitoringJob.application.id in :applicationIds
              and (:search = '' or
                   lower(r.name) like lower(concat('%', :search, '%')) or
                   lower(coalesce(r.description, '')) like lower(concat('%', :search, '%')) or
                   lower(r.monitoringJob.name) like lower(concat('%', :search, '%')) or
                   lower(r.monitoringJob.application.name) like lower(concat('%', :search, '%')) or
                   lower(coalesce(r.monitoringJob.environment.name, '')) like lower(concat('%', :search, '%')))
            """)
    Page<AlertRule> searchForManagement(@Param("applicationIds") List<Long> applicationIds,
            @Param("search") String search, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AlertRule r where r.monitoringJob.id = :jobId and r.enabled = true order by r.id")
    List<AlertRule> findEnabledForJobForUpdate(@Param("jobId") Long jobId);
}

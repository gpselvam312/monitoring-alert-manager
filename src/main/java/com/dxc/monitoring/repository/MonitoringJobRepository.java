package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.MonitoringJob;

public interface MonitoringJobRepository extends JpaRepository<MonitoringJob, Long>
{

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    @Query("""
            SELECT j
            FROM MonitoringJob j
            LEFT JOIN j.application a
            LEFT JOIN j.environment e
            LEFT JOIN j.machine m
            LEFT JOIN j.schedule s
            WHERE j.executionMode = :executionMode
                AND (
                    LOWER(j.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(a.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(m.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(m.hostname, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(s.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                )
            """)
    Page<MonitoringJob> findAllForList(@Param("search") String search,
            @Param("executionMode") MonitoringJob.ExecutionMode executionMode, Pageable pageable);

    @Query("""
            SELECT j
            FROM MonitoringJob j
            LEFT JOIN FETCH j.application
            LEFT JOIN FETCH j.environment
            LEFT JOIN FETCH j.machine
            LEFT JOIN FETCH j.schedule
            WHERE j.id = :id
            """)
    Optional<MonitoringJob> findByIdForDetails(@Param("id") Long id);

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    List<MonitoringJob> findByScheduleId(Long scheduleId);

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    @Query("""
            SELECT j FROM MonitoringJob j
            JOIN j.schedule s
            WHERE j.enabled = true
              AND j.executionMode = :executionMode
              AND s.enabled = true
              AND s.type IS NOT NULL
            ORDER BY j.name ASC
            """)
    List<MonitoringJob> findSchedulableJobs(@Param("executionMode") MonitoringJob.ExecutionMode executionMode);

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    java.util.List<MonitoringJob> findByExecutionModeOrderByNameAsc(
            MonitoringJob.ExecutionMode executionMode);

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    List<MonitoringJob> findByExecutionModeAndApplication_IdInOrderByNameAsc(
            MonitoringJob.ExecutionMode executionMode, List<Long> applicationIds);

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    List<MonitoringJob> findByApplicationIdIn(List<Long> applicationIds);

    @EntityGraph(attributePaths = { "application", "environment", "machine", "schedule" })
    @Query("""
            SELECT j
            FROM MonitoringJob j
            LEFT JOIN j.application a
            LEFT JOIN j.environment e
            LEFT JOIN j.machine m
            LEFT JOIN j.schedule s
            WHERE j.executionMode = :executionMode
              AND j.application.id IN :applicationIds
              AND (
                    LOWER(j.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(a.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(m.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(m.hostname, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(s.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<MonitoringJob> findAllForApplications(@Param("search") String search,
            @Param("executionMode") MonitoringJob.ExecutionMode executionMode,
            @Param("applicationIds") List<Long> applicationIds, Pageable pageable);

    @EntityGraph(attributePaths = { "application", "environment" })
    @Query("""
            SELECT j
            FROM MonitoringJob j
            JOIN j.application a
            LEFT JOIN j.environment e
            WHERE j.executionMode = :executionMode
              AND a.id IN :applicationIds
              AND (:applicationId IS NULL OR a.id = :applicationId)
              AND (:environmentId IS NULL OR e.id = :environmentId)
              AND (
                    :search = ''
                    OR LOWER(j.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<MonitoringJob> findAllForLookup(@Param("search") String search,
            @Param("applicationIds") List<Long> applicationIds,
            @Param("applicationId") Long applicationId,
            @Param("environmentId") Long environmentId,
            @Param("executionMode") MonitoringJob.ExecutionMode executionMode,
            Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.monitoring_jobs
            SET execution_claim_token = CAST(:claimToken AS UUID),
                execution_claim_until = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second')
            WHERE id = :jobId
              AND enabled = TRUE
              AND (execution_claim_until IS NULL OR execution_claim_until < CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int tryClaimExecution(@Param("jobId") Long jobId,
            @Param("claimToken") String claimToken,
            @Param("leaseSeconds") int leaseSeconds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ra_fcb.monitoring_jobs
            SET execution_claim_token = NULL, execution_claim_until = NULL
            WHERE id = :jobId AND execution_claim_token = CAST(:claimToken AS UUID)
            """, nativeQuery = true)
    int releaseExecutionClaim(@Param("jobId") Long jobId, @Param("claimToken") String claimToken);

    boolean existsByExecutionModeAndApplication_IdIn(MonitoringJob.ExecutionMode executionMode, List<Long> applicationIds);

    boolean existsByApplicationId(Long applicationId);

    @Query("""
            SELECT DISTINCT j.environment
            FROM MonitoringJob j
            WHERE j.application.id = :applicationId
              AND j.environment IS NOT NULL
              AND j.environment.enabled = true
            ORDER BY j.environment.name
            """)
    List<com.dxc.monitoring.entity.Environment> findDistinctEnabledEnvironmentsByApplicationId(@Param("applicationId") Long applicationId);

    boolean existsByApplicationIdAndEnvironmentId(Long applicationId, Long environmentId);

    boolean existsByEnvironmentId(Long environmentId);

}
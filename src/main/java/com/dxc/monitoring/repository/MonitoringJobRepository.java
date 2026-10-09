package com.dxc.monitoring.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
            WHERE
                LOWER(j.name) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(a.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(e.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(m.hostname, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(COALESCE(s.name, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<MonitoringJob> findAllForList(@Param("search") String search, Pageable pageable);

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

    java.util.List<MonitoringJob> findByExecutionModeAndEnabledTrueOrderByNameAsc(
            MonitoringJob.ExecutionMode executionMode);

    boolean existsByApplicationId(Long applicationId);

}
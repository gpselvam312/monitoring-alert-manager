package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dxc.monitoring.entity.DashboardTab;

@Repository
public interface DashboardTabRepository extends JpaRepository<DashboardTab, Long>
{
    @EntityGraph(attributePaths = { "application", "environment" })
    List<DashboardTab> findAllByEnabledTrueOrderBySortOrderAsc();

    @EntityGraph(attributePaths = { "application", "environment" })
    List<DashboardTab> findAllByApplication_IdAndEnvironment_IdAndEnabledTrueOrderBySortOrderAsc(
            Long applicationId, Long environmentId);

    @Query("""
            SELECT DISTINCT t.environment
            FROM DashboardTab t
            WHERE t.application.id = :applicationId
              AND t.enabled = true
              AND t.environment IS NOT NULL
              AND t.environment.enabled = true
            ORDER BY t.environment.name
            """)
    List<com.dxc.monitoring.entity.Environment> findDistinctEnabledEnvironmentsByApplicationId(
            @Param("applicationId") Long applicationId);

    List<DashboardTab> findAllByOrderBySortOrderAsc();

    Optional<DashboardTab> findByNameIgnoreCaseAndApplication_IdAndEnvironment_Id(
            String name, Long applicationId, Long environmentId);

    Optional<DashboardTab> findByEnvironment_Id(Long environmentId);

    boolean existsByNameIgnoreCaseAndApplication_IdAndEnvironment_Id(
            String name, Long applicationId, Long environmentId);

    @EntityGraph(attributePaths = { "application", "environment" })
    @Query("""
            SELECT t
            FROM DashboardTab t
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<DashboardTab> findAllForList(@Param("search") String search, Pageable pageable);
}
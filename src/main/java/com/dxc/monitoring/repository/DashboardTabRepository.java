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
    @EntityGraph(attributePaths = { "environment", "application" })
    List<DashboardTab> findAllByEnabledTrueOrderBySortOrderAsc();

    @EntityGraph(attributePaths = { "environment", "application" })
    List<DashboardTab> findAllByApplication_IdAndEnvironment_IdAndEnabledTrueOrderBySortOrderAsc(Long applicationId, Long environmentId);

    @EntityGraph(attributePaths = { "environment", "application" })
    List<DashboardTab> findAllByApplication_IdAndEnabledTrueOrderBySortOrderAsc(Long applicationId);

    List<DashboardTab> findAllByOrderBySortOrderAsc();

    Optional<DashboardTab> findByNameIgnoreCase(String name);

    Optional<DashboardTab> findByNameIgnoreCaseAndApplication_IdAndEnvironment_Id(
            String name, Long applicationId, Long environmentId);

    @EntityGraph(attributePaths = { "environment", "application" })
    List<DashboardTab> findAllByApplication_IdInOrderBySortOrderAsc(List<Long> applicationIds);

    Optional<DashboardTab> findByEnvironment_Id(Long environmentId);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByApplication_Id(Long applicationId);

    @EntityGraph(attributePaths = { "environment", "application" })
    @Query("""
            SELECT t
            FROM DashboardTab t
            WHERE t.application.id IN :applicationIds
              AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<DashboardTab> findAllForApplications(@Param("search") String search,
            @Param("applicationIds") List<Long> applicationIds, Pageable pageable);

    @EntityGraph(attributePaths = { "environment" })
    @Query("""
            SELECT t
            FROM DashboardTab t
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<DashboardTab> findAllForList(@Param("search") String search, Pageable pageable);
}
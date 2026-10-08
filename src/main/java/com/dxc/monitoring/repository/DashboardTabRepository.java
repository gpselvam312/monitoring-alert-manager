package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

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
    List<DashboardTab> findAllByEnabledTrueOrderBySortOrderAsc();

    List<DashboardTab> findAllByOrderBySortOrderAsc();

    Optional<DashboardTab> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            SELECT t
            FROM DashboardTab t
            WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<DashboardTab> findAllForList(@Param("search") String search, Pageable pageable);
}
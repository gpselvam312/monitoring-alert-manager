package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dxc.monitoring.entity.DashboardWidget;

@Repository
public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long>
{
    List<DashboardWidget> findAllByTabIdOrderBySortOrderAsc(Long tabId);

    List<DashboardWidget> findAllByTabIdAndEnabledTrueOrderBySortOrderAsc(Long tabId);

    List<DashboardWidget> findAllByEnabledTrueOrderByTabSortOrderAscSortOrderAsc();

    Page<DashboardWidget> findAllByTabIdAndNameContainingIgnoreCase(Long tabId, String search, Pageable pageable);

    @Query("""
            SELECT w
            FROM DashboardWidget w
            WHERE w.tab.id = :tabId
              AND (
                  LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(COALESCE(w.description, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(w.widgetType) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<DashboardWidget> findAllForList(@Param("tabId") Long tabId, @Param("search") String search, Pageable pageable);
}
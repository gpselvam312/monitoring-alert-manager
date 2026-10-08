package com.dxc.monitoring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.dxc.monitoring.entity.DashboardWidgetResult;

@Repository
public interface DashboardWidgetResultRepository extends JpaRepository<DashboardWidgetResult, Long>
{
    Optional<DashboardWidgetResult> findTopByWidgetIdOrderByResultTimeDesc(Long widgetId);

    List<DashboardWidgetResult> findAllByWidgetIdOrderByResultTimeDesc(Long widgetId);

    @Query("""
            SELECT r
            FROM DashboardWidgetResult r
            WHERE r.widget.id IN :widgetIds
              AND r.resultTime = (
                  SELECT MAX(r2.resultTime)
                  FROM DashboardWidgetResult r2
                  WHERE r2.widget.id = r.widget.id
              )
            """)
    List<DashboardWidgetResult> findLatestResultsByWidgetIds(List<Long> widgetIds);

    @Query("""
            SELECT r
            FROM DashboardWidgetResult r
            WHERE r.widget.id = :widgetId
              AND (
                  :search = ''
                  OR LOWER(COALESCE(r.status, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(COALESCE(r.message, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(CAST(r.resultData AS String)) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<DashboardWidgetResult> findAllForDetails(@Param("widgetId") Long widgetId, @Param("search") String search,
            Pageable pageable);
}
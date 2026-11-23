package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.AlertCleanupRun;

public interface AlertCleanupRunRepository extends JpaRepository<AlertCleanupRun, Long> {
    @EntityGraph(attributePaths = {"policy", "policy.application"})
    @Query(value = """
            select r from AlertCleanupRun r
            left join r.policy p
            where :includeAllScopes = true
               or p.application.id in :applicationIds
            order by r.startedAt desc, r.id desc
            """,
            countQuery = """
            select count(r) from AlertCleanupRun r
            left join r.policy p
            where :includeAllScopes = true
               or p.application.id in :applicationIds
            """)
    Page<AlertCleanupRun> findVisibleRuns(@Param("includeAllScopes") boolean includeAllScopes,
            @Param("applicationIds") List<Long> applicationIds, Pageable pageable);
}

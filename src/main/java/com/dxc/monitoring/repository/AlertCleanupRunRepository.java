package com.dxc.monitoring.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.AlertCleanupRun;

public interface AlertCleanupRunRepository extends JpaRepository<AlertCleanupRun, Long> {
    @EntityGraph(attributePaths = {"policy"})
    Page<AlertCleanupRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}

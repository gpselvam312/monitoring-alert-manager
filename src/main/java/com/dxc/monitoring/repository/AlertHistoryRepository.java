package com.dxc.monitoring.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.dxc.monitoring.entity.AlertHistory;

public interface AlertHistoryRepository extends JpaRepository<AlertHistory, Long> {
    @EntityGraph(attributePaths = {"alert", "execution"})
    Page<AlertHistory> findByAlertIdOrderByOccurredAtDescIdDesc(Long alertId, Pageable pageable);
}

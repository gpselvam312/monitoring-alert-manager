package com.dxc.monitoring.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.AlertComment;

public interface AlertCommentRepository extends JpaRepository<AlertComment, Long> {
    Page<AlertComment> findByAlertIdOrderByCreatedAtDescIdDesc(Long alertId, Pageable pageable);
}

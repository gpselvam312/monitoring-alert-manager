package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.AlertComment;

public interface AlertCommentRepository extends JpaRepository<AlertComment, Long> {
    List<AlertComment> findByAlertIdOrderByCreatedAtDescIdDesc(Long alertId);
}

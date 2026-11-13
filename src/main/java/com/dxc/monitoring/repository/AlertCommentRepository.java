package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.AlertComment;

public interface AlertCommentRepository extends JpaRepository<AlertComment, Long> {
    @EntityGraph(attributePaths = "author")
    List<AlertComment> findByAlertIdOrderByCreatedAtDescIdDesc(Long alertId);
}

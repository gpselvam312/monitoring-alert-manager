package com.dxc.monitoring.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.AlertRuleEvaluation;

public interface AlertRuleEvaluationRepository extends JpaRepository<AlertRuleEvaluation, Long> {
    boolean existsByAlertRuleIdAndExecutionId(Long alertRuleId, Long executionId);
    java.util.Optional<AlertRuleEvaluation> findTopByAlertRule_IdOrderByEvaluatedAtDescIdDesc(Long alertRuleId);
}

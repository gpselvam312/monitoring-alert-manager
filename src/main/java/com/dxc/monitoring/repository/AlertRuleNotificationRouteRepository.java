package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleNotificationRoute;
import com.dxc.monitoring.entity.NotificationRoute;

public interface AlertRuleNotificationRouteRepository extends JpaRepository<AlertRuleNotificationRoute, Long> {
    @EntityGraph(attributePaths = {"channel", "recipient", "group"})
    List<AlertRuleNotificationRoute> findByAlertRule_IdAndSeverityAndEventTypeAndEnabledTrue(
            Long alertRuleId, AlertRule.Severity severity, NotificationRoute.EventType eventType);

    @EntityGraph(attributePaths = {"channel", "recipient", "group"})
    List<AlertRuleNotificationRoute> findByAlertRule_IdOrderByIdAsc(Long alertRuleId);

    void deleteByAlertRule_Id(Long alertRuleId);
}

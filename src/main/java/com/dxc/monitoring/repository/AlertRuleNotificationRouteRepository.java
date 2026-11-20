package com.dxc.monitoring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleNotificationRoute;
import com.dxc.monitoring.entity.NotificationRoute;

public interface AlertRuleNotificationRouteRepository extends JpaRepository<AlertRuleNotificationRoute, Long> {
    @EntityGraph(attributePaths = {"channel", "recipient", "group", "recipients", "groups"})
    List<AlertRuleNotificationRoute> findByAlertRule_IdAndSeverityAndEventTypeAndEnabledTrue(
            Long alertRuleId, AlertRule.Severity severity, NotificationRoute.EventType eventType);

    @EntityGraph(attributePaths = {"channel", "recipient", "group"})
    List<AlertRuleNotificationRoute> findByAlertRule_IdOrderByIdAsc(Long alertRuleId);

    @Query("select distinct r.alertRule.id, r.channel.channelType from AlertRuleNotificationRoute r "
            + "where r.alertRule.id in :ruleIds and r.enabled = true and r.channel.enabled = true")
    List<Object[]> findConfiguredChannelTypesByAlertRuleIds(@Param("ruleIds") List<Long> ruleIds);

    void deleteByAlertRule_Id(Long alertRuleId);
}

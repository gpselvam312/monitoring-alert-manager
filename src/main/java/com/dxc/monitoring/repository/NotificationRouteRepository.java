package com.dxc.monitoring.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.NotificationRoute;

public interface NotificationRouteRepository extends JpaRepository<NotificationRoute, Long> {
    @EntityGraph(attributePaths = {"channel", "recipient", "group"})
    List<NotificationRoute> findByNotificationPolicy_IdAndSeverityAndEventTypeAndEnabledTrue(
            Long policyId, AlertRule.Severity severity, NotificationRoute.EventType eventType);
    List<NotificationRoute> findByNotificationPolicy_IdOrderByIdAsc(Long policyId);

    @EntityGraph(attributePaths = {"notificationPolicy", "channel", "recipient", "group"})
    List<NotificationRoute> findAllByOrderByIdAsc();
}

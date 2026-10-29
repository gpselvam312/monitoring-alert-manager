package com.dxc.monitoring.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleNotificationRoute;
import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.entity.NotificationGroupMember;
import com.dxc.monitoring.entity.NotificationRecipient;
import com.dxc.monitoring.entity.NotificationRoute;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;
import com.dxc.monitoring.repository.NotificationGroupMemberRepository;
import com.dxc.monitoring.repository.AlertRuleNotificationRouteRepository;
import com.dxc.monitoring.repository.NotificationRouteRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationRoutingService {
    private final AlertRuleNotificationRouteRepository routeRepository;
    private final NotificationGroupMemberRepository groupMemberRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public NotificationRoutingService(AlertRuleNotificationRouteRepository routeRepository,
            NotificationGroupMemberRepository groupMemberRepository,
            NotificationDeliveryRepository deliveryRepository,
            ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void queueForHistory(AlertHistory history) {
        if (history == null || history.getAlert() == null || history.getAlert().getAlertRule() == null) return;
        NotificationRoute.EventType eventType = routeEvent(history);
        if (eventType == null) return;

        Alert alert = history.getAlert();
        AlertRule rule = alert.getAlertRule();
        AlertRule.Severity severity = history.getNewSeverity() != null
                ? history.getNewSeverity() : alert.getSeverity();
        if (severity == null) return;

        List<AlertRuleNotificationRoute> routes =
                routeRepository.findByAlertRule_IdAndSeverityAndEventTypeAndEnabledTrue(
                        rule.getId(), severity, eventType);
        for (AlertRuleNotificationRoute route : routes) {
            NotificationChannel channel = route.getChannel();
            if (channel == null || !channel.isEnabled()) continue;
            if (channel.getChannelType() == NotificationChannel.ChannelType.TEAMS) {
                queueTeamsDelivery(history, route);
            } else {
                for (NotificationRecipient recipient : recipientsFor(route)) {
                    queuePersonDelivery(history, route, recipient);
                }
            }
        }
    }

    private void queueTeamsDelivery(AlertHistory history, NotificationRoute route) {
        if (route.getRecipient() != null && !route.getRecipient().isEnabled()) return;
        if (route.getGroup() != null) {
            if (!route.getGroup().isEnabled()) return;
            boolean hasEnabledMembers = !groupMemberRepository
                    .findByGroup_IdAndRecipient_EnabledTrue(route.getGroup().getId()).isEmpty();
            if (!hasEnabledMembers) {
                queueSkipped(history, route, null, "Notification group has no enabled members.");
                return;
            }
        }
        String key = history.getId() + ":" + route.getChannel().getId() + ":TEAMS";
        if (deliveryRepository.existsByIdempotencyKey(key)) return;
        OptionalRecent recent = recentDelivery(history.getAlert(), route.getChannel(), null);
        if (cooldownApplies(route) && withinCooldown(recent, route.getMinimumIntervalSeconds())) {
            queueSkipped(history, route, null, "Suppressed by the route's minimum notification interval.");
            return;
        }
        queueDelivery(history, route, null, route.getChannel().getName(), key, false, null);
    }

    private void queuePersonDelivery(AlertHistory history, NotificationRoute route, NotificationRecipient recipient) {
        if (recipient == null || !recipient.isEnabled()) return;
        NotificationChannel.ChannelType type = route.getChannel().getChannelType();
        String destination = switch (type) {
            case EMAIL -> recipient.getEmail();
            case WHATSAPP -> recipient.getPhoneNumber();
            case TEAMS -> null;
        };
        String key = history.getId() + ":" + route.getChannel().getId() + ":" + recipient.getId();
        if (deliveryRepository.existsByIdempotencyKey(key)) return;
        if (destination == null || destination.isBlank()) {
            queueSkipped(history, route, recipient, "Recipient has no destination configured for " + type + ".");
            return;
        }
        OptionalRecent recent = recentDelivery(history.getAlert(), route.getChannel(), recipient.getId());
        if (cooldownApplies(route) && withinCooldown(recent, route.getMinimumIntervalSeconds())) {
            queueSkipped(history, route, recipient, "Suppressed by the route's minimum notification interval.");
            return;
        }
        queueDelivery(history, route, recipient, destination, key, false, null);
    }

    private List<NotificationRecipient> recipientsFor(NotificationRoute route) {
        if (route.getRecipient() != null) {
            return route.getRecipient().isEnabled() ? List.of(route.getRecipient()) : List.of();
        }
        if (route.getGroup() == null || !route.getGroup().isEnabled()) return List.of();
        return groupMemberRepository.findByGroup_IdAndRecipient_EnabledTrue(route.getGroup().getId())
                .stream().map(NotificationGroupMember::getRecipient).filter(NotificationRecipient::isEnabled).toList();
    }

    private void queueSkipped(AlertHistory history, NotificationRoute route,
            NotificationRecipient recipient, String reason) {
        String target = recipient == null ? "route-" + route.getId() : "recipient-" + recipient.getId();
        String key = history.getId() + ":" + route.getChannel().getId() + ":" + target + ":SKIPPED";
        if (deliveryRepository.existsByIdempotencyKey(key)) return;
        queueDelivery(history, route, recipient, recipient == null ? route.getChannel().getName() : recipient.getName(),
                key, true, reason);
    }

    private void queueDelivery(AlertHistory history, NotificationRoute route, NotificationRecipient recipient,
            String destination, String key, boolean skipped, String reason) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setAlert(history.getAlert());
        delivery.setAlertHistory(history);
        delivery.setChannel(route.getChannel());
        delivery.setRecipient(recipient);
        delivery.setDestinationSnapshot(destination == null || destination.isBlank() ? "Unconfigured destination" : destination);
        delivery.setIdempotencyKey(key);
        delivery.setStatus(skipped ? NotificationDelivery.DeliveryStatus.SKIPPED : NotificationDelivery.DeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(OffsetDateTime.now());
        delivery.setLastError(reason);
        delivery.setMessagePayload(buildPayload(history));
        deliveryRepository.save(delivery);
    }

    private String buildPayload(AlertHistory history) {
        Alert alert = history.getAlert();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", alert.getTitle());
        payload.put("body", alert.getSummary() == null ? "" : alert.getSummary());
        payload.put("severity", alert.getSeverity().name());
        payload.put("eventType", history.getEventType().name());
        payload.put("alertId", alert.getId());
        payload.put("historyId", history.getId());
        payload.put("monitoringJob", alert.getMonitoringJob().getName());
        if (alert.getMonitoringJob().getApplication() != null) {
            payload.put("application", alert.getMonitoringJob().getApplication().getName());
        }
        if (alert.getMonitoringJob().getEnvironment() != null) {
            payload.put("environment", alert.getMonitoringJob().getEnvironment().getName());
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create notification message payload.", exception);
        }
    }

    private NotificationRoute.EventType routeEvent(AlertHistory history) {
        if (history == null || history.getEventType() == null) return null;
        return switch (history.getEventType()) {
            case CREATED -> NotificationRoute.EventType.CREATED;
            case OCCURRENCE -> NotificationRoute.EventType.RECURRENCE;
            case RESOLVED -> NotificationRoute.EventType.RECOVERED;
            case SEVERITY_CHANGED -> severityRank(history.getNewSeverity()) > severityRank(history.getPreviousSeverity())
                    ? NotificationRoute.EventType.ESCALATED : null;
            default -> null;
        };
    }

    private int severityRank(AlertRule.Severity severity) {
        if (severity == null) return 0;
        return switch (severity) {
            case INFO -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }

    private OptionalRecent recentDelivery(Alert alert, NotificationChannel channel, Long recipientId) {
        if (recipientId == null) {
            return new OptionalRecent(deliveryRepository.findTopByAlert_IdAndChannel_IdAndRecipientIsNullOrderByCreatedAtDesc(
                    alert.getId(), channel.getId()).orElse(null));
        }
        return new OptionalRecent(deliveryRepository.findTopByAlert_IdAndChannel_IdAndRecipient_IdOrderByCreatedAtDesc(
                alert.getId(), channel.getId(), recipientId).orElse(null));
    }

    private boolean cooldownApplies(NotificationRoute route) {
        return route.getEventType() == NotificationRoute.EventType.RECURRENCE
                || route.getEventType() == NotificationRoute.EventType.REMINDER;
    }

    private boolean withinCooldown(OptionalRecent recent, Integer intervalSeconds) {
        if (intervalSeconds == null || intervalSeconds <= 0 || recent.delivery() == null) return false;
        NotificationDelivery delivery = recent.delivery();
        if (delivery.getStatus() == NotificationDelivery.DeliveryStatus.SKIPPED) return false;
        if (delivery.getStatus() == NotificationDelivery.DeliveryStatus.FAILED
                && delivery.getAttemptCount() >= 8) return false;
        return delivery.getCreatedAt() != null
                && delivery.getCreatedAt().isAfter(OffsetDateTime.now().minusSeconds(intervalSeconds));
    }

    private record OptionalRecent(NotificationDelivery delivery) {}
}

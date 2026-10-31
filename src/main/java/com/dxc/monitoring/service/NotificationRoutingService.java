package com.dxc.monitoring.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleNotificationRoute;
import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.entity.NotificationGroup;
import com.dxc.monitoring.entity.NotificationGroupMember;
import com.dxc.monitoring.entity.NotificationRecipient;
import com.dxc.monitoring.entity.NotificationRoute;
import com.dxc.monitoring.repository.AlertRuleNotificationRouteRepository;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;
import com.dxc.monitoring.repository.NotificationGroupMemberRepository;

import tools.jackson.databind.ObjectMapper;

@Service
public class NotificationRoutingService {
    private static final Logger log = LoggerFactory.getLogger(NotificationRoutingService.class);
    private final AlertRuleNotificationRouteRepository routeRepository;
    private final NotificationGroupMemberRepository groupMemberRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public NotificationRoutingService(AlertRuleNotificationRouteRepository routeRepository,
            NotificationGroupMemberRepository groupMemberRepository,
            NotificationDeliveryRepository deliveryRepository, ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void queueForHistory(AlertHistory history) {
        if (history == null || history.getAlert() == null || history.getAlert().getAlertRule() == null) {
            log.warn("Notification routing skipped because alert history, alert, or alert rule is missing");
            return;
        }
        NotificationRoute.EventType eventType = routeEvent(history);
        if (eventType == null) {
            log.debug("No notification event mapping for alertHistoryId={} eventType={}", history.getId(), history.getEventType());
            return;
        }

        Alert alert = history.getAlert();
        AlertRule rule = alert.getAlertRule();
        AlertRule.Severity severity = history.getNewSeverity() != null
                ? history.getNewSeverity() : alert.getSeverity();
        if (severity == null || rule.getId() == null) {
            log.warn("Notification routing skipped for alertHistoryId={} because severity or rule id is missing", history.getId());
            return;
        }

        List<AlertRuleNotificationRoute> routes =
                routeRepository.findByAlertRule_IdAndSeverityAndEventTypeAndEnabledTrue(rule.getId(), severity, eventType);

        // A rule's CREATED route is the default notification destination for subsequent occurrences
        // unless the user has configured a dedicated RECURRENCE route for that severity.
        // This avoids silently losing notifications for an already-open alert while preserving
        // explicit recurrence routing when it exists.
        boolean usingCreatedRouteForRecurrence = false;
        if (eventType == NotificationRoute.EventType.RECURRENCE && routes.isEmpty()) {
            routes = routeRepository.findByAlertRule_IdAndSeverityAndEventTypeAndEnabledTrue(
                    rule.getId(), severity, NotificationRoute.EventType.CREATED);
            usingCreatedRouteForRecurrence = !routes.isEmpty();
        }
        log.info("Evaluating notifications for alertHistoryId={} alertId={} ruleId={} severity={} event={} matchingRoutes={} createdRouteFallback={}",
                history.getId(), alert.getId(), rule.getId(), severity, eventType, routes.size(),
                usingCreatedRouteForRecurrence);
        if (routes.isEmpty()) {
            log.warn("No enabled notification route matches alertHistoryId={} ruleId={} severity={} event={}",
                    history.getId(), rule.getId(), severity, eventType);
        }
        for (AlertRuleNotificationRoute route : routes) {
            NotificationChannel channel = route.getChannel();
            if (channel == null || !channel.isEnabled()) {
                log.warn("Skipping notification route id={} for alertHistoryId={} because its channel is missing or disabled",
                        route.getId(), history.getId());
                continue;
            }
            log.debug("Processing notification route id={} channelId={} channelType={} recipientId={} groupId={}",
                    route.getId(), channel.getId(), channel.getChannelType(),
                    route.getRecipient() == null ? null : route.getRecipient().getId(),
                    route.getGroup() == null ? null : route.getGroup().getId());
            RouteTarget target = new RouteTarget(route.getId(), channel, route.getRecipient(), route.getGroup(),
                    usingCreatedRouteForRecurrence ? eventType : route.getEventType(),
                    route.getMinimumIntervalSeconds());
            if (channel.getChannelType() == NotificationChannel.ChannelType.TEAMS) {
                queueTeamsDelivery(history, target);
            } else {
                for (NotificationRecipient recipient : recipientsFor(target)) {
                    queuePersonDelivery(history, target, recipient);
                }
            }
        }
    }

    private void queueTeamsDelivery(AlertHistory history, RouteTarget route) {
        if (route.recipient() != null && !route.recipient().isEnabled()) return;
        if (route.group() != null) {
            if (!route.group().isEnabled()) return;
            boolean hasEnabledMembers = !groupMemberRepository
                    .findByGroup_IdAndRecipient_EnabledTrue(route.group().getId()).isEmpty();
            if (!hasEnabledMembers) {
                queueSkipped(history, route, null, "Notification group has no enabled members.");
                return;
            }
        }
        String key = history.getId() + ":" + route.channel().getId() + ":TEAMS";
        if (deliveryRepository.existsByIdempotencyKey(key)) return;
        OptionalRecent recent = recentDelivery(history.getAlert(), route.channel(), null);
        if (cooldownApplies(route) && withinCooldown(recent, route.minimumIntervalSeconds())) {
            queueSkipped(history, route, null, "Suppressed by the route's minimum notification interval.");
            return;
        }
        queueDelivery(history, route, null, route.channel().getName(), key, false, null);
    }

    private void queuePersonDelivery(AlertHistory history, RouteTarget route, NotificationRecipient recipient) {
        if (recipient == null || !recipient.isEnabled()) return;
        String destination = switch (route.channel().getChannelType()) {
            case EMAIL -> recipient.getEmail();
            case WHATSAPP -> recipient.getPhoneNumber();
            case TEAMS -> null;
        };
        String key = history.getId() + ":" + route.channel().getId() + ":" + recipient.getId();
        if (deliveryRepository.existsByIdempotencyKey(key)) return;
        if (destination == null || destination.isBlank()) {
            queueSkipped(history, route, recipient, "Recipient has no destination configured for "
                    + route.channel().getChannelType() + ".");
            return;
        }
        OptionalRecent recent = recentDelivery(history.getAlert(), route.channel(), recipient.getId());
        if (cooldownApplies(route) && withinCooldown(recent, route.minimumIntervalSeconds())) {
            queueSkipped(history, route, recipient, "Suppressed by the route's minimum notification interval.");
            return;
        }
        queueDelivery(history, route, recipient, destination, key, false, null);
    }

    private List<NotificationRecipient> recipientsFor(RouteTarget route) {
        if (route.recipient() != null) {
            return route.recipient().isEnabled() ? List.of(route.recipient()) : List.of();
        }
        if (route.group() == null || !route.group().isEnabled()) return List.of();
        return groupMemberRepository.findByGroup_IdAndRecipient_EnabledTrue(route.group().getId())
                .stream().map(NotificationGroupMember::getRecipient).filter(NotificationRecipient::isEnabled).toList();
    }

    private void queueSkipped(AlertHistory history, RouteTarget route,
            NotificationRecipient recipient, String reason) {
        String target = recipient == null ? "route-" + route.id() : "recipient-" + recipient.getId();
        String key = history.getId() + ":" + route.channel().getId() + ":" + target + ":SKIPPED";
        if (deliveryRepository.existsByIdempotencyKey(key)) return;
        queueDelivery(history, route, recipient,
                recipient == null ? route.channel().getName() : recipient.getName(), key, true, reason);
    }

    private void queueDelivery(AlertHistory history, RouteTarget route, NotificationRecipient recipient,
            String destination, String key, boolean skipped, String reason) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setAlert(history.getAlert());
        delivery.setAlertHistory(history);
        delivery.setChannel(route.channel());
        delivery.setRecipient(recipient);
        delivery.setDestinationSnapshot(destination == null || destination.isBlank() ? "Unconfigured destination" : destination);
        delivery.setIdempotencyKey(key);
        delivery.setStatus(skipped ? NotificationDelivery.DeliveryStatus.SKIPPED : NotificationDelivery.DeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(OffsetDateTime.now());
        delivery.setLastError(reason);
        delivery.setMessagePayload(buildPayload(history));
        deliveryRepository.save(delivery);
        if (skipped) {
            log.warn("Notification delivery queued as SKIPPED alertHistoryId={} alertId={} routeId={} channelId={} recipientId={} reason={}",
                    history.getId(), history.getAlert().getId(), route.id(), route.channel().getId(),
                    recipient == null ? null : recipient.getId(), reason);
        } else {
            log.info("Notification delivery queued alertHistoryId={} alertId={} deliveryChannelId={} recipientId={} deliveryStatus=PENDING",
                    history.getId(), history.getAlert().getId(), route.channel().getId(),
                    recipient == null ? null : recipient.getId());
        }
    }

    private String buildPayload(AlertHistory history) {
        Alert alert = history.getAlert();
        AlertRule rule = alert.getAlertRule();
        AlertRule.Severity severity = history.getNewSeverity() != null
                ? history.getNewSeverity() : alert.getSeverity();
        NotificationRoute.EventType eventType = routeEvent(history);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", alert.getTitle());
        payload.put("body", alert.getSummary() == null ? "" : alert.getSummary());
        payload.put("severity", severity == null ? "INFO" : severity.name());
        payload.put("eventType", eventType == null ? "" : eventType.name());
        payload.put("alertId", alert.getId());
        payload.put("historyId", history.getId());
        if (rule != null) payload.put("ruleName", rule.getName());
        payload.put("alertStatus", alert.getStatus().name());
        payload.put("occurrenceCount", alert.getOccurrenceCount());
        payload.put("firstOccurredAt", alert.getFirstOccurredAt() == null ? "" : alert.getFirstOccurredAt().toString());
        payload.put("lastOccurredAt", alert.getLastOccurredAt() == null ? "" : alert.getLastOccurredAt().toString());
        if (alert.getCurrentValue() != null && !alert.getCurrentValue().isBlank()) {
            payload.put("observedValue", alert.getCurrentValue());
        }
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

    private boolean cooldownApplies(RouteTarget route) {
        return route.eventType() == NotificationRoute.EventType.RECURRENCE
                || route.eventType() == NotificationRoute.EventType.REMINDER;
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

    private record RouteTarget(Long id, NotificationChannel channel, NotificationRecipient recipient,
            NotificationGroup group, NotificationRoute.EventType eventType, Integer minimumIntervalSeconds) {}
    private record OptionalRecent(NotificationDelivery delivery) {}
}

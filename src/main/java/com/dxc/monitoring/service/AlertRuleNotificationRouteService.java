package com.dxc.monitoring.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleNotificationRoute;
import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationGroup;
import com.dxc.monitoring.entity.NotificationRecipient;
import com.dxc.monitoring.entity.NotificationRoute;
import com.dxc.monitoring.repository.AlertRuleNotificationRouteRepository;
import com.dxc.monitoring.repository.NotificationChannelRepository;
import com.dxc.monitoring.repository.NotificationGroupRepository;
import com.dxc.monitoring.repository.NotificationRecipientRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AlertRuleNotificationRouteService {
    private final AlertRuleNotificationRouteRepository routeRepository;
    private final AlertRuleRepository ruleRepository;
    private final NotificationChannelRepository channelRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationGroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    public AlertRuleNotificationRouteService(AlertRuleNotificationRouteRepository routeRepository,
            AlertRuleRepository ruleRepository, NotificationChannelRepository channelRepository,
            NotificationRecipientRepository recipientRepository, NotificationGroupRepository groupRepository,
            ObjectMapper objectMapper) {
        this.routeRepository = routeRepository;
        this.ruleRepository = ruleRepository;
        this.channelRepository = channelRepository;
        this.recipientRepository = recipientRepository;
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<NotificationChannel> enabledChannels() {
        return channelRepository.findByEnabledTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<NotificationRecipient> enabledRecipients() {
        return recipientRepository.findByEnabledTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<NotificationGroup> enabledGroups() {
        return groupRepository.findByEnabledTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public String routesAsJson(Long ruleId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AlertRuleNotificationRoute route : routeRepository.findByAlertRule_IdOrderByIdAsc(ruleId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("channelId", route.getChannel().getId());
            row.put("targetType", route.getRecipient() != null ? "RECIPIENT" : "GROUP");
            row.put("recipientId", route.getRecipient() == null ? null : route.getRecipient().getId());
            row.put("groupId", route.getGroup() == null ? null : route.getGroup().getId());
            row.put("severity", route.getSeverity().name());
            row.put("eventType", route.getEventType().name());
            row.put("enabled", route.isEnabled());
            row.put("minimumIntervalSeconds", route.getMinimumIntervalSeconds());
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load alert rule notification routes.", exception);
        }
    }

    @Transactional
    public void replaceRoutes(Long ruleId, String routesJson) {
        AlertRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("Alert rule was not found."));
        List<Map<String, Object>> rows;
        try {
            String json = routesJson == null || routesJson.isBlank() ? "[]" : routesJson;
            rows = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception exception) {
            throw new IllegalArgumentException("Notification routes are invalid. Review the route settings and try again.");
        }
        if (rows.size() > 100) throw new IllegalArgumentException("An alert rule can have at most 100 notification routes.");

        List<AlertRuleNotificationRoute> replacements = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long channelId = longValue(row.get("channelId"));
            Long recipientId = longValue(row.get("recipientId"));
            Long groupId = longValue(row.get("groupId"));
            String targetType = stringValue(row.get("targetType"));
            if (channelId == null) throw new IllegalArgumentException("Select a notification channel for every route.");
            if ("RECIPIENT".equals(targetType) ? recipientId == null || groupId != null
                    : !"GROUP".equals(targetType) || groupId == null || recipientId != null) {
                throw new IllegalArgumentException("Each notification route must target exactly one recipient or group.");
            }

            NotificationChannel channel = channelRepository.findById(channelId)
                    .filter(NotificationChannel::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Choose an enabled notification channel."));
            NotificationRecipient recipient = null;
            NotificationGroup group = null;
            if ("RECIPIENT".equals(targetType)) {
                recipient = recipientRepository.findById(recipientId).filter(NotificationRecipient::isEnabled)
                        .orElseThrow(() -> new IllegalArgumentException("Choose an enabled notification recipient."));
            } else {
                group = groupRepository.findById(groupId).filter(NotificationGroup::isEnabled)
                        .orElseThrow(() -> new IllegalArgumentException("Choose an enabled notification group."));
            }

            AlertRuleNotificationRoute route = new AlertRuleNotificationRoute();
            route.setAlertRule(rule);
            route.setChannel(channel);
            route.setRecipient(recipient);
            route.setGroup(group);
            try {
                route.setSeverity(AlertRule.Severity.valueOf(stringValue(row.get("severity"))));
                route.setEventType(NotificationRoute.EventType.valueOf(stringValue(row.get("eventType"))));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Choose a valid severity and notification event for every route.");
            }
            route.setEnabled(!Boolean.FALSE.equals(row.get("enabled")));
            Long interval = longValue(row.get("minimumIntervalSeconds"));
            if (interval == null) interval = 300L;
            if (interval < 0 || interval > 2_147_483_647L) {
                throw new IllegalArgumentException("Minimum notification interval must be zero or greater.");
            }
            route.setMinimumIntervalSeconds(interval.intValue());
            replacements.add(route);
        }

        routeRepository.deleteByAlertRule_Id(ruleId);
        routeRepository.flush();
        routeRepository.saveAll(replacements);
    }

    private Long longValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return new java.math.BigDecimal(String.valueOf(value)).longValueExact();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("A notification route contains an invalid identifier or interval.");
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

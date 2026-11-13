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
    public Map<Long, String> emailSenderAddresses() {
        Map<Long, String> senders = new LinkedHashMap<>();
        for (NotificationChannel channel : enabledChannels()) {
            if (channel.getChannelType() != NotificationChannel.ChannelType.EMAIL) continue;
            try {
                Map<String, Object> config = objectMapper.readValue(channel.getConfiguration(), new TypeReference<Map<String, Object>>() {});
                Object from = config.get("from");
                if (from != null && !String.valueOf(from).isBlank()) senders.put(channel.getId(), String.valueOf(from).trim());
            } catch (Exception ignored) {
                // Preview metadata is optional; channel delivery validates its own configuration.
            }
        }
        return senders;
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
            row.put("ccRecipients", route.getCcRecipients() == null ? "" : route.getCcRecipients());
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

            NotificationChannel channel = channelRepository.findById(channelId)
                    .filter(NotificationChannel::isEnabled)
                    .orElseThrow(() -> new IllegalArgumentException("Choose an enabled notification channel."));
            boolean teamsChannel = channel.getChannelType() == NotificationChannel.ChannelType.TEAMS;
            if (teamsChannel) {
                if (!"NONE".equals(targetType) || recipientId != null || groupId != null) {
                    throw new IllegalArgumentException("Teams routes use the destination configured on the Teams channel; no recipient or group is required.");
                }
            } else if ("RECIPIENT".equals(targetType) ? recipientId == null || groupId != null
                    : !"GROUP".equals(targetType) || groupId == null || recipientId != null) {
                throw new IllegalArgumentException("Each Email or WhatsApp route must target exactly one recipient or group.");
            }

            NotificationRecipient recipient = null;
            NotificationGroup group = null;
            if (!teamsChannel && "RECIPIENT".equals(targetType)) {
                recipient = recipientRepository.findById(recipientId).filter(NotificationRecipient::isEnabled)
                        .orElseThrow(() -> new IllegalArgumentException("Choose an enabled notification recipient."));
            } else if (!teamsChannel) {
                group = groupRepository.findById(groupId).filter(NotificationGroup::isEnabled)
                        .orElseThrow(() -> new IllegalArgumentException("Choose an enabled notification group."));
            }

            AlertRuleNotificationRoute route = new AlertRuleNotificationRoute();
            route.setAlertRule(rule);
            route.setChannel(channel);
            route.setRecipient(recipient);
            route.setGroup(group);
            String ccRecipients = normalizeCcRecipients(row.get("ccRecipients"));
            if (!ccRecipients.isBlank() && channel.getChannelType() != NotificationChannel.ChannelType.EMAIL) {
                throw new IllegalArgumentException("CC recipients can be configured only for Email routes.");
            }
            route.setCcRecipients(ccRecipients.isBlank() ? null : ccRecipients);
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

    private String normalizeCcRecipients(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return "";
        String[] addresses = String.valueOf(value).split("[,;\\s]+");
        if (addresses.length > 20) throw new IllegalArgumentException("An Email route can have at most 20 CC addresses.");
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        for (String address : addresses) {
            String candidate = address.trim();
            if (candidate.isEmpty()) continue;
            if (candidate.length() > 254 || !candidate.matches("(?i)[A-Z0-9.!#$%&*+/=?^_{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+")) {
                throw new IllegalArgumentException("Enter valid CC email addresses separated by commas.");
            }
            unique.add(candidate);
        }
        return String.join(",", unique);
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

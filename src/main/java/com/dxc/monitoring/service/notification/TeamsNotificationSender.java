package com.dxc.monitoring.service.notification;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;

@Component
public class TeamsNotificationSender implements NotificationChannelSender {
    private final RestClient restClient;

    public TeamsNotificationSender(NotificationRestClientFactory restClientFactory) {
        this.restClient = restClientFactory.create();
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.TEAMS;
    }

    @Override
    public String send(NotificationChannel channel, NotificationRecipient recipient,
            Map<String, Object> payload, String credential) {
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("Teams webhook secret is not configured.");
        }
        validateWebhook(credential);
        Map<String, Object> card = buildAdaptiveCard(payload);
        try {
            restClient.post().uri(URI.create(credential)).contentType(MediaType.APPLICATION_JSON)
                    .body(card).retrieve().toBodilessEntity();
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("Teams webhook delivery failed.");
        }
    }

    Map<String, Object> buildAdaptiveCard(Map<String, Object> payload) {
        String severity = value(payload, "severity", "INFO").toUpperCase(Locale.ROOT);
        String eventType = value(payload, "eventType", "").toUpperCase(Locale.ROOT);
        String title = value(payload, "title", "Monitoring alert");
        String message = value(payload, "body", "");
        boolean recovered = "RECOVERED".equals(eventType) || "RESOLVED".equals(eventType);
        String bannerStyle = recovered ? "good" : switch (severity) {
            case "CRITICAL" -> "attention";
            case "WARNING" -> "warning";
            default -> "accent";
        };
        String label = recovered ? "RECOVERY" : switch (severity) {
            case "CRITICAL" -> "CRITICAL ALERT";
            case "WARNING" -> "WARNING ALERT";
            default -> "INFORMATIONAL ALERT";
        };

        Map<String, Object> banner = new LinkedHashMap<>();
        banner.put("type", "Container");
        banner.put("style", bannerStyle);
        banner.put("bleed", true);
        banner.put("items", List.of(
                textBlock(label, "Bolder", "Large", "Light"),
                textBlock(title, "Bolder", "Medium", "Light")));

        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "Application", value(payload, "application", ""));
        addFact(facts, "Environment", value(payload, "environment", ""));
        addFact(facts, "Alert Rule", value(payload, "ruleName", ""));
        addFact(facts, "Monitoring Job", value(payload, "monitoringJob", ""));
        addFact(facts, "Alert ID", value(payload, "alertId", ""));
        addFact(facts, "Status", value(payload, "alertStatus", ""));
        addFact(facts, "Event", eventType);
        addFact(facts, "Occurrences", value(payload, "occurrenceCount", ""));
        addFact(facts, "Observed Value", value(payload, "observedValue", ""));
        addFact(facts, "Threshold", value(payload, "threshold", ""));
        addFact(facts, "First Occurred", value(payload, "firstOccurredAt", ""));
        addFact(facts, "Last Occurred", value(payload, "lastOccurredAt", ""));

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(banner);
        if (!message.isBlank()) {
            Map<String, Object> messageBlock = textBlock(message, "Default", "Medium", "Default");
            messageBlock.put("spacing", "Medium");
            body.add(messageBlock);
        }
        if (!facts.isEmpty()) {
            Map<String, Object> factSet = new LinkedHashMap<>();
            factSet.put("type", "FactSet");
            factSet.put("facts", facts);
            body.add(factSet);
        }
        Map<String, Object> footer = textBlock("Monitoring Alert Manager", "Default", "Small", "Default");
        footer.put("isSubtle", true);
        footer.put("spacing", "Medium");
        body.add(footer);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "https://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.2");
        card.put("body", body);
        return card;
    }

    private Map<String, Object> textBlock(String text, String weight, String size, String color) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "TextBlock");
        block.put("text", text);
        block.put("weight", weight);
        block.put("size", size);
        block.put("color", color);
        block.put("wrap", true);
        return block;
    }

    private void addFact(List<Map<String, String>> facts, String label, String value) {
        if (value == null || value.isBlank() || "—".equals(value)) return;
        facts.add(Map.of("title", label, "value", value));
    }

    private void validateWebhook(String webhook) {
        try {
            URI uri = URI.create(webhook);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean trustedHost = host.equals("outlook.office.com")
                    || host.equals("outlook.office365.com")
                    || host.endsWith(".webhook.office.com")
                    || host.endsWith(".webhook.office365.com")
                    || host.endsWith(".logic.azure.com")
                    || host.endsWith(".powerplatform.com");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !trustedHost || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Teams webhook must be an HTTPS Microsoft Teams webhook URL.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Teams webhook URL is invalid or uses an unsupported host.");
        }
    }

    private String value(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}

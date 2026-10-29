package com.dxc.monitoring.service.notification;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

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
        String severity = value(payload, "severity", "INFO");
        String title = value(payload, "title", "Monitoring alert");
        String body = value(payload, "body", "");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("@type", "MessageCard");
        card.put("@context", "http://schema.org/extensions");
        card.put("summary", title);
        card.put("themeColor", switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> "D13438";
            case "WARNING" -> "FFB900";
            default -> "0078D4";
        });
        card.put("title", title);
        card.put("text", body);
        card.put("sections", List.of(Map.of("facts", List.of(
                Map.of("name", "Application", "value", value(payload, "application", "—")),
                Map.of("name", "Environment", "value", value(payload, "environment", "—")),
                Map.of("name", "Monitoring Job", "value", value(payload, "monitoringJob", "—")),
                Map.of("name", "Alert ID", "value", value(payload, "alertId", "—"))))));
        try {
            restClient.post().uri(URI.create(credential)).contentType(MediaType.APPLICATION_JSON)
                    .body(card).retrieve().toBodilessEntity();
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("Teams webhook delivery failed.");
        }
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

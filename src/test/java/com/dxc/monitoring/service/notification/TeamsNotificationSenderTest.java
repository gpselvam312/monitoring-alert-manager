package com.dxc.monitoring.service.notification;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TeamsNotificationSenderTest {
    @Test
    void preservesLineBreaksInConfiguredTextSections() {
        TeamsNotificationSender sender = new TeamsNotificationSender(mock(NotificationRestClientFactory.class));
        Map<String, Object> card = sender.buildAdaptiveCard(Map.of(
                "title", "Multiline alert",
                "content", List.of(Map.of("type", "text", "text", "First line\nSecond line"))));

        assertTrue(String.valueOf(card.get("body")).contains("First line\nSecond line"));
    }

    @Test
    void rendersListAndJsonPresentationSectionsInAdaptiveCard() {
        TeamsNotificationSender sender = new TeamsNotificationSender(mock(NotificationRestClientFactory.class));
        Map<String, Object> card = sender.buildAdaptiveCard(Map.of(
                "title", "Batch job alert",
                "severity", "CRITICAL",
                "content", List.of(
                        Map.of("type", "list", "title", "Impacted groups", "items", List.of("orders", "payments")),
                        Map.of("type", "json", "title", "Cluster response", "value", "{\"status\":\"CRITICAL\"}"))));

        String rendered = String.valueOf(card.get("body"));
        assertTrue(rendered.contains("Impacted groups"));
        assertTrue(rendered.contains("orders"));
        assertTrue(rendered.contains("payments"));
        assertTrue(rendered.contains("Cluster response"));
        assertTrue(rendered.contains("CRITICAL"));
    }
}

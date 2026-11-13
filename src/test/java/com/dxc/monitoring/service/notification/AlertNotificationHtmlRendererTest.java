package com.dxc.monitoring.service.notification;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import java.util.List;

import org.junit.jupiter.api.Test;

class AlertNotificationHtmlRendererTest {
    private final AlertNotificationHtmlRenderer renderer = new AlertNotificationHtmlRenderer();

    @Test
    void criticalAlertUsesHighContrastRedHeaderAndEscapesPayloadValues() {
        String html = renderer.render(Map.of(
                "title", "Critical <payment> failure",
                "body", "Rate exceeded <strong>threshold</strong> & requires attention",
                "severity", "CRITICAL",
                "application", "Payments",
                "environment", "PROD"));

        assertTrue(html.contains("background:#B42318"));
        assertTrue(html.contains("color:#FFFFFF"));
        assertTrue(html.contains("CRITICAL ALERT"));
        assertTrue(html.contains("Critical &lt;payment&gt; failure"));
        assertTrue(html.contains("Rate exceeded &lt;strong&gt;threshold&lt;/strong&gt; &amp; requires attention"));
        assertFalse(html.contains("<strong>threshold</strong>"));
    }


    @Test
    void rendersConfiguredSectionsAndEscapesEverySectionValue() {
        String html = renderer.render(Map.of(
                "title", "Service report",
                "severity", "WARNING",
                "content", List.of(
                        Map.of("type", "text", "title", "Summary", "text", "Queue <b>degraded</b>"),
                        Map.of("type", "details", "title", "Facts", "fields",
                                List.of(Map.of("label", "Cluster", "value", "kafka & prod"))),
                        Map.of("type", "table", "title", "Nodes",
                                "columns", List.of(Map.of("key", "name", "label", "Node")),
                                "rows", List.of(Map.of("name", "<node-a>"))),
                        Map.of("type", "list", "title", "Impacted", "items", List.of("consumer-a", "consumer-b")),
                        Map.of("type", "json", "title", "Payload", "value", """
                                {"state":"DOWN"}
                                """))));

        assertTrue(html.contains("Queue &lt;b&gt;degraded&lt;/b&gt;"));
        assertTrue(html.contains("kafka &amp; prod"));
        assertTrue(html.contains("&lt;node-a&gt;"));
        assertTrue(html.contains("consumer-a"));
        assertTrue(html.contains("&quot;state&quot;"));
        assertFalse(html.contains("<b>degraded</b>"));
    }

    @Test
    void preservesLineBreaksInConfiguredTextSections() {
        String html = renderer.render(Map.of(
                "title", "Multiline alert",
                "content", List.of(Map.of("type", "text", "text", "First line\nSecond line\n\nFourth line"))));

        assertTrue(html.contains("white-space:pre-wrap;"));
        assertTrue(html.contains("First line\nSecond line\n\nFourth line"));
    }

    @Test
    void warningAlertUsesAmberHeader() {
        String html = renderer.render(Map.of("title", "Queue depth high", "severity", "WARNING"));

        assertTrue(html.contains("background:#B54708"));
        assertTrue(html.contains("WARNING ALERT"));
    }

    @Test
    void successAlertUsesGreenHeader() {
        String html = renderer.render(Map.of("title", "Service healthy", "severity", "SUCCESS"));

        assertTrue(html.contains("background:#067647"));
        assertTrue(html.contains("SUCCESS"));
    }

    @Test
    void recoveryEventUsesGreenHeaderRegardlessOfPreviousSeverity() {
        String html = renderer.render(Map.of(
                "title", "Payment service recovered",
                "severity", "CRITICAL",
                "eventType", "RECOVERED"));

        assertTrue(html.contains("background:#067647"));
        assertTrue(html.contains("RECOVERY"));
    }

    @Test
    void informationalAlertUsesBlueHeader() {
        String html = renderer.render(Map.of("title", "Monitor check", "severity", "INFO"));

        assertTrue(html.contains("background:#175CD3"));
        assertTrue(html.contains("INFORMATIONAL ALERT"));
    }
}

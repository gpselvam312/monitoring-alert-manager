package com.dxc.monitoring.service.notification;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

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
    void warningAlertUsesAmberHeader() {
        String html = renderer.render(Map.of("title", "Queue depth high", "severity", "WARNING"));

        assertTrue(html.contains("background:#B54708"));
        assertTrue(html.contains("WARNING ALERT"));
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

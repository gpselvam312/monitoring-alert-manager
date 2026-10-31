package com.dxc.monitoring.service.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Renders notification payloads as email-safe HTML using inline styles for broad mail-client support.
 * Payload values are always HTML-escaped before being inserted into the template.
 */
@Component
public class AlertNotificationHtmlRenderer {
    public String render(Map<String, Object> payload) {
        String severity = value(payload, "severity", "INFO").toUpperCase(Locale.ROOT);
        String eventType = value(payload, "eventType", "");
        Palette palette = palette(severity, eventType);
        String title = value(payload, "title", "Monitoring alert");
        String message = value(payload, "body", "");
        List<String[]> facts = new ArrayList<>();
        addFact(facts, "Application", value(payload, "application", ""));
        addFact(facts, "Environment", value(payload, "environment", ""));
        addFact(facts, "Monitoring job", value(payload, "monitoringJob", ""));
        addFact(facts, "Alert ID", value(payload, "alertId", ""));
        addFact(facts, "Event", eventType);
        addFact(facts, "Observed value", value(payload, "observedValue", ""));
        addFact(facts, "Threshold", value(payload, "threshold", ""));

        StringBuilder rows = new StringBuilder();
        for (String[] fact : facts) {
            rows.append("<tr>")
                .append("<td style=\"padding:10px 12px;border-bottom:1px solid #EAECF0;color:#667085;font-size:13px;width:38%;\">")
                .append(escape(fact[0])).append("</td>")
                .append("<td style=\"padding:10px 12px;border-bottom:1px solid #EAECF0;color:#101828;font-size:14px;font-weight:600;\">")
                .append(escape(fact[1])).append("</td></tr>");
        }

        return "<!doctype html><html><body style=\"margin:0;padding:0;background:#F2F4F7;font-family:Arial,Helvetica,sans-serif;color:#101828;\">"
            + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"background:#F2F4F7;padding:24px 8px;\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"600\" style=\"width:100%;max-width:600px;background:#FFFFFF;border:1px solid #EAECF0;border-radius:10px;overflow:hidden;\">"
            + "<tr><td style=\"background:" + palette.background() + ";border-left:7px solid " + palette.accent() + ";padding:22px 24px;\">"
            + "<div style=\"font-size:12px;letter-spacing:1px;font-weight:700;color:" + palette.foreground() + ";\">" + escape(palette.label()) + "</div>"
            + "<div style=\"font-size:23px;line-height:1.35;font-weight:700;color:" + palette.foreground() + ";padding-top:7px;\">" + escape(title) + "</div>"
            + "</td></tr><tr><td style=\"padding:24px;\">"
            + "<div style=\"font-size:15px;line-height:1.65;color:#344054;white-space:normal;\">" + escape(message).replace("\r\n", "\n").replace("\n", "<br>") + "</div>"
            + (facts.isEmpty() ? "" : "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"margin-top:20px;border:1px solid #EAECF0;border-collapse:collapse;\">" + rows + "</table>")
            + "<div style=\"margin-top:24px;padding-top:14px;border-top:1px solid #EAECF0;color:#667085;font-size:12px;\">Monitoring Alert Manager</div>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    private Palette palette(String severity, String eventType) {
        if ("RECOVERED".equalsIgnoreCase(eventType) || "RESOLVED".equalsIgnoreCase(eventType)) {
            return new Palette("Recovery", "#067647", "#D1FADF", "#FFFFFF");
        }
        return switch (severity) {
            case "CRITICAL" -> new Palette("CRITICAL ALERT", "#B42318", "#FEE4E2", "#7A271A");
            case "WARNING" -> new Palette("WARNING ALERT", "#B54708", "#FEF0C7", "#7A2E0E");
            default -> new Palette("INFORMATIONAL ALERT", "#175CD3", "#D1E9FF", "#1849A9");
        };
    }

    private void addFact(List<String[]> facts, String label, String value) {
        if (value != null && !value.isBlank() && !"—".equals(value)) facts.add(new String[] {label, value});
    }

    private String value(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private record Palette(String label, String accent, String background, String foreground) {}
}

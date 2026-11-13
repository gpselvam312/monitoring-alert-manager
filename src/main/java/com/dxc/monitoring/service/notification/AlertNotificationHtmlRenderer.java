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
        addFact(facts, "Alert rule", value(payload, "ruleName", ""));
        addFact(facts, "Monitoring job", value(payload, "monitoringJob", ""));
        addFact(facts, "Alert ID", value(payload, "alertId", ""));
        addFact(facts, "Status", value(payload, "alertStatus", ""));
        addFact(facts, "Event", eventType);
        addFact(facts, "Occurrences", value(payload, "occurrenceCount", ""));
        boolean hasConfiguredSections = payload.get("content") instanceof List<?> content && !content.isEmpty();
        if (!hasConfiguredSections) {
            addFact(facts, "Observed value", value(payload, "observedValue", ""));
            addFact(facts, "Threshold", value(payload, "threshold", ""));
        }
        addFact(facts, "First occurred", value(payload, "firstOccurredAt", ""));
        addFact(facts, "Last occurred", value(payload, "lastOccurredAt", ""));

        String sectionsHtml = renderSections(payload.get("content"));

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
            + sectionsHtml
            + (facts.isEmpty() ? "" : "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" style=\"margin-top:20px;border:1px solid #EAECF0;border-collapse:collapse;\">" + rows + "</table>")
            + "<div style=\"margin-top:24px;padding-top:14px;border-top:1px solid #EAECF0;color:#667085;font-size:12px;\">Monitoring Alert Manager</div>"
            + "</td></tr></table></td></tr></table></body></html>";
    }

    private String renderSections(Object rawContent) {
        if (!(rawContent instanceof List<?> sections) || sections.isEmpty()) return "";
        StringBuilder html = new StringBuilder();
        for (Object rawSection : sections.stream().limit(20).toList()) {
            if (!(rawSection instanceof Map<?, ?> section)) continue;
            String type = value(section, "type", "").toLowerCase(Locale.ROOT);
            String title = value(section, "title", "");
            if (!title.isBlank()) html.append("<h3 style=\"font-size:15px;margin:22px 0 8px;color:#101828;\">")
                    .append(escape(title)).append("</h3>");
            switch (type) {
                case "text" -> html.append("<div style=\"font-size:14px;line-height:1.6;color:#344054;white-space:pre-wrap;\">")
                        .append(escape(value(section, "text", ""))).append("</div>");
                case "details" -> {
                    Object rawFields = section.get("fields");
                    if (rawFields instanceof List<?> fields) {
                        html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"margin-top:8px;border:1px solid #EAECF0;border-collapse:collapse;\">");
                        for (Object rawField : fields.stream().limit(50).toList()) {
                            if (!(rawField instanceof Map<?, ?> field)) continue;
                            html.append("<tr><td style=\"padding:8px 10px;border-bottom:1px solid #EAECF0;color:#667085;font-size:13px;width:38%;\">")
                                    .append(escape(value(field, "label", ""))).append("</td><td style=\"padding:8px 10px;border-bottom:1px solid #EAECF0;color:#101828;font-size:14px;\">")
                                    .append(escape(display(field.get("value")))).append("</td></tr>");
                        }
                        html.append("</table>");
                    }
                }
                case "table" -> html.append(renderTable(section));
                case "list" -> html.append(renderList(section));
                case "json" -> html.append("<pre style=\"background:#F9FAFB;border:1px solid #EAECF0;padding:12px;white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px;color:#344054;\">")
                        .append(escape(value(section, "value", ""))).append("</pre>");
                default -> { }
            }
        }
        return html.toString();
    }

    private String renderList(Map<?, ?> section) {
        Object rawItems = section.get("items");
        if (!(rawItems instanceof List<?> items) || items.isEmpty()) return "";

        StringBuilder html = new StringBuilder(
                "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" width=\"100%\" "
                + "style=\"margin-top:8px;border:1px solid #EAECF0;border-collapse:collapse;background-color:#FFFFFF;color:#101828;font-size:13px;\">"
                + "<thead><tr>"
                + "<th align=\"left\" bgcolor=\"#F2F4F7\" style=\"padding:9px 11px;background-color:#F2F4F7;color:#344054;border-bottom:1px solid #EAECF0;font-weight:700;width:38%;\">Attribute</th>"
                + "<th align=\"left\" bgcolor=\"#F2F4F7\" style=\"padding:9px 11px;background-color:#F2F4F7;color:#344054;border-bottom:1px solid #EAECF0;font-weight:700;\">Value</th>"
                + "</tr></thead><tbody>");
        int rowIndex = 0;
        for (Object item : items.stream().limit(100).toList()) {
            String renderedItem = display(item);
            String[] attributes = renderedItem.split("\\s+\\|\\s+");
            for (String attribute : attributes) {
                int separator = attribute.indexOf(':');
                String label = separator > 0 ? attribute.substring(0, separator).trim() : "Value";
                String value = separator > 0 ? attribute.substring(separator + 1).trim() : attribute.trim();
                String background = rowIndex++ % 2 == 0 ? "#FFFFFF" : "#F8FAFC";
                html.append("<tr>")
                        .append("<td bgcolor=\"").append(background)
                        .append("\" style=\"padding:9px 11px;border-bottom:1px solid #EAECF0;background-color:")
                        .append(background).append(";color:#475467;font-weight:600;font-size:13px;vertical-align:top;\">")
                        .append(escape(label)).append("</td>")
                        .append("<td bgcolor=\"").append(background)
                        .append("\" style=\"padding:9px 11px;border-bottom:1px solid #EAECF0;background-color:")
                        .append(background).append(";color:#101828;font-size:13px;line-height:1.5;vertical-align:top;\">")
                        .append(escape(value)).append("</td></tr>");
            }
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String renderTable(Map<?, ?> section) {
        Object rawColumns = section.get("columns");
        Object rawRows = section.get("rows");
        if (!(rawColumns instanceof List<?> columns) || columns.isEmpty()
                || !(rawRows instanceof List<?> rows) || rows.isEmpty()) return "";
        StringBuilder html = new StringBuilder("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" width=\"100%\" style=\"margin-top:8px;border:1px solid #EAECF0;border-collapse:collapse;font-size:13px;\"><thead><tr>");
        List<String> keys = new ArrayList<>();
        for (Object rawColumn : columns) {
            String key = rawColumn instanceof Map<?, ?> column ? value(column, "key", "") : String.valueOf(rawColumn);
            String label = rawColumn instanceof Map<?, ?> column ? value(column, "label", key) : key;
            keys.add(key);
            html.append("<th style=\"padding:8px 10px;text-align:left;background:#F2F4F7;border-bottom:1px solid #EAECF0;\">")
                    .append(escape(label)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (Object rawRow : rows.stream().limit(100).toList()) {
            html.append("<tr>");
            for (String key : keys) {
                Object cell = rawRow instanceof Map<?, ?> row ? row.get(key) : null;
                html.append("<td style=\"padding:8px 10px;border-bottom:1px solid #EAECF0;\">")
                        .append(escape(display(cell))).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }

    private String display(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> String.valueOf(entry.getKey()) + ": " + display(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::display).collect(java.util.stream.Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private Palette palette(String severity, String eventType) {
        if ("RECOVERED".equalsIgnoreCase(eventType) || "RESOLVED".equalsIgnoreCase(eventType)) {
            return new Palette("RECOVERY", "#05603A", "#067647", "#FFFFFF");
        }
        return switch (severity) {
            case "CRITICAL" -> new Palette("CRITICAL ALERT", "#7A271A", "#B42318", "#FFFFFF");
            case "WARNING" -> new Palette("WARNING ALERT", "#7A2E0E", "#B54708", "#FFFFFF");
            case "SUCCESS" -> new Palette("SUCCESS", "#05603A", "#067647", "#FFFFFF");
            default -> new Palette("INFORMATIONAL ALERT", "#1849A9", "#175CD3", "#FFFFFF");
        };
    }

    private void addFact(List<String[]> facts, String label, String value) {
        if (value != null && !value.isBlank() && !"—".equals(value)) facts.add(new String[] {label, value});
    }

    private String value(Map<?, ?> payload, String key, String fallback) {
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

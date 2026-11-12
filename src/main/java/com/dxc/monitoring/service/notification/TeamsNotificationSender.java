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
            Map<String, Object> requestBody = Map.of("renderedCard", card);
            restClient.post().uri(URI.create(credential)).contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody).retrieve().toBodilessEntity();
            return null;
        } catch (Exception exception) {
            throw new IllegalStateException("Teams webhook delivery failed: " + safeError(exception), exception);
        }
    }

    Map<String, Object> buildAdaptiveCard(Map<String, Object> payload) {
        String severity = value(payload, "severity", "INFO").toUpperCase(Locale.ROOT);
        String eventType = value(payload, "eventType", "").toUpperCase(Locale.ROOT);
        String title = value(payload, "title", "Monitoring alert");
        boolean recovered = "RECOVERED".equals(eventType) || "RESOLVED".equals(eventType);

        String label;
        String bannerStyle;
        String bannerImage;
        if (recovered) {
            label = "RECOVERY";
            bannerStyle = "good";
            bannerImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgK3MHAAFJAMTiIx/MAAAAAElFTkSuQmCC";
        } else {
            switch (severity) {
                case "CRITICAL" -> {
                    label = "CRITICAL ALERT";
                    bannerStyle = "attention";
                    bannerImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGPYoiwBAAJ+APBMCjG1AAAAAElFTkSuQmCC";
                }
                case "WARNING" -> {
                    label = "WARNING ALERT";
                    bannerStyle = "warning";
                    bannerImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGPY6s4BAAK5AQViYejEAAAAAElFTkSuQmCC";
                }
                default -> {
                    label = "INFORMATIONAL ALERT";
                    bannerStyle = "accent";
                    bannerImage = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGMQj7kMAAHUAUemTM0RAAAAAElFTkSuQmCC";
                }
            }
        }

        Map<String, Object> banner = new LinkedHashMap<>();
        banner.put("type", "Container");
        banner.put("style", bannerStyle);
        banner.put("backgroundImage", bannerImage);
        banner.put("bleed", true);
        banner.put("items", List.of(
                textBlock(label, "Bolder", "Large", "Light"),
                textBlock(title, "Bolder", "Medium", "Light")));

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(banner);
        appendContent(body, payload);

        List<Map<String, String>> facts = new ArrayList<>();
        addFact(facts, "Application", value(payload, "application", ""));
        addFact(facts, "Environment", value(payload, "environment", ""));
        addFact(facts, "Monitoring Job", value(payload, "monitoringJob", ""));
        if (facts.isEmpty()) {
            // No fixed metadata is required; content sections can carry any report-specific details.
        } else {
            Map<String, Object> factSet = new LinkedHashMap<>();
            factSet.put("type", "FactSet");
            factSet.put("facts", facts);
            factSet.put("spacing", "Medium");
            body.add(factSet);
        }

        String occurrenceCount = value(payload, "occurrenceCount", "");
        String eventSummary = eventType;
        if (!occurrenceCount.isBlank()) {
            eventSummary = eventSummary.isBlank()
                    ? occurrenceCount + " occurrences"
                    : eventSummary + " · " + occurrenceCount + " occurrences";
        }
        if (!eventSummary.isBlank()) {
            Map<String, Object> eventBlock = textBlock(eventSummary, "Default", "Small", "Default");
            eventBlock.put("isSubtle", true);
            eventBlock.put("spacing", "Small");
            body.add(eventBlock);
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "https://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.2");
        card.put("body", body);
        return card;
    }

    /**
     * Content contract:
     * content: [{ type: "text", text: "..." },
     *           { type: "details", title: "...", fields: [{ label: "...", value: "..." }] },
     *           { type: "table", title: "...", columns: ["..."], rows: [{ "column": "value" }] }]
     * Unknown or malformed sections are ignored. Legacy body remains the fallback.
     */
    private void appendContent(List<Map<String, Object>> cardBody, Map<String, Object> payload) {
        Object contentValue = payload.get("content");
        if (contentValue instanceof List<?> sections && !sections.isEmpty()) {
            for (Object sectionValue : sections) {
                if (sectionValue instanceof Map<?, ?> section) {
                    appendSection(cardBody, section);
                }
            }
            return;
        }

        // Render known alert metadata as a readable detail list instead of exposing JSON arrays.
        String fieldPath = value(payload, "fieldPath", "");
        String observedValue = value(payload, "observedValue", "");
        String ruleName = value(payload, "ruleName", "");
        String threshold = value(payload, "threshold", "");
        if (!fieldPath.isBlank() || !observedValue.isBlank() || !threshold.isBlank()) {
            List<Map<String, String>> fields = new ArrayList<>();
            addDetail(fields, "Rule", ruleName);
            addDetail(fields, "Field", fieldPath);
            addDetail(fields, "Observed value", observedValue);
            addDetail(fields, "Threshold", threshold);
            appendDetails(cardBody, "Alert details", fields);
            return;
        }

        String message = value(payload, "body", "");
        if (!message.isBlank()) {
            Map<String, Object> block = textBlock(message, "Default", "Medium", "Default");
            block.put("spacing", "Medium");
            cardBody.add(block);
        }
    }

    private void appendSection(List<Map<String, Object>> cardBody, Map<?, ?> section) {
        String type = string(section.get("type")).toLowerCase(Locale.ROOT);
        String title = string(section.get("title"));
        switch (type) {
            case "text" -> {
                String text = string(section.get("text"));
                if (!text.isBlank()) {
                    if (!title.isBlank()) cardBody.add(textBlock(title, "Bolder", "Medium", "Default"));
                    Map<String, Object> block = textBlock(text, "Default", "Medium", "Default");
                    block.put("spacing", "Small");
                    cardBody.add(block);
                }
            }
            case "details" -> {
                List<Map<String, String>> fields = new ArrayList<>();
                Object fieldValue = section.get("fields");
                if (fieldValue instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> field) {
                            addDetail(fields, string(field.get("label")), displayValue(field.get("value")));
                        }
                    }
                }
                appendDetails(cardBody, title, fields);
            }
            case "table" -> appendTable(cardBody, title, section);
            default -> {
                // Ignore unsupported section types so one malformed section cannot block delivery.
            }
        }
    }

    private void appendDetails(List<Map<String, Object>> cardBody, String title, List<Map<String, String>> fields) {
        if (fields.isEmpty()) return;
        if (!title.isBlank()) cardBody.add(textBlock(title, "Bolder", "Medium", "Default"));
        Map<String, Object> factSet = new LinkedHashMap<>();
        factSet.put("type", "FactSet");
        factSet.put("facts", fields.stream()
                .map(field -> Map.of("title", field.get("label"), "value", field.get("value"))).toList());
        factSet.put("spacing", "Small");
        cardBody.add(factSet);
    }

    private void appendTable(List<Map<String, Object>> cardBody, String title, Map<?, ?> section) {
        Object columnsValue = section.get("columns");
        Object rowsValue = section.get("rows");
        if (!(columnsValue instanceof List<?> columns) || columns.isEmpty()
                || !(rowsValue instanceof List<?> rows) || rows.isEmpty()) return;

        if (!title.isBlank()) cardBody.add(textBlock(title, "Bolder", "Medium", "Default"));
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Object column : columns) {
            if (column instanceof Map<?, ?> columnMap) {
                String key = string(columnMap.get("key"));
                String label = string(columnMap.get("label"));
                if (key.isBlank()) key = label;
                if (label.isBlank()) label = key;
                if (!key.isBlank()) { keys.add(key); labels.add(label); }
            } else {
                String label = string(column);
                if (!label.isBlank()) { keys.add(label); labels.add(label); }
            }
        }
        if (keys.isEmpty()) return;

        cardBody.add(tableRow(labels, true, false));
        int rowIndex = 0;
        for (Object row : rows) {
            List<String> values = new ArrayList<>();
            for (String key : keys) {
                Object cell = row instanceof Map<?, ?> rowMap ? rowMap.get(key)
                        : row instanceof List<?> rowList && keys.indexOf(key) < rowList.size()
                                ? rowList.get(keys.indexOf(key)) : null;
                values.add(displayValue(cell));
            }
            cardBody.add(tableRow(values, false, rowIndex++ % 2 == 0));
        }
    }

    // Adaptive Card 1.2 has no native Table element. ColumnSet and semantic container styles
    // provide a Teams-compatible table with a prominent header and alternating row emphasis.
    private Map<String, Object> tableRow(List<String> values, boolean header, boolean alternate) {
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String value : values) {
            Map<String, Object> text = textBlock(value, header ? "Bolder" : "Default",
                    "Small", header ? "Light" : "Default");
            text.put("wrap", true);
            text.put("spacing", "Small");
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("type", "Column");
            column.put("width", "stretch");
            column.put("items", List.of(text));
            columns.add(column);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "Container");
        // "accent" gives the header a strong theme-colored background; "emphasis" creates
        // subtle zebra striping without relying on unsupported CSS or arbitrary color values.
        row.put("style", header ? "accent" : alternate ? "emphasis" : "default");
        row.put("bleed", true);
        row.put("items", List.of(Map.of(
                "type", "ColumnSet",
                "columns", columns,
                "spacing", "None")));
        row.put("spacing", "None");
        return row;
    }

    private void addDetail(List<Map<String, String>> fields, String label, String value) {
        if (label != null && !label.isBlank() && value != null && !value.isBlank() && !"—".equals(value)) {
            fields.add(Map.of("label", label, "value", value));
        }
    }

    private String displayValue(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) return String.join(", ", list.stream().map(this::displayValue).toList());
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> string(entry.getKey()) + ": " + displayValue(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
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
        if (value != null && !value.isBlank() && !"—".equals(value)) facts.add(Map.of("title", label, "value", value));
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

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 300 ? sanitized.substring(0, 300) : sanitized;
    }

    private String value(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : displayValue(value);
    }
}

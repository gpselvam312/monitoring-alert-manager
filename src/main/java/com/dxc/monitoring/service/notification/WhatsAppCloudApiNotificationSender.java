package com.dxc.monitoring.service.notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class WhatsAppCloudApiNotificationSender implements NotificationChannelSender {
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WhatsAppCloudApiNotificationSender(ObjectMapper objectMapper, NotificationRestClientFactory restClientFactory) {
        this.objectMapper = objectMapper;
        this.restClient = restClientFactory.create();
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.WHATSAPP;
    }

    @Override
    public String send(NotificationChannel channel, NotificationRecipient recipient,
            Map<String, Object> payload, String credential) {
        if (recipient == null || recipient.getPhoneNumber() == null || recipient.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("WhatsApp recipient phone number is not configured.");
        }
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("WhatsApp Cloud API access token is not configured.");
        }
        Map<String, Object> config = parseConfig(channel.getConfiguration());
        String phoneNumberId = string(config, "phoneNumberId", "");
        if (!phoneNumberId.matches("[0-9]{5,30}")) {
            throw new IllegalArgumentException("WhatsApp channel requires a valid phone number ID.");
        }
        String version = string(config, "apiVersion", "v23.0");
        if (!version.matches("v[0-9]{2,3}\\.[0-9]+")) {
            throw new IllegalArgumentException("WhatsApp API version is invalid.");
        }
        String phone = recipient.getPhoneNumber().replaceAll("[^0-9]", "");
        if (phone.length() < 8 || phone.length() > 15) {
            throw new IllegalArgumentException("WhatsApp recipient must include a country code and valid phone number.");
        }

        String title = string(payload, "title", "Monitoring alert");
        String severity = string(payload, "severity", "ALERT");
        String bodyText = title + "\n" + string(payload, "body", "");
        if (bodyText.length() > 4000) bodyText = bodyText.substring(0, 4000);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messaging_product", "whatsapp");
        request.put("to", phone);
        String templateName = string(config, "templateName", "");
        if (!templateName.isBlank()) {
            request.put("type", "template");
            request.put("template", Map.of(
                    "name", templateName,
                    "language", Map.of("code", string(config, "languageCode", "en_US")),
                    "components", List.of(Map.of("type", "body", "parameters",
                            List.of(Map.of("type", "text", "text", bodyText))))));
        } else {
            request.put("type", "text");
            request.put("text", Map.of("preview_url", false, "body", bodyText));
        }

        String response;
        try {
            response = restClient.post()
                    .uri("https://graph.facebook.com/" + version + "/" + phoneNumberId + "/messages")
                    .header("Authorization", "Bearer " + credential)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
        } catch (Exception exception) {
            throw new IllegalStateException("WhatsApp Cloud API delivery failed.");
        }
        return providerMessageId(response);
    }

    private String providerMessageId(String response) {
        if (response == null || response.isBlank()) return null;
        try {
            Map<String, Object> root = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
            Object messages = root.get("messages");
            if (messages instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Object id = first.get("id");
                return id == null ? null : String.valueOf(id);
            }
        } catch (Exception ignored) {
            // Provider accepted the request but returned an unexpected response shape.
        }
        return null;
    }

    private Map<String, Object> parseConfig(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception exception) { throw new IllegalArgumentException("WhatsApp channel configuration is invalid."); }
    }

    private String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}

package com.dxc.monitoring.service.notification;

import java.util.Map;
import java.util.Properties;

import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class EmailNotificationSender implements NotificationChannelSender {
    private final ObjectMapper objectMapper;
    private final AlertNotificationHtmlRenderer htmlRenderer;

    public EmailNotificationSender(ObjectMapper objectMapper, AlertNotificationHtmlRenderer htmlRenderer) {
        this.objectMapper = objectMapper;
        this.htmlRenderer = htmlRenderer;
    }

    @Override
    public NotificationChannel.ChannelType channelType() {
        return NotificationChannel.ChannelType.EMAIL;
    }

    @Override
    public String send(NotificationChannel channel, NotificationRecipient recipient,
            Map<String, Object> payload, String credential) {
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email recipient address is not configured.");
        }
        Map<String, Object> config = parseConfig(channel.getConfiguration());
        String host = string(config, "host", "");
        String from = string(config, "from", "");
        if (host.isBlank() || from.isBlank()) {
            throw new IllegalArgumentException("Email channel requires SMTP host and sender address.");
        }
        int port = integer(config, "port", 587);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("SMTP port is invalid.");

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setDefaultEncoding("UTF-8");
        String username = string(config, "username", "");
        boolean auth = Boolean.parseBoolean(string(config, "auth", Boolean.toString(!username.isBlank())));
        boolean startTls = Boolean.parseBoolean(string(config, "startTls", "true"));
        if (!username.isBlank()) mailSender.setUsername(username);
        if (credential != null && !credential.isBlank()) mailSender.setPassword(credential);

        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", Boolean.toString(auth));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        String title = string(payload, "title", "Monitoring alert");
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient.getEmail());
            helper.setSubject(title);
            helper.setText(plainText(payload), htmlRenderer.render(payload));
            mailSender.send(mimeMessage);
        } catch (Exception exception) {
            throw new IllegalStateException("Email notification delivery failed.");
        }
        return null;
    }

    private String plainText(Map<String, Object> payload) {
        StringBuilder text = new StringBuilder();
        text.append(string(payload, "title", "Monitoring alert")).append("\n\n");
        text.append(string(payload, "body", ""));
        appendLine(text, "Severity", string(payload, "severity", ""));
        appendLine(text, "Application", string(payload, "application", ""));
        appendLine(text, "Environment", string(payload, "environment", ""));
        appendLine(text, "Monitoring Job", string(payload, "monitoringJob", ""));
        appendLine(text, "Alert ID", string(payload, "alertId", ""));
        appendLine(text, "Observed Value", string(payload, "observedValue", ""));
        appendLine(text, "Threshold", string(payload, "threshold", ""));
        return text.toString();
    }

    private void appendLine(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) text.append("\n").append(label).append(": ").append(value);
    }

    private Map<String, Object> parseConfig(String json) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception exception) { throw new IllegalArgumentException("Email channel configuration is invalid."); }
    }

    private String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private int integer(Map<String, Object> map, String key, int fallback) {
        try { return Integer.parseInt(string(map, key, Integer.toString(fallback))); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("SMTP port must be a whole number."); }
    }
}

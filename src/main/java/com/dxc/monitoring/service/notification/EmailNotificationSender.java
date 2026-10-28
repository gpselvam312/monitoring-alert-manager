package com.dxc.monitoring.service.notification;

import java.util.Map;
import java.util.Properties;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class EmailNotificationSender implements NotificationChannelSender {
    private final ObjectMapper objectMapper;

    public EmailNotificationSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

        String severity = string(payload, "severity", "ALERT");
        String title = string(payload, "title", "Monitoring alert");
        String body = string(payload, "body", "");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient.getEmail());
        message.setSubject("[" + severity + "] " + title);
        message.setText(body);
        mailSender.send(message);
        return null;
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

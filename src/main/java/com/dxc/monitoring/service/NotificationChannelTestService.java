package com.dxc.monitoring.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationRecipient;
import com.dxc.monitoring.repository.NotificationChannelRepository;
import com.dxc.monitoring.service.notification.NotificationChannelSender;

@Service
public class NotificationChannelTestService {
    private final NotificationChannelRepository channelRepository;
    private final NotificationChannelSecretService secretService;
    private final List<NotificationChannelSender> senders;

    public NotificationChannelTestService(NotificationChannelRepository channelRepository,
            NotificationChannelSecretService secretService, List<NotificationChannelSender> senders) {
        this.channelRepository = channelRepository;
        this.secretService = secretService;
        this.senders = senders;
    }

    @Transactional(readOnly = true)
    public void sendTest(Long channelId, String emailAddress, String phoneNumber) {
        NotificationChannel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Notification channel was not found."));
        NotificationRecipient destination = new NotificationRecipient();
        switch (channel.getChannelType()) {
            case EMAIL -> {
                String email = clean(emailAddress);
                if (email == null || email.length() > 255 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                    throw new IllegalArgumentException("Enter a valid test email address.");
                }
                destination.setEmail(email);
            }
            case TEAMS -> {
                // Teams delivers to the webhook configured for this channel; there is no separate destination.
            }
            case WHATSAPP -> {
                String phone = clean(phoneNumber);
                String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
                if (digits.length() < 8 || digits.length() > 15) {
                    throw new IllegalArgumentException("Enter a valid WhatsApp test number with country code.");
                }
                destination.setPhoneNumber(digits);
            }
        }

        NotificationChannelSender sender = senders.stream()
                .filter(candidate -> candidate.channelType() == channel.getChannelType())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No sender is configured for this channel type."));

        String secretKey = channel.getCredentialReference();
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = switch (channel.getChannelType()) {
                case EMAIL -> "smtp-password";
                case TEAMS -> "webhook-url";
                case WHATSAPP -> "access-token";
            };
        }
        String credential = secretService.getSecret(channel.getId(), secretKey);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", "Monitoring Alert Manager - Test Notification");
        payload.put("body", "This is a test notification from Monitoring Alert Manager. No alert was created.");
        payload.put("severity", "INFO");
        payload.put("application", "Monitoring Alert Manager");
        payload.put("environment", "Test");
        payload.put("monitoringJob", "Notification channel test");
        payload.put("alertId", "N/A");

        try {
            sender.send(channel, destination, payload, credential);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            // Do not surface provider exceptions; they may contain webhook URLs or request details.
            throw new IllegalStateException("The test notification could not be sent. Check the saved channel settings and provider connectivity.");
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}

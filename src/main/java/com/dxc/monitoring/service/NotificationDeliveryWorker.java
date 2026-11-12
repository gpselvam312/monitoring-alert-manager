package com.dxc.monitoring.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;
import com.dxc.monitoring.service.notification.NotificationChannelSender;
import com.dxc.monitoring.service.external.ExternalSecretEncryptionService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationDeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 8;

    private final NotificationDeliveryQueueService queueService;
    private final NotificationChannelSecretService secretService;
    private final List<NotificationChannelSender> senders;
    private final ObjectMapper objectMapper;

    public NotificationDeliveryWorker(NotificationDeliveryQueueService queueService,
            NotificationChannelSecretService secretService, List<NotificationChannelSender> senders,
            ObjectMapper objectMapper) {
        this.queueService = queueService;
        this.secretService = secretService;
        this.senders = senders;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${alerts.notifications.poll-interval-ms:2000}")
    public void poll() {
        List<NotificationDeliveryQueueService.ClaimedDelivery> deliveries =
                queueService.claimDueDeliveries(BATCH_SIZE, MAX_ATTEMPTS);
        if (!deliveries.isEmpty()) {
            log.info("Notification delivery worker claimed {} pending deliveries", deliveries.size());
        }
        for (NotificationDeliveryQueueService.ClaimedDelivery claim : deliveries) {
            try {
                NotificationDelivery delivery = queueService.loadForDelivery(claim.deliveryId());
                NotificationChannel channel = delivery.getChannel();
                if (!channel.isEnabled()) {
                    log.warn("Skipping notification deliveryId={} because channelId={} is disabled", claim.deliveryId(), channel.getId());
                    queueService.markSkipped(claim.deliveryId(), "Notification channel is disabled.");
                    continue;
                }
                if (delivery.getRecipient() != null && !delivery.getRecipient().isEnabled()) {
                    log.warn("Skipping notification deliveryId={} because recipientId={} is disabled",
                            claim.deliveryId(), delivery.getRecipient().getId());
                    queueService.markSkipped(claim.deliveryId(), "Notification recipient is disabled.");
                    continue;
                }
                NotificationChannelSender sender = senders.stream()
                        .filter(candidate -> candidate.channelType() == channel.getChannelType())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No sender is configured for this notification channel."));
                Map<String, Object> payload = objectMapper.readValue(delivery.getMessagePayload(),
                        new TypeReference<Map<String, Object>>() {});
                String secretKey = channel.getCredentialReference();
                if (secretKey == null || secretKey.isBlank()) {
                    secretKey = switch (channel.getChannelType()) {
                        case EMAIL -> "smtp-password";
                        case TEAMS -> "webhook-url";
                        case WHATSAPP -> "access-token";
                    };
                }
                String credential = secretService.getSecret(channel.getId(), secretKey);
                log.info("Sending notification deliveryId={} alertId={} channelId={} channelType={} recipientId={} attempt={}",
                        claim.deliveryId(), delivery.getAlert() == null ? null : delivery.getAlert().getId(),
                        channel.getId(), channel.getChannelType(),
                        delivery.getRecipient() == null ? null : delivery.getRecipient().getId(), claim.attemptCount());
                String providerId = sender.send(channel, delivery.getRecipient(), payload, credential);
                queueService.markSent(claim.deliveryId(), providerId);
                log.info("Notification deliveryId={} marked SENT via channelId={} channelType={}",
                        claim.deliveryId(), channel.getId(), channel.getChannelType());
            } catch (Exception exception) {
                log.error("Notification deliveryId={} failed on attempt {} exceptionType={} message={}",
                        claim.deliveryId(), claim.attemptCount(), exception.getClass().getSimpleName(), safeMessage(exception), exception);
                queueService.markFailed(claim.deliveryId(), claim.attemptCount(), MAX_ATTEMPTS,
                        safeMessage(exception));
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "Notification provider delivery failed.";
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 1000 ? sanitized.substring(0, 1000) : sanitized;
    }
}

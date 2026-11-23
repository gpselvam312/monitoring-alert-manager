package com.dxc.monitoring.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dxc.monitoring.config.BackgroundWorkReadiness;
import com.dxc.monitoring.entity.NotificationChannel;
import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.service.notification.NotificationChannelSender;

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
    private final BackgroundWorkReadiness readiness;

    public NotificationDeliveryWorker(NotificationDeliveryQueueService queueService,
            NotificationChannelSecretService secretService, List<NotificationChannelSender> senders,
            ObjectMapper objectMapper, BackgroundWorkReadiness readiness) {
        this.queueService = queueService;
        this.secretService = secretService;
        this.senders = senders;
        this.objectMapper = objectMapper;
        this.readiness = readiness;
    }

    @Scheduled(fixedDelayString = "${alerts.notifications.poll-interval-ms:2000}")
    public void poll() {
        if (!readiness.isReady()) return;
        List<NotificationDeliveryQueueService.ClaimedDelivery> deliveries =
                queueService.claimDueDeliveries(BATCH_SIZE, MAX_ATTEMPTS);
        if (!deliveries.isEmpty()) log.info("Notification delivery worker claimed {} pending deliveries", deliveries.size());
        for (NotificationDeliveryQueueService.ClaimedDelivery claim : deliveries) {
            try {
                NotificationDelivery delivery = queueService.loadForDelivery(claim.deliveryId());
                NotificationChannel channel = delivery.getChannel();
                if (!channel.isEnabled()) {
                    log.warn("Skipping notification deliveryId={} because channelId={} is disabled", claim.deliveryId(), channel.getId());
                    if (!queueService.markSkipped(claim.deliveryId(), claim.attemptCount(), "Notification channel is disabled."))
                        log.warn("Ignoring stale notification claim deliveryId={} attempt={}", claim.deliveryId(), claim.attemptCount());
                    continue;
                }
                if (delivery.getRecipient() != null && !delivery.getRecipient().isEnabled()) {
                    log.warn("Skipping notification deliveryId={} because recipientId={} is disabled",
                            claim.deliveryId(), delivery.getRecipient().getId());
                    if (!queueService.markSkipped(claim.deliveryId(), claim.attemptCount(), "Notification recipient is disabled."))
                        log.warn("Ignoring stale notification claim deliveryId={} attempt={}", claim.deliveryId(), claim.attemptCount());
                    continue;
                }
                NotificationChannelSender sender = senders.stream()
                        .filter(candidate -> candidate.channelType() == channel.getChannelType())
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "No sender is configured for this notification channel."));
                Map<String, Object> payload = objectMapper.readValue(delivery.getMessagePayload(),
                        new TypeReference<Map<String, Object>>() {});
                if (delivery.getCcRecipientsSnapshot() != null && !delivery.getCcRecipientsSnapshot().isBlank())
                    payload.put("ccRecipients", delivery.getCcRecipientsSnapshot());
                String secretKey = channel.getCredentialReference();
                if (secretKey == null || secretKey.isBlank()) secretKey = switch (channel.getChannelType()) {
                    case EMAIL -> "smtp-password";
                    case TEAMS -> "webhook-url";
                    case WHATSAPP -> "access-token";
                };
                String credential = secretService.getSecret(channel.getId(), secretKey);
                log.info("Sending notification deliveryId={} alertId={} channelId={} channelType={} recipientId={} attempt={}",
                        claim.deliveryId(), delivery.getAlert() == null ? null : delivery.getAlert().getId(),
                        channel.getId(), channel.getChannelType(),
                        delivery.getRecipient() == null ? null : delivery.getRecipient().getId(), claim.attemptCount());
                String providerId = sender.send(channel, delivery.getRecipient(), payload, credential);
                if (!queueService.markSent(claim.deliveryId(), claim.attemptCount(), providerId)) {
                    log.warn("Ignoring stale notification success deliveryId={} attempt={}", claim.deliveryId(), claim.attemptCount());
                    continue;
                }
                log.info("Notification deliveryId={} marked SENT via channelId={} channelType={}",
                        claim.deliveryId(), channel.getId(), channel.getChannelType());
            } catch (Exception exception) {
                String failure = safeMessage(exception);
                log.error("Notification deliveryId={} failed on attempt {} exceptionType={} message={}",
                        claim.deliveryId(), claim.attemptCount(), exception.getClass().getSimpleName(), failure);
                try {
                    if (!queueService.markFailed(claim.deliveryId(), claim.attemptCount(), MAX_ATTEMPTS, failure))
                        log.warn("Ignoring stale notification failure deliveryId={} attempt={}", claim.deliveryId(), claim.attemptCount());
                } catch (RuntimeException stateException) {
                    log.error("Unable to persist failure state for notification deliveryId={} exceptionType={}",
                            claim.deliveryId(), stateException.getClass().getSimpleName());
                }
            }
        }
    }

    private String safeMessage(Exception exception) {
        return "Notification provider delivery failed (" + exception.getClass().getSimpleName() + ").";
    }
}

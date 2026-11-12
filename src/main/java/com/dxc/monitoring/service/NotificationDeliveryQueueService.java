package com.dxc.monitoring.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;

@Service
public class NotificationDeliveryQueueService {
    private final NotificationDeliveryRepository repository;

    public NotificationDeliveryQueueService(NotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<ClaimedDelivery> claimDueDeliveries(int batchSize, int maxAttempts) {
        List<NotificationDelivery> deliveries = repository.lockDueDeliveries(batchSize, maxAttempts);
        OffsetDateTime now = OffsetDateTime.now();
        return deliveries.stream().map(delivery -> {
            delivery.setStatus(NotificationDelivery.DeliveryStatus.SENDING);
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setLockedAt(now);
            delivery.setLastAttemptAt(now);
            return new ClaimedDelivery(delivery.getId(), delivery.getAttemptCount());
        }).toList();
    }

    @Transactional(readOnly = true)
    public NotificationDelivery loadForDelivery(Long id) {
        return repository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification delivery was not found."));
    }

    @Transactional
    public void markSent(Long id, String providerMessageId) {
        NotificationDelivery delivery = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification delivery was not found."));
        delivery.setStatus(NotificationDelivery.DeliveryStatus.SENT);
        delivery.setProviderMessageId(providerMessageId);
        delivery.setSentAt(OffsetDateTime.now());
        delivery.setLockedAt(null);
        delivery.setLastError(null);
    }

    @Transactional
    public void markSkipped(Long id, String reason) {
        NotificationDelivery delivery = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification delivery was not found."));
        delivery.setStatus(NotificationDelivery.DeliveryStatus.SKIPPED);
        delivery.setLockedAt(null);
        delivery.setLastError(sanitize(reason));
    }

    @Transactional
    public void markFailed(Long id, int attemptCount, int maxAttempts, String error) {
        NotificationDelivery delivery = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification delivery was not found."));
        delivery.setStatus(NotificationDelivery.DeliveryStatus.FAILED);
        delivery.setLockedAt(null);
        delivery.setLastError(sanitize(error));
        if (attemptCount >= maxAttempts) {
            delivery.setNextAttemptAt(OffsetDateTime.now());
        } else {
            long delaySeconds = Math.min(900L, 2L << Math.min(attemptCount, 8));
            delivery.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
        }
    }

    private String sanitize(String error) {
        if (error == null || error.isBlank()) return "Notification delivery failed.";
        String value = error.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public record ClaimedDelivery(Long deliveryId, int attemptCount) {}
}

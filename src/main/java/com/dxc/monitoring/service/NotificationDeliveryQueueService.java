package com.dxc.monitoring.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;

@Service
public class NotificationDeliveryQueueService {
    private final NotificationDeliveryRepository repository;

    public NotificationDeliveryQueueService(NotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public NotificationDelivery loadForDelivery(Long id) {
        return repository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification delivery was not found."));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSent(Long id, int attemptCount, String providerMessageId) {
        return repository.completeClaimedAsSent(id, attemptCount,
                NotificationDelivery.DeliveryStatus.SENDING, NotificationDelivery.DeliveryStatus.SENT,
                providerMessageId, OffsetDateTime.now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markSkipped(Long id, int attemptCount, String reason) {
        return repository.completeClaimedAsSkipped(id, attemptCount,
                NotificationDelivery.DeliveryStatus.SENDING, NotificationDelivery.DeliveryStatus.SKIPPED,
                sanitize(reason)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(Long id, int attemptCount, int maxAttempts, String error) {
        long delaySeconds = attemptCount >= maxAttempts
                ? 0L : Math.min(900L, 2L << Math.min(attemptCount, 8));
        OffsetDateTime nextAttemptAt = OffsetDateTime.now().plusSeconds(delaySeconds);
        return repository.completeClaimedAsFailed(id, attemptCount,
                NotificationDelivery.DeliveryStatus.SENDING, NotificationDelivery.DeliveryStatus.FAILED,
                sanitize(error), nextAttemptAt) == 1;
    }

    private String sanitize(String error) {
        if (error == null || error.isBlank()) return "Notification delivery failed.";
        String value = error.replaceAll("[\\r\\n\\t]+", " ").trim();
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    public record ClaimedDelivery(Long deliveryId, int attemptCount) {}
}

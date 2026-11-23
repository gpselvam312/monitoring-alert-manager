package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.NotificationDelivery;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;

class NotificationDeliveryQueueServiceTest {

    @Test
    void staleSuccessCannotOverwriteAReclaimedDelivery() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.completeClaimedAsSent(eq(42L), eq(3),
                eq(NotificationDelivery.DeliveryStatus.SENDING), eq(NotificationDelivery.DeliveryStatus.SENT),
                eq("provider-1"), any(OffsetDateTime.class))).thenReturn(0);

        NotificationDeliveryQueueService service = new NotificationDeliveryQueueService(repository);

        assertFalse(service.markSent(42L, 3, "provider-1"));
    }

    @Test
    void currentClaimCanBeMarkedSent() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.completeClaimedAsSent(eq(42L), eq(3),
                eq(NotificationDelivery.DeliveryStatus.SENDING), eq(NotificationDelivery.DeliveryStatus.SENT),
                eq("provider-1"), any(OffsetDateTime.class))).thenReturn(1);

        NotificationDeliveryQueueService service = new NotificationDeliveryQueueService(repository);

        assertTrue(service.markSent(42L, 3, "provider-1"));
        verify(repository).completeClaimedAsSent(eq(42L), eq(3),
                eq(NotificationDelivery.DeliveryStatus.SENDING), eq(NotificationDelivery.DeliveryStatus.SENT),
                eq("provider-1"), any(OffsetDateTime.class));
    }

    @Test
    void failedClaimUsesBoundedRetryDelay() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.completeClaimedAsFailed(eq(42L), eq(2),
                eq(NotificationDelivery.DeliveryStatus.SENDING), eq(NotificationDelivery.DeliveryStatus.FAILED),
                eq("provider unavailable"), any(OffsetDateTime.class), any(OffsetDateTime.class))).thenReturn(1);

        NotificationDeliveryQueueService service = new NotificationDeliveryQueueService(repository);

        assertTrue(service.markFailed(42L, 2, 8, "provider unavailable"));
        verify(repository).completeClaimedAsFailed(eq(42L), eq(2),
                eq(NotificationDelivery.DeliveryStatus.SENDING), eq(NotificationDelivery.DeliveryStatus.FAILED),
                eq("provider unavailable"), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }
}

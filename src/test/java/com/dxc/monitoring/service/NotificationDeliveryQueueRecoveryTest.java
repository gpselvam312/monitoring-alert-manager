package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.repository.NotificationDeliveryRepository;

class NotificationDeliveryQueueRecoveryTest {
    @Test
    void recoversStaleClaimsThatAlreadyExhaustedTheirAttempts() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        when(repository.failExhaustedStaleClaims(8)).thenReturn(2);

        NotificationDeliveryQueueService service = new NotificationDeliveryQueueService(repository);

        assertEquals(2, service.recoverExhaustedClaims(8));
    }
}

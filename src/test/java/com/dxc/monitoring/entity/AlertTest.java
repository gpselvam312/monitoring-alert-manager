package com.dxc.monitoring.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class AlertTest {
    @Test
    void createsStableReadableKeyFromCreationYearAndDatabaseId() {
        Alert alert = new Alert();
        alert.setId(1248L);
        alert.setCreatedAt(OffsetDateTime.parse("2026-11-13T10:15:30Z"));

        assertEquals("ALT-2026-00001248", alert.getAlertKey());
    }

    @Test
    void doesNotExposeKeyBeforeAlertIsPersisted() {
        Alert alert = new Alert();
        alert.setCreatedAt(OffsetDateTime.parse("2026-11-13T10:15:30Z"));

        assertNull(alert.getAlertKey());
    }
}

package com.dxc.monitoring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import com.dxc.monitoring.config.LookupProperties;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleNotificationRouteRepository;
import com.dxc.monitoring.repository.NotificationDeliveryRepository;
import com.dxc.monitoring.service.ReferenceDataCacheService;
import com.dxc.monitoring.service.SystemSettingService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;
import com.dxc.monitoring.web.PaginationProperties;

class AlertHistoryControllerFilterValidationTest {
    private DashboardAccessService accessService;
    private AlertHistoryController controller;

    @BeforeEach
    void setUp() {
        accessService = mock(DashboardAccessService.class);
        when(accessService.getApplicationsWithPermission("ALERT_VIEW")).thenReturn(List.of());
        controller = new AlertHistoryController(
                mock(AlertRepository.class),
                accessService,
                mock(ReferenceDataCacheService.class),
                mock(LookupProperties.class),
                mock(SystemSettingService.class),
                mock(AlertRuleNotificationRouteRepository.class),
                mock(NotificationDeliveryRepository.class),
                new PageRequestSupport(new PaginationProperties()));
    }

    @Test
    void rejectsUnknownStatusInsteadOfSilentlyRemovingTheFilter() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.history(null, 5, "BROKEN", null, null, null,
                        "triggeredAt", "desc", new ExtendedModelMap()));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("Choose a valid alert status.", exception.getReason());
    }

    @Test
    void rejectsUnknownSeverityInsteadOfSilentlyRemovingTheFilter() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.history(null, 5, null, "BROKEN", null, null,
                        "triggeredAt", "desc", new ExtendedModelMap()));

        assertEquals(400, exception.getStatusCode().value());
        assertEquals("Choose a valid alert severity.", exception.getReason());
    }

    @Test
    void acceptsCaseInsensitiveStatusAndSeverityValues() {
        controller.history(null, 5, "active", "critical", null, null,
                "triggeredAt", "desc", new ExtendedModelMap());
    }
}

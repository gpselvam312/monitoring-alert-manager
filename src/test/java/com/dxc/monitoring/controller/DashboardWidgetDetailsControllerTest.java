package com.dxc.monitoring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.ExtendedModelMap;

import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.service.MonitoringExecutionService;
import com.dxc.monitoring.service.dashboard.DashboardService;
import com.dxc.monitoring.web.PageRequestSupport;
import com.dxc.monitoring.web.PaginationProperties;

class DashboardWidgetDetailsControllerTest {

    @Test
    void normalizesPaginationAndClampsOutOfRangePage() {
        DashboardService dashboardService = mock(DashboardService.class);
        MonitoringExecutionService executionService = mock(MonitoringExecutionService.class);
        PageRequestSupport pageSupport = new PageRequestSupport(new PaginationProperties());
        DashboardWidget widget = new DashboardWidget();
        widget.setDataSourceType("MONITORING_JOB");
        widget.setDataSourceId(42L);
        when(dashboardService.getWidget(7L)).thenReturn(widget);
        when(executionService.findByJobId(eq(42L), eq("failure"), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable requested = invocation.getArgument(2);
                    if (requested.getPageNumber() == 99) {
                        return new PageImpl<>(List.of(), requested, 11);
                    }
                    return new PageImpl<>(List.of(), requested, 11);
                });

        ExtendedModelMap model = new ExtendedModelMap();
        DashboardWidgetDetailsController controller =
                new DashboardWidgetDetailsController(dashboardService, executionService, pageSupport);

        assertEquals("dashboard-widget-details",
                controller.details(7L, 99, 999, " failure ", "unknown", "sideways", model));

        org.springframework.data.domain.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(executionService, org.mockito.Mockito.times(2))
                .findByJobId(eq(42L), eq("failure"), captor.capture());
        List<Pageable> requests = captor.getAllValues();
        assertEquals(5, requests.get(0).getPageSize());
        assertEquals(99, requests.get(0).getPageNumber());
        assertEquals(2, requests.get(1).getPageNumber());
        assertEquals(5, model.get("pageSize"));
        assertEquals("startedAt", model.get("sort"));
        assertEquals("desc", model.get("direction"));
        assertEquals("failure", model.get("search"));
    }

    @Test
    void nonMonitoringWidgetDoesNotQueryExecutionHistory() {
        DashboardService dashboardService = mock(DashboardService.class);
        MonitoringExecutionService executionService = mock(MonitoringExecutionService.class);
        DashboardWidget widget = new DashboardWidget();
        widget.setDataSourceType("EXTERNAL_APPLICATION");
        when(dashboardService.getWidget(7L)).thenReturn(widget);

        ExtendedModelMap model = new ExtendedModelMap();
        DashboardWidgetDetailsController controller = new DashboardWidgetDetailsController(
                dashboardService, executionService, new PageRequestSupport(new PaginationProperties()));

        assertEquals("dashboard-widget-details",
                controller.details(7L, -5, 10, null, "status", "asc", model));

        verify(executionService, never()).findByJobId(eq(42L), any(), any(Pageable.class));
        assertEquals(0, ((Page<?>) model.get("history")).getNumber());
        assertEquals(10, model.get("pageSize"));
        assertEquals("", model.get("search"));
        assertEquals("status", model.get("sort"));
        assertEquals("asc", model.get("direction"));
    }
}

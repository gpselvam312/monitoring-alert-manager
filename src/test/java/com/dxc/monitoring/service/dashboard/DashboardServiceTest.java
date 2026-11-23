package com.dxc.monitoring.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.DashboardTab;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.repository.DashboardTabRepository;
import com.dxc.monitoring.repository.DashboardWidgetRepository;
import com.dxc.monitoring.repository.ExternalApplicationRepository;
import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;
import com.dxc.monitoring.service.executor.MonitoringResultNormalizer;
import tools.jackson.databind.ObjectMapper;

class DashboardServiceTest {

    @Test
    void loadsWidgetsOnlyForTabsInTheAuthorizedDashboardScope() {
        DashboardTabRepository tabs = mock(DashboardTabRepository.class);
        DashboardWidgetRepository widgets = mock(DashboardWidgetRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        DashboardTab tab = mock(DashboardTab.class);
        Environment environment = mock(Environment.class);
        when(tab.getId()).thenReturn(77L);
        when(tab.getName()).thenReturn("Production");
        when(tab.getEnvironment()).thenReturn(environment);
        when(environment.isEnabled()).thenReturn(true);
        when(environment.getId()).thenReturn(22L);
        when(environment.getName()).thenReturn("PROD");
        when(tabs.findAllByApplication_IdAndEnvironment_IdAndEnabledTrueOrderBySortOrderAsc(11L, 22L))
                .thenReturn(List.of(tab));
        when(widgets.findAllByTab_IdInAndEnabledTrueOrderByTab_SortOrderAscSortOrderAsc(List.of(77L)))
                .thenReturn(List.of());

        DashboardService service = new DashboardService(tabs, widgets,
                mock(MonitoringExecutionRepository.class), mock(MonitoringResultRepository.class),
                mock(MonitoringJobRepository.class), mock(ExternalApplicationRepository.class),
                mock(ObjectMapper.class), access, mock(MonitoringResultNormalizer.class));

        List<DashboardTabResponse> response = service.getDashboard(11L, 22L);

        assertEquals(1, response.size());
        assertEquals("Production", response.get(0).getName());
        verify(access).assertCanAccessEnvironment(11L, 22L);
        verify(widgets).findAllByTab_IdInAndEnabledTrueOrderByTab_SortOrderAscSortOrderAsc(List.of(77L));
    }

    @Test
    void doesNotQueryWidgetsWhenAuthorizedDashboardHasNoTabs() {
        DashboardTabRepository tabs = mock(DashboardTabRepository.class);
        DashboardWidgetRepository widgets = mock(DashboardWidgetRepository.class);
        DashboardAccessService access = mock(DashboardAccessService.class);
        when(tabs.findAllByApplication_IdAndEnvironment_IdAndEnabledTrueOrderBySortOrderAsc(11L, 22L))
                .thenReturn(List.of());

        DashboardService service = new DashboardService(tabs, widgets,
                mock(MonitoringExecutionRepository.class), mock(MonitoringResultRepository.class),
                mock(MonitoringJobRepository.class), mock(ExternalApplicationRepository.class),
                mock(ObjectMapper.class), access, mock(MonitoringResultNormalizer.class));

        assertEquals(List.of(), service.getDashboard(11L, 22L));
        org.mockito.Mockito.verifyNoInteractions(widgets);
    }
}

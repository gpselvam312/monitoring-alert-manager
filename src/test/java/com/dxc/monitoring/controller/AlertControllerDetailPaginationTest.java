package com.dxc.monitoring.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.ui.ExtendedModelMap;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertComment;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.Application;
import com.dxc.monitoring.repository.AlertCommentRepository;
import com.dxc.monitoring.repository.AlertHistoryRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleEvaluationRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.UserAlertStateRepository;
import com.dxc.monitoring.service.AlertRuleConfigurationService;
import com.dxc.monitoring.service.AlertRuleFieldDiscoveryService;
import com.dxc.monitoring.service.AlertRuleManagementService;
import com.dxc.monitoring.service.AlertRuleNotificationRouteService;
import com.dxc.monitoring.service.ReferenceDataCacheService;
import com.dxc.monitoring.service.SystemSettingService;
import com.dxc.monitoring.service.dashboard.DashboardAccessService;
import com.dxc.monitoring.web.PageRequestSupport;
import com.dxc.monitoring.web.PaginationProperties;

class AlertControllerDetailPaginationTest {
    private AlertRepository alertRepository;
    private AlertHistoryRepository historyRepository;
    private AlertCommentRepository commentRepository;
    private DashboardAccessService accessService;
    private SystemSettingService systemSettingService;
    private AlertController controller;

    @BeforeEach
    void setUp() {
        alertRepository = mock(AlertRepository.class);
        historyRepository = mock(AlertHistoryRepository.class);
        commentRepository = mock(AlertCommentRepository.class);
        accessService = mock(DashboardAccessService.class);
        systemSettingService = mock(SystemSettingService.class);

        PaginationProperties paginationProperties = new PaginationProperties();
        paginationProperties.setAllowedPageSizes(List.of(5, 10, 25, 50));
        controller = new AlertController(alertRepository, mock(ReferenceDataCacheService.class),
                historyRepository, commentRepository, mock(AlertRuleRepository.class),
                mock(AlertRuleEvaluationRepository.class), mock(MonitoringJobRepository.class),
                mock(AlertRuleManagementService.class), mock(AlertRuleConfigurationService.class),
                accessService, mock(UserAlertStateRepository.class),
                mock(AlertRuleFieldDiscoveryService.class), mock(AlertRuleNotificationRouteService.class),
                systemSettingService, new PageRequestSupport(paginationProperties));

        Application application = mock(Application.class);
        when(application.getId()).thenReturn(12L);
        when(accessService.getApplicationsWithPermission("ALERT_VIEW")).thenReturn(List.of(application));
        when(alertRepository.findByIdAndMonitoringJob_Application_IdIn(77L, List.of(12L)))
                .thenReturn(Optional.of(mock(Alert.class)));
        when(historyRepository.findByAlertIdOrderByOccurredAtDescIdDesc(any(), any()))
                .thenReturn(Page.empty());
        when(commentRepository.findByAlertIdOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(Page.empty());
        when(systemSettingService.getDateTimeFormat()).thenReturn("yyyy-MM-dd HH:mm");
    }

    @Test
    void honorsConfiguredPageSizesBeyondTheDefaultSet() {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.alertDetails(77L, 0, 50, 0, "active", null, model);

        ArgumentCaptor<Pageable> historyPage = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByAlertIdOrderByOccurredAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(77L), historyPage.capture());
        assertEquals(50, historyPage.getValue().getPageSize());
        assertEquals(50, model.getAttribute("pageSize"));
    }

    @Test
    void independentlyPagesLifecycleHistoryAndCommentsWithinAuthorizedAlertScope() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("alerts/detail", controller.alertDetails(77L, -4, 10, 2,
                "history", null, model));

        ArgumentCaptor<Pageable> historyPage = ArgumentCaptor.forClass(Pageable.class);
        verify(historyRepository).findByAlertIdOrderByOccurredAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(77L), historyPage.capture());
        assertEquals(0, historyPage.getValue().getPageNumber());
        assertEquals(10, historyPage.getValue().getPageSize());
        assertEquals("occurredAt", historyPage.getValue().getSort().getOrderFor("occurredAt").getProperty());

        ArgumentCaptor<Pageable> commentPage = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findByAlertIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(77L), commentPage.capture());
        assertEquals(2, commentPage.getValue().getPageNumber());
        assertEquals(10, commentPage.getValue().getPageSize());
        assertEquals("createdAt", commentPage.getValue().getSort().getOrderFor("createdAt").getProperty());
        assertEquals(10, model.getAttribute("pageSize"));
    }
}

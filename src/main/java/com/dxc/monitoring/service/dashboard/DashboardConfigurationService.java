package com.dxc.monitoring.service.dashboard;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dxc.monitoring.entity.DashboardTab;
import com.dxc.monitoring.entity.Environment;
import com.dxc.monitoring.entity.DashboardWidget;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.DashboardTabRepository;
import com.dxc.monitoring.repository.EnvironmentRepository;
import com.dxc.monitoring.repository.DashboardWidgetRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;

@Service
public class DashboardConfigurationService
{
    private final DashboardTabRepository dashboardTabRepository;
    private final EnvironmentRepository environmentRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final MonitoringJobRepository monitoringJobRepository;
    private final MonitoringResultRepository monitoringResultRepository;

    public DashboardConfigurationService(DashboardTabRepository dashboardTabRepository,
            DashboardWidgetRepository dashboardWidgetRepository,
            MonitoringJobRepository monitoringJobRepository, MonitoringResultRepository monitoringResultRepository,
            EnvironmentRepository environmentRepository)
    {
        this.dashboardTabRepository = dashboardTabRepository;
        this.environmentRepository = environmentRepository;
        this.dashboardWidgetRepository = dashboardWidgetRepository;
        this.monitoringJobRepository = monitoringJobRepository;
        this.monitoringResultRepository = monitoringResultRepository;
    }

    @Transactional(readOnly = true)
    public List<DashboardTab> findAllTabs()
    {
        return dashboardTabRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional(readOnly = true)
    public List<Environment> findEnabledEnvironments()
    {
        return environmentRepository.findByEnabledTrueOrderByName();
    }

    @Transactional(readOnly = true)
    public Environment findEnvironmentById(Long id)
    {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + id));
    }

    @Transactional(readOnly = true)
    public DashboardTab findTabById(Long id)
    {
        return dashboardTabRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard tab not found: " + id));
    }

    @Transactional
    public DashboardTab saveTab(DashboardTab tab)
    {
        if (tab.getName() == null || tab.getName().isBlank())
        {
            throw new IllegalArgumentException("Dashboard tab name is required.");
        }

        String name = tab.getName().trim();

        dashboardTabRepository.findByNameIgnoreCase(name).ifPresent(existingTab -> {
            if (tab.getId() == null || !existingTab.getId().equals(tab.getId()))
            {
                throw new IllegalArgumentException("Dashboard tab already exists: " + name);
            }
        });

        tab.setName(name);

        if (tab.getEnvironment() == null || tab.getEnvironment().getId() == null)
        {
            throw new IllegalArgumentException("Dashboard tab environment is required.");
        }

        dashboardTabRepository.findByEnvironment_Id(tab.getEnvironment().getId()).ifPresent(existingTab -> {
            if (tab.getId() == null || !existingTab.getId().equals(tab.getId()))
            {
                throw new IllegalArgumentException("A dashboard tab already exists for environment: "
                        + tab.getEnvironment().getName());
            }
        });

        if (tab.getSortOrder() == null || tab.getSortOrder() < 0)
        {
            tab.setSortOrder(0);
        }

        if (tab.getEnabled() == null)
        {
            tab.setEnabled(true);
        }

        return dashboardTabRepository.save(tab);
    }

    @Transactional
    public String deleteTab(Long id)
    {
        DashboardTab tab = findTabById(id);

        String tabName = tab.getName();

        dashboardTabRepository.delete(tab);

        return tabName;
    }

    @Transactional
    public void toggleTabEnabled(Long id)
    {
        DashboardTab tab = findTabById(id);

        tab.setEnabled(!Boolean.TRUE.equals(tab.getEnabled()));

        dashboardTabRepository.save(tab);
    }

    @Transactional(readOnly = true)
    public List<DashboardWidget> findWidgetsByTabId(Long tabId)
    {
        return dashboardWidgetRepository.findAllByTabIdOrderBySortOrderAsc(tabId);
    }

    @Transactional(readOnly = true)
    public DashboardWidget findWidgetById(Long id)
    {
        return dashboardWidgetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dashboard widget not found: " + id));
    }

    private void validateWidget(DashboardWidget widget)
    {
        if (widget.getName() == null || widget.getName().isBlank())
        {
            throw new IllegalArgumentException("Dashboard widget name is required.");
        }

        if (widget.getTab() == null || widget.getTab().getId() == null)
        {
            throw new IllegalArgumentException("Dashboard widget tab is required.");
        }

        if (widget.getWidgetType() == null || widget.getWidgetType().isBlank())
        {
            throw new IllegalArgumentException("Dashboard widget type is required.");
        }

        if (widget.getSize() == null || widget.getSize().isBlank())
        {
            widget.setSize("MEDIUM");
        }

        if (widget.getSortOrder() == null || widget.getSortOrder() < 0)
        {
            widget.setSortOrder(0);
        }

        if (widget.getEnabled() == null)
        {
            widget.setEnabled(true);
        }

        if (widget.getAutoRefresh() == null)
        {
            widget.setAutoRefresh(false);
        }

        if (!Boolean.TRUE.equals(widget.getAutoRefresh()))
        {
            widget.setRefreshInterval(null);
            widget.setRefreshIntervalUnit(null);
        } else
        {
            if (widget.getRefreshInterval() == null || widget.getRefreshInterval() <= 0)
            {
                throw new IllegalArgumentException("Refresh interval must be greater than zero.");
            }

            if (widget.getRefreshIntervalUnit() == null || widget.getRefreshIntervalUnit().isBlank())
            {
                throw new IllegalArgumentException("Refresh interval unit is required.");
            }
        }

        if (widget.getDetailsEnabled() == null)
        {
            widget.setDetailsEnabled(false);
        }

        String dataSourceType = widget.getDataSourceType();
        Long dataSourceId = widget.getDataSourceId();

        if (dataSourceType == null || dataSourceType.isBlank())
        {
            if (dataSourceId != null)
            {
                throw new IllegalArgumentException("Select a data source type when a data source ID is provided.");
            }
        }
        else
        {
            if (dataSourceId == null)
            {
                throw new IllegalArgumentException("A data source ID is required for " + dataSourceType + " widgets.");
            }

            switch (dataSourceType)
            {
                case "MONITORING_JOB" ->
                {
                    if (!monitoringJobRepository.existsById(dataSourceId))
                    {
                        throw new IllegalArgumentException("Monitoring job not found: " + dataSourceId);
                    }
                }
                default -> throw new IllegalArgumentException("Unsupported dashboard data source type: " + dataSourceType);
            }
        }

        widget.setName(widget.getName().trim());

    }

    @Transactional
    public void toggleWidgetEnabled(Long id)
    {
        DashboardWidget widget = findWidgetById(id);

        widget.setEnabled(!Boolean.TRUE.equals(widget.getEnabled()));

        dashboardWidgetRepository.save(widget);
    }

    @Transactional
    public String deleteWidget(Long id)
    {
        DashboardWidget widget = findWidgetById(id);

        String widgetName = widget.getName();

        dashboardWidgetRepository.delete(widget);

        return widgetName;
    }

    @Transactional(readOnly = true)
    public List<MonitoringJob> findDashboardMonitoringJobs()
    {
        return monitoringJobRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<DashboardTab> findTabs(String search, Pageable pageable)
    {
        if (search == null)
        {
            search = "";
        }

        return dashboardTabRepository.findAllForList(search.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<DashboardWidget> findWidgetsByTabId(Long tabId, String search, Pageable pageable)
    {
        if (tabId == null)
        {
            throw new IllegalArgumentException("Dashboard tab is required.");
        }

        if (search == null)
        {
            search = "";
        }

        return dashboardWidgetRepository.findAllForList(tabId, search.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public DashboardWidgetForm getWidgetForm(Long id)
    {
        DashboardWidget widget = findWidgetById(id);

        DashboardWidgetForm form = new DashboardWidgetForm();

        form.setId(widget.getId());
        form.setTabId(widget.getTab().getId());
        form.setName(widget.getName());
        form.setDescription(widget.getDescription());
        form.setWidgetType(widget.getWidgetType());
        form.setChartType(widget.getChartType());
        form.setIcon(widget.getIcon());
        form.setSize(widget.getSize());
        form.setSortOrder(widget.getSortOrder());
        form.setEnabled(widget.getEnabled());
        form.setDataSourceType(widget.getDataSourceType());
        form.setDataSourceId(widget.getDataSourceId());

        // Convert legacy widgets that referenced one stored result into a live job source.
        if ("MONITORING_RESULT".equals(form.getDataSourceType()) && form.getDataSourceId() != null)
        {
            MonitoringResult legacyResult =
                    monitoringResultRepository.findById(form.getDataSourceId()).orElse(null);
            if (legacyResult != null && legacyResult.getExecution() != null
                    && legacyResult.getExecution().getMonitoringJob() != null)
            {
                form.setDataSourceType("MONITORING_JOB");
                form.setDataSourceId(legacyResult.getExecution().getMonitoringJob().getId());
            }
            else
            {
                form.setDataSourceType(null);
                form.setDataSourceId(null);
            }
        }

        form.setAutoRefresh(widget.getAutoRefresh());
        form.setRefreshInterval(widget.getRefreshInterval());
        form.setRefreshIntervalUnit(widget.getRefreshIntervalUnit());
        form.setDetailsEnabled(widget.getDetailsEnabled());

        return form;
    }

    @Transactional
    public DashboardWidget saveWidget(DashboardWidgetForm form)
    {
        DashboardWidget widget;

        if (form.getId() == null)
        {
            widget = new DashboardWidget();
        } else
        {
            widget = findWidgetById(form.getId());
        }

        DashboardTab tab = findTabById(form.getTabId());

        widget.setTab(tab);
        widget.setName(form.getName().trim());
        widget.setDescription(form.getDescription() == null ? null : form.getDescription().trim());
        widget.setWidgetType(form.getWidgetType());
        widget.setChartType(
                form.getChartType() == null || form.getChartType().isBlank() ? null : form.getChartType().trim());

        widget.setIcon(form.getIcon() == null || form.getIcon().isBlank() ? null : form.getIcon().trim());
        widget.setSize(form.getSize());
        widget.setSortOrder(form.getSortOrder());
        widget.setEnabled(form.getEnabled());
        widget.setDataSourceType(form.getDataSourceType() == null || form.getDataSourceType().isBlank() ? null
                : form.getDataSourceType().trim());
        widget.setDataSourceId(form.getDataSourceId());
        widget.setAutoRefresh(form.getAutoRefresh());
        widget.setRefreshInterval(form.getRefreshInterval());
        widget.setRefreshIntervalUnit(form.getRefreshIntervalUnit());
        widget.setDetailsEnabled(form.getDetailsEnabled());

        validateWidget(widget);

        return dashboardWidgetRepository.save(widget);
    }
}
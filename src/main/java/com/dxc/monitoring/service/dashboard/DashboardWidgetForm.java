package com.dxc.monitoring.service.dashboard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidDashboardWidget
public class DashboardWidgetForm
{
    private Long id;

    @NotNull(message = "Dashboard tab is required.")
    private Long tabId;

    @NotBlank(message = "Widget name is required.")
    @Size(max = 150, message = "Widget name must be 150 characters or fewer.")
    private String name;

    @Size(max = 500, message = "Description must be 500 characters or fewer.")
    private String description;

    @NotBlank(message = "Widget type is required.")
    private String widgetType;

    private String chartType;

    @Size(max = 100, message = "Icon must be 100 characters or fewer.")
    private String icon;

    @NotBlank(message = "Widget size is required.")
    private String size = "MEDIUM";

    @NotNull(message = "Sort order is required.")
    @Min(value = 0, message = "Sort order must be zero or greater.")
    private Integer sortOrder;

    private Boolean enabled;

    private String dataSourceType;

    private Long dataSourceId;

    @Size(max = 10000, message = "Widget field configuration must be 10000 characters or fewer.")
    private String fieldConfigJson = "{}";

    private Boolean autoRefresh;

    @Min(value = 1, message = "Refresh interval must be greater than zero.")
    private Integer refreshInterval;

    private String refreshIntervalUnit;

    private Boolean detailsEnabled;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getTabId()
    {
        return tabId;
    }

    public void setTabId(Long tabId)
    {
        this.tabId = tabId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getWidgetType()
    {
        return widgetType;
    }

    public void setWidgetType(String widgetType)
    {
        this.widgetType = widgetType;
    }

    public String getChartType()
    {
        return chartType;
    }

    public void setChartType(String chartType)
    {
        this.chartType = chartType;
    }

    public String getIcon()
    {
        return icon;
    }

    public void setIcon(String icon)
    {
        this.icon = icon;
    }

    public String getSize()
    {
        return size;
    }

    public void setSize(String size)
    {
        this.size = size;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public Boolean getEnabled()
    {
        return enabled;
    }

    public void setEnabled(Boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getDataSourceType()
    {
        return dataSourceType;
    }

    public void setDataSourceType(String dataSourceType)
    {
        this.dataSourceType = dataSourceType;
    }

    public Long getDataSourceId()
    {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId)
    {
        this.dataSourceId = dataSourceId;
    }

    public String getFieldConfigJson()
    {
        return fieldConfigJson;
    }

    public void setFieldConfigJson(String fieldConfigJson)
    {
        this.fieldConfigJson = fieldConfigJson;
    }

    public Boolean getAutoRefresh()
    {
        return autoRefresh;
    }

    public void setAutoRefresh(Boolean autoRefresh)
    {
        this.autoRefresh = autoRefresh;
    }

    public Integer getRefreshInterval()
    {
        return refreshInterval;
    }

    public void setRefreshInterval(Integer refreshInterval)
    {
        this.refreshInterval = refreshInterval;
    }

    public String getRefreshIntervalUnit()
    {
        return refreshIntervalUnit;
    }

    public void setRefreshIntervalUnit(String refreshIntervalUnit)
    {
        this.refreshIntervalUnit = refreshIntervalUnit;
    }

    public Boolean getDetailsEnabled()
    {
        return detailsEnabled;
    }

    public void setDetailsEnabled(Boolean detailsEnabled)
    {
        this.detailsEnabled = detailsEnabled;
    }
}
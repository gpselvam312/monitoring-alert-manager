package com.dxc.monitoring.service.dashboard;

public class DashboardWidgetDefinition
{

    private Long id;

    private String name;

    private String description;

    private Long tabId;

    private String widgetType;

    private String chartType;

    private String icon;

    private String size;

    private Integer sortOrder;

    private Boolean enabled;

    private Boolean autoRefresh;

    private Integer refreshInterval;

    private String refreshIntervalUnit;

    private Boolean detailsEnabled;

    private Boolean runNowEnabled;

    private Boolean jobRunning;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
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

    public Long getTabId()
    {
        return tabId;
    }

    public void setTabId(Long tabId)
    {
        this.tabId = tabId;
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

    public Boolean getRunNowEnabled()
    {
        return runNowEnabled;
    }

    public void setRunNowEnabled(Boolean runNowEnabled)
    {
        this.runNowEnabled = runNowEnabled;
    }

    public Boolean getJobRunning()
    {
        return jobRunning;
    }

    public void setJobRunning(Boolean jobRunning)
    {
        this.jobRunning = jobRunning;
    }
}
package com.dxc.monitoring.service.dashboard;

import java.util.ArrayList;
import java.util.List;

public class DashboardTabResponse
{
    private Long id;

    private String name;

    private Long applicationId;

    private String applicationName;

    private Long environmentId;

    private String environmentName;

    private Integer sortOrder;

    private List<DashboardWidgetResponse> widgets = new ArrayList<>();

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

    public Long getApplicationId()
    {
        return applicationId;
    }

    public void setApplicationId(Long applicationId)
    {
        this.applicationId = applicationId;
    }

    public String getApplicationName()
    {
        return applicationName;
    }

    public void setApplicationName(String applicationName)
    {
        this.applicationName = applicationName;
    }

    public Long getEnvironmentId()
    {
        return environmentId;
    }

    public void setEnvironmentId(Long environmentId)
    {
        this.environmentId = environmentId;
    }

    public String getEnvironmentName()
    {
        return environmentName;
    }

    public void setEnvironmentName(String environmentName)
    {
        this.environmentName = environmentName;
    }

    public Integer getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder)
    {
        this.sortOrder = sortOrder;
    }

    public List<DashboardWidgetResponse> getWidgets()
    {
        return widgets;
    }

    public void setWidgets(List<DashboardWidgetResponse> widgets)
    {
        this.widgets = widgets;
    }
}
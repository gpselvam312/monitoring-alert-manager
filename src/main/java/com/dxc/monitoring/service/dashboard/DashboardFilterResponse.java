package com.dxc.monitoring.service.dashboard;

import java.util.ArrayList;
import java.util.List;

public class DashboardFilterResponse
{
    private List<DashboardOption> applications = new ArrayList<>();
    private List<DashboardOption> environments = new ArrayList<>();
    private Long selectedApplicationId;
    private Long selectedEnvironmentId;

    public List<DashboardOption> getApplications()
    {
        return applications;
    }

    public void setApplications(List<DashboardOption> applications)
    {
        this.applications = applications;
    }

    public List<DashboardOption> getEnvironments()
    {
        return environments;
    }

    public void setEnvironments(List<DashboardOption> environments)
    {
        this.environments = environments;
    }

    public Long getSelectedApplicationId()
    {
        return selectedApplicationId;
    }

    public void setSelectedApplicationId(Long selectedApplicationId)
    {
        this.selectedApplicationId = selectedApplicationId;
    }

    public Long getSelectedEnvironmentId()
    {
        return selectedEnvironmentId;
    }

    public void setSelectedEnvironmentId(Long selectedEnvironmentId)
    {
        this.selectedEnvironmentId = selectedEnvironmentId;
    }
}

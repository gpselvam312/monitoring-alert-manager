package com.dxc.monitoring.service.dashboard;

public class DashboardWidgetResponse
{

    private DashboardWidgetDefinition widget;

    private WidgetResult result;

    public DashboardWidgetDefinition getWidget()
    {
        return widget;
    }

    public void setWidget(DashboardWidgetDefinition widget)
    {
        this.widget = widget;
    }

    public WidgetResult getResult()
    {
        return result;
    }

    public void setResult(WidgetResult result)
    {
        this.result = result;
    }
}
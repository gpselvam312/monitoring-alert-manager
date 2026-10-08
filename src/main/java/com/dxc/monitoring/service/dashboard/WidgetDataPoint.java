package com.dxc.monitoring.service.dashboard;

public class WidgetDataPoint
{

    private String label;

    private Object value;

    private String series;

    public WidgetDataPoint()
    {
    }

    public WidgetDataPoint(String label, Object value)
    {
        this.label = label;
        this.value = value;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public Object getValue()
    {
        return value;
    }

    public void setValue(Object value)
    {
        this.value = value;
    }

    public String getSeries()
    {
        return series;
    }

    public void setSeries(String series)
    {
        this.series = series;
    }
}
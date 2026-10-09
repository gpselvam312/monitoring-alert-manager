package com.dxc.monitoring.service.dashboard;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WidgetResult
{

    private WidgetStatus status;

    private String message;

    private String value;

    private Map<String, Object> metrics = new LinkedHashMap<>();

    private List<WidgetColumn> columns;

    private List<Map<String, Object>> rows;

    private List<WidgetDataPoint> data;

    private Map<String, Object> details = new LinkedHashMap<>();

    /** Complete normalized JSON result, retained for generic widget rendering. */
    private Object payload;

    private LocalDateTime lastUpdated;

    public WidgetStatus getStatus()
    {
        return status;
    }

    public void setStatus(WidgetStatus status)
    {
        this.status = status;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public Map<String, Object> getMetrics()
    {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics)
    {
        this.metrics = metrics;
    }

    public List<WidgetColumn> getColumns()
    {
        return columns;
    }

    public void setColumns(List<WidgetColumn> columns)
    {
        this.columns = columns;
    }

    public List<Map<String, Object>> getRows()
    {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows)
    {
        this.rows = rows;
    }

    public List<WidgetDataPoint> getData()
    {
        return data;
    }

    public void setData(List<WidgetDataPoint> data)
    {
        this.data = data;
    }

    public Map<String, Object> getDetails()
    {
        return details;
    }

    public void setDetails(Map<String, Object> details)
    {
        this.details = details;
    }

    public Object getPayload()
    {
        return payload;
    }

    public void setPayload(Object payload)
    {
        this.payload = payload;
    }

    public LocalDateTime getLastUpdated()
    {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated)
    {
        this.lastUpdated = lastUpdated;
    }
}
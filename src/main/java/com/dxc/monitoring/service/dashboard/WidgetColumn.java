package com.dxc.monitoring.service.dashboard;

public class WidgetColumn
{

    private String key;

    private String label;

    private String dataType;

    private String alignment;

    private Boolean sortable;

    public WidgetColumn()
    {
    }

    public WidgetColumn(String key, String label)
    {
        this.key = key;
        this.label = label;
        this.sortable = true;
    }

    public String getKey()
    {
        return key;
    }

    public void setKey(String key)
    {
        this.key = key;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getDataType()
    {
        return dataType;
    }

    public void setDataType(String dataType)
    {
        this.dataType = dataType;
    }

    public String getAlignment()
    {
        return alignment;
    }

    public void setAlignment(String alignment)
    {
        this.alignment = alignment;
    }

    public Boolean getSortable()
    {
        return sortable;
    }

    public void setSortable(Boolean sortable)
    {
        this.sortable = sortable;
    }
}
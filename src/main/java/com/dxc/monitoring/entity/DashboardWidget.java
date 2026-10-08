package com.dxc.monitoring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_widgets", schema = "ra_fcb")
public class DashboardWidget
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tab_id", nullable = false)
    private DashboardTab tab;

    @Column(name = "widget_type", nullable = false, length = 30)
    private String widgetType;

    @Column(name = "chart_type", length = 30)
    private String chartType;

    @Column(length = 100)
    private String icon;

    @Column(nullable = false, length = 20)
    private String size = "MEDIUM";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "data_source_type", length = 30)
    private String dataSourceType;

    @Column(name = "data_source_id")
    private Long dataSourceId;

    @Column(name = "auto_refresh", nullable = false)
    private Boolean autoRefresh = false;

    @Column(name = "refresh_interval")
    private Integer refreshInterval;

    @Column(name = "refresh_interval_unit", length = 20)
    private String refreshIntervalUnit;

    @Column(name = "store_result", nullable = false)
    private Boolean storeResult = false;

    @Column(name = "details_enabled", nullable = false)
    private Boolean detailsEnabled = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate()
    {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate()
    {
        updatedAt = LocalDateTime.now();
    }

    public Long getId()
    {
        return id;
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

    public DashboardTab getTab()
    {
        return tab;
    }

    public void setTab(DashboardTab tab)
    {
        this.tab = tab;
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

    public Boolean getStoreResult()
    {
        return storeResult;
    }

    public void setStoreResult(Boolean storeResult)
    {
        this.storeResult = storeResult;
    }

    public Boolean getDetailsEnabled()
    {
        return detailsEnabled;
    }

    public void setDetailsEnabled(Boolean detailsEnabled)
    {
        this.detailsEnabled = detailsEnabled;
    }

    public LocalDateTime getCreatedAt()
    {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt()
    {
        return updatedAt;
    }
}
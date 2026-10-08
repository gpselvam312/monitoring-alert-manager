package com.dxc.monitoring.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dashboard_widget_results", schema = "ra_fcb")
public class DashboardWidgetResult
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "widget_id", nullable = false)
    private DashboardWidget widget;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "result_data", columnDefinition = "jsonb")
    private String resultData;

    @Column(name = "result_time", nullable = false)
    private LocalDateTime resultTime;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate()
    {
        LocalDateTime now = LocalDateTime.now();

        if (resultTime == null)
        {
            resultTime = now;
        }

        createdAt = now;
    }

    public Long getId()
    {
        return id;
    }

    public DashboardWidget getWidget()
    {
        return widget;
    }

    public void setWidget(DashboardWidget widget)
    {
        this.widget = widget;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
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

    public String getResultData()
    {
        return resultData;
    }

    public void setResultData(String resultData)
    {
        this.resultData = resultData;
    }

    public LocalDateTime getResultTime()
    {
        return resultTime;
    }

    public void setResultTime(LocalDateTime resultTime)
    {
        this.resultTime = resultTime;
    }

    public LocalDateTime getCreatedAt()
    {
        return createdAt;
    }
}
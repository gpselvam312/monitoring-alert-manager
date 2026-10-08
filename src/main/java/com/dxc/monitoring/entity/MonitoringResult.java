package com.dxc.monitoring.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "monitoring_results",
       schema = "ra_fcb",
       indexes = { @Index(name = "idx_monitoring_results_execution", columnList = "execution_id") })
public class MonitoringResult
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private MonitoringExecution execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 20)
    private ResultType resultType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResultStatus status;

    @Column(length = 500)
    private String value;

    @Column(length = 50)
    private String unit;

    @Column(columnDefinition = "text")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_data", columnDefinition = "jsonb")
    private String resultData;

    @Column(name = "raw_output", columnDefinition = "text")
    private String rawOutput;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate()
    {
        if (createdAt == null)
        {
            createdAt = OffsetDateTime.now();
        }
    }

    public enum ResultType
    {
        STATUS, VALUE, METRICS, TABLE, TEXT
    }

    public enum ResultStatus
    {
        OK, WARNING, FAILED
    }

    public Long getId()
    {
        return id;
    }

    public MonitoringExecution getExecution()
    {
        return execution;
    }

    public void setExecution(MonitoringExecution execution)
    {
        this.execution = execution;
    }

    public ResultType getResultType()
    {
        return resultType;
    }

    public void setResultType(ResultType resultType)
    {
        this.resultType = resultType;
    }

    public ResultStatus getStatus()
    {
        return status;
    }

    public void setStatus(ResultStatus status)
    {
        this.status = status;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

    public String getUnit()
    {
        return unit;
    }

    public void setUnit(String unit)
    {
        this.unit = unit;
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

    public String getRawOutput()
    {
        return rawOutput;
    }

    public void setRawOutput(String rawOutput)
    {
        this.rawOutput = rawOutput;
    }

    public OffsetDateTime getCreatedAt()
    {
        return createdAt;
    }
}
package com.dxc.monitoring.service.executor;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringResult;

public class MonitoringExecutionResult
{

    private MonitoringExecution.ExecutionStatus executionStatus;

    private MonitoringResult.ResultType resultType;

    private MonitoringResult.ResultStatus resultStatus;

    private String value;

    private String unit;

    private String message;

    private String resultData;

    private String rawOutput;

    private String errorMessage;

    public MonitoringExecution.ExecutionStatus getExecutionStatus()
    {
        return executionStatus;
    }

    public void setExecutionStatus(MonitoringExecution.ExecutionStatus executionStatus)
    {
        this.executionStatus = executionStatus;
    }

    public MonitoringResult.ResultType getResultType()
    {
        return resultType;
    }

    public void setResultType(MonitoringResult.ResultType resultType)
    {
        this.resultType = resultType;
    }

    public MonitoringResult.ResultStatus getResultStatus()
    {
        return resultStatus;
    }

    public void setResultStatus(MonitoringResult.ResultStatus resultStatus)
    {
        this.resultStatus = resultStatus;
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

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }
}
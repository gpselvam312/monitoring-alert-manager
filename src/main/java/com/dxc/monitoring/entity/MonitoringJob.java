package com.dxc.monitoring.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_jobs", schema = "ra_fcb")
public class MonitoringJob
{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private MonitorType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id")
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "retry_delay_seconds", nullable = false)
    private Integer retryDelaySeconds;

    @Column(name = "expected_result", columnDefinition = "text")
    private String expectedResult;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Column(name = "recovery_enabled", nullable = false)
    private boolean recoveryEnabled = true;

    @Column(name = "dashboard_enabled", nullable = false)
    private boolean dashboardEnabled = false;

    @Column(name = "dashboard_title", length = 200)
    private String dashboardTitle;

    @Column(name = "dashboard_width", nullable = false)
    private Integer dashboardWidth = 6;

    @Column(name = "dashboard_sort_order", nullable = false)
    private Integer dashboardSortOrder = 100;

    // Script configuration

    @Column(name = "script_path", length = 1000)
    private String scriptPath;

    @Column(name = "working_directory", length = 1000)
    private String workingDirectory;

    @Column(name = "command_arguments", columnDefinition = "text")
    private String commandArguments;

    // API configuration

    @Column(name = "http_method", length = 20)
    private String httpMethod;

    @Column(name = "url", length = 2000)
    private String url;

    @Column(name = "request_headers", columnDefinition = "text")
    private String requestHeaders;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "expected_http_status")
    private Integer expectedHttpStatus;

    @Column(name = "expected_response", columnDefinition = "text")
    private String expectedResponse;

    // Health check configuration

    @Column(name = "health_check_type", length = 30)
    private String healthCheckType;

    @Column(name = "target_host", length = 255)
    private String targetHost;

    @Column(name = "target_port")
    private Integer targetPort;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    public enum MonitorType
    {
        SCRIPT, API, HEALTH_CHECK
    }

    public enum Severity
    {
        INFO, WARNING, CRITICAL
    }

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

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public MonitorType getType()
    {
        return type;
    }

    public void setType(MonitorType type)
    {
        this.type = type;
    }

    public Machine getMachine()
    {
        return machine;
    }

    public void setMachine(Machine machine)
    {
        this.machine = machine;
    }

    public Schedule getSchedule()
    {
        return schedule;
    }

    public void setSchedule(Schedule schedule)
    {
        this.schedule = schedule;
    }

    public Application getApplication()
    {
        return application;
    }

    public void setApplication(Application application)
    {
        this.application = application;
    }

    public Environment getEnvironment()
    {
        return environment;
    }

    public void setEnvironment(Environment environment)
    {
        this.environment = environment;
    }

    public Severity getSeverity()
    {
        return severity;
    }

    public void setSeverity(Severity severity)
    {
        this.severity = severity;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public Integer getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getRetryCount()
    {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount)
    {
        this.retryCount = retryCount;
    }

    public Integer getRetryDelaySeconds()
    {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(Integer retryDelaySeconds)
    {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public String getExpectedResult()
    {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult)
    {
        this.expectedResult = expectedResult;
    }

    public String getFailureMessage()
    {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage)
    {
        this.failureMessage = failureMessage;
    }

    public boolean isRecoveryEnabled()
    {
        return recoveryEnabled;
    }

    public void setRecoveryEnabled(boolean recoveryEnabled)
    {
        this.recoveryEnabled = recoveryEnabled;
    }

    public boolean isDashboardEnabled()
    {
        return dashboardEnabled;
    }

    public void setDashboardEnabled(boolean dashboardEnabled)
    {
        this.dashboardEnabled = dashboardEnabled;
    }

    public String getDashboardTitle()
    {
        return dashboardTitle;
    }

    public void setDashboardTitle(String dashboardTitle)
    {
        this.dashboardTitle = dashboardTitle;
    }

    public Integer getDashboardWidth()
    {
        return dashboardWidth;
    }

    public void setDashboardWidth(Integer dashboardWidth)
    {
        this.dashboardWidth = dashboardWidth;
    }

    public Integer getDashboardSortOrder()
    {
        return dashboardSortOrder;
    }

    public void setDashboardSortOrder(Integer dashboardSortOrder)
    {
        this.dashboardSortOrder = dashboardSortOrder;
    }

    public String getScriptPath()
    {
        return scriptPath;
    }

    public void setScriptPath(String scriptPath)
    {
        this.scriptPath = scriptPath;
    }

    public String getWorkingDirectory()
    {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory)
    {
        this.workingDirectory = workingDirectory;
    }

    public String getCommandArguments()
    {
        return commandArguments;
    }

    public void setCommandArguments(String commandArguments)
    {
        this.commandArguments = commandArguments;
    }

    public String getHttpMethod()
    {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod)
    {
        this.httpMethod = httpMethod;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getRequestHeaders()
    {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders)
    {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody()
    {
        return requestBody;
    }

    public void setRequestBody(String requestBody)
    {
        this.requestBody = requestBody;
    }

    public Integer getExpectedHttpStatus()
    {
        return expectedHttpStatus;
    }

    public void setExpectedHttpStatus(Integer expectedHttpStatus)
    {
        this.expectedHttpStatus = expectedHttpStatus;
    }

    public String getExpectedResponse()
    {
        return expectedResponse;
    }

    public void setExpectedResponse(String expectedResponse)
    {
        this.expectedResponse = expectedResponse;
    }

    public String getHealthCheckType()
    {
        return healthCheckType;
    }

    public void setHealthCheckType(String healthCheckType)
    {
        this.healthCheckType = healthCheckType;
    }

    public String getTargetHost()
    {
        return targetHost;
    }

    public void setTargetHost(String targetHost)
    {
        this.targetHost = targetHost;
    }

    public Integer getTargetPort()
    {
        return targetPort;
    }

    public void setTargetPort(Integer targetPort)
    {
        this.targetPort = targetPort;
    }

    public LocalDateTime getCreatedAt()
    {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt()
    {
        return updatedAt;
    }

    public Long getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy)
    {
        this.createdBy = createdBy;
    }

    public Long getUpdatedBy()
    {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy)
    {
        this.updatedBy = updatedBy;
    }
}
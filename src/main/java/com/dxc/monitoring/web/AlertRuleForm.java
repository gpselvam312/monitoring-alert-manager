package com.dxc.monitoring.web;

public class AlertRuleForm {
    private Long id;
    private Long monitoringJobId;
    private String name;
    private String description;
    private boolean enabled = true;
    private String severity = "CRITICAL";
    private String executionSourcePolicy = "SCHEDULED_ONLY";
    private String conditionFieldPath = "";
    private String conditionOperator = "GREATER_THAN";
    private String threshold = "";
    private int consecutiveMatches = 1;
    private String correlationFieldPath = "";
    private Long notificationPolicyId;
    private boolean recoveryEnabled = true;
    private String recoveryFieldPath = "";
    private String recoveryOperator = "LESS_THAN_OR_EQUAL";
    private String recoveryThreshold = "";
    private int recoveryConsecutiveMatches = 2;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMonitoringJobId() { return monitoringJobId; }
    public void setMonitoringJobId(Long monitoringJobId) { this.monitoringJobId = monitoringJobId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getExecutionSourcePolicy() { return executionSourcePolicy; }
    public void setExecutionSourcePolicy(String executionSourcePolicy) { this.executionSourcePolicy = executionSourcePolicy; }
    public String getConditionFieldPath() { return conditionFieldPath; }
    public void setConditionFieldPath(String conditionFieldPath) { this.conditionFieldPath = conditionFieldPath; }
    public String getConditionOperator() { return conditionOperator; }
    public void setConditionOperator(String conditionOperator) { this.conditionOperator = conditionOperator; }
    public String getThreshold() { return threshold; }
    public void setThreshold(String threshold) { this.threshold = threshold; }
    public int getConsecutiveMatches() { return consecutiveMatches; }
    public void setConsecutiveMatches(int consecutiveMatches) { this.consecutiveMatches = consecutiveMatches; }
    public Long getNotificationPolicyId() { return notificationPolicyId; }
    public void setNotificationPolicyId(Long notificationPolicyId) { this.notificationPolicyId = notificationPolicyId; }
    public String getCorrelationFieldPath() { return correlationFieldPath; }
    public void setCorrelationFieldPath(String correlationFieldPath) { this.correlationFieldPath = correlationFieldPath; }
    public boolean isRecoveryEnabled() { return recoveryEnabled; }
    public void setRecoveryEnabled(boolean recoveryEnabled) { this.recoveryEnabled = recoveryEnabled; }
    public String getRecoveryFieldPath() { return recoveryFieldPath; }
    public void setRecoveryFieldPath(String recoveryFieldPath) { this.recoveryFieldPath = recoveryFieldPath; }
    public String getRecoveryOperator() { return recoveryOperator; }
    public void setRecoveryOperator(String recoveryOperator) { this.recoveryOperator = recoveryOperator; }
    public String getRecoveryThreshold() { return recoveryThreshold; }
    public void setRecoveryThreshold(String recoveryThreshold) { this.recoveryThreshold = recoveryThreshold; }
    public int getRecoveryConsecutiveMatches() { return recoveryConsecutiveMatches; }
    public void setRecoveryConsecutiveMatches(int recoveryConsecutiveMatches) { this.recoveryConsecutiveMatches = recoveryConsecutiveMatches; }
}

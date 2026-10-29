package com.dxc.monitoring.web;

public class AlertRuleForm {
    private Long id;
    private Long monitoringJobId;
    private String name;
    private String description;
    private boolean enabled = true;
    private String severity = "CRITICAL";
    private String executionSourcePolicy = "SCHEDULED_ONLY";

    private String conditionType = "CONDITION";
    private String conditionTreeJson = "{\"type\":\"CONDITION\",\"fieldPath\":\"\",\"operator\":\"GREATER_THAN\",\"threshold\":\"\"}";
    private String collectionPath = "";
    private String countFieldPath = "";
    private String countFieldOperator = "EQUALS";
    private String countFieldThreshold = "";
    private String countOperator = "GREATER_THAN_OR_EQUAL";
    private String countThreshold = "1";
    private int consecutiveMatches = 1;
    private String correlationFieldPath = "";

    private String alertSubject = "[{severity}] {ruleName}";
    private String alertMessage = "Job {jobName} in application {applicationName}, environment {environment}: {fieldPath} evaluated to {actualValue} (threshold: {threshold}).";
    private Long notificationPolicyId;
    private String notificationRoutesJson = "[]";
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

    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    public String getConditionTreeJson() { return conditionTreeJson; }
    public void setConditionTreeJson(String conditionTreeJson) { this.conditionTreeJson = conditionTreeJson; }
    public String getCollectionPath() { return collectionPath; }
    public void setCollectionPath(String collectionPath) { this.collectionPath = collectionPath; }
    public String getCountFieldPath() { return countFieldPath; }
    public void setCountFieldPath(String countFieldPath) { this.countFieldPath = countFieldPath; }
    public String getCountFieldOperator() { return countFieldOperator; }
    public void setCountFieldOperator(String countFieldOperator) { this.countFieldOperator = countFieldOperator; }
    public String getCountFieldThreshold() { return countFieldThreshold; }
    public void setCountFieldThreshold(String countFieldThreshold) { this.countFieldThreshold = countFieldThreshold; }
    public String getCountOperator() { return countOperator; }
    public void setCountOperator(String countOperator) { this.countOperator = countOperator; }
    public String getCountThreshold() { return countThreshold; }
    public void setCountThreshold(String countThreshold) { this.countThreshold = countThreshold; }
    public int getConsecutiveMatches() { return consecutiveMatches; }
    public void setConsecutiveMatches(int consecutiveMatches) { this.consecutiveMatches = consecutiveMatches; }
    public String getCorrelationFieldPath() { return correlationFieldPath; }
    public void setCorrelationFieldPath(String correlationFieldPath) { this.correlationFieldPath = correlationFieldPath; }

    public String getAlertSubject() { return alertSubject; }
    public void setAlertSubject(String alertSubject) { this.alertSubject = alertSubject; }
    public String getAlertMessage() { return alertMessage; }
    public void setAlertMessage(String alertMessage) { this.alertMessage = alertMessage; }
    public Long getNotificationPolicyId() { return notificationPolicyId; }
    public void setNotificationPolicyId(Long notificationPolicyId) { this.notificationPolicyId = notificationPolicyId; }
    public String getNotificationRoutesJson() { return notificationRoutesJson; }
    public void setNotificationRoutesJson(String notificationRoutesJson) { this.notificationRoutesJson = notificationRoutesJson; }
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

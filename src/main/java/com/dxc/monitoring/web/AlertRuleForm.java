package com.dxc.monitoring.web;

public class AlertRuleForm {
    private Long id;
    private Long monitoringJobId;
    private String name;
    private String description;
    private boolean enabled = true;
    private String severity = "CRITICAL";
    private String executionSourcePolicy = "SCHEDULED_ONLY";
    private boolean conditionGroupEnabled;
    private String conditionGroupOperator = "AND";
    private String secondConditionFieldPath = "";
    private String secondConditionOperator = "GREATER_THAN";
    private String secondThreshold = "";
    private String conditionType = "CONDITION";
    private String collectionPath = "";
    private String countFieldPath = "";
    private String countFieldOperator = "EQUALS";
    private String countFieldThreshold = "";
    private String countOperator = "GREATER_THAN_OR_EQUAL";
    private String countThreshold = "1";
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
    public boolean isConditionGroupEnabled() { return conditionGroupEnabled; }
    public void setConditionGroupEnabled(boolean conditionGroupEnabled) { this.conditionGroupEnabled = conditionGroupEnabled; }
    public String getConditionGroupOperator() { return conditionGroupOperator; }
    public void setConditionGroupOperator(String conditionGroupOperator) { this.conditionGroupOperator = conditionGroupOperator; }
    public String getSecondConditionFieldPath() { return secondConditionFieldPath; }
    public void setSecondConditionFieldPath(String secondConditionFieldPath) { this.secondConditionFieldPath = secondConditionFieldPath; }
    public String getSecondConditionOperator() { return secondConditionOperator; }
    public void setSecondConditionOperator(String secondConditionOperator) { this.secondConditionOperator = secondConditionOperator; }
    public String getSecondThreshold() { return secondThreshold; }
    public void setSecondThreshold(String secondThreshold) { this.secondThreshold = secondThreshold; }
    public String getConditionType() { return conditionType; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
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

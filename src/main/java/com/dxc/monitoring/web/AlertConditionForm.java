package com.dxc.monitoring.web;

public class AlertConditionForm {
    private String fieldPath = "";
    private String operator = "GREATER_THAN";
    private String threshold = "";

    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getThreshold() { return threshold; }
    public void setThreshold(String threshold) { this.threshold = threshold; }
}

package com.dxc.monitoring.service.dashboard;

import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DashboardWidgetValidator implements ConstraintValidator<ValidDashboardWidget, DashboardWidgetForm>
{
    private static final Set<String> WIDGET_TYPES = Set.of("STAT", "STATUS", "TABLE", "CHART", "SERVER_HEALTH", "TEXT");
    private static final Set<String> CHART_TYPES = Set.of("LINE", "BAR", "PIE", "DONUT");
    private static final Set<String> DATA_SOURCE_TYPES = Set.of("MONITORING_JOB");

    @Override
    public boolean isValid(DashboardWidgetForm form, ConstraintValidatorContext context)
    {
        if (form == null)
        {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (!isBlank(form.getWidgetType()) && !WIDGET_TYPES.contains(form.getWidgetType()))
        {
            addFieldError(context, "widgetType", "Select a valid widget type.");
            valid = false;
        }

        if (Boolean.TRUE.equals(form.getAutoRefresh()))
        {
            if (form.getRefreshInterval() == null || form.getRefreshInterval() <= 0)
            {
                addFieldError(context, "refreshInterval",
                        "Refresh interval is required and must be greater than zero.");
                valid = false;
            }

            if (isBlank(form.getRefreshIntervalUnit())
                    || !Set.of("SECONDS", "MINUTES").contains(form.getRefreshIntervalUnit()))
            {
                addFieldError(context, "refreshIntervalUnit",
                        "Select a valid refresh interval unit when auto refresh is enabled.");
                valid = false;
            }
        }

        String sourceType = form.getDataSourceType();
        Long sourceId = form.getDataSourceId();

        if (isBlank(sourceType))
        {
            if (sourceId != null)
            {
                addFieldError(context, "dataSourceType",
                        "Select a data source type or clear the selected source.");
                valid = false;
            }
        }
        else if (!DATA_SOURCE_TYPES.contains(sourceType))
        {
            addFieldError(context, "dataSourceType", "Select a valid data source type.");
            valid = false;
        }
        else if (sourceId == null)
        {
            addFieldError(context, "dataSourceId", "Select a monitoring job for this widget.");
            valid = false;
        }

        if ("CHART".equals(form.getWidgetType()))
        {
            if (isBlank(form.getChartType()) || !CHART_TYPES.contains(form.getChartType()))
            {
                addFieldError(context, "chartType", "Select a valid chart type for chart widgets.");
                valid = false;
            }
        }

        return valid;
    }

    private void addFieldError(ConstraintValidatorContext context, String field, String message)
    {
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
    }

    private boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }
}
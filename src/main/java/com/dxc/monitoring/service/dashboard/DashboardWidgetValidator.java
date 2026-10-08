package com.dxc.monitoring.service.dashboard;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DashboardWidgetValidator implements ConstraintValidator<ValidDashboardWidget, DashboardWidgetForm>
{
    @Override
    public boolean isValid(DashboardWidgetForm form, ConstraintValidatorContext context)
    {
        if (form == null)
        {
            return true;
        }

        boolean valid = true;

        context.disableDefaultConstraintViolation();

        // Auto Refresh validation
        if (Boolean.TRUE.equals(form.getAutoRefresh()))
        {
            if (form.getRefreshInterval() == null || form.getRefreshInterval() <= 0)
            {
                addFieldError(context, "refreshInterval",
                        "Refresh interval is required and must be greater than zero.");
                valid = false;
            }

            if (isBlank(form.getRefreshIntervalUnit()))
            {
                addFieldError(context, "refreshIntervalUnit",
                        "Refresh interval unit is required when auto refresh is enabled.");
                valid = false;
            }
        }

        // Monitoring Job data source validation
        if ("MONITORING_JOB".equals(form.getDataSourceType()) && form.getDataSourceId() == null)
        {
            addFieldError(context, "dataSourceId",
                    "Monitoring job is required when the data source is Monitoring Job.");
            valid = false;
        }

        // Chart widget validation
        if ("CHART".equals(form.getWidgetType()) && isBlank(form.getChartType()))
        {
            addFieldError(context, "chartType", "Chart type is required for chart widgets.");
            valid = false;
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
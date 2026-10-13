package com.dxc.monitoring.service.dashboard;

import java.util.Set;
import java.util.Map;
import java.util.List;

import tools.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DashboardWidgetValidator implements ConstraintValidator<ValidDashboardWidget, DashboardWidgetForm>
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> RESPONSE_PARSER_TYPES = Set.of("AUTO", "JSON", "DELIMITED", "KEY_VALUE", "REGEX");
    private static final Set<String> WIDGET_TYPES = Set.of("STAT", "STATUS", "TABLE", "SUMMARY_TABLE", "CHART", "TEXT", "SYSTEM_METRICS", "ELASTICSEARCH");
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

        valid &= validateFieldConfiguration(form, context);

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


    private boolean validateFieldConfiguration(DashboardWidgetForm form, ConstraintValidatorContext context)
    {
        String json = form.getFieldConfigJson();
        if (isBlank(json))
        {
            return true;
        }

        Object parsed;
        try
        {
            parsed = OBJECT_MAPPER.readValue(json, Object.class);
        }
        catch (Exception exception)
        {
            addFieldError(context, "fieldConfigJson", "Field configuration must be valid JSON.");
            return false;
        }

        if (!(parsed instanceof Map<?, ?> config))
        {
            addFieldError(context, "fieldConfigJson", "Field configuration must be a JSON object.");
            return false;
        }

        Object parserValue = config.get("responseParser");
        if (parserValue == null)
        {
            return true;
        }
        if (!(parserValue instanceof Map<?, ?> parser))
        {
            addFieldError(context, "fieldConfigJson", "responseParser must be a JSON object.");
            return false;
        }

        Object parserType = parser.get("type");
        String type = String.valueOf(parserType == null ? "AUTO" : parserType).trim().toUpperCase();
        if (!RESPONSE_PARSER_TYPES.contains(type))
        {
            addFieldError(context, "fieldConfigJson", "Select a supported response parser type.");
            return false;
        }
        Object delimiter = parser.get("delimiter");
        if ("DELIMITED".equals(type) && isBlank(delimiter == null ? "" : String.valueOf(delimiter)))
        {
            addFieldError(context, "fieldConfigJson", "Delimited response parsing requires a delimiter.");
            return false;
        }
        Object separator = parser.get("separator");
        if ("KEY_VALUE".equals(type) && isBlank(separator == null ? "" : String.valueOf(separator)))
        {
            addFieldError(context, "fieldConfigJson", "Key-value response parsing requires a separator.");
            return false;
        }
        if ("REGEX".equals(type))
        {
            Object fieldsValue = parser.get("fields");
            if (!(fieldsValue instanceof List<?> fields) || fields.isEmpty())
            {
                addFieldError(context, "fieldConfigJson", "Regex response parsing requires at least one field.");
                return false;
            }
            for (Object fieldValue : fields)
            {
                if (!(fieldValue instanceof Map<?, ?> field)
                        || isBlank(field.get("key") == null ? "" : String.valueOf(field.get("key")))
                        || isBlank(field.get("pattern") == null ? "" : String.valueOf(field.get("pattern")))
                        || (field.containsKey("group") && !(field.get("group") instanceof Number)))
                {
                    addFieldError(context, "fieldConfigJson",
                            "Each regex parser field requires a key, pattern, and numeric capture group when specified.");
                    return false;
                }
            }
        }
        return true;
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
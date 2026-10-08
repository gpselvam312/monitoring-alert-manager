package com.dxc.monitoring.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component("monitoringDateTime")
public class DateTimeFormatterUtil
{
    private final DateTimeFormatter formatter;

    public DateTimeFormatterUtil(
            @Value("${monitoring.datetime-format:MM/dd/yyyy hh:mm:ss a}")
            String pattern)
    {
        this.formatter = DateTimeFormatter
                .ofPattern(pattern, Locale.ENGLISH);
    }

    public String format(OffsetDateTime value)
    {
        if (value == null)
        {
            return "-";
        }

        return formatter.format(value).toUpperCase(Locale.ENGLISH);
    }
}
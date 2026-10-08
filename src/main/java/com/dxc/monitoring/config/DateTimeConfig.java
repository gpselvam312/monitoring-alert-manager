package com.dxc.monitoring.config;

import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DateTimeConfig
{
    @Bean
    public DateTimeFormatter monitoringDateTimeFormatter(
            @Value("${monitoring.datetime-format:MM/dd/yyyy hh:mm:ss a}") String pattern)
    {
        return DateTimeFormatter.ofPattern(pattern);
    }
}
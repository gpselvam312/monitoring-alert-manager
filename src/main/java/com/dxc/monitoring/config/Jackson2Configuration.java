package com.dxc.monitoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the Jackson 2 mapper used by legacy/application JSON settings code.
 *
 * Spring Boot 4 auto-configures Jackson 3 by default; the application currently
 * uses Jackson 2 APIs, so this mapper must be registered explicitly.
 */
@Configuration
public class Jackson2Configuration
{
    @Bean
    public ObjectMapper jackson2ObjectMapper()
    {
        return new ObjectMapper();
    }
}

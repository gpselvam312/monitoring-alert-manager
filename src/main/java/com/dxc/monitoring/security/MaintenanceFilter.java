package com.dxc.monitoring.security;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dxc.monitoring.service.SystemSettingService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MaintenanceFilter extends OncePerRequestFilter
{
    private static final Logger log = LoggerFactory.getLogger(MaintenanceFilter.class);
    private static final String MAINTENANCE_PATH = "/maintenance";

    private final SystemSettingService systemSettingService;
    private final ObjectMapper objectMapper;

    public MaintenanceFilter(SystemSettingService systemSettingService, ObjectMapper objectMapper)
    {
        this.systemSettingService = systemSettingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException
    {
        String requestUri = request.getRequestURI();

        if (isExcludedPath(requestUri))
        {
            filterChain.doFilter(request, response);
            return;
        }

        boolean siteOffline;
        boolean bypassIp = false;
        try
        {
            siteOffline = systemSettingService.isSiteOffline();
            if (siteOffline)
            {
                bypassIp = isBypassIp(request);
            }
        }
        catch (RuntimeException exception)
        {
            // Maintenance status must not prevent the common error handler from handling a database outage.
            log.warn("Could not read maintenance settings; allowing request to continue to normal error handling: {}",
                    exception.getClass().getSimpleName());
            siteOffline = false;
        }

        if (!siteOffline || bypassIp)
        {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendRedirect(request.getContextPath() + MAINTENANCE_PATH);
    }

    private boolean isExcludedPath(String requestUri)
    {
        return requestUri.equals("/maintenance") || requestUri.equals("/maintenance/check")
                || requestUri.equals("/login") || requestUri.startsWith("/css/") || requestUri.startsWith("/js/")
                || requestUri.startsWith("/images/") || requestUri.startsWith("/fonts/")
                || requestUri.startsWith("/webjars/") || requestUri.equals("/actuator/health");
    }

    private boolean isBypassIp(HttpServletRequest request)
    {
        String configuredIps = systemSettingService.getMaintenanceBypassIps();

        if (!StringUtils.hasText(configuredIps))
        {
            return false;
        }

        String clientIp = request.getRemoteAddr();

        if (!StringUtils.hasText(clientIp))
        {
            return false;
        }

        List<String> bypassIps = parseIps(configuredIps);

        return bypassIps.contains(clientIp);
    }

    private List<String> parseIps(String json)
    {
        try
        {
            List<String> ips = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return ips == null ? List.of() : ips.stream()
                    .filter(StringUtils::hasText).map(String::trim).toList();
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception)
        {
            log.warn("Maintenance bypass IP configuration is invalid JSON.");
            return List.of();
        }
    }

}

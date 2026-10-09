package com.dxc.monitoring.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public MaintenanceFilter(SystemSettingService systemSettingService)
    {
        this.systemSettingService = systemSettingService;
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
        String trimmed = json.trim();

        if (!trimmed.startsWith("[") || !trimmed.endsWith("]"))
        {
            return List.of();
        }

        String content = trimmed.substring(1, trimmed.length() - 1).trim();

        if (content.isEmpty())
        {
            return List.of();
        }

        String[] values = content.split(",");

        List<String> ips = new ArrayList<>();

        for (String value : values)
        {
            String ip = value.trim();

            if (ip.startsWith("\"") && ip.endsWith("\""))
            {
                ip = ip.substring(1, ip.length() - 1);
            }

            if (StringUtils.hasText(ip))
            {
                ips.add(ip);
            }
        }

        return ips;
    }

}

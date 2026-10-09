package com.dxc.monitoring.controller;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public void handleMissingStaticResource(NoResourceFoundException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException
    {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        if (!"/favicon.ico".equals(request.getRequestURI()))
        {
            log.debug("Static resource not found: {}", request.getRequestURI());
        }
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpectedException(Exception exception, HttpServletRequest request,
            HttpServletResponse response, RedirectAttributes redirectAttributes)
    {
        log.error("Unhandled application error for {} {}", request.getMethod(), request.getRequestURI(), exception);

        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String referer = request.getHeader("Referer");
        String returnPath = safeReturnPath(referer, request);

        // For form submissions, return to the originating screen so the shared layout can display the error modal.
        if (returnPath != null && ("POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method)))
        {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "The operation could not be completed. Please try again. If the problem continues, contact support.");
            return "redirect:" + returnPath;
        }

        // A GET request may itself be failing (for example, while the database is unavailable).
        // Render a standalone fallback so the user never sees Spring Boot's Whitelabel Error Page.
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        return "error/general";
    }

    private String safeReturnPath(String referer, HttpServletRequest request)
    {
        if (referer == null || referer.isBlank())
        {
            return null;
        }

        try
        {
            URI uri = URI.create(referer);
            int refererPort = uri.getPort();
            int requestPort = request.getServerPort();
            boolean samePort = refererPort == requestPort
                    || (refererPort == -1 && (requestPort == 80 || requestPort == 443));
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(request.getServerName())
                    || !samePort || uri.getUserInfo() != null)
            {
                return null;
            }

            String path = uri.getRawPath();
            String contextPath = request.getContextPath();
            if (path == null || !path.startsWith(contextPath + "/")
                    || path.equals(request.getRequestURI())
                    || path.startsWith(contextPath + "/error"))
            {
                return null;
            }

            String applicationPath = path.substring(contextPath.length());
            return contextPath + applicationPath
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }
    }
}

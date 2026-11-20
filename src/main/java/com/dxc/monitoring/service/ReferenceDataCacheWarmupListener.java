package com.dxc.monitoring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class ReferenceDataCacheWarmupListener {
    private static final Logger log = LoggerFactory.getLogger(ReferenceDataCacheWarmupListener.class);
    private final ReferenceDataCacheService referenceDataCache;

    public ReferenceDataCacheWarmupListener(ReferenceDataCacheService referenceDataCache) {
        this.referenceDataCache = referenceDataCache;
    }

    @EventListener
    public void onSuccessfulLogin(InteractiveAuthenticationSuccessEvent event) {
        try {
            var applications = referenceDataCache.getEnabledApplications();
            applications.forEach(application -> referenceDataCache.getEnabledEnvironments(application.getId()));
            log.debug("Warmed application/environment reference data after successful login.");
        } catch (RuntimeException exception) {
            log.warn("Application/environment reference data warm-up failed; it will load on first use.", exception);
        }
    }
}

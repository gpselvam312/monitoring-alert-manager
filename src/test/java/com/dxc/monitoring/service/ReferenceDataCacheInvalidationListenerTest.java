package com.dxc.monitoring.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class ReferenceDataCacheInvalidationListenerTest {
    @Test
    void clearsApplicationAndEnvironmentCachesWhenChangeEventIsHandled() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache applications = mock(Cache.class);
        Cache environments = mock(Cache.class);
        when(cacheManager.getCache("enabledApplications")).thenReturn(applications);
        when(cacheManager.getCache("enabledEnvironmentsByApplication")).thenReturn(environments);

        ReferenceDataCacheInvalidationListener listener = new ReferenceDataCacheInvalidationListener(cacheManager);
        listener.onReferenceDataChanged(new ReferenceDataChangedEvent());

        verify(applications).clear();
        verify(environments).clear();
    }
}

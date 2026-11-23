package com.dxc.monitoring.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class MenuDefinitionCacheInvalidationListenerTest {
    @Test
    void clearsMenuDefinitionsCacheWhenChangeEventIsHandled() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache menuDefinitions = mock(Cache.class);
        when(cacheManager.getCache("menuDefinitions")).thenReturn(menuDefinitions);

        MenuDefinitionCacheInvalidationListener listener = new MenuDefinitionCacheInvalidationListener(cacheManager);
        listener.onMenuDefinitionsChanged(new MenuDefinitionsChangedEvent());

        verify(menuDefinitions).clear();
    }
}

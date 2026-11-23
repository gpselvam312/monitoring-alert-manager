package com.dxc.monitoring.service;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MenuDefinitionCacheInvalidationListener {
    private final CacheManager cacheManager;

    public MenuDefinitionCacheInvalidationListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMenuDefinitionsChanged(MenuDefinitionsChangedEvent event) {
        var cache = cacheManager.getCache("menuDefinitions");
        if (cache != null) cache.clear();
    }
}

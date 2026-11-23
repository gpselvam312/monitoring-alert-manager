package com.dxc.monitoring.service;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReferenceDataCacheInvalidationListener {
    private final CacheManager cacheManager;

    public ReferenceDataCacheInvalidationListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReferenceDataChanged(ReferenceDataChangedEvent event) {
        clear("enabledApplications");
        clear("enabledEnvironmentsByApplication");
    }

    private void clear(String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}

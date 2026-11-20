package com.dxc.monitoring.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/** Keeps scheduled workers idle until startup validation and secret migration finish. */
@Component
public class BackgroundWorkReadiness {
    private final AtomicBoolean ready = new AtomicBoolean(false);
    public boolean isReady() { return ready.get(); }
    public void markReady() { ready.set(true); }
}

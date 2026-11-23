package com.dxc.monitoring.service.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

class ApiExecutorTest
{
    @Test
    void acceptsHttpAndHttpsTargetsIncludingInternalMonitoringHosts()
    {
        assertEquals(URI.create("http://localhost:8080/health"),
                ApiExecutor.validateTargetUri("http://localhost:8080/health"));
        assertEquals(URI.create("https://monitoring.internal.example/ready"),
                ApiExecutor.validateTargetUri("https://monitoring.internal.example/ready"));
    }

    @Test
    void rejectsNonHttpSchemesAndRelativeTargets()
    {
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("ftp://example.com/health"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("/health"));
    }

    @Test
    void rejectsEmbeddedCredentialsAndFragments()
    {
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("https://user:password@example.com/health"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("https://example.com/health#fragment"));
    }

    @Test
    void rejectsInvalidPortsAndMissingHosts()
    {
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("https://example.com:0/health"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("https://example.com:65536/health"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiExecutor.validateTargetUri("https:///health"));
    }
}

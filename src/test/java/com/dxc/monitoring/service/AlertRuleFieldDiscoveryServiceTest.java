package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;

import tools.jackson.databind.ObjectMapper;

class AlertRuleFieldDiscoveryServiceTest {

    @Test
    void discoversReusableDotPathsAcrossNestedArrays() {
        AlertRuleFieldDiscoveryService service = new AlertRuleFieldDiscoveryService(
                mock(MonitoringExecutionRepository.class),
                mock(MonitoringResultRepository.class),
                new ObjectMapper());

        AlertRuleFieldDiscoveryService.Discovery discovery = service.discoverSample("""
                {
                  "machines": [
                    {
                      "hostname": "host-a",
                      "status": "UP",
                      "metrics": [
                        {"name": "cpu", "value": 95},
                        {"name": "memory", "value": "unknown"}
                      ]
                    },
                    {
                      "hostname": "host-b",
                      "status": "DOWN",
                      "metrics": [
                        {"name": "cpu", "value": 40}
                      ]
                    }
                  ]
                }
                """);

        Map<String, AlertRuleFieldDiscoveryService.Field> fields = discovery.fields().stream()
                .collect(Collectors.toMap(AlertRuleFieldDiscoveryService.Field::path, field -> field));

        assertTrue(fields.containsKey("machines.hostname"));
        assertTrue(fields.containsKey("machines.status"));
        assertTrue(fields.containsKey("machines.metrics.name"));
        assertTrue(fields.containsKey("machines.metrics.value"));
        assertEquals("mixed", fields.get("machines.metrics.value").type());
        assertFalse(fields.keySet().stream().anyMatch(path -> path.contains(".0.") || path.contains(".1.")));
    }
}

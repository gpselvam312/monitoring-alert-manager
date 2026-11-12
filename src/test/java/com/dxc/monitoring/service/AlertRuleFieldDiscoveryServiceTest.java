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

        assertTrue(fields.containsKey("response.machines.hostname"));
        assertTrue(fields.containsKey("data.machines.hostname"));
        assertTrue(fields.containsKey("data.machines.status"));
        assertTrue(fields.containsKey("data.machines.metrics.name"));
        assertTrue(fields.containsKey("data.machines.metrics.value"));
        assertEquals("mixed", fields.get("data.machines.metrics.value").type());
        assertFalse(fields.keySet().stream().anyMatch(path -> path.contains(".0.") || path.contains(".1.")));
    }
    @Test
    void discoversPlatformMetadataAndPayloadAsSeparateNamespaces() {
        AlertRuleFieldDiscoveryService service = new AlertRuleFieldDiscoveryService(
                mock(MonitoringExecutionRepository.class),
                mock(MonitoringResultRepository.class),
                new ObjectMapper());

        AlertRuleFieldDiscoveryService.Discovery discovery = service.discoverSample("""
                {
                  "schemaVersion": 1,
                  "status": "CRITICAL",
                  "checkedAt": "2026-10-11T10:30:00+05:30",
                  "data": {
                    "cluster": {"name": "kafka-sit"},
                    "consumerGroups": [{"name": "orders", "totalLag": 120}]
                  }
                }
                """);

        Map<String, AlertRuleFieldDiscoveryService.Field> fields = discovery.fields().stream()
                .collect(Collectors.toMap(AlertRuleFieldDiscoveryService.Field::path, field -> field));

        assertTrue(fields.containsKey("response.status"));
        assertTrue(fields.containsKey("response.data.cluster.name"));
        assertTrue(fields.containsKey("data.cluster.name"));
        assertTrue(fields.containsKey("data.consumerGroups.totalLag"));
        assertFalse(fields.containsKey("cluster.name"));
    }

    @Test
    void exposesFlatLegacyResponseUnderBothExplicitNamespaces() {
        AlertRuleFieldDiscoveryService service = new AlertRuleFieldDiscoveryService(
                mock(MonitoringExecutionRepository.class),
                mock(MonitoringResultRepository.class),
                new ObjectMapper());

        AlertRuleFieldDiscoveryService.Discovery discovery = service.discoverSample(
                "{\\"status\\":\\"UP\\",\\"cpu\\":72}");

        assertTrue(discovery.fields().stream().anyMatch(field -> field.path().equals("response.status")));
        assertTrue(discovery.fields().stream().anyMatch(field -> field.path().equals("data.status")));
        assertTrue(discovery.fields().stream().anyMatch(field -> field.path().equals("data.cpu")));
    }

}

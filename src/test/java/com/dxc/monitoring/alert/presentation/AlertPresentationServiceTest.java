package com.dxc.monitoring.alert.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.MonitoringResultRepository;

import tools.jackson.databind.ObjectMapper;

class AlertPresentationServiceTest {
    private MonitoringResultRepository resultRepository;
    private AlertPresentationService service;

    @BeforeEach
    void setUp() {
        resultRepository = mock(MonitoringResultRepository.class);
        service = new AlertPresentationService(new ObjectMapper(), resultRepository);
    }

    @Test
    void buildsOneChannelNeutralPresentationFromTheTriggeringExecution() {
        AlertRule rule = new AlertRule();
        rule.setName("Kafka Health");
        rule.setPresentationConfig("""
                {
                  "version":1,
                  "titleTemplate":"{severity} - {ruleName}",
                  "messageTemplate":"{jobName} observed {actualValue} on {data.cluster.version}",
                  "sections":[
                    {"id":"cluster","type":"KEY_VALUE","title":"Cluster details","fields":[
                      {"label":"Cluster","path":"data.cluster.name"},
                      {"label":"Platform status","path":"response.status"}
                    ]},
                    {"id":"nodes","type":"TABLE","title":"Nodes","sourcePath":"data.nodes",
                     "columns":[{"key":"name","label":"Node"},{"key":"state","label":"State"}],"maxRows":1},
                    {"id":"states","type":"LIST","title":"States","sourcePath":"data.states","maxItems":2},
                    {"id":"payload","type":"JSON","title":"Cluster JSON","sourcePath":"data.cluster","maxLength":1000}
                  ]
                }
                """);

        MonitoringJob job = new MonitoringJob();
        job.setName("Batch Processing");
        Alert alert = new Alert();
        alert.setAlertRule(rule);
        alert.setMonitoringJob(job);
        alert.setSeverity(AlertRule.Severity.CRITICAL);

        MonitoringExecution execution = mock(MonitoringExecution.class);
        when(execution.getId()).thenReturn(101L);
        AlertHistory history = new AlertHistory();
        history.setAlert(alert);
        history.setExecution(execution);
        history.setNewSeverity(AlertRule.Severity.CRITICAL);
        history.setOccurredAt(OffsetDateTime.parse("2026-11-12T10:00:00+05:30"));

        MonitoringResult result = new MonitoringResult();
        result.setResultData("""
                {
                  "schemaVersion":1,
                  "status":"CRITICAL",
                  "data":{
                    "cluster":{"name":"kafka-sit","version":"3.8"},
                    "nodes":[
                      {"name":"node-a","state":"DOWN","lag":90},
                      {"name":"node-b","state":"UP","lag":5}
                    ],
                    "states":["DOWN","UP"]
                  }
                }
                """);
        when(resultRepository.findFirstByExecution_IdOrderByCreatedAtDescIdDesc(101L)).thenReturn(Optional.of(result));

        AlertPresentationService.Presentation presentation = service.build(history, Map.of(
                "title", "Legacy title", "body", "Legacy message", "severity", "CRITICAL",
                "ruleName", "Kafka Health", "observedValue", "55", "fieldPath", "data.lag"));

        assertEquals("CRITICAL - Kafka Health", presentation.title());
        assertEquals("Batch Processing observed 55 on 3.8", presentation.message());
        assertEquals(List.of("details", "table", "list", "json"),
                presentation.sections().stream().map(section -> section.get("type")).toList());
        assertEquals(1, ((List<?>) presentation.sections().get(1).get("rows")).size());
        assertEquals(true, presentation.sections().get(1).get("truncated"));
        assertEquals(List.of("DOWN", "UP"), presentation.sections().get(2).get("items"));
        assertTrue(String.valueOf(presentation.sections().get(3).get("value")).contains("kafka-sit"));
    }

    @Test
    void omitsMissingOptionalFieldsWithoutFailingNotificationPresentation() {
        AlertRule rule = new AlertRule();
        rule.setName("Simple rule");
        rule.setPresentationConfig("""
                {"version":1,"sections":[
                  {"type":"KEY_VALUE","fields":[{"label":"Missing","path":"data.missing"}]}
                ]}
                """);
        Alert alert = new Alert();
        alert.setAlertRule(rule);
        alert.setSeverity(AlertRule.Severity.WARNING);
        AlertHistory history = new AlertHistory();
        history.setAlert(alert);
        history.setNewSeverity(AlertRule.Severity.WARNING);

        AlertPresentationService.Presentation presentation = service.build(history,
                Map.of("title", "Existing title", "body", "Existing body"));

        assertEquals("Existing title", presentation.title());
        assertEquals("Existing body", presentation.message());
        assertTrue(presentation.sections().isEmpty());
    }
}

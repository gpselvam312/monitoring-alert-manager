package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.Alert;
import com.dxc.monitoring.entity.AlertHistory;
import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.AlertRuleEvaluation;
import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.entity.MonitoringResult;
import com.dxc.monitoring.repository.AlertHistoryRepository;
import com.dxc.monitoring.repository.AlertRepository;
import com.dxc.monitoring.repository.AlertRuleEvaluationRepository;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringExecutionRepository;
import com.dxc.monitoring.repository.MonitoringResultRepository;

import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;

class AlertEvaluationServiceTest {
    private MonitoringExecutionRepository executionRepository;
    private MonitoringResultRepository resultRepository;
    private AlertRuleRepository ruleRepository;
    private AlertRepository alertRepository;
    private AlertHistoryRepository historyRepository;
    private AlertRuleEvaluationRepository evaluationRepository;
    private AlertEvaluationService service;
    private MonitoringExecution execution;
    private AlertRule rule;
    private MonitoringJob job;

    @BeforeEach
    void setUp() {
        executionRepository = mock(MonitoringExecutionRepository.class);
        resultRepository = mock(MonitoringResultRepository.class);
        ruleRepository = mock(AlertRuleRepository.class);
        alertRepository = mock(AlertRepository.class);
        historyRepository = mock(AlertHistoryRepository.class);
        evaluationRepository = mock(AlertRuleEvaluationRepository.class);
        service = new AlertEvaluationService(executionRepository, resultRepository, ruleRepository,
                alertRepository, historyRepository, evaluationRepository, new ObjectMapper(), mock(NotificationRoutingService.class));

        execution = mock(MonitoringExecution.class);
        rule = mock(AlertRule.class);
        job = mock(MonitoringJob.class);
        when(execution.getId()).thenReturn(101L);
        when(execution.getStatus()).thenReturn(MonitoringExecution.ExecutionStatus.SUCCESS);
        when(execution.getTriggerSource()).thenReturn(MonitoringExecution.TriggerSource.SCHEDULED);
        when(execution.getMonitoringJob()).thenReturn(job);
        when(job.getId()).thenReturn(5L);
        when(rule.getId()).thenReturn(7L);
        when(rule.getExecutionSourcePolicy()).thenReturn(AlertRule.ExecutionSourcePolicy.SCHEDULED_ONLY);
        when(rule.getSeverity()).thenReturn(AlertRule.Severity.CRITICAL);
        when(rule.getName()).thenReturn("CPU usage high");
        when(rule.getAlertSubject()).thenReturn("[{severity}] {ruleName} — {jobName}");
        when(rule.getAlertMessage()).thenReturn("{fieldPath}: {actualValue} > {threshold} (execution {executionId})");
        when(job.getName()).thenReturn("API CPU");
        when(rule.getConditionConfig()).thenReturn("""
                {"fieldPath":"data.cpu.usage","operator":"GREATER_THAN","threshold":85,"consecutiveMatches":1}
                """);
        when(rule.getRecoveryConfig()).thenReturn("""
                {"enabled":true,"fieldPath":"data.cpu.usage","operator":"LESS_THAN_OR_EQUAL","threshold":75,"consecutiveMatches":2}
                """);
        when(executionRepository.findById(101L)).thenReturn(Optional.of(execution));
        when(ruleRepository.findEnabledForJobForUpdate(5L)).thenReturn(List.of(rule));
        when(evaluationRepository.existsByAlertRuleIdAndExecutionId(7L, 101L)).thenReturn(false);
        when(evaluationRepository.findTopByAlertRule_IdOrderByEvaluatedAtDescIdDesc(7L)).thenReturn(Optional.empty());
        when(alertRepository.findByAlertRuleIdAndCorrelationKeyAndStatus(
                7L, "DEFAULT", Alert.AlertStatus.ACTIVE)).thenReturn(Optional.empty());
        when(alertRepository.save(any(Alert.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(historyRepository.save(any(AlertHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(evaluationRepository.save(any(AlertRuleEvaluation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoringResult result = mock(MonitoringResult.class);
        when(result.getResultData()).thenReturn("""
                {"schemaVersion":1,"status":"SUCCESS","data":{"cpu":{"usage":92}}}
                """);
        when(result.getStatus()).thenReturn(MonitoringResult.ResultStatus.OK);
        when(result.getValue()).thenReturn("92");
        when(result.getUnit()).thenReturn("%");
        when(resultRepository.findAllByExecutionId(101L)).thenReturn(List.of(result));
    }

    @Test
    void createsAlertWhenScheduledConditionMatches() {
        service.evaluateExecution(101L);

        org.mockito.ArgumentCaptor<Alert> alertCaptor = org.mockito.ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        assertEquals("[CRITICAL] CPU usage high — API CPU", alertCaptor.getValue().getTitle());
        assertEquals("data.cpu.usage: 92 > 85 (execution 101)", alertCaptor.getValue().getSummary());
        verify(historyRepository).save(any(AlertHistory.class));
        verify(evaluationRepository).save(any(AlertRuleEvaluation.class));
    }

    @Test
    void evaluatesOrOfNestedAndConditionGroups() {
        when(rule.getConditionConfig()).thenReturn("""
                {
                  "type":"GROUP",
                  "operator":"OR",
                  "consecutiveMatches":1,
                  "children":[
                    {
                      "type":"GROUP",
                      "operator":"AND",
                      "children":[
                        {"type":"CONDITION","fieldPath":"data.cpu.usage","operator":"LESS_THAN","threshold":85},
                        {"type":"CONDITION","fieldPath":"data.cpu.usage","operator":"LESS_THAN","threshold":100}
                      ]
                    },
                    {
                      "type":"GROUP",
                      "operator":"AND",
                      "children":[
                        {"type":"CONDITION","fieldPath":"data.cpu.usage","operator":"GREATER_THAN","threshold":90},
                        {"type":"CONDITION","fieldPath":"data.cpu.usage","operator":"LESS_THAN","threshold":100}
                      ]
                    }
                  ]
                }
                """);

        service.evaluateExecution(101L);

        verify(alertRepository).save(any(Alert.class));
        verify(historyRepository).save(any(AlertHistory.class));
        verify(evaluationRepository).save(any(AlertRuleEvaluation.class));
    }

    @Test
    void evaluatesCountMatchingConditions() {
        when(rule.getConditionConfig()).thenReturn("""
                {
                  "type":"COUNT_MATCHING",
                  "collectionPath":"data.servers",
                  "fieldPath":"status",
                  "operator":"EQUALS",
                  "threshold":"DOWN",
                  "countOperator":"GREATER_THAN_OR_EQUAL",
                  "countThreshold":2,
                  "consecutiveMatches":1
                }
                """);
        MonitoringResult result = mock(MonitoringResult.class);
        when(result.getMessage()).thenReturn("""
                {"data":{"servers":[{"name":"a","status":"DOWN"},{"name":"b","status":"UP"},{"name":"c","status":"DOWN"}]}}
                """);
        when(result.getStatus()).thenReturn(MonitoringResult.ResultStatus.OK);
        when(result.getValue()).thenReturn("3");
        when(result.getUnit()).thenReturn("servers");
        when(resultRepository.findAllByExecutionId(101L)).thenReturn(List.of(result));

        service.evaluateExecution(101L);

        verify(alertRepository).save(any(Alert.class));
        verify(historyRepository).save(any(AlertHistory.class));
        verify(evaluationRepository).save(any(AlertRuleEvaluation.class));
    }

    @Test
    void evaluatesTotalCollectionCountConditions() {
        when(rule.getConditionConfig()).thenReturn("""
                {
                  "type":"COUNT_TOTAL",
                  "collectionPath":"data.servers",
                  "countOperator":"EQUALS",
                  "countThreshold":3,
                  "consecutiveMatches":1
                }
                """);
        MonitoringResult result = mock(MonitoringResult.class);
        when(result.getMessage()).thenReturn("""
                {"data":{"servers":[{"name":"a"},{"name":"b"},{"name":"c"}]}}
                """);
        when(result.getStatus()).thenReturn(MonitoringResult.ResultStatus.OK);
        when(result.getValue()).thenReturn("3");
        when(result.getUnit()).thenReturn("servers");
        when(resultRepository.findAllByExecutionId(101L)).thenReturn(List.of(result));

        service.evaluateExecution(101L);

        verify(alertRepository).save(any(Alert.class));
        verify(historyRepository).save(any(AlertHistory.class));
        verify(evaluationRepository).save(any(AlertRuleEvaluation.class));
    }

    @Test
    void evaluatesDotPathAcrossNestedJsonArraysUsingActualPayload() {
        when(rule.getConditionConfig()).thenReturn("""
                {"fieldPath":"machines.metrics.value","operator":"GREATER_THAN","threshold":90}
                """);
        MonitoringResult result = mock(MonitoringResult.class);
        when(result.getResultData()).thenReturn("""
                {
                  "schemaVersion":1,
                  "status":"SUCCESS",
                  "data":{
                    "machines":[
                      {"hostname":"host-a","metrics":[{"name":"cpu","value":"unknown"},{"name":"memory","value":95}]},
                      {"hostname":"host-b","metrics":[{"name":"cpu","value":40}]}
                    ]
                  }
                }
                """);
        when(result.getMessage()).thenReturn("Script completed successfully.");
        when(result.getStatus()).thenReturn(MonitoringResult.ResultStatus.OK);
        when(resultRepository.findAllByExecutionId(101L)).thenReturn(List.of(result));

        service.evaluateExecution(101L);

        verify(alertRepository).save(any(Alert.class));
        verify(historyRepository).save(any(AlertHistory.class));
        verify(evaluationRepository).save(any(AlertRuleEvaluation.class));
    }

    @Test
    void rejectsInvalidConditionGroupOperator() {
        when(rule.getConditionConfig()).thenReturn("""
                {
                  "type":"GROUP",
                  "operator":"XOR",
                  "children":[
                    {"type":"CONDITION","fieldPath":"data.cpu.usage","operator":"GREATER_THAN","threshold":85}
                  ]
                }
                """);

        service.evaluateExecution(101L);

        org.mockito.ArgumentCaptor<AlertRuleEvaluation> captor =
                org.mockito.ArgumentCaptor.forClass(AlertRuleEvaluation.class);
        verify(evaluationRepository).save(captor.capture());
        assertEquals(AlertRuleEvaluation.Outcome.ERROR, captor.getValue().getOutcome());
        verify(alertRepository, never()).save(any(Alert.class));
    }

    @Test
    void ignoresManualExecutionForScheduledOnlyRule() {
        when(execution.getTriggerSource()).thenReturn(MonitoringExecution.TriggerSource.MANUAL);

        service.evaluateExecution(101L);

        verify(alertRepository, never()).save(any(Alert.class));
        verify(evaluationRepository, never()).save(any(AlertRuleEvaluation.class));
    }

    @Test
    void recordsEvaluationErrorInsteadOfTreatingMissingFieldAsHealthy() {
        when(rule.getConditionConfig()).thenReturn("""
                {"fieldPath":"data.memory.usage","operator":"GREATER_THAN","threshold":80}
                """);

        service.evaluateExecution(101L);

        org.mockito.ArgumentCaptor<AlertRuleEvaluation> captor =
                org.mockito.ArgumentCaptor.forClass(AlertRuleEvaluation.class);
        verify(evaluationRepository).save(captor.capture());
        assertEquals(AlertRuleEvaluation.Outcome.ERROR, captor.getValue().getOutcome());
        verify(alertRepository, never()).save(any(Alert.class));
    }
}

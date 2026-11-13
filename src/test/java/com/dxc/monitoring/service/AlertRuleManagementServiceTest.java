package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dxc.monitoring.entity.AlertRule;
import com.dxc.monitoring.entity.MonitoringJob;
import com.dxc.monitoring.repository.AlertRuleRepository;
import com.dxc.monitoring.repository.MonitoringJobRepository;
import com.dxc.monitoring.repository.NotificationPolicyRepository;
import com.dxc.monitoring.web.AlertRuleForm;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AlertRuleManagementServiceTest {
    private static final Long JOB_ID = 17L;

    @Mock
    private AlertRuleRepository ruleRepository;
    @Mock
    private MonitoringJobRepository jobRepository;
    @Mock
    private NotificationPolicyRepository notificationPolicyRepository;

    private AlertRuleManagementService service;
    private MonitoringJob job;

    @BeforeEach
    void setUp() {
        service = new AlertRuleManagementService(ruleRepository, jobRepository,
                new ObjectMapper(), notificationPolicyRepository);
        job = new MonitoringJob();
        job.setId(JOB_ID);
        job.setName("API Health");
        job.setExecutionMode(MonitoringJob.ExecutionMode.STANDARD);
    }

    @Test
    void savesNestedOrOfAndGroupsWithoutFlatteningTheExpression() throws Exception {
        stubJobLookup();
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AlertRuleForm form = validForm();
        form.setConditionTreeJson("""
                {
                  "type":"GROUP",
                  "operator":"OR",
                  "children":[
                    {
                      "type":"GROUP",
                      "operator":"AND",
                      "children":[
                        {"type":"CONDITION","fieldPath":"data.cpu","operator":"GREATER_THAN","threshold":"80"},
                        {"type":"CONDITION","fieldPath":"data.memory","operator":"GREATER_THAN","threshold":"85"}
                      ]
                    },
                    {
                      "type":"GROUP",
                      "operator":"AND",
                      "children":[
                        {"type":"CONDITION","fieldPath":"data.status","operator":"EQUALS","threshold":"DOWN"},
                        {"type":"CONDITION","fieldPath":"data.retries","operator":"GREATER_THAN","threshold":"3"}
                      ]
                    }
                  ]
                }
                """);

        AlertRule saved = service.save(form, 5L);
        Map<?, ?> root = new ObjectMapper().readValue(saved.getConditionConfig(), Map.class);
        List<?> groups = (List<?>) root.get("children");

        assertEquals("[{severity}] {ruleName}", saved.getAlertSubject());
        assertEquals("GROUP", root.get("type"));
        assertEquals("OR", root.get("operator"));
        assertEquals(1, root.get("consecutiveMatches"));
        assertEquals(2, groups.size());
        assertEquals("AND", ((Map<?, ?>) groups.get(0)).get("operator"));
        assertEquals("AND", ((Map<?, ?>) groups.get(1)).get("operator"));
        assertEquals(2, ((List<?>) ((Map<?, ?>) groups.get(0)).get("children")).size());
        assertEquals("data.retries", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) groups.get(1)).get("children")).get(1)).get("fieldPath"));
    }

    @Test
    void savesSuccessSeverityForHealthyMonitoringRules() {
        stubJobLookup();
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AlertRuleForm form = validForm();
        form.setSeverity("SUCCESS");

        AlertRule saved = service.save(form, 5L);

        assertEquals(AlertRule.Severity.SUCCESS, saved.getSeverity());
        assertEquals("SUCCESS", service.toForm(saved).getSeverity());
    }

    @Test
    void rejectsGroupsWithFewerThanTwoChildren() {
        stubJobLookup();
        AlertRuleForm form = validForm();
        form.setConditionTreeJson("""
                {
                  "type":"GROUP",
                  "operator":"OR",
                  "children":[
                    {"type":"CONDITION","fieldPath":"data.cpu","operator":"GREATER_THAN","threshold":"80"}
                  ]
                }
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.save(form, 5L));
        assertEquals("Each condition group must contain at least two children.", exception.getMessage());
    }

    @Test
    void restoresNestedConditionTreeWhenEditingAnExistingRule() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setId(29L);
        rule.setMonitoringJob(job);
        rule.setName("Nested CPU rule");
        rule.setDescription("Nested rule test");
        rule.setAlertSubject("[{severity}] {ruleName}");
        rule.setConditionConfig("""
                {
                  "type":"GROUP",
                  "operator":"OR",
                  "consecutiveMatches":2,
                  "children":[
                    {"type":"GROUP","operator":"AND","children":[
                      {"type":"CONDITION","fieldPath":"data.cpu","operator":"GREATER_THAN","threshold":80},
                      {"type":"CONDITION","fieldPath":"data.memory","operator":"GREATER_THAN","threshold":85}
                    ]},
                    {"type":"GROUP","operator":"AND","children":[
                      {"type":"CONDITION","fieldPath":"data.status","operator":"EQUALS","threshold":"DOWN"},
                      {"type":"CONDITION","fieldPath":"data.retries","operator":"GREATER_THAN","threshold":3}
                    ]}
                  ]
                }
                """);
        rule.setRecoveryConfig("""
                {"enabled":true,"fieldPath":"data.cpu","operator":"LESS_THAN_OR_EQUAL","threshold":75,"consecutiveMatches":2}
                """);

        AlertRuleForm form = service.toForm(rule);
        Map<?, ?> root = new ObjectMapper().readValue(form.getConditionTreeJson(), Map.class);
        List<?> groups = (List<?>) root.get("children");

        assertEquals("[{severity}] {ruleName}", form.getAlertSubject());
        assertEquals("CONDITION", form.getConditionType());
        assertEquals(2, form.getConsecutiveMatches());
        assertEquals("OR", root.get("operator"));
        assertEquals(2, groups.size());
        assertEquals("data.memory", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) groups.get(0)).get("children")).get(1)).get("fieldPath"));
    }


    @Test
    void savesAdvancedDetectionAndRecoveryConditionTrees() throws Exception {
        stubJobLookup();
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AlertRuleForm form = validForm();
        form.setConditionTreeJson("""
                {
                  "type":"AGGREGATE",
                  "collectionPath":"data.nodes",
                  "operation":"SUM",
                  "valuePath":"lag",
                  "operator":"GT",
                  "threshold":100
                }
                """);
        form.setRecoveryFieldPath("");
        form.setRecoveryTreeJson("""
                {
                  "type":"COLLECTION",
                  "collectionPath":"data.nodes",
                  "quantifier":"ALL",
                  "condition":{"fieldPath":"state","operator":"EQ","threshold":"UP"}
                }
                """);

        AlertRule saved = service.save(form, 5L);
        Map<?, ?> detection = new ObjectMapper().readValue(saved.getConditionConfig(), Map.class);
        Map<?, ?> recovery = new ObjectMapper().readValue(saved.getRecoveryConfig(), Map.class);

        assertEquals("AGGREGATE", detection.get("type"));
        assertEquals("SUM", detection.get("operation"));
        assertEquals("COLLECTION", recovery.get("type"));
        assertEquals(true, recovery.get("enabled"));
        assertEquals(2, recovery.get("consecutiveMatches"));
    }

    @Test
    void restoresAdvancedDetectionAndRecoveryTreesWhenEditing() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setId(31L);
        rule.setMonitoringJob(job);
        rule.setName("Advanced collection rule");
        rule.setAlertSubject("[{severity}] {ruleName}");
        rule.setConditionConfig("""
                {"type":"GROUP","operator":"AND","consecutiveMatches":1,
                 "children":[
                   {"type":"CONDITION","fieldPath":"data.status","operator":"EQ","threshold":"UP"},
                   {"type":"COLLECTION","collectionPath":"data.nodes","quantifier":"ANY",
                    "condition":{"fieldPath":"state","operator":"EQ","threshold":"DOWN"}}
                 ]}
                """);
        rule.setRecoveryConfig("""
                {"type":"AGGREGATE","collectionPath":"data.nodes","operation":"MAX",
                 "valuePath":"lag","operator":"LTE","threshold":10,"enabled":true,"consecutiveMatches":3}
                """);

        AlertRuleForm form = service.toForm(rule);
        Map<?, ?> detection = new ObjectMapper().readValue(form.getConditionTreeJson(), Map.class);
        Map<?, ?> recovery = new ObjectMapper().readValue(form.getRecoveryTreeJson(), Map.class);
        List<?> children = (List<?>) detection.get("children");

        assertEquals("COLLECTION", ((Map<?, ?>) children.get(1)).get("type"));
        assertEquals("AGGREGATE", recovery.get("type"));
        assertEquals(3, form.getRecoveryConsecutiveMatches());
    }


    @Test
    void savesAndRestoresChannelNeutralPresentationConfiguration() throws Exception {
        stubJobLookup();
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AlertRuleForm form = validForm();
        form.setPresentationConfigJson("""
                {
                  "version":1,
                  "titleTemplate":"[{severity}] {ruleName}",
                  "messageTemplate":"{jobName}: {fieldPath} = {actualValue}",
                  "sections":[
                    {"id":"summary","type":"TEXT","title":"Summary","text":"Current status: {severity}"},
                    {"id":"facts","type":"KEY_VALUE","title":"Details","fields":[
                      {"label":"Cluster","path":"data.cluster.name"},
                      {"label":"Status","path":"response.status"}
                    ]},
                    {"id":"nodes","type":"TABLE","title":"Nodes","sourcePath":"data.nodes",
                     "columns":[{"key":"name","label":"Node"},{"key":"state","label":"State"}],"maxRows":10},
                    {"id":"states","type":"LIST","sourcePath":"data.states","valuePath":"value","maxItems":5},
                    {"id":"payload","type":"JSON","sourcePath":"data.cluster","maxLength":2000}
                  ]
                }
                """);

        AlertRule saved = service.save(form, 5L);
        Map<?, ?> config = new ObjectMapper().readValue(saved.getPresentationConfig(), Map.class);
        assertEquals(1, saved.getConfigVersion());
        assertEquals(5, ((List<?>) config.get("sections")).size());

        AlertRuleForm restored = service.toForm(saved);
        assertEquals(saved.getPresentationConfig(), restored.getPresentationConfigJson());
    }

    @Test
    void updatesExistingRuleWithoutCreatingANewRecordAndTracksUpdater() {
        stubJobLookup();
        AlertRule existing = new AlertRule();
        existing.setId(29L);
        existing.setMonitoringJob(job);
        existing.setCreatedBy(3L);
        when(ruleRepository.findById(29L)).thenReturn(Optional.of(existing));
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleForm form = validForm();
        form.setId(29L);
        form.setName("Updated CPU rule");
        form.setSeverity("WARNING");

        AlertRule saved = service.save(form, 8L);

        assertEquals(29L, saved.getId());
        assertEquals("Updated CPU rule", saved.getName());
        assertEquals(AlertRule.Severity.WARNING, saved.getSeverity());
        assertEquals(3L, saved.getCreatedBy());
        assertEquals(8L, saved.getUpdatedBy());
        verify(ruleRepository).save(existing);
    }

    @Test
    void supportsEveryConfiguredSeverityAndExecutionSourcePolicy() {
        stubJobLookup();
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (AlertRule.Severity severity : AlertRule.Severity.values()) {
            for (AlertRule.ExecutionSourcePolicy source : AlertRule.ExecutionSourcePolicy.values()) {
                AlertRuleForm form = validForm();
                form.setSeverity(severity.name());
                form.setExecutionSourcePolicy(source.name());

                AlertRule saved = service.save(form, 5L);

                assertEquals(severity, saved.getSeverity());
                assertEquals(source, saved.getExecutionSourcePolicy());
            }
        }
    }

    @Test
    void rejectsMovingAnExistingRuleToAnotherMonitoringJob() {
        stubJobLookup();
        MonitoringJob otherJob = new MonitoringJob();
        otherJob.setId(18L);
        AlertRule existing = new AlertRule();
        existing.setId(29L);
        existing.setMonitoringJob(otherJob);
        when(ruleRepository.findById(29L)).thenReturn(Optional.of(existing));

        AlertRuleForm form = validForm();
        form.setId(29L);

        assertThrows(IllegalArgumentException.class, () -> service.save(form, 5L));
    }

    @Test
    void rejectsMissingRuleDuringUpdate() {
        stubJobLookup();
        when(ruleRepository.findById(404L)).thenReturn(Optional.empty());
        AlertRuleForm form = validForm();
        form.setId(404L);

        assertThrows(IllegalArgumentException.class, () -> service.save(form, 5L));
    }

    @Test
    void rejectsMissingOrDisabledNotificationPolicy() {
        stubJobLookup();
        when(notificationPolicyRepository.findById(77L)).thenReturn(Optional.empty());
        AlertRuleForm form = validForm();
        form.setNotificationPolicyId(77L);

        assertThrows(IllegalArgumentException.class, () -> service.save(form, 5L));
    }

    @Test
    void rejectsInvalidRecoveryAndConsecutiveMatchSettings() {
        stubJobLookup();
        AlertRuleForm disabledRecovery = validForm();
        disabledRecovery.setRecoveryEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.save(disabledRecovery, 5L));

        AlertRuleForm invalidMatches = validForm();
        invalidMatches.setConsecutiveMatches(0);
        assertThrows(IllegalArgumentException.class, () -> service.save(invalidMatches, 5L));

        AlertRuleForm invalidRecoveryMatches = validForm();
        invalidRecoveryMatches.setRecoveryConsecutiveMatches(101);
        assertThrows(IllegalArgumentException.class, () -> service.save(invalidRecoveryMatches, 5L));
    }

    private void stubJobLookup() {
        when(jobRepository.findByIdForDetails(JOB_ID)).thenReturn(Optional.of(job));
    }

    private AlertRuleForm validForm() {
        AlertRuleForm form = new AlertRuleForm();
        form.setMonitoringJobId(JOB_ID);
        form.setName("Nested alert rule");
        form.setSeverity("CRITICAL");
        form.setExecutionSourcePolicy("SCHEDULED_ONLY");
        form.setConditionType("CONDITION");
        form.setConsecutiveMatches(1);
        form.setRecoveryFieldPath("data.cpu");
        form.setRecoveryOperator("LESS_THAN_OR_EQUAL");
        form.setRecoveryThreshold("75");
        form.setRecoveryConsecutiveMatches(2);
        return form;
    }
}

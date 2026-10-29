package com.dxc.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

        assertEquals("CONDITION", form.getConditionType());
        assertEquals(2, form.getConsecutiveMatches());
        assertEquals("OR", root.get("operator"));
        assertEquals(2, groups.size());
        assertEquals("data.memory", ((Map<?, ?>) ((List<?>) ((Map<?, ?>) groups.get(0)).get("children")).get(1)).get("fieldPath"));
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

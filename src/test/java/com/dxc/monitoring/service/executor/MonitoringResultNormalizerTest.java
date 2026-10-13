package com.dxc.monitoring.service.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.dxc.monitoring.entity.MonitoringExecution;
import com.dxc.monitoring.entity.MonitoringResult;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class MonitoringResultNormalizerTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MonitoringResultNormalizer normalizer = new MonitoringResultNormalizer(objectMapper);

    @Test
    void parsesDelimitedRowsForWidgetSpecificConfiguration() throws Exception
    {
        String parserConfig = """
                {"type":"DELIMITED","delimiter":"|","headerRow":true}
                """;

        MonitoringResultNormalizer.NormalizedResult normalized = normalize(
                "group|lag\norders|1200\npayments|50", parserConfig);

        Map<String, Object> envelope = objectMapper.readValue(normalized.json(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> data = objectMapper.convertValue(envelope.get("data"),
                new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> rows = objectMapper.convertValue(data.get("rows"),
                new TypeReference<List<Map<String, Object>>>() {});

        assertEquals(2, rows.size());
        assertEquals("orders", rows.get(0).get("group"));
        assertEquals("1200", rows.get(0).get("lag"));
        assertEquals(MonitoringResult.ResultType.TABLE, normalized.resultType());
    }

    @Test
    void parsesKeyValueOutputAndPreservesHealthStatus() throws Exception
    {
        String parserConfig = """
                {"type":"KEY_VALUE","separator":":","ignorePrefixes":["#"]}
                """;

        MonitoringResultNormalizer.NormalizedResult normalized = normalize(
                "status: WARNING\nlag: 1200\n# generated", parserConfig);

        Map<String, Object> envelope = objectMapper.readValue(normalized.json(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> data = objectMapper.convertValue(envelope.get("data"),
                new TypeReference<Map<String, Object>>() {});

        assertEquals("WARNING", data.get("status"));
        assertEquals("1200", data.get("lag"));
        assertEquals(MonitoringResult.ResultStatus.WARNING, normalized.resultStatus());
    }

    @Test
    void extractsConfiguredRegexFields() throws Exception
    {
        String parserConfig = """
                {
                  "type":"REGEX",
                  "fields":[
                    {"key":"group","pattern":"group=(\\\\w+)","group":1,"type":"string","required":true},
                    {"key":"lag","pattern":"lag=(\\\\d+)","group":1,"type":"integer","required":true}
                  ]
                }
                """;

        MonitoringResultNormalizer.NormalizedResult normalized = normalize(
                "group=orders lag=1200", parserConfig);

        Map<String, Object> envelope = objectMapper.readValue(normalized.json(),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> data = objectMapper.convertValue(envelope.get("data"),
                new TypeReference<Map<String, Object>>() {});

        assertEquals("orders", data.get("group"));
        assertEquals(1200, data.get("lag"));
        assertTrue(normalized.json().contains("\"schemaVersion\""));
    }

    private MonitoringResultNormalizer.NormalizedResult normalize(String output, String parserConfig)
    {
        return normalizer.normalize(output, MonitoringExecution.ExecutionStatus.SUCCESS,
                MonitoringResult.ResultStatus.OK, "Completed", null, null, parserConfig);
    }
}

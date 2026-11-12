package com.dxc.monitoring.alert.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PresentationConfigurationValidatorTest {
    private final PresentationConfigurationValidator validator = new PresentationConfigurationValidator();

    @Test
    void normalizesAllSupportedSectionTypes() {
        Map<String, Object> config = validator.normalize(Map.of(
                "version", 1,
                "titleTemplate", "[{severity}] {ruleName}",
                "sections", List.of(
                        Map.of("type", "TEXT", "text", "Status {severity}"),
                        Map.of("type", "KEY_VALUE", "fields", List.of(Map.of("label", "Status", "path", "response.status"))),
                        Map.of("type", "TABLE", "sourcePath", "data.nodes",
                                "columns", List.of(Map.of("key", "name", "label", "Node"))),
                        Map.of("type", "LIST", "sourcePath", "data.states"),
                        Map.of("type", "JSON", "sourcePath", "data.cluster"))));

        assertEquals(1, config.get("version"));
        assertEquals(5, ((List<?>) config.get("sections")).size());
    }

    @Test
    void rejectsUnknownTypesAndInvalidPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.normalize(Map.of("sections", List.of(Map.of("type", "SCRIPT", "text", "bad")))));
        assertThrows(IllegalArgumentException.class,
                () -> validator.normalize(Map.of("sections", List.of(Map.of("type", "JSON", "sourcePath", "data..x")))));
    }

    @Test
    void rejectsDuplicateSectionIds() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.normalize(Map.of("sections", List.of(
                        Map.of("id", "same", "type", "TEXT", "text", "a"),
                        Map.of("id", "same", "type", "TEXT", "text", "b")))));
    }
}

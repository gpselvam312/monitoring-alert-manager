package com.dxc.monitoring.alert.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JsonPathResolverTest {
    private JsonPathResolver resolver;
    private JsonDocument document;

    @BeforeEach
    void setUp() {
        resolver = new JsonPathResolver();
        document = JsonDocument.from(Map.of(
                "status", "CRITICAL",
                "summary", Map.of("count", 2),
                "data", Map.of("groups", Map.of("items", List.of(
                        Map.of("groupId", "orders", "totalLag", 120),
                        Map.of("groupId", "payments", "totalLag", 450)
                )))
        ));
    }

    @Test
    void resolvesPlatformMetadataAndPayloadThroughSeparateNamespaces() {
        assertEquals("CRITICAL", resolver.resolve(document, "response.status").singleValue());
        assertEquals(2, resolver.resolve(document, "response.summary.count").singleValue());
        assertEquals(120, resolver.resolve(document, "data.groups.items.totalLag").values().get(0));
        assertEquals(450, resolver.resolve(document, "data.groups.items.totalLag").values().get(1));
    }

    @Test
    void doesNotFallBackAcrossNamespaces() {
        assertFalse(resolver.resolve(document, "data.status").found());
        assertFalse(resolver.resolve(document, "response.totalLag").found());
    }

    @Test
    void preservesExplicitNullAsAnExistingValue() {
        JsonDocument nullable = JsonDocument.from(Map.of("data", new java.util.HashMap<String, Object>() {{
            put("optional", null);
        }}));

        JsonPathResolver.ResolvedValues result = resolver.resolve(nullable, "data.optional");

        assertTrue(result.found());
        assertEquals(1, result.values().size());
        assertEquals(null, result.singleValue());
    }

    @Test
    void supportsNumericArrayIndexes() {
        assertEquals("payments",
                resolver.resolve(document, "data.groups.items.1.groupId").singleValue());
    }

    @Test
    void supportsLegacyFlatResponsesThroughDataNamespace() {
        JsonDocument legacy = JsonDocument.from(Map.of("cpu", Map.of("usage", 92)));

        assertEquals(92, resolver.resolve(legacy, "data.cpu.usage").singleValue());
        assertEquals(92, resolver.resolve(legacy, "response.cpu.usage").singleValue());
    }

    @Test
    void rejectsUnqualifiedAndMalformedPaths() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(document, "groups.items"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(document, "data..items"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(document, "data.items[0]"));
    }

    @Test
    void reportsMixedTypesAcrossArrayRecords() {
        JsonDocument mixed = JsonDocument.from(Map.of("data", Map.of("items", List.of(
                Map.of("value", 10),
                Map.of("value", "unknown")
        ))));

        JsonPathResolver.ResolvedValues values = resolver.resolve(mixed, "data.items.value");

        assertEquals(JsonValueTypeResolver.ValueType.MIXED,
                new JsonValueTypeResolver().resolve(values));
    }

    @Test
    void enforcesTraversalLimit() {
        JsonPathResolver limited = new JsonPathResolver(2);
        assertThrows(IllegalArgumentException.class,
                () -> limited.resolve(document, "data.groups.items.totalLag"));
    }
}

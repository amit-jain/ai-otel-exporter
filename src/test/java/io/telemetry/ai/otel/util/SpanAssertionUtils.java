package io.telemetry.ai.otel.util;

import io.telemetry.ai.otel.system.TelemetrySystem;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class for span assertions and TestInMemoryExporter setup in tests.
 * This class is designed to work with both Java and Kotlin tests.
 */
public class SpanAssertionUtils {
    private static final Logger logger = LoggerFactory.getLogger(SpanAssertionUtils.class);

    /**
     * Sets up the TestInMemoryExporter for testing.
     * @return The configured TestInMemoryExporter instance
     */
    public static TestInMemorySpanExporter setupTestExporter() {
        // Set common system properties
        System.setProperty("OTLP_EXPORT", "true");
        System.setProperty("OTLP_EXPORTER", "http://localhost:4317"); // gRPC endpoint
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", "500"); // Set a shorter schedule delay for testing

        // Create a new TestInMemorySpanExporter for the test
        TestInMemorySpanExporter testExporter = new TestInMemorySpanExporter();
        logger.info("Created new TestInMemorySpanExporter instance: {}", testExporter);

        // Register the exporter with TelemetrySystem
        TelemetrySystem.registerExporter(testExporter);
        logger.info("Registered TestInMemorySpanExporter with TelemetrySystem");

        return testExporter;
    }

    /**
     * Cleans up the test environment.
     * @param telemetryConfig The TelemetrySystem instance to shut down
     * @param testExporter The TestInMemorySpanExporter instance to clear
     */
    public static void cleanupTestExporter(TelemetrySystem telemetryConfig, TestInMemorySpanExporter testExporter) {
        // Wait for spans to be exported
        logger.info("Waiting for spans to be exported before cleanup...");
        if (telemetryConfig != null) {
            telemetryConfig.shutdown();
        }

        // Log the current state of the TestInMemorySpanExporter for diagnostic purposes
        if (testExporter != null) {
            logger.info("TestInMemorySpanExporter state before cleanup: {}", testExporter.getDiagnosticReport());
            testExporter.clear();
            logger.info("Cleared TestInMemorySpanExporter state");
        }

        // Clear common system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("OTEL_BSP_SCHEDULE_DELAY");

        // Clear registered exporters
        TelemetrySystem.clearRegisteredExporters();

        logger.info("Test cleanup complete");
    }

    /**
     * Wait for spans to be exported.
     * @param timeMillis The time to wait in milliseconds
     */
    public static void waitForSpanExport(long timeMillis) {
        try {
            logger.info("Adding a short delay to ensure spans are fully exported...");
            Thread.sleep(timeMillis);
            logger.info("Delay completed");
        } catch (InterruptedException e) {
            logger.error("Sleep interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Logs all exported spans for debugging.
     * @param exportedSpans The list of exported spans
     */
    public static void logExportedSpans(List<SpanData> exportedSpans) {
        logger.info("==== EXPORTED SPANS ====");
        for (SpanData span : exportedSpans) {
            logger.info("Span: {} ({}) Parent: {} Kind: {}",
                    span.getName(),
                    span.getSpanId(),
                    span.getParentSpanContext().getSpanId(),
                    span.getKind());
        }
    }

    /**
     * Logs all parent-child relationships for debugging.
     * @param relationships The map of parent-child relationships
     */
    public static void logParentChildRelationships(Map<String, List<String>> relationships) {
        logger.info("==== PARENT-CHILD RELATIONSHIPS ====");
        for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
            logger.info("Parent: {} -> Children: {}", entry.getKey(), entry.getValue());
        }
    }

    /**
     * Logs all attributes of a span for debugging.
     * @param span The span to log attributes for
     * @param spanName A descriptive name for the span
     */
    public static void logSpanAttributes(SpanData span, String spanName) {
        logger.info("==== {} SPAN ATTRIBUTES ====", spanName.toUpperCase());
        span.getAttributes().forEach((key, value) -> logger.info("{}: {}", key, value));
    }

    /**
     * Verify a span has an attribute with the given key and expected value.
     * @param span The span to verify
     * @param key The attribute key
     * @param expectedValue The expected attribute value
     */
    public static void verifySpanAttribute(SpanData span, String key, String expectedValue) {
        boolean found = false;
        
        for (AttributeKey<?> attrKey : span.getAttributes().asMap().keySet()) {
            if (attrKey.getKey().equals(key)) {
                found = true;
                Object value = span.getAttributes().asMap().get(attrKey);
                if (expectedValue != null) {
                    assertEquals(expectedValue, value.toString(), 
                         "Attribute " + key + " should have expected value");
                }
                logger.info("Verified attribute {} with value {}", key, value);
                break;
            }
        }
        
        assertTrue(found, "Span should have attribute: " + key);
    }
    
    /**
     * Verify a span has an attribute with the given key.
     * @param span The span to verify
     * @param key The attribute key
     */
    public static void verifySpanAttributeExists(SpanData span, String key) {
        boolean found = false;
        
        for (AttributeKey<?> attrKey : span.getAttributes().asMap().keySet()) {
            if (attrKey.getKey().equals(key)) {
                found = true;
                logger.info("Verified attribute {} exists", key);
                break;
            }
        }
        
        assertTrue(found, "Span should have attribute: " + key);
    }
    
    /**
     * Verify parent-child relationships from the exporter.
     * @param relationships The map of parent-child relationships
     * @param spanById Map of spans by their IDs
     */
    public static void verifyParentChildRelationships(Map<String, List<String>> relationships, Map<String, SpanData> spanById) {
        // Verify parent-child relationships
        for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
            String parentId = entry.getKey();
            List<String> childIds = entry.getValue();
            
            SpanData parentSpan = spanById.get(parentId);
            if (parentSpan != null) {
                logger.info("Checking children for parent span: {} ({})", parentSpan.getName(), parentId);
                
                for (String childId : childIds) {
                    SpanData childSpan = spanById.get(childId);
                    if (childSpan != null) {
                        logger.info("  - Child span: {} ({})", childSpan.getName(), childId);
                        
                        // Verify the child's parent context points back to this parent
                        assertEquals(parentId, childSpan.getParentSpanContext().getSpanId(),
                                "Child span's parent ID should match the parent span ID");
                    }
                }
            }
        }
    }

    /**
     * Assert that spans matching the specified criteria exist.
     * @param exportedSpans list of exported spans
     * @param spanNameFilter filter to match span names
     * @param minCount minimum number of spans to expect (default 1)
     */
    public static void assertSpansExist(List<SpanData> exportedSpans, Predicate<String> spanNameFilter, int minCount) {
        List<SpanData> matchingSpans = exportedSpans.stream()
                .filter(span -> spanNameFilter.test(span.getName()))
                .collect(Collectors.toList());
        
        assertFalse(matchingSpans.isEmpty(), "No spans found matching the filter");
        assertTrue(matchingSpans.size() >= minCount, 
                String.format("Expected at least %d matching spans, but found %d", minCount, matchingSpans.size()));
        
        logger.info("Found {} spans matching the filter", matchingSpans.size());
        logExportedSpans(matchingSpans);
    }

    /**
     * Assert that spans with the exact name exist.
     * @param exportedSpans list of exported spans
     * @param spanName exact span name to match
     * @param minCount minimum number of spans to expect (default 1)
     */
    public static void assertSpansWithNameExist(List<SpanData> exportedSpans, String spanName, int minCount) {
        assertSpansExist(exportedSpans, name -> name.equals(spanName), minCount);
    }

    /**
     * Assert that spans with names containing the specified text exist.
     * @param exportedSpans list of exported spans
     * @param spanNamePart text that the span name should contain
     * @param minCount minimum number of spans to expect (default 1)
     */
    public static void assertSpansWithNameContainingExist(List<SpanData> exportedSpans, String spanNamePart, int minCount) {
        assertSpansExist(exportedSpans, name -> name.contains(spanNamePart), minCount);
    }

    /**
     * Get spans with a specific name from the list of exported spans
     * @param exportedSpans list of exported spans
     * @param spanName exact span name to match
     * @return list of matching spans
     */
    public static List<SpanData> getSpansWithName(List<SpanData> exportedSpans, String spanName) {
        return exportedSpans.stream()
                .filter(span -> span.getName().equals(spanName))
                .collect(Collectors.toList());
    }

    /**
     * Get spans containing a specific name part from the list of exported spans
     * @param exportedSpans list of exported spans
     * @param spanNamePart text that the span name should contain
     * @return list of matching spans
     */
    public static List<SpanData> getSpansWithNameContaining(List<SpanData> exportedSpans, String spanNamePart) {
        return exportedSpans.stream()
                .filter(span -> span.getName().contains(spanNamePart))
                .collect(Collectors.toList());
    }

    /**
     * Create a map of spans by their IDs for easier lookups.
     * @param exportedSpans List of exported spans
     * @return Map of spans by their IDs
     */
    public static Map<String, SpanData> createSpanByIdMap(List<SpanData> exportedSpans) {
        Map<String, SpanData> spanById = new HashMap<>();
        for (SpanData span : exportedSpans) {
            spanById.put(span.getSpanId(), span);
        }
        return spanById;
    }

    /**
     * Assert that a span with the given name has the attribute with the expected value.
     * This is a convenience method that combines finding the span and checking its attribute.
     * 
     * @param exportedSpans list of exported spans
     * @param spanName name of the span to check
     * @param attributeKey attribute key to check
     * @param expectedValue expected value of the attribute
     */
    public static void assertSpanAttributeValue(List<SpanData> exportedSpans, String spanName, 
                                              String attributeKey, String expectedValue) {
        List<SpanData> spans = getSpansWithName(exportedSpans, spanName);
        assertFalse(spans.isEmpty(), "No spans found with name: " + spanName);
        
        SpanData span = spans.getFirst();
        verifySpanAttribute(span, attributeKey, expectedValue);
    }
} 
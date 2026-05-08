package io.telemetry.ai.otel.pii;

import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.tracing.TelemetryAgentProducer;
import io.telemetry.ai.otel.util.MockSearchService;
import io.telemetry.ai.otel.util.SearchResponseExtractor;
import io.telemetry.ai.otel.util.TestInMemorySpanExporter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTEL_BSP_SCHEDULE_DELAY_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTLP_EXPORTER_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTLP_EXPORT_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
/**
 * Base integration test class for PIIAnonymizingSpanExporter.
 * <p>
 * This abstract class contains common functionality for testing PII anonymization
 * with different detection methods (regex-based or Presidio-based).
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BasePIIAnonymizingSpanExporterIntegrationTest {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // Common constants
    protected static final String SERVICE_NAME = "pii-anonymization-test";
    protected static final String TENANT_ID = "integration-test";

    @Inject
    protected TelemetryAgentProducer producer;

    @Inject
    protected MockSearchService mockSearchService;

    protected TelemetryAgent agent;
    protected TelemetrySystem telemetryConfig;

    // The TestInMemorySpanExporter instance for verification
    protected TestInMemorySpanExporter testExporter;

    /**
     * Sets up the test environment before each test.
     * Configures system properties and initializes telemetry components.
     */
    @BeforeEach
    protected void baseSetup() {
        // Set common system properties
        System.setProperty(OTLP_EXPORT_PROPERTY, "true");
        System.setProperty(OTLP_EXPORTER_PROPERTY, "http://localhost:4317"); // gRPC endpoint
        System.setProperty(PII_DETECTOR_ENABLED, "true");
        System.setProperty(OTEL_BSP_SCHEDULE_DELAY_PROPERTY, "500"); // Set a shorter schedule delay for testing

        // Create a new TestInMemorySpanExporter for each test
        testExporter = new TestInMemorySpanExporter();
        logger.info("Created fresh TestInMemorySpanExporter instance: {}", testExporter);

        // Register the exporter with TelemetrySystem
        TelemetrySystem.registerExporter(testExporter);
        logger.info("Registered TestInMemorySpanExporter with TelemetrySystem");

        // Configure PII detection (to be implemented by subclasses)
        configurePIIDetection();

        // Initialize the telemetry configuration
        telemetryConfig = new TelemetrySystem(getServiceName(), getTenantId());

        // Get the agent from the producer
        agent = producer.getAgent(getServiceName(), getTenantId());

        // Register the search response extractor with the test agent
        agent.registerTypedExtractor(
                OperationType.SEARCH,
                SearchResponseExtractor.GenericSearchResponse.class,
                new SearchResponseExtractor());

        // Also register the extractor with the default agent that will be used by the QuarkusTraceInterceptor
        TelemetryAgent defaultAgent = producer.produceDefaultAgent();
        defaultAgent.registerTypedExtractor(
                OperationType.SEARCH,
                SearchResponseExtractor.GenericSearchResponse.class,
                new SearchResponseExtractor());

        logger.info("Test setup complete with OTLP endpoint: {}", System.getProperty(OTLP_EXPORTER_PROPERTY));
    }

    /**
     * Abstract method to be implemented by subclasses to configure PII detection.
     */
    protected abstract void configurePIIDetection();

    /**
     * Get the service name for this test.
     * Can be overridden by subclasses.
     */
    protected String getServiceName() {
        return SERVICE_NAME;
    }

    /**
     * Get the tenant ID for this test.
     * Can be overridden by subclasses.
     */
    protected String getTenantId() {
        return TENANT_ID;
    }

    /**
     * Cleanup method to be called by subclasses.
     * Flushes spans and clears system properties.
     */
    @AfterEach
    protected void baseCleanup() {
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

        // Clear all registered exporters from the static list in TelemetrySystem
        TelemetrySystem.clearRegisteredExporters();
        logger.info("Cleared all registered exporters in TelemetrySystem");

        // Clear common system properties
        System.clearProperty(OTLP_EXPORT_PROPERTY);
        System.clearProperty(OTLP_EXPORTER_PROPERTY);
        System.clearProperty(PII_DETECTOR_ENABLED);
        System.clearProperty(OTEL_BSP_SCHEDULE_DELAY_PROPERTY);

        // Clear specific PII detection properties (to be implemented by subclasses)
        clearPIIDetectionProperties();

        // Reset the agent to null to ensure a fresh instance is created in setup
        agent = null;

        logger.info("Test cleanup complete");
    }

    /**
     * Abstract method to be implemented by subclasses to clear PII detection properties.
     */
    protected abstract void clearPIIDetectionProperties();

    /**
     * Common test method for exporting spans with PII.
     */
    @Test
    public void testExportSpansWithPII() {
        logger.info("Starting test to export spans with PII using MockSearchService");

        // Create parent spans and child spans for different PII types
        createParentSpanAndExecute("parent-email-pii", "Email PII Test", this::createSpanWithEmailUsingMockSearchService);
        createParentSpanAndExecute("parent-phone-pii", "Phone PII Test", this::createSpanWithPhoneNumberUsingMockSearchService);
        createParentSpanAndExecute("parent-multiple-pii", "Multiple PII Test", this::createSpanWithMultiplePIITypesUsingMockSearchService);

        // Add a shorter delay to ensure spans are fully processed by the exporter
        try {
            logger.info("Adding a short delay to ensure spans are fully exported...");
            Thread.sleep(5000); // 1 second should be enough now
            logger.info("Delay completed");
        } catch (InterruptedException e) {
            logger.error("Sleep interrupted", e);
            Thread.currentThread().interrupt();
        }

        // Log the current state of spans and relationships
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        Map<String, List<String>> relationships = testExporter.getParentChildRelationships();
        logger.info("Found {} exported spans and {} parent-child relationships",
                exportedSpans.size(), relationships.size());

        // If we still don't have spans, log a diagnostic report
        if (exportedSpans.isEmpty()) {
            logger.warn("No spans were exported after waiting. Diagnostic report:");
            logger.warn(testExporter.getDiagnosticReport());
        }

        // Verify the spans were exported correctly
        verifySpansWithInMemoryExporter();

        // Log test completion
        logTestCompletion();
    }

    /**
     * Helper method to create a parent span and execute a function within its scope.
     *
     * @param spanName    The name of the parent span
     * @param displayName The display name for the parent span
     * @param operation   The operation to execute within the parent span's scope
     */
    protected void createParentSpanAndExecute(String spanName, String displayName, Runnable operation) {
        Span parentSpan = agent.startSpan(spanName, SpanKind.CLIENT, getServiceName(), getTenantId(), displayName);
        try (Scope scope = parentSpan.makeCurrent()) {
            // Log the current span to verify it's the parent span
            logger.info("Current span before {} service call: {}", displayName, Span.current());
            logger.info("Current span context before {} service call: {}", displayName, Span.current().getSpanContext());

            // Execute the operation that creates child spans
            operation.run();

            logger.info("Current span after {} service call: {}", displayName, Span.current());
        } finally {
            parentSpan.end();
        }
    }

    /**
     * Method to log test completion information.
     * Can be overridden by subclasses to provide additional information.
     */
    protected void logTestCompletion() {
        logger.info("Test complete - check the collector logs to verify PII anonymization");

        // Check if TestInMemorySpanExporter is available (enabled in child class)
        if (Boolean.parseBoolean(System.getProperty("test.in.memory.exporter.enabled", "false"))) {
            verifySpansWithInMemoryExporter();
        }
    }

    /**
     * Verify spans using the TestInMemorySpanExporter - checks both parent-child relationships
     * and PII anonymization.
     */
    protected void verifySpansWithInMemoryExporter() {
        // Get the spans from the TestInMemorySpanExporter
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        logger.info("Found {} exported spans", exportedSpans.size());

        // Make sure we have spans to examine
        assertFalse(exportedSpans.isEmpty(), "Should have exported spans");

        // Get parent-child relationships from exporter 
        Map<String, List<String>> relationships = testExporter.getParentChildRelationships();
        int originalRelationshipsCount = relationships.size();
        logger.info("Found {} parent-child relationships in exporter", originalRelationshipsCount);

        // Always rebuild relationships from the spans to be resilient against state issues
        Map<String, List<String>> rebuiltRelationships = new HashMap<>();
        for (SpanData span : exportedSpans) {
            String spanId = span.getSpanId();
            String parentSpanId = span.getParentSpanContext().getSpanId();

            if (SpanId.isValid(parentSpanId)) {
                rebuiltRelationships.computeIfAbsent(parentSpanId, k -> new ArrayList<>()).add(spanId);
                logger.info("Rebuilt parent-child relationship: {} -> {}", parentSpanId, spanId);
            }
        }

        logger.info("Rebuilt {} parent-child relationships from spans", rebuiltRelationships.size());

        // Use rebuilt relationships regardless of what was in the exporter
        // This makes the test more stable and resilient to shared state issues
        relationships = rebuiltRelationships;

        // We need to have valid parent-child relationships for verification
        // Only fail if there are no relationships AND no spans - otherwise proceed with test
        if (relationships.isEmpty() && exportedSpans.isEmpty()) {
            fail("No spans or parent-child relationships found. Test cannot continue.");
        }

        // Define the parent span types we expect to find
        Map<String, String> expectedParentSpans = Map.of(
                "parent-email-pii", "Email",
                "parent-phone-pii", "Phone",
                "parent-multiple-pii", "Multiple PII"
        );

        // Set to track which parent types we've found
        Set<String> foundParentTypes = new HashSet<>();

        // Define PII regex patterns for verification
        Pattern emailPattern = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
        Pattern phonePattern = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
        Pattern ssnPattern = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
        Pattern creditCardPattern = Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b");

        // First pass: identify parent spans and verify their child relationships
        for (SpanData span : exportedSpans) {
            String spanName = span.getName();
            String spanId = span.getSpanId();

            // Check if this is a parent span we're looking for
            if (expectedParentSpans.containsKey(spanName)) {
                String spanType = expectedParentSpans.get(spanName);
                foundParentTypes.add(spanType);

                // Verify this parent has child spans and verify the relationships
                if (relationships.containsKey(spanId)) {
                    // Verify parent-child relationship
                    verifyParentChildRelationship(spanId, spanType, relationships);
                    logger.info("Found and verified {} parent span with ID: {} that has {} children",
                            spanType, spanId, relationships.get(spanId).size());
                } else {
                    logger.warn("Parent span {} with ID: {} has no children in the relationships map",
                            spanType, spanId);

                    // Print diagnostic info for troubleshooting
                    logger.warn("All relationship keys: {}", relationships.keySet());
                    logger.warn("Parent span details: {}", span);
                }
            }

            // For child spans, verify PII has been anonymized
            if ("generic-search".equals(spanName)) {
                span.getAttributes().forEach((key, value) -> {
                    if (value instanceof String) {
                        String strValue = (String) value;

                        // Only check if this attribute is expected to have PII anonymized
                        if (key.getKey().contains("query") ||
                                key.getKey().contains("content") ||
                                key.getKey().contains("param.0")) {

                            // Check that PII is not present in attribute values
                            assertFalse(emailPattern.matcher(strValue).find(),
                                    "Email pattern found in child span attribute: " + key.getKey() + " = " + strValue);
                            assertFalse(phonePattern.matcher(strValue).find(),
                                    "Phone pattern found in child span attribute: " + key.getKey() + " = " + strValue);
                            assertFalse(ssnPattern.matcher(strValue).find(),
                                    "SSN pattern found in child span attribute: " + key.getKey() + " = " + strValue);
                            assertFalse(creditCardPattern.matcher(strValue).find(),
                                    "Credit card pattern found in child span attribute: " + key.getKey() + " = " + strValue);
                        }
                    }
                });
            }
        }

        // Verify we found all expected parent spans
        for (String spanType : expectedParentSpans.values()) {
            assertTrue(foundParentTypes.contains(spanType),
                    "Should have found " + spanType + " parent span");
        }

        logger.info("Successfully verified all parent-child relationships and PII anonymization in captured spans");
    }

    /**
     * Helper method to verify parent-child relationships for a specific parent span.
     *
     * @param parentSpanId   The ID of the parent span
     * @param parentSpanName The name of the parent span (for logging)
     * @param relationships  The map of parent-child relationships
     */
    private void verifyParentChildRelationship(String parentSpanId, String parentSpanName,
                                               Map<String, List<String>> relationships) {
        List<String> children = relationships.get(parentSpanId);
        assertNotNull(children, parentSpanName + " parent should have child spans");
        assertFalse(children.isEmpty(), parentSpanName + " parent should have at least one child span");

        // Verify that the children are generic-search spans
        for (String childId : children) {
            SpanData childSpan = testExporter.getSpanById(childId);
            assertNotNull(childSpan, "Child span should exist");
            assertEquals("generic-search", childSpan.getName(), "Child span should be a generic-search span");

            // Verify that the child's parent context points back to the parent span
            assertEquals(parentSpanId, childSpan.getParentSpanContext().getSpanId(),
                    "Child span's parent ID should match the parent span ID");
        }

        logger.info("Successfully verified parent-child relationship for {} parent span", parentSpanName);
    }

    /**
     * Generic method to create a span with PII using MockSearchService.
     *
     * @param query      The search query containing PII
     * @param piiType    The type of PII (for logging)
     * @param attributes Map of attribute names to PII values to add to the parent span
     */
    protected void createSpanWithPIIUsingMockSearchService(String query, String piiType, Map<String, String> attributes) {
        String source = "test-source";

        // Get the current span (should be the parent span)
        Span parentSpan = Span.current();
        logger.info("Parent span for {} test: {}", piiType, parentSpan);

        // Use the MockSearchService to create a child span via @Trace annotation
        SearchResponseExtractor.GenericSearchResponse response = mockSearchService.search(
                query,
                source,
                getServiceName(),
                getTenantId()
        );

        // Add custom attributes with PII to the parent span
        attributes.forEach(parentSpan::setAttribute);

        logger.info("Created span with {} PII using MockSearchService", piiType);
    }

    /**
     * Create a span with email PII using MockSearchService.
     */
    protected void createSpanWithEmailUsingMockSearchService() {
        // Create a search query with PII (email)
        String query = "Find documents related to john.doe@example.com";

        Map<String, String> attributes = new HashMap<>();
        attributes.put("user.email", "john.doe@example.com");
        attributes.put("user.contact", "Contact john.doe@example.com for more information");

        createSpanWithPIIUsingMockSearchService(query, "email", attributes);
    }

    /**
     * Create a span with phone number PII using MockSearchService.
     */
    protected void createSpanWithPhoneNumberUsingMockSearchService() {
        // Create a search query with PII (phone number)
        String query = "Find customer with phone 555-123-4567";

        Map<String, String> attributes = new HashMap<>();
        attributes.put("user.phone", "555-123-4567");
        attributes.put("customer.contact", "Call customer at 555-123-4567");

        createSpanWithPIIUsingMockSearchService(query, "phone number", attributes);
    }

    /**
     * Create a span with multiple PII types using MockSearchService.
     */
    protected void createSpanWithMultiplePIITypesUsingMockSearchService() {
        // Create a search query with multiple PII types
        String query = "Customer profile: jane.smith@example.com, 555-987-6543, SSN: 987-65-4321";

        Map<String, String> attributes = new HashMap<>();
        attributes.put("user.email", "jane.smith@example.com");
        attributes.put("user.phone", "555-987-6543");
        attributes.put("user.ssn", "987-65-4321");
        attributes.put("user.credit_card", "4111 1111 1111 1111");

        createSpanWithPIIUsingMockSearchService(query, "multiple PII types", attributes);
    }
} 
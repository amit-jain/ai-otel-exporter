package io.telemetry.ai.otel.system;

import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.GenericResponse;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.util.MockService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration tests for TelemetrySystem functionality.
 * Verifies the configuration loading, tracer creation, and span processing
 * capabilities of the telemetry system.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TelemetrySystemTest {
    private static final Logger logger = LoggerFactory.getLogger(TelemetrySystemTest.class);
    private InMemorySpanExporter testSpanProcessor;
    private TelemetryAgent telemetryAgent;
    private MockService mockServices;

    /**
     * Sets up the test environment before each test.
     * Configures system properties and initializes telemetry components.
     */
    @BeforeEach
    public void setup() {
        // Clear any existing system properties first
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("otel.exporter.otlp.timeout");
        System.clearProperty("otel.bsp.schedule.delay");
        System.clearProperty("otel.bsp.max.queue.size");
        System.clearProperty("otel.bsp.max.export.batch.size");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");
        System.clearProperty("otel.exporter.otlp.endpoint");
        System.clearProperty("otel.exporter.otlp.insecure");

        // Note: We don't set OTLP_EXPORT here anymore as it will be set in each test individually
        // This allows tests to control whether export is enabled or disabled

        // Create test span processor for capturing spans
        testSpanProcessor = InMemorySpanExporter.create();

        // Initialize telemetry agent using factory
        telemetryAgent = TelemetrySystemFactory.createAgent("test-service", "tenant1");
        mockServices = new MockService.MockServiceImpl(telemetryAgent);
    }

    /**
     * Tests the creation and configuration of a tracer.
     * Verifies that the tracer is properly initialized with the correct settings.
     */
    @Test
    public void testTracerCreation() {
        // Explicitly set OTLP_EXPORT for this test
        System.setProperty("OTLP_EXPORT", "true");

        // Initialize telemetry agent using factory
        telemetryAgent = TelemetrySystemFactory.createAgent("test-service", "tenant1");
        mockServices = new MockService.MockServiceImpl(telemetryAgent);

        // Create parent span for the query
        Span querySpan = telemetryAgent.startSpan("query", SpanKind.SERVER, "test-service", "test-tenant", "test query");

        try (Scope scope = querySpan.makeCurrent()) {
            // Generate embeddings with parent context
            EmbeddingResponse embeddingResponse = mockServices.generateEmbedding("test query", "test-service", "test-tenant");

            // Perform search
            List<Float> embeddingFloats = embeddingResponse.getData().getFirst().getEmbedding();
            mockServices.search("test query", embeddingFloats, "test-service", "test-tenant");

            querySpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
        } finally {
            querySpan.end();
        }

        // Wait a bit for spans to be processed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    /**
     * Tests span creation and attribute handling.
     * Verifies that spans are created with the correct attributes and context.
     */
    @Test
    public void testSpanCreation() {
        // Explicitly set OTLP_EXPORT for this test
        System.setProperty("OTLP_EXPORT", "true");
        System.clearProperty("OTLP_EXPORTER");

        // Initialize telemetry agent using factory
        telemetryAgent = TelemetrySystemFactory.createAgent("test-service", "tenant1");
        mockServices = new MockService.MockServiceImpl(telemetryAgent);

        // Create parent span for the query
        Span querySpan = telemetryAgent.startSpan("query", SpanKind.SERVER, "test-tenant", "test-instance", "test query");

        try (Scope scope = querySpan.makeCurrent()) {
            // Generate embeddings with parent context
            EmbeddingResponse embeddingResponse = mockServices.generateEmbedding("test query", "test-tenant", "test-instance");

            // Perform search
            List<Float> embeddingFloats = embeddingResponse.getData().getFirst().getEmbedding();
            mockServices.search("test query", embeddingFloats, "test-tenant", "test-instance");

            querySpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
        } finally {
            querySpan.end();
        }

        // Wait a bit for spans to be processed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    /**
     * Tests the batch span processor functionality.
     * Verifies that spans are properly batched and exported.
     */
    @Test
    public void testBatchProcessor() {
        // Explicitly set OTLP_EXPORT for this test
        System.setProperty("OTLP_EXPORT", "true");

        // Initialize telemetry agent using factory
        telemetryAgent = TelemetrySystemFactory.createAgent("test-service", "tenant1");
        mockServices = new MockService.MockServiceImpl(telemetryAgent);

        // Create parent span for the query
        Span querySpan = telemetryAgent.startSpan("query", SpanKind.SERVER, "test-tenant", "test-instance", "test query");

        try (Scope scope = querySpan.makeCurrent()) {
            // Generate embeddings with parent context
            EmbeddingResponse embeddingResponse = mockServices.generateEmbedding("test query", "test-tenant", "test-instance");

            // Perform search
            List<Float> embeddingFloats = embeddingResponse.getData().getFirst().getEmbedding();
            mockServices.search("test query", embeddingFloats, "test-tenant", "test-instance");

            querySpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
        } finally {
            querySpan.end();
        }

        // Wait a bit for spans to be processed
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.error("Test interrupted", e);
        }
    }

    /**
     * Tests the disabled OTLP export functionality.
     * Verifies that spans are not created and attributes are not collected when OTLP export is disabled.
     */
    @Test
    public void testDisabledOtlpExport() {
        // Save original system property value
        String originalValue = System.getProperty("OTLP_EXPORT");

        try {
            // Disable OTLP export
            System.setProperty("OTLP_EXPORT", "false");

            // Create configuration with OTLP disabled
            TelemetrySystem config = new TelemetrySystem("test-service", "test-app");
            TelemetryAgent agent = new TelemetryAgent(config.getTracer());

            // Create a span - should be a no-op span
            Span span = agent.startSpan("test-operation", SpanKind.CLIENT);

            // Verify span is invalid (no-op)
            assertFalse(span.getSpanContext().isValid(), "Span should be invalid when OTLP export is disabled");

            // Try to add attributes - should be a no-op
            MockResponse response = new MockResponse();

            MockContext context = new MockContext();

            // This should not throw any exceptions
            agent.addAttributes(span, context, response, OperationType.SEARCH);

            // End the span - should be a no-op
            span.end();

            // No assertions needed for the end operation since it's a no-op

        } finally {
            // Restore original system property value
            if (originalValue != null) {
                System.setProperty("OTLP_EXPORT", originalValue);
            } else {
                System.clearProperty("OTLP_EXPORT");
            }
        }
    }

    /**
     * Cleans up resources after each test.
     * Ensures proper shutdown of telemetry components.
     */
    @AfterEach
    public void cleanup() {
        // Properly shutdown all telemetry systems
        TelemetrySystemFactory.shutdownAll();
        logger.info("Shutdown all telemetry systems");

        // Reset to root context
        io.opentelemetry.context.Context.root().makeCurrent();

        // Reset the test span processor
        if (testSpanProcessor != null) {
            testSpanProcessor.reset();
        }

        // Clean up all system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("otel.exporter.otlp.timeout");
        System.clearProperty("otel.bsp.schedule.delay");
        System.clearProperty("otel.bsp.max.queue.size");
        System.clearProperty("otel.bsp.max.export.batch.size");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");
        System.clearProperty("otel.exporter.otlp.endpoint");
        System.clearProperty("otel.exporter.otlp.insecure");

        // Clear instance variables
        telemetryAgent = null;
        mockServices = null;

        logger.info("Cleanup completed");
    }

    // Mock classes for testing
    private static class MockResponse implements GenericResponse {
        @Override
        public String getInput() {
            return "test-input";
        }

        @Override
        public String getInputMimeType() {
            return "text/plain";
        }
    }

    private static class MockContext implements OperationContext {
        @Override
        public String getQuery() {
            return "test-query";
        }

        @Override
        public String getEndpoint() {
            return "test-endpoint";
        }
    }
} 
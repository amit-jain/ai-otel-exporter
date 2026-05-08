package io.telemetry.ai.otel.annotation;

import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.system.TelemetrySystemFactory;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.tracing.TracingProxyFactory;
import io.telemetry.ai.otel.util.MockService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test class for verifying annotation-based tracing functionality.
 * Tests the complete telemetry pipeline including span creation, attribute extraction,
 * and OTLP export for both embedding and search operations.
 */
public class AnnotationBasedTracingTest {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationBasedTracingTest.class);
    private TelemetryAgent telemetryAgent;
    private MockService testService;
    private TelemetrySystem telemetryConfig;

    /**
     * Sets up the test environment before each test.
     * Configures system properties, initializes telemetry components,
     * and creates the test service with tracing capabilities.
     */
    @BeforeEach
    public void setup() {
        // Set OTLP properties
        System.setProperty("OTLP_EXPORT", String.valueOf(true));
        System.setProperty("OTLP_EXPORTER", "http://localhost:4317");
        System.setProperty("TRACING_MAX_SPAN_SIZE_BYTES", String.valueOf(32 * 1024)); // 32KB per span
        System.setProperty("OTEL_EXPORTER_OTLP_TIMEOUT", "5"); // 5 second timeout

        // Set service properties
        System.setProperty("EMBEDDING_ENDPOINT", "http://localhost:4317");
        System.setProperty("SEARCH_SYSTEM", "vector-db-test");

        // Initialize OpenTelemetry using TelemetryConfiguration factory
        telemetryConfig = TelemetrySystemFactory.getConfiguration("test-annotation-service", "tenant1");
        telemetryAgent = TelemetrySystemFactory.createAgent("test-annotation-service", "tenant1");

        // Create proxied test service using MockServiceImpl
        TracingProxyFactory proxyFactory = new TracingProxyFactory(telemetryAgent);
        MockService.MockServiceImpl impl = new MockService.MockServiceImpl(telemetryAgent);

        // Create the proxy around MockServiceImpl
        testService = proxyFactory.createTracingProxy(impl, MockService.class);
    }

    /**
     * Cleans up resources after each test.
     * Ensures proper shutdown of telemetry components.
     */
    @AfterEach
    public void cleanup() {
        logger.info("Cleaning up after test...");

        // Force flush any remaining spans
        if (telemetryConfig != null && telemetryConfig.getTracerProvider() != null) {
            CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
            result.join(5, TimeUnit.SECONDS);
            logger.info("Span flush completed");
        }

        // Shutdown telemetry system for this test
        TelemetrySystemFactory.shutdown("test-annotation-service", "tenant1");

        // Reset to root context
        io.opentelemetry.context.Context.root().makeCurrent();

        // The TestInMemorySpanExporter cleanup code has been removed as this test doesn't use it

        // Ensure all telemetry systems are shut down
        try {
            TelemetrySystemFactory.shutdownAll();
            logger.info("Successfully shut down all telemetry systems");
        } catch (Exception e) {
            logger.warn("Error shutting down telemetry systems", e);
        }

        // Clear system properties that might affect other tests
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("OTEL_BSP_SCHEDULE_DELAY");
        System.clearProperty("TRACING_MAX_SPAN_SIZE_BYTES");
        System.clearProperty("OTEL_EXPORTER_OTLP_TIMEOUT");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");

        logger.info("Cleanup completed");
    }

    /**
     * Tests the complete telemetry pipeline with annotation-based tracing.
     * Verifies that spans are created, attributes are extracted, and data is
     * exported correctly through the OTLP exporter.
     *
     */
    @Test
    public void testOtlpExporterWithAnnotations() {
        String query = "test query 2";
        String testTenantId = "test-tenant-2";
        String testInstanceId = "test-instance-2";

        // Create parent span for the query with tenant context
        Span parentSpan = telemetryAgent.startSpan("query-aspect", SpanKind.CLIENT, testTenantId, testInstanceId);

        try (Scope scope = parentSpan.makeCurrent()) {
            // Generate embeddings - spans created via annotation
            EmbeddingResponse embeddingResponse = testService.generateEmbedding(query, testTenantId, testInstanceId);

            // Perform search - spans created via annotation
            List<Float> embeddingFloats = embeddingResponse.getData().getFirst().getEmbedding();
            testService.search(query, embeddingFloats, testTenantId, testInstanceId);
        } finally {
            telemetryAgent.endSpan(parentSpan, null);
        }

        // Wait for spans to be processed
        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(10, TimeUnit.SECONDS);
        assertTrue(result.isSuccess(), "Timeout waiting for spans to be processed");
    }
} 
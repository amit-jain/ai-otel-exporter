package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.config.TracingLimits;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for SpanBuilder functionality.
 * Tests parameter validation, span creation, and attribute handling.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SpanBuilderTest {
    private Tracer tracer;
    private TracingLimits tracingLimits;
    private InMemorySpanExporter spanExporter;

    @BeforeEach
    void setup() {
        // Set OTLP_EXPORT to true for testing
        System.setProperty("OTLP_EXPORT", "true");

        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = tracerProvider.get("test-tracer");
        tracingLimits = TracingLimits.DEFAULT;
    }

    @AfterEach
    void cleanup() {
        // Clear the span exporter
        if (spanExporter != null) {
            spanExporter.reset();
        }

        // Reset to root context
        io.opentelemetry.context.Context.root().makeCurrent();

        // Clear system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("OTEL_BSP_SCHEDULE_DELAY");

        // Force cleanup of any pending spans
        try {
            io.telemetry.ai.otel.system.TelemetrySystemFactory.shutdownAll();
        } catch (Exception e) {
            // Ignore any exceptions during cleanup
        }
    }

    @Test
    void testNullTracer() {
        assertThrows(IllegalArgumentException.class, () ->
                        new SpanBuilder(null, "test-operation", tracingLimits),
                "Should throw IllegalArgumentException for null tracer"
        );
    }

    @Test
    void testNullOperationName() {
        assertThrows(IllegalArgumentException.class, () ->
                        new SpanBuilder(tracer, null, tracingLimits),
                "Should throw IllegalArgumentException for null operation name"
        );
    }

    @Test
    void testEmptyOperationName() {
        assertThrows(IllegalArgumentException.class, () ->
                        new SpanBuilder(tracer, "", tracingLimits),
                "Should throw IllegalArgumentException for empty operation name"
        );
    }

    @Test
    void testNullTracingLimits() {
        assertThrows(IllegalArgumentException.class, () ->
                        new SpanBuilder(tracer, "test-operation", null),
                "Should throw IllegalArgumentException for null tracing limits"
        );
    }

    @Test
    void testSpanCreationWithMinimalParameters() {
        SpanBuilder builder = new SpanBuilder(tracer, "test-operation", tracingLimits);
        Span span = builder.build();

        assertNotNull(span);
        assertNotEquals(Span.getInvalid(), span);
        span.end();
    }

    @Test
    void testSpanCreationWithAllParameters() {
        SpanBuilder builder = new SpanBuilder(tracer, "test-operation", tracingLimits)
                .setSpanKind(SpanKind.CLIENT)
                .setServiceName("test-span-builder")
                .setTenantId("test-tenant")
                .setQuery("test query");

        Span span = builder.build();

        assertNotNull(span);
        assertNotEquals(Span.getInvalid(), span);
        span.end();

        // Verify span attributes
        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var spanData = spans.getFirst();

        assertEquals("test-tenant", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.TENANT_ID_ATTRIBUTE)));
        assertEquals("test-span-builder", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE)));
        assertEquals("test query", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.INPUT_VALUE)));
        assertEquals("text/plain", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.INPUT_MIME_TYPE)));
    }

    @Test
    void testSpanKindDefaultsToInternal() {
        SpanBuilder builder = new SpanBuilder(tracer, "test-operation", tracingLimits);
        Span span = builder.build();
        span.end();

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals(SpanKind.INTERNAL, spans.getFirst().getKind());
    }

    @Test
    void testRootSpanDefaultsToClient() {
        SpanBuilder builder = new SpanBuilder(tracer, "root-operation", tracingLimits);
        Span span = builder.build();
        span.end();

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals(SpanKind.CLIENT, spans.getFirst().getKind());
    }

    @Test
    void testQuerySpanDefaultsToClient() {
        SpanBuilder builder = new SpanBuilder(tracer, "query-operation", tracingLimits);
        Span span = builder.build();
        span.end();

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals(SpanKind.CLIENT, spans.getFirst().getKind());
    }
} 
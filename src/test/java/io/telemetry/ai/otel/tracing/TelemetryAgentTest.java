package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.config.TelemetryConfigConstants;
import io.telemetry.ai.otel.model.context.LLMOperationContext;
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
 * Unit tests for TelemetryAgent functionality.
 * Tests parameter validation and span management.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TelemetryAgentTest {
    private TelemetryAgent agent;
    private InMemorySpanExporter spanExporter;
    private Tracer tracer;

    @BeforeEach
    void setup() {
        // Set OTLP_EXPORT to true for testing
        System.setProperty("OTLP_EXPORT", "true");

        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        tracer = tracerProvider.get("test-telemetry-agent");
        agent = new TelemetryAgent(tracer);
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
    void testStartSpanNullOperationName() {
        assertThrows(IllegalArgumentException.class, () ->
                        agent.startSpan(null, SpanKind.INTERNAL, "test-telemetry-agent", "test-tenant"),
                "Should throw IllegalArgumentException for null operation name"
        );
    }

    @Test
    void testStartSpanEmptyOperationName() {
        assertThrows(IllegalArgumentException.class, () ->
                        agent.startSpan("", SpanKind.INTERNAL, "test-telemetry-agent", "test-tenant"),
                "Should throw IllegalArgumentException for empty operation name"
        );
    }

    @Test
    void testEndSpanNullSpan() {
        assertThrows(IllegalArgumentException.class, () ->
                        agent.endSpan(null, null),
                "Should throw IllegalArgumentException for null span"
        );
    }

    @Test
    void testStartSpanWithMinimalParameters() {
        Span span = agent.startSpan("test-operation", SpanKind.INTERNAL);
        assertNotNull(span);
        assertNotEquals(Span.getInvalid(), span);
        agent.endSpan(span, null);

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        assertEquals("test-operation", spans.getFirst().getName());
    }

    @Test
    void testStartSpanWithAllParameters() {
        Span span = agent.startSpan(
                "test-operation",
                SpanKind.CLIENT,
                "test-telemetry-agent",
                "test-tenant",
                "test query"
        );

        assertNotNull(span);
        assertNotEquals(Span.getInvalid(), span);
        agent.endSpan(span, null);

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var spanData = spans.getFirst();

        assertEquals("test-operation", spanData.getName());
        assertEquals(SpanKind.CLIENT, spanData.getKind());
        assertEquals("test-tenant", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.TENANT_ID_ATTRIBUTE)));
        assertEquals("test-telemetry-agent", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE)));
        assertEquals("test query", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(OpenInferenceAttributes.INPUT_VALUE)));
    }

    @Test
    void testEndSpanWithError() {
        Span span = agent.startSpan("test-operation", SpanKind.INTERNAL);
        Exception testError = new RuntimeException("Test error");
        agent.endSpan(span, testError);

        var spans = spanExporter.getFinishedSpanItems();
        assertEquals(1, spans.size());
        var spanData = spans.getFirst();

        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, spanData.getStatus().getStatusCode());
        assertEquals("Test error", spanData.getStatus().getDescription());
        assertEquals("java.lang.RuntimeException", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(TelemetryConfigConstants.EXCEPTION_TYPE)));
        assertEquals("Test error", spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(TelemetryConfigConstants.EXCEPTION_MESSAGE)));
        assertNotNull(spanData.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(TelemetryConfigConstants.EXCEPTION_STACKTRACE)));
    }

    @Test
    void testAddAttributesNullSpan() {
        assertThrows(IllegalArgumentException.class, () ->
                        agent.addAttributes(null, null, null, null),
                "Should throw IllegalArgumentException for null span"
        );
    }

    @Test
    void testAddAttributesNullContext() {
        Span span = agent.startSpan("test-operation", SpanKind.INTERNAL);
        assertThrows(IllegalArgumentException.class, () ->
                        agent.addAttributes(span, null, null, null),
                "Should throw IllegalArgumentException for null context"
        );
        agent.endSpan(span, null);
    }

    @Test
    void testAddAttributesNullResponse() {
        Span span = agent.startSpan("test-operation", SpanKind.INTERNAL);
        LLMOperationContext context = LLMOperationContext.builder()
                .query("test")
                .endpoint("test")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                        agent.addAttributes(span, context, null, null),
                "Should throw IllegalArgumentException for null response"
        );
        agent.endSpan(span, null);
    }

    @Test
    void testAddAttributesNullOperationType() {
        Span span = agent.startSpan("test-operation", SpanKind.INTERNAL);
        LLMOperationContext context = LLMOperationContext.builder()
                .query("test")
                .endpoint("test")
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                        agent.addAttributes(span, context, null, null),
                "Should throw IllegalArgumentException for null operation type"
        );
        agent.endSpan(span, null);
    }
} 
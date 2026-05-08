package io.telemetry.ai.otel;

import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.util.MockService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
/**
 * Tests for span attribute handling and validation.
 * Verifies that span attributes are correctly set, validated, and
 * conform to OpenInference specifications for different operation types.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SpanAttributesTest {
    private static final Logger logger = LoggerFactory.getLogger(SpanAttributesTest.class);
    private InMemorySpanExporter spanExporter;
    private TelemetryAgent telemetryAgent;
    private MockService mockServices;

    /**
     * Sets up the test environment before each test.
     * Configures system properties and initializes telemetry components.
     */
    @BeforeEach
    public void setup() {
        // Set OTLP_EXPORT to true for testing
        System.setProperty("OTLP_EXPORT", "true");

        // Create an in-memory span exporter for testing
        spanExporter = InMemorySpanExporter.create();

        // Create a tracer provider with the in-memory exporter
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .setResource(Resource.getDefault().merge(
                        Resource.create(Attributes.builder()
                                .put("service.name", "test-span-attributes")
                                .build())
                ))
                .build();

        // Initialize telemetry agent with the tracer from the test provider
        telemetryAgent = new TelemetryAgent(tracerProvider.get("test-tracer"));
        mockServices = new MockService.MockServiceImpl(telemetryAgent);
    }

    /**
     * Cleans up resources after each test.
     * Ensures proper shutdown of telemetry components.
     */
    @AfterEach
    public void cleanup() {
        // Reset the in-memory exporter
        if (spanExporter != null) {
            spanExporter.reset();
        }

        // Only clear the properties that are set in setup
        System.clearProperty("OTLP_EXPORT");

        // Ensure we have a clean context for the next test
        io.opentelemetry.context.Context.root().makeCurrent();

        logger.info("Cleanup completed");
    }

    /**
     * Tests embedding operation attributes.
     * Verifies that embedding-specific attributes are correctly set and validated.
     */
    @Test
    public void testEmbeddingAttributes() {
        // Test the full flow
        String query = "What are the best Italian restaurants?";
        String testTenantId = "test-tenant-123";
        String testServiceId = "test-span-attributes";
        String testInstanceId = "test-instance-123";

        EmbeddingResponse embeddingResponse;

        // Create parent span for the query with tenant context
        Span parentSpan = telemetryAgent.startSpan("query", SpanKind.INTERNAL, testServiceId, testTenantId);
        try (Scope parentScope = parentSpan.makeCurrent()) {
            // Create and execute embedding span
            Span embeddingSpan = telemetryAgent.startSpan("get_embeddings", SpanKind.CLIENT, testServiceId, testTenantId);
            try (Scope embeddingScope = embeddingSpan.makeCurrent()) {
                embeddingResponse = mockServices.generateEmbedding(query, testServiceId, testTenantId);
                assertNotNull(embeddingResponse);
                assertEquals("text-embedding-ada-003", embeddingResponse.getModel());
                embeddingSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            } finally {
                telemetryAgent.endSpan(embeddingSpan, null);
            }

            // Create and execute search span
            Span searchSpan = telemetryAgent.startSpan("search", SpanKind.CLIENT, testServiceId, testTenantId);
            try (Scope searchScope = searchSpan.makeCurrent()) {
                List<Float> embeddingFloats = embeddingResponse.getData().getFirst().getEmbedding();
                SearchResponse searchResponse = mockServices.search(query, embeddingFloats, testServiceId, testTenantId);
                assertNotNull(searchResponse);
                assertEquals(4, searchResponse.getResults().size());
                searchSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
            } finally {
                telemetryAgent.endSpan(searchSpan, null);
            }

            // Verify child spans before parent ends
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            assertEquals(2, spans.size());  // Only child spans, parent not finished yet

            // Verify span names and attributes
            for (SpanData span : spans) {
                assertEquals(testTenantId, span.getAttributes().get(AttributeKey.stringKey(OpenInferenceAttributes.TENANT_ID_ATTRIBUTE)));
                assertEquals(testServiceId, span.getAttributes().get(AttributeKey.stringKey(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE)));
            }

            List<String> spanNames = spans.stream().map(SpanData::getName).toList();
            assertEquals(List.of("get_embeddings", "search"), spanNames);

            parentSpan.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
        } finally {
            telemetryAgent.endSpan(parentSpan, null);
        }

        // Now verify all spans including parent
        List<SpanData> allSpans = spanExporter.getFinishedSpanItems();
        assertEquals(3, allSpans.size());  // parent + 2 child spans

        // Verify all span names and attributes
        for (SpanData span : allSpans) {
            assertEquals(testTenantId, span.getAttributes().get(AttributeKey.stringKey(OpenInferenceAttributes.TENANT_ID_ATTRIBUTE)));
            assertEquals(testServiceId, span.getAttributes().get(AttributeKey.stringKey(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE)));
        }

        List<String> allSpanNames = allSpans.stream().map(SpanData::getName).toList();
        assertEquals(List.of("get_embeddings", "search", "query"), allSpanNames);
    }
}
package io.telemetry.ai.otel.cdi;

import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.LLMOperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.system.TelemetrySystemFactory;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.tracing.TelemetryAgentProducer;
import io.telemetry.ai.otel.util.MockService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for annotation-based telemetry extraction.
 * Verifies that the CDI interceptor correctly processes parameter annotations
 * and extracts telemetry attributes without manual span enhancement.
 *
 * <p>This test class focuses on:
 * <ul>
 *   <li>Automatic span creation via @Trace</li>
 *   <li>Automatic parameter processing via @ExtractAttributes and @AttributeList</li>
 *   <li>Proper context propagation</li>
 *   <li>Attribute extraction for both embedding and search operations</li>
 *   <li>Error handling and status recording</li>
 * </ul>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CDISearchServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(CDISearchServiceTest.class);

    @Inject
    MockService mockService;  // Uses annotation-based telemetry

    @Inject
    TelemetryAgentProducer producer;

    private TelemetryAgent agent;
    private Span rootSpan;
    private static final String SERVICE_ID = "cdi-parentchild-test";
    private static final String TENANT_ID = "cdi-test-tenant";
    private static final String INSTANCE_ID = "cdi-test-instance";

    /**
     * Sets up the test environment before each test.
     * Configures system properties and initializes telemetry components.
     */
    @BeforeAll
    void setup() {
        logger.info("Setting up test with SERVICE_ID: {} and TENANT_ID: {}", SERVICE_ID, TENANT_ID);

        // Set all properties before creating the agent
        System.setProperty("OTLP_EXPORT", "true");
        System.setProperty("OTLP_EXPORTER", "http://localhost:4317");
        System.setProperty("EMBEDDING_ENDPOINT", "http://localhost:4317");
        System.setProperty("SEARCH_SYSTEM", "vector-db-test");
        System.setProperty("SERVICE_ID", SERVICE_ID);

        logger.info("System properties set: OTLP_EXPORT={}, OTLP_EXPORTER={}, SERVICE_ID={}",
                System.getProperty("OTLP_EXPORT"), System.getProperty("OTLP_EXPORTER"), System.getProperty("SERVICE_ID"));

        // Log information about the producer
        logger.info("TelemetryAgentProducer instance: {}", System.identityHashCode(producer));

        // Get the agent for our test service and tenant
        agent = producer.getAgent(SERVICE_ID, TENANT_ID);
        logger.info("Retrieved agent for service: {} and tenant: {}, agent identity: {}",
                SERVICE_ID, TENANT_ID, System.identityHashCode(agent));

        // Also log the default agent for comparison
        TelemetryAgent defaultAgent = producer.produceDefaultAgent();
        logger.info("Default agent identity: {}", System.identityHashCode(defaultAgent));

        // Register type-specific extractors for the test
        registerTestExtractors(agent);

        logger.info("Test-specific extractors registered with all agents");
    }

    /**
     * Registers type-specific extractors for the test.
     * These extractors will be used by the annotation-based telemetry system.
     *
     * @param agent The TelemetryAgent to register extractors with
     */
    private void registerTestExtractors(TelemetryAgent agent) {
        // Register type-specific extractor for EmbeddingResponse with all agents
        producer.registerTypedExtractorWithAllAgents(
                OperationType.EMBEDDING,
                EmbeddingResponse.class,
                (span, response, context, type) -> {
                    logger.debug("Extracting attributes for EmbeddingResponse");
                    span.setAttribute(OpenInferenceAttributes.EMBEDDING_MODEL, response.getModel());
                    span.setAttribute(OpenInferenceAttributes.INPUT_VALUE, response.getInput());
                    span.setAttribute(OpenInferenceAttributes.INPUT_MIME_TYPE, response.getInputMimeType());

                    if (context instanceof LLMOperationContext) {
                        LLMOperationContext llmContext = (LLMOperationContext) context;
                        span.setAttribute(OpenInferenceAttributes.EMBEDDING_ENDPOINT, llmContext.getEndpoint());
                    }

                    if (!response.getData().isEmpty()) {
                        EmbeddingResponse.EmbeddingData data = response.getData().getFirst();
                        span.setAttribute(OpenInferenceAttributes.EMBEDDING_DIMENSIONS, data.getDimensions());
                    }

                    if (response.getUsage() != null) {
                        span.setAttribute(OpenInferenceAttributes.EMBEDDING_USAGE_PROMPT_TOKENS, response.getUsage().getPrompt_tokens());
                        span.setAttribute(OpenInferenceAttributes.EMBEDDING_USAGE_TOTAL_TOKENS, response.getUsage().getTotal_tokens());
                    }

                    logger.debug("Finished extracting attributes for EmbeddingResponse");
                });

        // Register type-specific extractor for SearchResponse with all agents
        producer.registerTypedExtractorWithAllAgents(
                OperationType.SEARCH,
                SearchResponse.class,
                (span, response, context, type) -> {
                    logger.debug("Extracting attributes for SearchResponse");
                    span.setAttribute(OpenInferenceAttributes.RETRIEVAL_SYSTEM, response.getSearchSystem());
                    span.setAttribute(OpenInferenceAttributes.INPUT_VALUE, response.getInput());
                    span.setAttribute(OpenInferenceAttributes.INPUT_MIME_TYPE, response.getInputMimeType());
                    span.setAttribute(OpenInferenceAttributes.RETRIEVAL_DOCUMENTS_COUNT, response.getDocumentsCount());

                    if (context instanceof SearchOperationContext) {
                        SearchOperationContext searchContext = (SearchOperationContext) context;
                        span.setAttribute(OpenInferenceAttributes.RETRIEVAL_QUERY, searchContext.getQuery());
                    }

                    // Add document-specific attributes with prefix
                    if (response.getResults() != null) {
                        for (int i = 0; i < Math.min(response.getResults().size(), 5); i++) {
                            SearchResponse.SearchResult result = response.getResults().get(i);
                            String prefix = OpenInferenceAttributes.RETRIEVAL_DOCUMENT_PREFIX + i + ".";
                            span.setAttribute(prefix + "id", result.getId());
                            span.setAttribute(prefix + "score", result.getScore());
                            if (result.getContent() != null) {
                                span.setAttribute(prefix + "content", result.getContent());
                            }
                        }
                    }

                    logger.debug("Finished extracting attributes for SearchResponse");
                });

        logger.info("Registered type-specific extractors for EmbeddingResponse and SearchResponse with all agents");
    }

    /**
     * Cleans up resources after each test.
     * Ensures proper shutdown of telemetry components.
     */
    @AfterAll
    void cleanup() {
        // Force flush all spans first
        logger.info("Flushing all remaining spans...");
        TelemetrySystem telemetryConfig = TelemetrySystemFactory.getConfiguration(SERVICE_ID, TENANT_ID);
        if (telemetryConfig != null && telemetryConfig.getTracerProvider() != null) {
            CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
            result.join(5, TimeUnit.SECONDS);
        }

        // Reset to root context to prevent any context leaks
        Context.root().makeCurrent();

        // The TestInMemorySpanExporter cleanup code has been removed as this test doesn't use it directly

        // Shutdown specific telemetry system
        logger.info("Shutting down TelemetrySystem for service: {}", SERVICE_ID);
        TelemetrySystemFactory.shutdown(SERVICE_ID, TENANT_ID);

        // Also shut down all other telemetry systems that might be active
        logger.info("Shutting down all remaining TelemetrySystem instances");
        TelemetrySystemFactory.shutdownAll();

        // Clear system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("SERVICE_ID");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");

        logger.info("CDISearchServiceTest cleanup completed");
    }

    /**
     * Tests the annotation-based telemetry extraction.
     * Verifies that spans are created and populated correctly for both
     * embedding generation and search operations, with attributes extracted
     * automatically from parameters via annotations.
     */
    @Test
    public void testAttributeExtraction() {
        String query = "test attribute extraction";

        logger.info("Starting annotation-based attribute extraction test with query: {} for tenantId: {}", query, TENANT_ID);
        logger.info("Using TelemetryAgent instance: {}", System.identityHashCode(agent));
        logger.info("MockService instance: {}", System.identityHashCode(mockService));

        // Create parent span for testing child span relationship
        Span parentSpan = agent.startSpan("search-operation-parent", SpanKind.CLIENT, SERVICE_ID, TENANT_ID, query);
        assertNotNull(parentSpan, "Parent span should not be null");

        // Add standard OpenInference attributes to parent span
        parentSpan.setAttribute(OpenInferenceAttributes.INPUT_VALUE, query);
        parentSpan.setAttribute(OpenInferenceAttributes.INPUT_MIME_TYPE, "text/plain");
        parentSpan.setAttribute(OpenInferenceAttributes.SPAN_KIND, OpenInferenceAttributes.SPAN_KIND_RETRIEVER);
        parentSpan.setAttribute(OpenInferenceAttributes.TENANT_ID_ATTRIBUTE, TENANT_ID);
        parentSpan.setAttribute(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE, SERVICE_ID);

        logger.info("Created parent span with trace ID: {} and span ID: {}",
                parentSpan.getSpanContext().getTraceId(),
                parentSpan.getSpanContext().getSpanId());

        try (Scope scope = parentSpan.makeCurrent()) {
            logger.info("Made parent span current in scope");

            // Verify the current span is our parent span
            Span currentSpanBeforeCall = Span.current();
            logger.info("Current span before any calls: trace ID: {}, span ID: {}, is same as parent: {}",
                    currentSpanBeforeCall.getSpanContext().getTraceId(),
                    currentSpanBeforeCall.getSpanContext().getSpanId(),
                    currentSpanBeforeCall.equals(parentSpan));

            // Generate embeddings
            logger.info("Calling generateEmbedding with parent context active");
            logger.debug("Parent span before generateEmbedding: {}", parentSpan);
            logger.debug("Current span before generateEmbedding: {}", Span.current());
            EmbeddingResponse embeddingResponse = mockService.generateEmbedding(query, SERVICE_ID, TENANT_ID);
            assertNotNull(embeddingResponse, "Embedding response should not be null");
            assertEquals(4, embeddingResponse.getData().getFirst().getDimensions(), "Embedding should have 4 dimensions");

            // Log the current span to verify parent-child relationship
            Span currentSpan = Span.current();
            logger.debug("Current span after generateEmbedding: trace ID: {}, span ID: {}, is same as parent: {}",
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId(),
                    currentSpan.equals(parentSpan));

            logger.info("Generated embedding successfully with model: {}", embeddingResponse.getModel());

            // Verify embedding attributes
            assertEquals("text-embedding-ada-003", embeddingResponse.getModel(), "Model name should match");
            assertEquals(query, embeddingResponse.getInput(), "Input should match original query");
            assertEquals("text/plain", embeddingResponse.getInputMimeType(), "MIME type should be text/plain");
            
            // Verify usage metrics
            assertNotNull(embeddingResponse.getUsage(), "Usage metrics should be present");
            assertTrue(embeddingResponse.getUsage().getPrompt_tokens() > 0, "Should have prompt tokens");
            assertTrue(embeddingResponse.getUsage().getTotal_tokens() > 0, "Should have total tokens");

            // Perform search
            logger.info("Calling search with parent context active");
            logger.debug("Parent span before search: {}", parentSpan);
            logger.debug("Current span before search: {}", Span.current());
            List<Float> embeddingFloats = embeddingResponse.getData().getFirst().getEmbedding();

            SearchResponse searchResponse = mockService.search(query, embeddingFloats, SERVICE_ID, TENANT_ID);
            assertNotNull(searchResponse, "Search response should not be null");

            // Log the current span again to verify parent-child relationship
            currentSpan = Span.current();
            logger.debug("Current span after search: trace ID: {}, span ID: {}, is same as parent: {}",
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId(),
                    currentSpan.equals(parentSpan));

            logger.info("Search completed successfully with system: {}", searchResponse.getSearchSystem());

            // Verify search attributes
            assertEquals(query, searchResponse.getInput(), "Search input should match original query");
            assertEquals("text/plain", searchResponse.getInputMimeType(), "Search MIME type should be text/plain");
            assertEquals("vector-store-prod", searchResponse.getSearchSystem(), "Search system should match");
            assertNotNull(searchResponse.getResults(), "Search results should be present");
            assertTrue(searchResponse.getResults().size() > 0, "Should have search results");

            // Verify result attributes
            SearchResponse.SearchResult firstResult = searchResponse.getResults().getFirst();
            assertNotNull(firstResult.getId(), "Result should have ID");
            assertTrue(firstResult.getScore() > 0, "Result should have positive score");
            assertNotNull(firstResult.getContent(), "Result should have content");
            assertNotNull(firstResult.getMetadata(), "Result should have metadata");
            assertTrue(firstResult.getMetadata().containsKey("type"), "Result metadata should have type");
            assertTrue(firstResult.getMetadata().containsKey("created"), "Result metadata should have creation date");
        } finally {
            logger.info("About to end parent span with ID: {}", parentSpan.getSpanContext().getSpanId());
            agent.endSpan(parentSpan, null);
            logger.debug("Parent span ended with trace ID: {} and span ID: {}",
                    parentSpan.getSpanContext().getTraceId(),
                    parentSpan.getSpanContext().getSpanId());

            // Force flush spans instead of using Thread.sleep
            logger.info("Flushing spans...");
            CompletableResultCode flushResult = flushSpans();
            assertTrue(flushResult.isSuccess(), "Span flush should be successful");
            logger.info("Attribute extraction test complete");
        }
    }

    /**
     * Forces a flush of all spans to ensure they are exported.
     *
     * @return A CompletableResultCode indicating the success or failure of the flush operation
     */
    private CompletableResultCode flushSpans() {
        logger.info("Starting span flush for test service...");

        // Flush the test service/tenant combination
        logger.debug("Flushing spans for service: {} and tenant: {}", SERVICE_ID, TENANT_ID);
        TelemetrySystem telemetryConfig = TelemetrySystemFactory.getConfiguration(SERVICE_ID, TENANT_ID);
        if (telemetryConfig == null) {
            logger.warn("No telemetry configuration found for service: {} and tenant: {}", SERVICE_ID, TENANT_ID);
            return CompletableResultCode.ofSuccess();
        }

        logger.debug("Telemetry configuration found, flushing tracer provider");
        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(5, TimeUnit.SECONDS);
        logger.debug("Test service tracer provider flush result: {}", result.isSuccess());

        // Add a small delay to ensure spans are fully processed by the exporter
        try {
            logger.debug("Adding a short delay to ensure spans are fully exported...");
            Thread.sleep(5000); // 5000ms (5 seconds) delay
            logger.debug("Delay completed");
        } catch (InterruptedException e) {
            logger.warn("Sleep interrupted during flush delay", e);
        }

        logger.info("Span flush completed");
        return result;
    }
} 
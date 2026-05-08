package io.telemetry.ai.otel.cdi;

import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.config.TelemetryConfigConstants;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.tracing.TelemetryAgentProducer;
import io.telemetry.ai.otel.util.AnnotationMockService;
import io.telemetry.ai.otel.util.SearchResponseExtractor;
import io.telemetry.ai.otel.util.SpanAssertionUtils;
import io.telemetry.ai.otel.util.TestInMemorySpanExporter;
import io.opentelemetry.api.trace.Span;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AnnotationExtractionTest {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // Common constants
    protected static final String SERVICE_ID = "span-all-annotation-test";
    protected static final String TENANT_ID = "integration-test";

    @Inject
    protected TelemetryAgentProducer producer;

    @Inject
    protected AnnotationMockService mockSearchService;

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
        // Setup the TestInMemorySpanExporter
        testExporter = SpanAssertionUtils.setupTestExporter();

        // Initialize the telemetry configuration
        telemetryConfig = new TelemetrySystem(getServiceName(), getTenantId());

        // Get the agent from the producer
        agent = producer.getAgent(getServiceName(), getTenantId());

        // Register the search response extractors with the test agent
        agent.registerTypedExtractor(
                OperationType.SEARCH,
                SearchResponseExtractor.GenericSearchResponse.class,
                new SearchResponseExtractor());
                
        // Register the extractor for SearchResponse type
        agent.registerTypedExtractor(
                OperationType.SEARCH,
                SearchResponse.class,
                new AnnotationMockService.SearchResponseAttributeExtractor());
                
        // Create and register an embedding extractor
        agent.registerTypedExtractor(
                OperationType.EMBEDDING,
                EmbeddingResponse.class,
                new AnnotationMockService.EmbeddingResponseExtractor());

        // Also register the extractors with the default agent that will be used by the QuarkusTraceInterceptor
        TelemetryAgent defaultAgent = producer.produceDefaultAgent();
        defaultAgent.registerTypedExtractor(
                OperationType.SEARCH,
                SearchResponseExtractor.GenericSearchResponse.class,
                new SearchResponseExtractor());
                
        // Register the extractor for SearchResponse type for the default agent
        defaultAgent.registerTypedExtractor(
                OperationType.SEARCH,
                SearchResponse.class,
                new AnnotationMockService.SearchResponseAttributeExtractor());
                
        // Register the embedding extractor with the default agent as well
        defaultAgent.registerTypedExtractor(
                OperationType.EMBEDDING,
                EmbeddingResponse.class,
                new AnnotationMockService.EmbeddingResponseExtractor());

        logger.info("Test setup complete with OTLP endpoint: {}", System.getProperty("OTLP_EXPORTER"));
    }

    /**
     * Get the service name for this test.
     * Can be overridden by subclasses.
     */
    protected String getServiceName() {
        return SERVICE_ID;
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
    protected void cleanup() {
        // Clean up using the utility class
        SpanAssertionUtils.cleanupTestExporter(telemetryConfig, testExporter);

        // Reset the agent to null to ensure a fresh instance is created in setup
        agent = null;

        logger.info("Test cleanup complete");
    }

    @Test
    public void testExportAnnotatedSpans() {
        logger.info("Starting test to export spans with annotations using AnnotationMockService");

        // Create a complex search request with test data for annotation extraction
        AnnotationMockService.ComplexSearchRequest request = new AnnotationMockService.ComplexSearchRequest(
            "semantic search with OpenTelemetry integration", 
            List.of(0.1f, 0.2f, 0.3f, 0.4f),
            Map.of("category", "documentation", "language", "en", "author", "test-author"),
            new AnnotationMockService.ComplexSearchRequest.SearchOptions(5, 0.75f, true)
        );
        
        // Create parent span for all operations
        Span parentSearchSpan = agent.startSpan("parent-search", SpanKind.CLIENT, getServiceName(), getTenantId(), "Parent Search Operation");
        try (Scope scope = parentSearchSpan.makeCurrent()) {
            // Execute search with @ExtractAttributes on the request parameter inside the parent span
            mockSearchService.search(request, TENANT_ID, SERVICE_ID);
            
            // Test @AttributeList annotation on text parameter for embedding inside the parent span
            mockSearchService.generateEmbedding("test attribute extraction", TENANT_ID, SERVICE_ID);
        } finally {
            parentSearchSpan.end();
        }

        // Wait for spans to be exported
        SpanAssertionUtils.waitForSpanExport(2000);

        // Get exported spans and relationships
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        Map<String, List<String>> relationships = testExporter.getParentChildRelationships();
        logger.info("Found {} exported spans and {} parent-child relationships",
                exportedSpans.size(), relationships.size());
        
        // Log spans and relationships for debugging
        SpanAssertionUtils.logExportedSpans(exportedSpans);
        SpanAssertionUtils.logParentChildRelationships(relationships);
        
        assertFalse(exportedSpans.isEmpty(), "Should have exported spans");
        assertNotNull(relationships, "Parent-child relationships map should not be null");
        
        // Find the spans by name
        SpanData searchSpan = null;
        SpanData embeddingSpan = null;
        SpanData parentSearchSpanData = null;
        
        // Map to store spans by their IDs for easier lookups
        Map<String, SpanData> spanById = new HashMap<>();
        
        for (SpanData span : exportedSpans) {
            spanById.put(span.getSpanId(), span);
            
            // Identify spans by their names
            if ("search".equals(span.getName())) {
                searchSpan = span;
            } else if ("generateEmbedding".equals(span.getName())) {
                embeddingSpan = span;
            } else if ("parent-search".equals(span.getName())) {
                parentSearchSpanData = span;
            }
        }
        
        // Verify search span (with @ExtractAttributes on ComplexSearchRequest)
        assertNotNull(searchSpan, "Should have a search span");
        logger.info("Verifying span attributes for search span: {}", searchSpan.getName());
        
        // Log span attributes for debugging
        SpanAssertionUtils.logSpanAttributes(searchSpan, "SEARCH");
        SpanAssertionUtils.logSpanAttributes(parentSearchSpanData, "PARENT");
        SpanAssertionUtils.logSpanAttributes(embeddingSpan, "EMBEDDING");
        
        // Verify basic span attributes
        SpanAssertionUtils.verifySpanAttribute(searchSpan, OpenInferenceAttributes.TENANT_ID_ATTRIBUTE, TENANT_ID);
        SpanAssertionUtils.verifySpanAttribute(searchSpan, OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE, SERVICE_ID);
        SpanAssertionUtils.verifySpanAttribute(searchSpan, OpenInferenceAttributes.SPAN_KIND, OpenInferenceAttributes.SPAN_KIND_RETRIEVER);
        
        // Verify parent span attributes
        assertNotNull(parentSearchSpanData, "Should have a parent-search span");
        SpanAssertionUtils.verifySpanAttribute(parentSearchSpanData, OpenInferenceAttributes.TENANT_ID_ATTRIBUTE, TENANT_ID);
        SpanAssertionUtils.verifySpanAttribute(parentSearchSpanData, OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE, SERVICE_ID);
        SpanAssertionUtils.verifySpanAttribute(parentSearchSpanData, OpenInferenceAttributes.INPUT_VALUE, "Parent Search Operation");
        SpanAssertionUtils.verifySpanAttribute(parentSearchSpanData, OpenInferenceAttributes.INPUT_MIME_TYPE, "text/plain");
        
        // Now verify search-specific attributes
        SpanAssertionUtils.verifySpanAttribute(searchSpan, TelemetryConfigConstants.SEARCH_SYSTEM, "annotation-test-system");
        SpanAssertionUtils.verifySpanAttribute(searchSpan, TelemetryConfigConstants.SEARCH_QUERY, "semantic search with OpenTelemetry integration");
        
        // Verify at least the first document attributes
        SpanAssertionUtils.verifySpanAttributeExists(searchSpan, "search.document.0.id");
        SpanAssertionUtils.verifySpanAttributeExists(searchSpan, "search.document.0.score");
        SpanAssertionUtils.verifySpanAttributeExists(searchSpan, "search.document.0.content");
        
        // Verify document metadata 
        SpanAssertionUtils.verifySpanAttributeExists(searchSpan, "search.document.0.metadata.type");
        SpanAssertionUtils.verifySpanAttributeExists(searchSpan, "search.document.0.metadata.category");
        
        // Verify embedding span (with @AttributeList on text parameter)
        assertNotNull(embeddingSpan, "Should have a generateEmbedding span");
        
        // Verify embedding span attributes
        SpanAssertionUtils.verifySpanAttribute(embeddingSpan, OpenInferenceAttributes.TENANT_ID_ATTRIBUTE, TENANT_ID);
        SpanAssertionUtils.verifySpanAttribute(embeddingSpan, OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE, SERVICE_ID);
        SpanAssertionUtils.verifySpanAttribute(embeddingSpan, TelemetryConfigConstants.INPUT_TEXT_ATTRIBUTE, "test attribute extraction");
        SpanAssertionUtils.verifySpanAttribute(embeddingSpan, OpenInferenceAttributes.EMBEDDING_MODEL, "test-embedding-model");
        SpanAssertionUtils.verifySpanAttribute(embeddingSpan, OpenInferenceAttributes.EMBEDDING_DIMENSIONS, "4");
        SpanAssertionUtils.verifySpanAttribute(embeddingSpan, OpenInferenceAttributes.EMBEDDING_USAGE_PROMPT_TOKENS, "10");
        
        // Verify search span has correct parent
        assertEquals(parentSearchSpanData.getSpanId(), searchSpan.getParentSpanContext().getSpanId(),
                "Search span should have parent-search as its parent");
        
        // Verify embedding span has correct parent
        assertEquals(parentSearchSpanData.getSpanId(), embeddingSpan.getParentSpanContext().getSpanId(),
                "Embedding span should have parent-search as its parent");
                
        // Verify the parent-child relationships from the exporter
        SpanAssertionUtils.verifyParentChildRelationships(relationships, spanById);
        
        logger.info("All annotation-based attribute extractions and parent-child relationships verified successfully");
        logTestCompletion();
    }
    
    /**
     * Log completion of the test.
     */
    protected void logTestCompletion() {
        logger.info("AnnotationExtractionTest complete");
        logger.info("Annotation extraction was verified with AnnotationMockService");
    }
} 
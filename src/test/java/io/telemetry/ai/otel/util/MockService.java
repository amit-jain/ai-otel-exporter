package io.telemetry.ai.otel.util;

import io.telemetry.ai.otel.annotation.AttributeList;
import io.telemetry.ai.otel.annotation.QueryText;
import io.telemetry.ai.otel.annotation.ServiceName;
import io.telemetry.ai.otel.annotation.TenantId;
import io.telemetry.ai.otel.annotation.Trace;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.LLMOperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mock service interface for testing telemetry and tracing functionality.
 * Provides test implementations of embedding and search operations with
 * simulated responses and telemetry data collection.
 * 
 * This service uses the annotation-based approach to telemetry extraction:
 * - @Trace marks methods to be traced
 * - @ExtractAttributes marks parameters for attribute extraction
 * - @AttributeList marks list parameters that should be added as attributes
 * - @TenantId and @ServiceName identify special parameters
 */
public interface MockService {
    /**
     * Generates an embedding vector for the given query.
     *
     * @param query       The input text to generate embeddings for
     * @param serviceName The service identifier
     * @param tenantId    The tenant identifier for multi-tenancy support
     * @return An EmbeddingResponse containing the generated embeddings and usage metrics
     */
    @Trace(spanName = "embeddings", spanKind = SpanKind.CLIENT, operationType = OperationType.EMBEDDING,
            responseType = EmbeddingResponse.class)
    EmbeddingResponse generateEmbedding(@QueryText String query, @ServiceName String serviceName, @TenantId String tenantId);

    /**
     * Performs a semantic search using the provided query and embeddings.
     *
     * @param query       The search query text
     * @param embeddings  The embedding vectors to use for search
     * @param serviceName The service identifier
     * @param tenantId    The tenant identifier for multi-tenancy support
     * @return A SearchResponse containing the search results and metadata
     */
    @Trace(spanName = "search", spanKind = SpanKind.CLIENT, operationType = OperationType.SEARCH,
            responseType = SearchResponse.class)
    SearchResponse search(@QueryText String query, @AttributeList(attributeName = "search.embeddings") List<Float> embeddings, @ServiceName String serviceName, @TenantId String tenantId);

    /**
     * Default mock implementation for testing purposes.
     * Provides simulated responses with realistic test data and proper telemetry integration.
     */
    @ApplicationScoped
    class MockServiceImpl implements MockService {
        private final TelemetryAgent agent;

        /**
         * Creates a new MockServiceImpl with the specified telemetry agent.
         *
         * @param agent The telemetry agent to use for tracing operations
         */
        @Inject
        public MockServiceImpl(TelemetryAgent agent) {
            this.agent = agent;
        }

        @Override
        @Trace(
            spanName = "get_embeddings", 
            spanKind = SpanKind.CLIENT, 
            includeParameters = true, 
            responseType = EmbeddingResponse.class, 
            operationType = OperationType.EMBEDDING
        )
        public EmbeddingResponse generateEmbedding(@QueryText String query, @ServiceName String serviceName, @TenantId String tenantId) {
            List<Float> embedding = List.of(0.2f, 0.3f, 0.4f, 0.5f);

            EmbeddingResponse response = EmbeddingResponse.builder()
                    .model("text-embedding-ada-003")
                    .input(query)
                    .inputMimeType("text/plain")
                    .data(List.of(EmbeddingResponse.EmbeddingData.builder()
                            .embedding(embedding)
                            .dimensions(4)
                            .build()))
                    .usage(EmbeddingResponse.Usage.builder()
                            .prompt_tokens(12)
                            .total_tokens(12)
                            .build())
                    .build();

            LLMOperationContext context = LLMOperationContext.builder()
                    .query(query)
                    .endpoint("http://embedding-service:8080")
                    .build();

            agent.addAttributes(Span.current(), context, response, OperationType.EMBEDDING);

            return response;
        }

        @Override
        @Trace(
            spanName = "search", 
            spanKind = SpanKind.CLIENT, 
            includeParameters = true, 
            responseType = SearchResponse.class, 
            operationType = OperationType.SEARCH
        )
        public SearchResponse search(
                @QueryText String query, 
                @AttributeList(attributeName = "search.embeddings") List<Float> embeddings, 
                @ServiceName String serviceName, 
                @TenantId String tenantId) {
            
            List<Map<String, Object>> results = createSearchResults();

            SearchResponse response = SearchResponse.builder()
                    .input(query)
                    .inputMimeType("text/plain")
                    .searchSystem("vector-store-prod")
                    .documentsCount(results.size())
                    .results(results.stream()
                            .map(r -> SearchResponse.SearchResult.builder()
                                    .id((String) r.get("id"))
                                    .score((Float) r.get("score"))
                                    .content((String) r.get("content"))
                                    .metadata((Map<String, String>) r.get("metadata"))
                                    .build())
                            .collect(Collectors.toList()))
                    .build();

            SearchOperationContext context = SearchOperationContext.builder()
                    .searchSystem("vector-store-prod")
                    .query(query)
                    .build();

            agent.addAttributes(Span.current(), context, response, OperationType.SEARCH);

            return response;
        }

        /**
         * Creates a list of mock search results with realistic test data.
         *
         * @return A list of search result maps containing IDs, scores, content, and metadata
         */
        private List<Map<String, Object>> createSearchResults() {
            List<Map<String, Object>> results = new ArrayList<>();
            String[] docIds = {"article-123", "blog-456", "faq-789", "guide-012"};
            float[] scores = {0.98f, 0.85f, 0.76f, 0.72f};
            String[] contents = {
                    "How to use semantic search",
                    "Best practices for vector databases",
                    "Common questions about embeddings",
                    "Implementation guide for LLM applications"
            };

            for (int i = 0; i < docIds.length; i++) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", docIds[i]);
                result.put("score", scores[i]);
                result.put("content", contents[i]);
                result.put("metadata", Map.of(
                        "type", docIds[i].split("-")[0],
                        "created", "2024-01-31"
                ));
                results.add(result);
            }

            return results;
        }
    }
} 
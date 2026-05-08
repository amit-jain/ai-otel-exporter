package io.telemetry.ai.otel.util;

import io.telemetry.ai.otel.annotation.ExtractAttributes;
import io.telemetry.ai.otel.annotation.QueryText;
import io.telemetry.ai.otel.annotation.ServiceName;
import io.telemetry.ai.otel.annotation.TenantId;
import io.telemetry.ai.otel.annotation.Trace;
import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.config.TelemetryConfigConstants;
import io.telemetry.ai.otel.extractor.TypedAttributeExtractor;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Interface defining mock service methods for testing annotation processing
 * and automatic attribute extraction.
 */
public interface AnnotationMockService {
    
    /**
     * Search using a complex request object that contains query, embeddings, and filters.
     * Tests the @ExtractAttributes annotation on a parameter.
     *
     * @param request The search request with query and filters
     * @param tenantId The tenant ID
     * @param serviceName The service name
     * @return SearchResponse with results
     */
    @Trace(spanName = "search", 
           spanKind = SpanKind.CLIENT, 
           operationType = OperationType.SEARCH,
           responseType = SearchResponse.class)
    SearchResponse search(
            @ExtractAttributes ComplexSearchRequest request,
            @TenantId String tenantId,
            @ServiceName String serviceName);
    
    /**
     * Generate embeddings for a text input.
     * Tests the annotation processing for generating embeddings.
     *
     * @param text The text to embed
     * @param tenantId The tenant ID
     * @param serviceName The service name
     * @return EmbeddingResponse with the generated embeddings
     */
    @Trace(spanName = "generateEmbedding", 
           spanKind = SpanKind.CLIENT, 
           operationType = OperationType.EMBEDDING,
           responseType = EmbeddingResponse.class)
    EmbeddingResponse generateEmbedding(
            @QueryText String text,
            @TenantId String tenantId,
            @ServiceName String serviceName);

    /**
     * Complex search request class that demonstrates @ExtractAttributes usage
     */
    @Getter
    class ComplexSearchRequest {
        private String query;
        private List<Float> embeddings;
        private Map<String, String> filters;
        private SearchOptions options;

        public ComplexSearchRequest(String query, List<Float> embeddings) {
            this.query = query;
            this.embeddings = embeddings;
            this.filters = new HashMap<>();
            this.options = new SearchOptions();
        }

        public ComplexSearchRequest(String query, List<Float> embeddings, 
                                   Map<String, String> filters, SearchOptions options) {
            this.query = query;
            this.embeddings = embeddings;
            this.filters = filters;
            this.options = options;
        }

        @Getter
        public static class SearchOptions {
            private int limit = 10;
            private float scoreThreshold = 0.5f;
            private boolean includeMetadata = true;

            public SearchOptions() {
            }

            public SearchOptions(int limit, float scoreThreshold, boolean includeMetadata) {
                this.limit = limit;
                this.scoreThreshold = scoreThreshold;
                this.includeMetadata = includeMetadata;
            }

        }
    }

    /**
     * Implementation of the AnnotationMockService using annotation-based telemetry.
     * This is a standalone class to ensure proper CDI and interceptor functionality.
     */
    @ApplicationScoped
    class AnnotationMockServiceImpl implements AnnotationMockService {
        private static final Logger logger = LoggerFactory.getLogger(AnnotationMockServiceImpl.class);

        @Inject
        private TelemetryAgent agent;

        @Override
        @Trace(spanName = "generateEmbedding",
               spanKind = SpanKind.CLIENT,
               operationType = OperationType.EMBEDDING,
               responseType = EmbeddingResponse.class)
        public EmbeddingResponse generateEmbedding(
                @QueryText String text,
                @TenantId String tenantId,
                @ServiceName String serviceName) {
            if (logger.isDebugEnabled()) {
                logger.debug("Executing generateEmbedding with text: {}, tenantId: {}, serviceName: {}",
                        text, tenantId, serviceName);
            }
            List<Float> embedding = List.of(0.2f, 0.3f, 0.4f, 0.5f);

            // Add artificial delay to simulate processing
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return EmbeddingResponse.builder()
                    .model("test-embedding-model")
                    .input(text)
                    .inputMimeType("text/plain")
                    .data(List.of(EmbeddingResponse.EmbeddingData.builder()
                            .embedding(embedding)
                            .dimensions(4)
                            .build()))
                    .usage(EmbeddingResponse.Usage.builder()
                            .prompt_tokens(10)
                            .total_tokens(10)
                            .build())
                    .build();
        }

        @Override
        @Trace(spanName = "search",
               spanKind = SpanKind.CLIENT,
               operationType = OperationType.SEARCH,
               responseType = SearchResponse.class)
        public SearchResponse search(
                @ExtractAttributes ComplexSearchRequest request,
                @TenantId String tenantId,
                @ServiceName String serviceName) {
            if (logger.isDebugEnabled()) {
                logger.debug("Executing search with request: {}, tenantId: {}, serviceName: {}",
                        request.getQuery(), tenantId, serviceName);
            }

            // Add artificial delay to simulate processing
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Get query components for more realistic search
            String queryText = request.getQuery();
            List<Float> queryEmbeddings = request.getEmbeddings();
            if (logger.isDebugEnabled()) {
                logger.debug("Processing search query: '{}' with embedding dimensions: {}",
                        queryText, queryEmbeddings != null ? queryEmbeddings.size() : 0);
            }

            // Simulate semantic search by calculating cosine similarity with embeddings
            // (just for illustration - not actual vector similarity)
            Map<String, Float> enhancedScores = new HashMap<>();
            if (queryEmbeddings != null && !queryEmbeddings.isEmpty()) {
                // Simulate semantic similarity calculation
                enhancedScores.put("doc-123", 0.98f);
                enhancedScores.put("article-456", 0.92f);
                enhancedScores.put("guide-789", 0.85f);
                enhancedScores.put("faq-012", 0.72f);
            }

            List<Map<String, Object>> results = createSearchResults();

            // Apply vector search boosts if we have embeddings
            if (!enhancedScores.isEmpty()) {
                for (Map<String, Object> result : results) {
                    String id = (String) result.get("id");
                    if (enhancedScores.containsKey(id)) {
                        // Blend lexical and vector scores
                        float lexicalScore = (Float) result.get("score");
                        float vectorScore = enhancedScores.get(id);
                        float blendedScore = (lexicalScore + vectorScore * 2) / 3.0f; // Favor vector score
                        result.put("score", blendedScore);
                        result.put("vector_score", vectorScore);
                        result.put("lexical_score", lexicalScore);
                    }
                }
            }

            // Apply limit from request options
            if (results.size() > request.getOptions().getLimit()) {
                results = results.subList(0, request.getOptions().getLimit());
            }

            // Filter results based on request filters if any
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                results = results.stream()
                    .filter(result -> {
                        @SuppressWarnings("unchecked")
                        Map<String, String> metadata = (Map<String, String>)result.get("metadata");
                        // Check if any filter matches
                        return request.getFilters().entrySet().stream()
                            .anyMatch(entry ->
                                metadata.containsKey(entry.getKey()) &&
                                metadata.get(entry.getKey()).contains(entry.getValue()));
                    })
                    .collect(Collectors.toList());

                if (logger.isDebugEnabled()) {
                    logger.debug("Applied filters: {}. Results remaining: {}",
                            request.getFilters(), results.size());
                }
            }

            // Apply score threshold
            int beforeThreshold = results.size();
            results = results.stream()
                .filter(result -> (Float)result.get("score") >= request.getOptions().getScoreThreshold())
                .collect(Collectors.toList());

            if (beforeThreshold != results.size() && logger.isDebugEnabled()) {
                logger.debug("Applied score threshold {}. Filtered out {} results.",
                        request.getOptions().getScoreThreshold(), beforeThreshold - results.size());
            }

            // Calculate additional search metrics - store in metadata since the SearchResponse API is limited
            long searchTimeMs = 120 + (long)(Math.random() * 50); // Simulate search latency
            int totalMatches = results.size() + 5; // Simulate total matches in index
            boolean hasMoreResults = totalMatches > results.size();
            String queryType = queryEmbeddings != null && !queryEmbeddings.isEmpty() ? "hybrid" : "lexical";

            // Add search metrics to the first result's metadata (since SearchResponse doesn't support these fields directly)
            if (!results.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> firstResultMetadata = (Map<String, String>) results.getFirst().get("metadata");
                firstResultMetadata.put(TelemetryConfigConstants.SEARCH_LATENCY_MS, String.valueOf(searchTimeMs));
                firstResultMetadata.put("search.total_matches", String.valueOf(totalMatches));
                firstResultMetadata.put("search.has_more_results", String.valueOf(hasMoreResults));
                firstResultMetadata.put("search.query_type", queryType);
            }

            return SearchResponse.builder()
                    .input(request.getQuery())
                    .inputMimeType("text/plain")
                    .searchSystem("annotation-test-system")
                    .documentsCount(results.size())
                    .results(results.stream()
                            .map(r -> {
                                @SuppressWarnings("unchecked")
                                Map<String, String> metadata = new HashMap<>((Map<String, String>) r.get("metadata"));

                                // Add vector and lexical scores to metadata if available
                                if (r.containsKey("vector_score")) {
                                    metadata.put("search.vector_score", r.get("vector_score").toString());
                                }
                                if (r.containsKey("lexical_score")) {
                                    metadata.put("search.lexical_score", r.get("lexical_score").toString());
                                }

                                return SearchResponse.SearchResult.builder()
                                    .id((String) r.get("id"))
                                    .score((Float) r.get("score"))
                                    .content((String) r.get("content"))
                                    .metadata(request.getOptions().isIncludeMetadata() ? metadata : null)
                                    .build();
                            })
                            .collect(Collectors.toList()))
                    .build();
        }

        /**
         * Creates test search results with realistic sample data.
         */
        private List<Map<String, Object>> createSearchResults() {
            List<Map<String, Object>> results = new ArrayList<>();

            // Create sample documents
            String[] docIds = {"doc-123", "article-456", "guide-789", "faq-012"};
            float[] scores = {0.95f, 0.87f, 0.78f, 0.65f};
            String[] contents = {
                "This is a test document about annotations and semantic search functionality",
                "Testing annotation-based telemetry extraction with OpenTelemetry and semantic tracing",
                "OpenTelemetry annotations guide for developers building search systems",
                "Frequently asked questions about telemetry, tracing and search observability"
            };

            String[] summaries = {
                "A detailed overview of annotation systems",
                "Implementation guide for telemetry extraction",
                "Developer documentation for OpenTelemetry",
                "FAQ about telemetry implementation"
            };

            String[] categories = {"documentation", "guide", "tutorial", "faq"};

            for (int i = 0; i < docIds.length; i++) {
                Map<String, Object> result = new HashMap<>();
                result.put("id", docIds[i]);
                result.put("score", scores[i]);
                result.put("content", contents[i]);

                // Create rich metadata
                String type = docIds[i].split("-")[0];
                Map<String, String> metadata = new HashMap<>();
                metadata.put("type", type);
                metadata.put("created", "2024-05-15");
                metadata.put("author", "test-author");
                metadata.put("language", "en");
                metadata.put("summary", summaries[i]);
                metadata.put("category", categories[i]);
                metadata.put("word_count", String.valueOf(100 + i * 50));
                metadata.put("source", "sample-docs");

                result.put("metadata", metadata);
                results.add(result);
            }

            return results;
        }
    }

    /**
     * Extractor for embedding response attributes.
     * Extracts model name, input text, and embedding dimensions from embedding responses.
     */
    class EmbeddingResponseExtractor implements TypedAttributeExtractor<EmbeddingResponse, OperationContext> {
        private static final Logger logger = LoggerFactory.getLogger(EmbeddingResponseExtractor.class);

        @Override
        public void extractAttributes(Span span, EmbeddingResponse response, OperationContext context, OperationType operationType) {
            if (response == null) {
                logger.warn("EmbeddingResponse is null, cannot extract attributes");
                return;
            }

            // Set basic attributes from the response
            span.setAttribute(OpenInferenceAttributes.EMBEDDING_MODEL, response.getModel());
            span.setAttribute(TelemetryConfigConstants.INPUT_TEXT_ATTRIBUTE, response.getInput());
            
            // Extract dimensions if available
            if (response.getData() != null && !response.getData().isEmpty()) {
                span.setAttribute(OpenInferenceAttributes.EMBEDDING_DIMENSIONS, response.getData().getFirst().getDimensions());
            }

            // Extract usage information if available
            if (response.getUsage() != null) {
                span.setAttribute(OpenInferenceAttributes.EMBEDDING_USAGE_PROMPT_TOKENS, response.getUsage().getTotal_tokens());
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Extracted embedding attributes - model: {}, input text length: {}",
                        response.getModel(),
                        response.getInput() != null ? response.getInput().length() : 0);
            }
        }

        @Override
        public OperationContext getContext() {
            // Create a simple default context
            return new OperationContext() {
                @Override
                public String getQuery() {
                    // Default query for embedding
                    return "embedding-request";
                }

                @Override
                public String getEndpoint() {
                    return "embedding-service";
                }
            };
        }
    }

    /**
     * Extractor for SearchResponse objects.
     * This extractor handles the standard SearchResponse class return type.
     */
    class SearchResponseAttributeExtractor implements TypedAttributeExtractor<SearchResponse, SearchOperationContext> {
        private static final Logger logger = LoggerFactory.getLogger(SearchResponseAttributeExtractor.class);
        private static final String SEARCH_SYSTEM = "annotation-test-system";

        @Override
        public void extractAttributes(Span span, SearchResponse response, SearchOperationContext context, OperationType operationType) {
            if (logger.isDebugEnabled()) {
                logger.debug("Search response extraction started");
            }

            if (response == null) {
                logger.warn("Cannot extract attributes from null response");
                return;
            }
            
            // Add standard search attributes
            span.setAttribute(TelemetryConfigConstants.SEARCH_SYSTEM, response.getSearchSystem() != null ? response.getSearchSystem() : SEARCH_SYSTEM);
            span.setAttribute(TelemetryConfigConstants.SEARCH_QUERY, response.getInput());
            span.setAttribute(TelemetryConfigConstants.SEARCH_COUNT, response.getDocumentsCount() != null ? response.getDocumentsCount() : 0);

            // Extract custom attributes from context if available
            if (context != null) {
                for (Map.Entry<String, Object> entry : context.getCustomAttributes().entrySet()) {
                    if (entry.getValue() instanceof String) {
                        span.setAttribute(entry.getKey(), (String) entry.getValue());
                        if (logger.isDebugEnabled()) {
                            logger.debug("Set context attribute: {} = {}", entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            // Process search results if available
            if (response.getResults() != null) {
                int resultCount = 0;
                for (SearchResponse.SearchResult result : response.getResults()) {
                    String prefix = "search.document." + resultCount + ".";

                    // Add document ID and score
                    span.setAttribute(prefix + "id", result.getId());
                    span.setAttribute(prefix + "score", result.getScore());

                    // Add content snippet if available (truncated for size)
                    if (result.getContent() != null) {
                        String content = result.getContent();
                        if (content.length() > 100) {
                            content = content.substring(0, 97) + "...";
                        }
                        span.setAttribute(prefix + "content", content);
                    }

                    // Add metadata if available
                    if (result.getMetadata() != null) {
                        for (Map.Entry<String, String> entry : result.getMetadata().entrySet()) {
                            span.setAttribute(prefix + "metadata." + entry.getKey(), entry.getValue());
                            if (logger.isTraceEnabled()) {
                                logger.trace("Added metadata: {} = {}", prefix + "metadata." + entry.getKey(), entry.getValue());
                            }
                        }
                    }

                    resultCount++;
                    if (resultCount >= 5) {
                        // Limit to 5 results to avoid overwhelming the span
                        break;
                    }
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Search response extraction completed");
            }

            // Add the openinference.span.kind attribute for retriever spans
            context.addCustomAttribute(OpenInferenceAttributes.SPAN_KIND, OpenInferenceAttributes.SPAN_KIND_RETRIEVER);
        }

        @Override
        public SearchOperationContext getContext() {
            SearchOperationContext context = SearchOperationContext.builder()
                    .searchSystem("annotation-test-system")
                    .endpoint("search-service")
                    .query("*")
                    .maxResults(10)
                    .includeMetadata(true)
                    .build();

            // Add the openinference.span.kind attribute for retriever spans
            context.addCustomAttribute(OpenInferenceAttributes.SPAN_KIND, OpenInferenceAttributes.SPAN_KIND_RETRIEVER);

            return context;
        }
    }
}
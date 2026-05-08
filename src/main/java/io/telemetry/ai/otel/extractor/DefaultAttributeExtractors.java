package io.telemetry.ai.otel.extractor;

import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.LLMOperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.telemetry.ai.otel.tracing.TelemetryAgent;

/**
 * Default implementations of attribute extractors for different operation types.
 * This class provides standard attribute extraction logic for embedding and search operations,
 * ensuring consistent telemetry data collection across the system.
 */
public class DefaultAttributeExtractors {

    /**
     * Registers all default extractors with the given TelemetryAgent.
     * This method should be called during TelemetryAgent initialization to ensure
     * standard attribute extraction is available for all supported operation types.
     *
     * @param agent The TelemetryAgent to register extractors with
     */
    public static void registerDefaults(TelemetryAgent agent) {
        // Register standard extractors
        agent.registerExtractor(OperationType.EMBEDDING, embeddingExtractor());
        agent.registerExtractor(OperationType.SEARCH, searchExtractor());

        // Register type-specific extractors
        registerTypedExtractors(agent);
    }

    /**
     * Registers all type-specific attribute extractors with the given TelemetryAgent.
     * This method registers extractors that work with specific response types
     * rather than the generic interfaces.
     *
     * @param agent The TelemetryAgent to register extractors with
     */
    private static void registerTypedExtractors(TelemetryAgent agent) {
        /*
         Register type-specific extractors here
         Example:
         agent.registerTypedExtractor(
                 OperationType.SEARCH,
                 Response.class,
                 new ResponseExtractor());
        */
    }

    /**
     * Creates an attribute extractor for embedding operations.
     * Extracts model information, input details, endpoint configuration,
     * embedding dimensions, vector data, and token usage metrics.
     *
     * @return AttributeExtractor for embedding operations
     */
    private static AttributeExtractor<EmbeddingResponse, LLMOperationContext> embeddingExtractor() {
        return (span, context, response, type) -> {
            span.setAttribute(type.getAttributeKey("model"), response.getModel());
            span.setAttribute(type.getAttributeKey("input"), response.getInput());
            span.setAttribute(type.getAttributeKey("endpoint"), context.getEndpoint());

            if (!response.getData().isEmpty()) {
                EmbeddingResponse.EmbeddingData data = response.getData().getFirst();
                span.setAttribute(type.getAttributeKey("dimensions"), data.getDimensions());
                span.setAttribute(type.getAttributeKey("vector"), data.getEmbedding().toString());
            }

            if (response.getUsage() != null) {
                span.setAttribute(type.getAttributeKey("prompt_tokens"), response.getUsage().getPrompt_tokens());
                span.setAttribute(type.getAttributeKey("total_tokens"), response.getUsage().getTotal_tokens());
            }
        };
    }

    /**
     * Creates an attribute extractor for search operations.
     * Extracts search system information, query details, result counts,
     * and individual document metadata including scores and custom attributes.
     *
     * @return AttributeExtractor for search operations
     */
    private static AttributeExtractor<SearchResponse, SearchOperationContext> searchExtractor() {
        return (span, context, response, type) -> {
            span.setAttribute(type.getAttributeKey("system"), response.getSearchSystem());
            span.setAttribute(type.getAttributeKey("query"), response.getInput());
            span.setAttribute(type.getAttributeKey("count"), response.getDocumentsCount());

            // Add document-specific attributes with prefix
            if (response.getResults() != null) {
                for (int i = 0; i < response.getResults().size(); i++) {
                    SearchResponse.SearchResult result = response.getResults().get(i);
                    String prefix = type.getAttributeKey("document_prefix") + i + ".";
                    span.setAttribute(prefix + "id", result.getId());
                    span.setAttribute(prefix + "score", result.getScore());
                    if (result.getMetadata() != null) {
                        result.getMetadata().forEach((key, value) ->
                                span.setAttribute(prefix + "metadata." + key, value));
                    }
                }
            }
        };
    }
} 
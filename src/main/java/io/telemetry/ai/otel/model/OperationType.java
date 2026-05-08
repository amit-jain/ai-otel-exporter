package io.telemetry.ai.otel.model;

import java.util.Map;

import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_DIMENSIONS;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_ENDPOINT;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_INPUT;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_MODEL;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_USAGE_PROMPT_TOKENS;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_USAGE_TOTAL_TOKENS;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.EMBEDDING_VECTOR;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.RETRIEVAL_DOCUMENTS_COUNT;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.RETRIEVAL_DOCUMENT_PREFIX;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.RETRIEVAL_QUERY;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.RETRIEVAL_SYSTEM;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.SEARCH_LATENCY_MS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.SEARCH_SOURCE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.SEARCH_TOTAL_HITS;

/**
 * Enumeration of supported operation types for telemetry tracking.
 * Defines the mapping between logical attribute names and their corresponding
 * OpenInference attribute keys for different types of operations.
 */
public enum OperationType {
    /**
     * Represents embedding generation operations.
     * Maps logical names to OpenInference attribute keys for tracking
     * input text, model information, token usage, and embedding details.
     */
    EMBEDDING(Map.of(
            "input", EMBEDDING_INPUT,
            "endpoint", EMBEDDING_ENDPOINT,
            "model", EMBEDDING_MODEL,
            "prompt_tokens", EMBEDDING_USAGE_PROMPT_TOKENS,
            "total_tokens", EMBEDDING_USAGE_TOTAL_TOKENS,
            "vector", EMBEDDING_VECTOR,
            "dimensions", EMBEDDING_DIMENSIONS
    )),

    /**
     * Represents search operations.
     * Maps logical names to OpenInference attribute keys for tracking
     * search system, query details, result metadata, and performance metrics.
     */
    SEARCH(Map.of(
            "system", RETRIEVAL_SYSTEM,
            "query", RETRIEVAL_QUERY,
            "count", RETRIEVAL_DOCUMENTS_COUNT,
            "document_prefix", RETRIEVAL_DOCUMENT_PREFIX,
            "source", SEARCH_SOURCE,
            "latency_ms", SEARCH_LATENCY_MS,
            "total_hits", SEARCH_TOTAL_HITS
    ));

    /**
     * Mapping from logical attribute names to OpenInference attribute keys
     */
    private final Map<String, String> attributeMappings;

    /**
     * Creates a new OperationType with the specified attribute mappings.
     *
     * @param attributeMappings Map of logical names to OpenInference attribute keys
     */
    OperationType(Map<String, String> attributeMappings) {
        this.attributeMappings = attributeMappings;
    }

    /**
     * Gets the OpenInference attribute key for a logical attribute name.
     *
     * @param logicalName The logical name of the attribute
     * @return The corresponding OpenInference attribute key, or null if not found
     */
    public String getAttributeKey(String logicalName) {
        return attributeMappings.get(logicalName);
    }
} 
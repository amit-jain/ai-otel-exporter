package io.telemetry.ai.otel.model.context;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Context for semantic search operations.
 * Provides specific context information for operations involving
 * search systems, including the search query and system identifier.
 */
@Data
@Builder
public class SearchOperationContext implements OperationContext {
    private final String query;
    private final String endpoint;
    private final String searchSystem;

    // Enhanced fields for search operations
    private final Integer maxResults;
    private final String filter;
    private final String sortBy;
    private final Boolean includeMetadata;

    @Builder.Default
    private final Map<String, Object> customAttributes = new HashMap<>();

    @Override
    public String getEndpoint() {
        return endpoint != null ? endpoint : searchSystem;
    }

    /**
     * Gets the custom attributes map.
     *
     * @return An unmodifiable view of the custom attributes map
     */
    public Map<String, Object> getCustomAttributes() {
        return Collections.unmodifiableMap(customAttributes);
    }

    /**
     * Adds a custom attribute to the context.
     *
     * @param key   The attribute key
     * @param value The attribute value
     * @return This context instance for method chaining
     */
    public SearchOperationContext addCustomAttribute(String key, Object value) {
        customAttributes.put(key, value);
        return this;
    }
} 
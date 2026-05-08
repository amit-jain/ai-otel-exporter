package io.telemetry.ai.otel.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response class for semantic search operations.
 * Contains search results, relevance scores, and metadata
 * about the search operation. This class implements GenericResponse
 * to provide standard input handling capabilities.
 */
@Data
@Builder
public class SearchResponse implements GenericResponse {
    /**
     * List of search results with scores and metadata
     */
    private List<SearchResult> results;
    /**
     * Identifier for the search/retrieval system used
     */
    private String searchSystem;
    /**
     * The original search query text
     */
    private String input;
    /**
     * Number of documents retrieved in the search
     */
    private Integer documentsCount;
    /**
     * Input format (e.g., "text/plain")
     */
    private String inputMimeType;

    /**
     * Container for individual search result data.
     * Holds document information, relevance scores, and metadata
     * for each retrieved document.
     */
    @Data
    @Builder
    public static class SearchResult {
        /**
         * Unique identifier for the retrieved document
         */
        private String id;
        /**
         * Relevance/similarity score for the result
         */
        private float score;
        /**
         * Optional document content or snippet
         */
        private String content;
        /**
         * Optional document metadata key-value pairs
         */
        private Map<String, String> metadata;
    }
} 
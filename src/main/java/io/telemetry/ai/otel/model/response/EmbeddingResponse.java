package io.telemetry.ai.otel.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response class for embedding generation operations.
 * Contains the generated embedding vectors, model information,
 * and token usage statistics. This class implements GenericResponse
 * to provide standard input handling capabilities.
 */
@Data
@Builder
public class EmbeddingResponse implements GenericResponse {
    /**
     * The model used to generate embeddings
     */
    private String model;
    /**
     * The endpoint that generated the embeddings
     */
    private String endpoint;
    /**
     * List of generated embeddings with their metadata
     */
    private List<EmbeddingData> data;
    /**
     * Token usage statistics for the operation
     */
    private Usage usage;
    /**
     * Original input text that was embedded
     */
    private String input;
    /**
     * Input format (e.g., "text/plain")
     */
    private String inputMimeType;

    /**
     * Container for embedding vector data.
     * Holds both the embedding vector and its dimensional information.
     */
    @Data
    @Builder
    public static class EmbeddingData {
        /**
         * The embedding vector values
         */
        private List<Float> embedding;
        /**
         * Number of dimensions in the embedding vector
         */
        private Integer dimensions;
    }

    /**
     * Token usage statistics for the embedding operation.
     * Tracks token consumption for billing and monitoring.
     */
    @Data
    @Builder
    public static class Usage {
        /**
         * Number of tokens in the input prompt
         */
        private int prompt_tokens;
        /**
         * Total number of tokens processed
         */
        private int total_tokens;
    }
} 
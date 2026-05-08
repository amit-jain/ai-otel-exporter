package io.telemetry.ai.otel.model.context;

import lombok.Builder;
import lombok.Data;

/**
 * Context for LLM (Language Learning Model) operations.
 * Provides specific context information for operations involving
 * language models, such as text generation or embeddings.
 */
@Data
@Builder
public class LLMOperationContext implements OperationContext {
    private final String query;
    private final String endpoint;
} 
package io.telemetry.ai.otel.model.context;

import lombok.Builder;
import lombok.Data;

/**
 * Generic context for operations that don't have a specialized context type.
 * Provides basic context information that applies to all operation types.
 */
@Data
@Builder
public class GenericOperationContext implements OperationContext {
    private final String query;
    private final String endpoint;
    private final String operationType;
} 
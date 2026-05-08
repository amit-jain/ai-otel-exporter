package io.telemetry.ai.otel.model.context;

/**
 * Base interface for operation context objects.
 * Defines common attributes that all operations must provide
 * for telemetry data collection.
 */
public interface OperationContext {
    /**
     * Gets the query associated with the operation.
     *
     * @return The query text
     */
    String getQuery();

    /**
     * Gets the endpoint associated with the operation.
     *
     * @return The endpoint URL
     */
    String getEndpoint();
} 
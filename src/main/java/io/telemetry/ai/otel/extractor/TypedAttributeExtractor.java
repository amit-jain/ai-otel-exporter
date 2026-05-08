package io.telemetry.ai.otel.extractor;

import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.opentelemetry.api.trace.Span;

/**
 * Interface for extracting telemetry attributes from specific response types.
 * Provides a type-safe mechanism for operation-specific attribute extraction,
 * allowing different response types to contribute their own telemetry data.
 *
 * @param <T> The specific type of response to extract attributes from
 * @param <C> The type of operation context providing additional data (optional)
 */
@FunctionalInterface
public interface TypedAttributeExtractor<T, C extends OperationContext> {
    /**
     * Extracts attributes from a response object and adds them to a span.
     *
     * @param span          The span to add attributes to
     * @param response      The response object to extract attributes from
     * @param context       The operation context providing additional data (may be null)
     * @param operationType The type of operation being performed
     */
    void extractAttributes(Span span, T response, C context, OperationType operationType);

    /**
     * Convenience method for cases where context is not available.
     * This default implementation calls the main method with a null context.
     *
     * @param span          The span to add attributes to
     * @param response      The response object to extract attributes from
     * @param operationType The type of operation being performed
     */
    default void extractAttributes(Span span, T response, OperationType operationType) {
        extractAttributes(span, response, null, operationType);
    }

    /**
     * Provides a default context for this extractor.
     * This context will be used when no specific context is provided.
     *
     * @return A default context with common values, or null if no default is available
     */
    default C getContext() {
        return null;
    }
} 
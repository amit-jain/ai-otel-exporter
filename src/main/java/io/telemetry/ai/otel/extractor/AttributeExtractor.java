package io.telemetry.ai.otel.extractor;

import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.model.response.GenericResponse;
import io.opentelemetry.api.trace.Span;

/**
 * Interface for extracting telemetry attributes from operation responses.
 * Provides a mechanism for operation-specific attribute extraction,
 * allowing different operations to contribute their own telemetry data.
 *
 * @param <T> The type of response to extract attributes from
 * @param <C> The type of operation context providing additional data
 */
@FunctionalInterface
public interface AttributeExtractor<T extends GenericResponse, C extends OperationContext> {
    /**
     * Extracts attributes from an operation response and adds them to a span.
     *
     * @param span     The span to add attributes to
     * @param context  The operation context providing additional data
     * @param response The operation response to extract attributes from
     * @param type     The type of operation being performed
     */
    void extractAttributes(Span span, C context, T response, OperationType type);
} 
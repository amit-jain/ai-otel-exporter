package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.model.OperationType;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for managing dynamic context attributes for spans.
 * Provides methods to add, get, and apply context attributes to spans.
 * This class is thread-safe and uses OpenTelemetry Context propagation
 * which properly handles asynchronous operations and thread pools.
 */
public final class TelemetryContext {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryContext.class);

    // Context key for storing telemetry attributes
    private static final ContextKey<Map<String, Object>> CONTEXT_ATTRIBUTES_KEY =
            ContextKey.named("telemetry-context-attributes");

    private TelemetryContext() {
        // Private constructor to prevent instantiation
    }

    /**
     * Add a dynamic attribute for the current span.
     * This attribute will be propagated with the OpenTelemetry context
     * across async boundaries (e.g., CompletableFuture, thread pools).
     *
     * @param key   The attribute key
     * @param value The attribute value
     */
    public static void addAttribute(String key, Object value) {
        Context current = Context.current();
        Map<String, Object> attributes = current.get(CONTEXT_ATTRIBUTES_KEY);

        // Create new attributes map or copy existing one (context is immutable)
        Map<String, Object> newAttributes;
        if (attributes == null) {
            newAttributes = new HashMap<>();
        } else {
            newAttributes = new HashMap<>(attributes);
        }

        // Add the new attribute
        newAttributes.put(key, value);

        // Update the context with the new attributes map
        Context updated = current.with(CONTEXT_ATTRIBUTES_KEY, newAttributes);
        updated.makeCurrent();
    }

    /**
     * Get all dynamic attributes for the current context.
     *
     * @return Map of attribute keys to values
     */
    public static Map<String, Object> getAttributes() {
        Map<String, Object> attributes = Context.current().get(CONTEXT_ATTRIBUTES_KEY);
        if (attributes == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Get a specific attribute from the current context.
     *
     * @param key The attribute key
     * @return The attribute value or null if not found
     */
    public static Object getAttribute(String key) {
        Map<String, Object> attributes = Context.current().get(CONTEXT_ATTRIBUTES_KEY);
        if (attributes == null) {
            return null;
        }
        return attributes.get(key);
    }

    /**
     * Clear all dynamic attributes for the current context.
     * Note: This creates a new context without attributes and makes it current.
     */
    public static void clearAttributes() {
        if (Context.current().get(CONTEXT_ATTRIBUTES_KEY) != null) {
            // Remove the attributes by creating a new context without them
            Context newContext = Context.root();
            newContext.makeCurrent();
        }
    }

    /**
     * Apply dynamic attributes to a span.
     *
     * @param span The span to add attributes to
     */
    public static void applyAttributes(Span span) {
        Map<String, Object> attributes = getAttributes();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            addSpanAttribute(span, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Get the operation type from the current context.
     *
     * @return The operation type or null if not found
     */
    public static OperationType getOperationType() {
        Object operationType = getAttribute("operation.type");
        if (operationType instanceof String) {
            try {
                return OperationType.valueOf((String) operationType);
            } catch (IllegalArgumentException e) {
                LOG.warn("Invalid operation type name: {}", operationType);
                return null;
            }
        }
        return null;
    }

    /**
     * Add an attribute to a span based on its type.
     */
    private static void addSpanAttribute(Span span, String key, Object value) {
        if (value instanceof String) {
            span.setAttribute(key, (String) value);
        } else if (value instanceof Long) {
            span.setAttribute(key, (Long) value);
        } else if (value instanceof Double) {
            span.setAttribute(key, (Double) value);
        } else if (value instanceof Boolean) {
            span.setAttribute(key, (Boolean) value);
        } else if (value instanceof Integer) {
            span.setAttribute(key, (Integer) value);
        } else {
            span.setAttribute(key, value.toString());
        }
    }
} 
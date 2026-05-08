package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.common.OpenInferenceAttributes;
import io.telemetry.ai.otel.config.TelemetryConfig;
import io.telemetry.ai.otel.config.TelemetryConfigConstants;
import io.telemetry.ai.otel.config.TracingLimits;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder class for creating OpenTelemetry spans with a fluent API.
 * Provides a type-safe way to configure span attributes and context
 * while ensuring compliance with OpenInference specifications.
 *
 * <p>This builder handles:
 * <ul>
 *   <li>Span creation and configuration</li>
 *   <li>Parent context propagation</li>
 *   <li>Sampling and rate limiting</li>
 *   <li>OpenInference attribute management</li>
 *   <li>Multi-tenancy support</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * Span span = new SpanBuilder(tracer, "search-operation", limits)
 *     .setSpanKind(SpanKind.CLIENT)
 *     .setTenantId("tenant-123")
 *     .setServiceId("service-456")
 *     .setQuery("search query")
 *     .build();
 * }</pre>
 */
public class SpanBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SpanBuilder.class);

    private final Tracer tracer;
    private final String operationName;
    private SpanKind spanKind = SpanKind.INTERNAL;
    private String tenantId;
    private String serviceName;
    private String query;
    private final TracingLimits tracingLimits;

    /**
     * Creates a new SpanBuilder with required parameters.
     * Validates inputs and initializes the builder with default settings.
     *
     * @param tracer        The OpenTelemetry tracer to use
     * @param operationName The name of the operation being traced
     * @param tracingLimits The tracing limits configuration
     * @throws IllegalArgumentException if any parameter is null or if operationName is empty
     */
    public SpanBuilder(Tracer tracer, String operationName, TracingLimits tracingLimits) {
        if (tracer == null) {
            throw new IllegalArgumentException("Tracer cannot be null");
        }
        if (operationName == null || operationName.isEmpty()) {
            throw new IllegalArgumentException("Operation name cannot be null or empty");
        }
        if (tracingLimits == null) {
            throw new IllegalArgumentException("Tracing limits cannot be null");
        }

        this.tracer = tracer;
        this.operationName = operationName;
        this.tracingLimits = tracingLimits;
    }

    /**
     * Sets the span kind.
     * The span kind indicates the relationship between spans
     * (e.g., CLIENT spans for outbound requests).
     *
     * @param spanKind The kind of span to create
     * @return This builder instance
     */
    public SpanBuilder setSpanKind(SpanKind spanKind) {
        this.spanKind = spanKind != null ? spanKind : SpanKind.INTERNAL;
        return this;
    }

    /**
     * Sets the tenant ID for multi-tenancy support.
     * This ID is used to attribute telemetry data to specific tenants
     * and is added as a span attribute.
     *
     * @param tenantId The tenant identifier
     * @return This builder instance
     */
    public SpanBuilder setTenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * Sets the service name for service identification.
     * This name helps track which service handled the request
     * and is added as a span attribute.
     *
     * @param serviceName The service name
     * @return This builder instance
     */
    public SpanBuilder setServiceName(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }

    /**
     * Sets the query text being processed.
     * The query is added as an OpenInference attribute and helps track
     * the input that generated this span.
     *
     * @param query The query being processed
     * @return This builder instance
     */
    public SpanBuilder setQuery(String query) {
        this.query = query;
        return this;
    }

    /**
     * Builds and starts the span with the configured attributes.
     * This method:
     * <ul>
     *   <li>Checks sampling and rate limits</li>
     *   <li>Sets up parent context if available</li>
     *   <li>Adds standard span attributes</li>
     *   <li>Adds OpenInference-specific attributes</li>
     *   <li>Configures span kind based on operation type</li>
     * </ul>
     *
     * @return The created span, or an invalid span if sampling limits are exceeded
     */
    public Span build() {
        // Check if OTEL export is disabled
        TelemetryConfig config = TelemetryConfig.fromSystemProperties();
        if (!config.isOtlpEnabled()) {
            if (logger.isDebugEnabled()) {
                logger.debug("OTEL export is disabled, returning no-op span");
            }
            return Span.getInvalid();
        }

        // Check sampling limits
        if (!tracingLimits.shouldSample()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping span creation due to sampling limits");
            }
            return Span.getInvalid();
        }

        // Get current context and check if this is a child span
        io.opentelemetry.context.Context currentContext = io.opentelemetry.context.Context.current();
        Span currentSpan = Span.fromContext(currentContext);
        String parentTraceId = currentSpan.getSpanContext().isValid() ? currentSpan.getSpanContext().getTraceId() : null;
        String parentSpanId = currentSpan.getSpanContext().isValid() ? currentSpan.getSpanContext().getSpanId() : null;

        // Check per-trace limits
        if (!tracingLimits.shouldCreateSpan(parentTraceId)) {
            if (logger.isDebugEnabled()) {
                logger.debug("Skipping span creation due to per-trace limits");
            }
            return Span.getInvalid();
        }

        // For root spans and query spans, always use CLIENT kind
        if (operationName.contains("root") || operationName.contains("query")) {
            this.spanKind = SpanKind.CLIENT;
            if (logger.isDebugEnabled()) {
                logger.debug("Adjusted span kind to CLIENT for root/query span");
            }
        }

        // Create span builder with explicit parent context
        io.opentelemetry.api.trace.SpanBuilder spanBuilder = tracer.spanBuilder(operationName)
                .setSpanKind(spanKind);

        // Only set parent if current context has a valid span
        if (currentSpan.getSpanContext().isValid()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Setting parent context from valid current span. Parent trace ID: {}, Parent span ID: {}",
                        parentTraceId, parentSpanId);
            }
            spanBuilder.setParent(currentContext);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("No valid parent span found, creating root span");
            }
            spanBuilder.setNoParent();
        }

        Span span = spanBuilder.startSpan();

        // Log the created span details
        if (logger.isDebugEnabled()) {
            logger.debug("Created span: {}, Trace ID: {}, Span ID: {}",
                    operationName, span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
            if (currentSpan.getSpanContext().isValid()) {
                logger.debug("Parent-child relationship: Parent span ID: {}, Child span ID: {}",
                        parentSpanId, span.getSpanContext().getSpanId());
            }
        }

        // Copy any context attributes from the current context to the span
        TelemetryContext.applyAttributes(span);

        // Add tenant and instance identification
        if (tenantId != null) {
            span.setAttribute(OpenInferenceAttributes.TENANT_ID_ATTRIBUTE, tenantId);
        }
        
        // Set service name - use parameter or fall back to system property
        if (serviceName != null) {
            span.setAttribute(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE, serviceName);
        } else {
            String systemServiceName = System.getProperty(TelemetryConfigConstants.SERVICE_NAME_PROPERTY);
            if (systemServiceName != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Using system property {} as fallback: {}", TelemetryConfigConstants.SERVICE_NAME_PROPERTY, systemServiceName);
                }
                span.setAttribute(OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE, systemServiceName);
            }
        }

        // Add OpenInference attributes
        if (query != null) {
            span.setAttribute(OpenInferenceAttributes.INPUT_VALUE, query);
            span.setAttribute(OpenInferenceAttributes.INPUT_MIME_TYPE, "text/plain");
        }

        // Set semantic span kind based on operation
        if ("search".equals(operationName) || operationName.contains("root") || operationName.contains("query")) {
            span.setAttribute(OpenInferenceAttributes.SPAN_KIND, OpenInferenceAttributes.SPAN_KIND_RETRIEVER);
            if (logger.isDebugEnabled()) {
                logger.debug("Set span kind to RETRIEVER");
            }
        } else if ("get_embeddings".equals(operationName)) {
            span.setAttribute(OpenInferenceAttributes.SPAN_KIND, OpenInferenceAttributes.SPAN_KIND_EMBEDDING);
            if (logger.isDebugEnabled()) {
                logger.debug("Set span kind to EMBEDDING");
            }
        }

        return span;
    }
} 
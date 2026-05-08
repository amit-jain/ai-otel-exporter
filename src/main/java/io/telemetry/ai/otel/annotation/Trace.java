package io.telemetry.ai.otel.annotation;

import io.telemetry.ai.otel.model.OperationType;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking methods that should be traced using OpenTelemetry.
 * When applied to a method, the TracingProxyFactory will automatically create
 * spans and collect telemetry data for the method execution.
 */
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Trace {
    /**
     * The name to use for the created span.
     *
     * @return The span name
     */
    @Nonbinding
    String spanName() default "";  // If empty, will use method name

    /**
     * The kind of span to create (CLIENT, SERVER, etc.).
     *
     * @return The span kind
     */
    @Nonbinding
    SpanKind spanKind() default SpanKind.INTERNAL;

    /**
     * Whether to include method parameters as span attributes.
     *
     * @return True if parameters should be included, false otherwise
     */
    @Nonbinding
    boolean includeParameters() default false;

    /**
     * The operation type to use for attribute extraction.
     * This determines which attribute extractor will be used.
     *
     * @return The operation type
     */
    @Nonbinding
    OperationType operationType() default OperationType.SEARCH;

    /**
     * The explicit response type for attribute extraction.
     * If specified, the tracing system will use an extractor registered for this type.
     * This avoids reflection overhead and works better with generic types.
     *
     * @return The response class
     */
    @Nonbinding
    Class<?> responseType() default Object.class;
} 
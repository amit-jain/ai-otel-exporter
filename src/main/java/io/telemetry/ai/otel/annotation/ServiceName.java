package io.telemetry.ai.otel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a parameter as a service name.
 * Used by the tracing system to identify which parameter contains the service name
 * for service identification. This value will be propagated to spans as the standard
 * OpenTelemetry "service.name" attribute.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ServiceName {
} 
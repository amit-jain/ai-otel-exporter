package io.telemetry.ai.otel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a parameter as an instance ID.
 * Used by the tracing system to identify which parameter contains the instance ID
 * for service instance identification.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface InstanceId {
}
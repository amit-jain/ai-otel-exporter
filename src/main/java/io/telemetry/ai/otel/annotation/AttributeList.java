package io.telemetry.ai.otel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a list parameter to be recorded as a single telemetry attribute.
 * This is useful for recording identifiers, resource names, or other collections
 * that should be captured as a single attribute in the telemetry data.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface AttributeList {
    
    /**
     * Name of the attribute to be attached to the telemetry span.
     * If not specified, the name of the method parameter will be used.
     *
     * @return The name of the attribute
     */
    String attributeName() default "";
    
    /**
     * Delimiter to split the list elements with.
     * If not specified, default to comma.
     *
     * @return The delimiter used to split the list elements
     */
    String delimiter() default ",";
    
    /**
     * Maximum number of elements to include in the attribute value.
     * If not specified, default to 10.
     *
     * @return The maximum number of elements to include
     */
    int maxElements() default 10;
} 
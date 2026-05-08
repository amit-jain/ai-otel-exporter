package io.telemetry.ai.otel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter for telemetry attribute extraction.
 * The parameter's value will be processed by appropriate attribute extractors.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ExtractAttributes {
    
    /**
     * Optional name of the extractor to use.
     * If not specified, the system will try to find an appropriate extractor based on the parameter type.
     * 
     * @return The name of the extractor to use, or an empty string to use automatic extractor selection
     */
    String value() default "";
} 
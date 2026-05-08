package io.telemetry.ai.otel.cdi;

import io.telemetry.ai.otel.annotation.AttributeList;
import io.telemetry.ai.otel.annotation.ExtractAttributes;
import io.telemetry.ai.otel.annotation.QueryText;
import io.telemetry.ai.otel.annotation.ServiceName;
import io.telemetry.ai.otel.annotation.TenantId;
import io.telemetry.ai.otel.annotation.Trace;
import io.telemetry.ai.otel.config.TelemetryConfigConstants;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.GenericOperationContext;
import io.telemetry.ai.otel.model.context.LLMOperationContext;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.processor.AnnotationProcessor;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.tracing.TelemetryAgentProducer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.concurrent.ConcurrentHashMap;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_TENANT_ID;

/**
 * CDI interceptor for automatic tracing in Quarkus applications.
 * Provides automatic OpenTelemetry instrumentation for methods annotated with @Trace.
 * This interceptor creates and manages spans, extracts context information, and handles
 * error cases automatically.
 *
 * <p>The interceptor supports:
 * <ul>
 *   <li>Method and class level @Trace annotations</li>
 *   <li>Parameter annotations for service ID, tenant ID, instance ID, and query text</li>
 *   <li>Parameter recording as span attributes</li>
 *   <li>Error handling and status recording</li>
 *   <li>Type-specific attribute extraction based on response type</li>
 *   <li>Reflection caching for significant performance improvements</li>
 *   <li>ExtractionAttributes for complex objects that need attribute extraction</li>
 *   <li>AttributeList for parameters that should be directly added as attributes</li>
 * </ul>
 */
@Interceptor
@Dependent
@Priority(2020)
@Trace
public class CDITraceInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(CDITraceInterceptor.class);

    // Reflection caches for significant performance improvement
    private static final ConcurrentHashMap<Method, Trace> TRACE_ANNOTATION_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Trace> CLASS_TRACE_ANNOTATION_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Method, MethodMetadata> METHOD_METADATA_CACHE = new ConcurrentHashMap<>();

    private final TelemetryAgentProducer agentProducer;
    
    @Inject
    AnnotationProcessor annotationProcessor;

    /**
         * Value object to store cached method metadata
         */
        private record MethodMetadata(String spanName, SpanKind spanKind, OperationType operationType,
                                      Class<?> responseType, boolean includeParameters, ParameterIndexes parameterIndexes,
                                      boolean hasExtractAttributesParameters, boolean hasAttributeListParameters) {
    }

    /**
         * Value object to store indexes of important annotated parameters
         */
        private record ParameterIndexes(int serviceNameIndex, int tenantIdIndex, int queryTextIndex) {
    }

    /**
     * Creates a new QuarkusTraceInterceptor with the specified telemetry agent producer.
     *
     * @param agentProducer The producer to get appropriate telemetry agents
     */
    @Inject
    public CDITraceInterceptor(TelemetryAgentProducer agentProducer) {
        this.agentProducer = agentProducer;
    }

    /**
     * Intercepts method calls and adds tracing instrumentation.
     * Creates spans, manages context, and handles errors for traced methods.
     * Uses caching for reflection operations to significantly improve performance.
     *
     * @param context The invocation context containing method and parameter information
     * @return The result of the method invocation
     * @throws Exception If an error occurs during method execution
     */
    @AroundInvoke
    public Object trace(InvocationContext context) throws Exception {
        Method method = context.getMethod();
        Object target = context.getTarget();
        Object[] parameters = context.getParameters();

        // Get cached method metadata or compute it if not available
        MethodMetadata metadata = METHOD_METADATA_CACHE.computeIfAbsent(method, m -> {
            // Get trace annotation from method or class cache (or compute it)
            Trace traceAnnotation = getTraceAnnotation(m, target.getClass());

            // If no trace annotation found, return null (will be filtered later)
            if (traceAnnotation == null) {
                return null;
            }

            // Extract span name from annotation or use method name
            String spanName = traceAnnotation.spanName().isEmpty()
                    ? m.getName() : traceAnnotation.spanName();

            // Extract other annotation values
            SpanKind spanKind = traceAnnotation.spanKind();
            OperationType operationType = traceAnnotation.operationType();
            Class<?> responseType = !traceAnnotation.responseType().equals(Object.class)
                    ? traceAnnotation.responseType() : null;
            boolean includeParameters = traceAnnotation.includeParameters();

            // Find indexes of important annotated parameters
            ParameterIndexes paramIndexes = findParameterIndexes(m.getParameters());
            
            // Check if we have any ExtractAttributes or AttributeList parameters
            boolean hasExtractAttributesParameters = hasAnnotatedParameters(m.getParameters(), ExtractAttributes.class);
            boolean hasAttributeListParameters = hasAnnotatedParameters(m.getParameters(), AttributeList.class);

            return new MethodMetadata(spanName, spanKind, operationType,
                    responseType, includeParameters, paramIndexes, 
                    hasExtractAttributesParameters, hasAttributeListParameters);
        });

        // If no metadata (no trace annotation), just proceed with the method invocation
        if (metadata == null) {
            return context.proceed();
        }

        // Extract parameters using cached indexes
        ParameterIndexes indexes = metadata.parameterIndexes;
        String serviceName = getParameterValue(parameters, indexes.serviceNameIndex);
        String tenantId = getParameterValue(parameters, indexes.tenantIdIndex);
        String query = getParameterValue(parameters, indexes.queryTextIndex);

        // Get the appropriate agent based on service name and tenant ID
        TelemetryAgent agent = getAppropriateAgent(serviceName, tenantId);

        // Log debugging information about the intercepted method
        if (logger.isDebugEnabled()) {
            logger.debug("Intercepting method: {}, operationType: {}, responseType: {}",
                    metadata.spanName, metadata.operationType,
                    metadata.responseType != null ? metadata.responseType.getName() : "null");
            logger.debug("Parameters: serviceName={}, tenantId={}, query={}",
                    serviceName, tenantId, query);
        }

        // Log parent span information for debugging parent-child span relationships
        io.opentelemetry.context.Context currentContext = io.opentelemetry.context.Context.current();
        Span currentSpan = Span.fromContext(currentContext);
        boolean hasParentSpan = logParentSpanDetails(currentContext, currentSpan);

        // Create the span
        Span span = agent.startSpan(metadata.spanName, metadata.spanKind, serviceName, tenantId, query);

        // Log created span details for debugging
        if (logger.isDebugEnabled()) {
            logger.debug("Created child span: {}, Trace ID: {}, Span ID: {}",
                    metadata.spanName, span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());

            if (hasParentSpan) {
                logger.debug("Parent-child relationship: Parent span ID: {}, Child span ID: {}",
                        currentSpan.getSpanContext().getSpanId(), span.getSpanContext().getSpanId());
            }
        }

        // Add method parameters as span attributes if requested
        if (metadata.includeParameters && parameters != null) {
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i] != null) {
                    span.setAttribute("param." + i, parameters[i].toString());
                }
            }
        }
        
        // Process new parameter annotations using the AnnotationProcessor if present
        if ((metadata.hasExtractAttributesParameters || metadata.hasAttributeListParameters) && 
            annotationProcessor != null) {
            logger.debug("Processing parameter annotations using AnnotationProcessor");
            try {
                annotationProcessor.processParameterAnnotations(
                    method, parameters, span, metadata.operationType);
            } catch (Exception e) {
                logger.warn("Error processing parameter annotations: {}", e.getMessage(), e);
            }
        }

        // Store the original context to restore it later

        try (var scope = span.makeCurrent()) {
            // Log current context and span after making the new span current
            if (logger.isDebugEnabled()) {
                logger.debug("Current context after makeCurrent(): {}", io.opentelemetry.context.Context.current());
                logger.debug("Current span after makeCurrent(): {}", Span.current());
            }

            Object result = context.proceed();
            span.setStatus(StatusCode.OK);

            // If we have a result and a response type is specified, extract attributes
            processOperationResult(span, result, metadata, agent, query, method);

            return result;
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();

            if (logger.isDebugEnabled()) {
                logger.debug("Span ended: {}, Trace ID: {}, Span ID: {}",
                        metadata.spanName, span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
            }

            // Restore the original context to ensure proper parent-child relationship
            try (var scope = currentContext.makeCurrent()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Restored original context: {}", io.opentelemetry.context.Context.current());
                }
            }
        }
    }
    
    /**
     * Checks if the method has parameters annotated with the specified annotation.
     * 
     * @param parameters The method parameters to check
     * @param annotationClass The annotation class to look for
     * @return True if any parameter has the specified annotation
     */
    private boolean hasAnnotatedParameters(Parameter[] parameters, Class<? extends java.lang.annotation.Annotation> annotationClass) {
        if (parameters == null) {
            return false;
        }
        
        for (Parameter param : parameters) {
            if (param.isAnnotationPresent(annotationClass)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Gets the trace annotation from the method or class, using a cache for performance.
     *
     * @param method      The method to check for annotations
     * @param targetClass The class to check for annotations if not found on method
     * @return The Trace annotation, or null if not found
     */
    private Trace getTraceAnnotation(Method method, Class<?> targetClass) {
        // Check if we've already cached this method's trace annotation
        Trace traceAnnotation = TRACE_ANNOTATION_CACHE.get(method);
        if (traceAnnotation != null) {
            return traceAnnotation;
        }

        // Check if the method has a @Trace annotation
        traceAnnotation = method.getAnnotation(Trace.class);
        if (traceAnnotation != null) {
            TRACE_ANNOTATION_CACHE.put(method, traceAnnotation);
            return traceAnnotation;
        }

        // Check if we've already cached this class's trace annotation
        traceAnnotation = CLASS_TRACE_ANNOTATION_CACHE.get(targetClass);
        if (traceAnnotation != null) {
            TRACE_ANNOTATION_CACHE.put(method, traceAnnotation);
            return traceAnnotation;
        }

        // Check if the class has a @Trace annotation
        traceAnnotation = targetClass.getAnnotation(Trace.class);
        if (traceAnnotation != null) {
            CLASS_TRACE_ANNOTATION_CACHE.put(targetClass, traceAnnotation);
            TRACE_ANNOTATION_CACHE.put(method, traceAnnotation);
            return traceAnnotation;
        }

        return null;
    }

    /**
     * Finds the indexes of important annotated parameters.
     *
     * @param methodParameters The method parameters to scan for annotations
     * @return A ParameterIndexes object with the indexes of special parameters
     */
    private ParameterIndexes findParameterIndexes(Parameter[] methodParameters) {
        int serviceNameIndex = -1;
        int tenantIdIndex = -1;
        int queryTextIndex = -1;

        for (int i = 0; i < methodParameters.length; i++) {
            Parameter param = methodParameters[i];
            if (param.isAnnotationPresent(ServiceName.class)) {
                serviceNameIndex = i;
            }
            if (param.isAnnotationPresent(TenantId.class)) {
                tenantIdIndex = i;
            }
            if (param.isAnnotationPresent(QueryText.class)) {
                queryTextIndex = i;
            }
        }

        // Return the found indexes
        return new ParameterIndexes(serviceNameIndex, tenantIdIndex, queryTextIndex);
    }

    /**
     * Gets a parameter value at a specific index as the specified type.
     *
     * @param parameters The method parameters
     * @param index      The index of the parameter to get
     * @param <T>        The type to cast the parameter to
     * @return The parameter value, or null if the index is invalid or the parameter is null
     */
    @SuppressWarnings("unchecked")
    private <T> T getParameterValue(Object[] parameters, int index) {
        if (parameters == null || index < 0 || index >= parameters.length) {
            return null;
        }

        Object parameter = parameters[index];
        if (parameter == null) {
            return null;
        }

        try {
            return (T) parameter;
        } catch (ClassCastException e) {
            logger.warn("Parameter at index {} is not of expected type: {}", index, e.getMessage());
            return null;
        }
    }

    /**
     * Gets the appropriate telemetry agent based on the service name and tenant ID.
     * Handles default values and fallbacks when parameters are missing.
     *
     * @param serviceName The service name to use for the agent
     * @param tenantId The tenant ID to use for the agent
     * @return The appropriate telemetry agent
     */
    private TelemetryAgent getAppropriateAgent(String serviceName, String tenantId) {
        // Create a default agent if both service ID and tenant ID are null
        if (serviceName == null && tenantId == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using default agent (no service name or tenant ID)");
            }
            return agentProducer.produceDefaultAgent();
        }

        // Use service name from parameter or system property
        String effectiveServiceName = serviceName;
        if (effectiveServiceName == null) {
            effectiveServiceName = System.getProperty(TelemetryConfigConstants.SERVICE_NAME_PROPERTY);
            if (logger.isDebugEnabled()) {
                logger.debug("Using {} from system property: {}", TelemetryConfigConstants.SERVICE_NAME_PROPERTY, effectiveServiceName);
            }
        }

        // Use tenant ID from parameter or default
        String effectiveTenantId = tenantId;
        if (effectiveTenantId == null) {
            effectiveTenantId = DEFAULT_TENANT_ID;
            if (logger.isDebugEnabled()) {
                logger.debug("Using default tenant ID: {}", effectiveTenantId);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Getting agent for service name: {} and tenant ID: {}", effectiveServiceName, effectiveTenantId);
        }

        // Get from cache or create new
        return agentProducer.getAgent(effectiveServiceName, effectiveTenantId);
    }

    /**
     * Logs details about the parent span for debugging purposes.
     *
     * @param currentContext The current context
     * @param currentSpan    The current span
     * @return True if there's a parent span, false otherwise
     */
    private boolean logParentSpanDetails(io.opentelemetry.context.Context currentContext, Span currentSpan) {
        if (!logger.isDebugEnabled()) {
            return false;
        }

        boolean hasParentSpan = currentSpan != null &&
                currentSpan.getSpanContext().isValid() &&
                !io.opentelemetry.context.Context.current().equals(io.opentelemetry.context.Context.root());

        if (hasParentSpan) {
            logger.debug("Found parent span in context: {}", currentSpan);
            logger.debug("Parent span details - Trace ID: {}, Span ID: {}, sampled: {}",
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId(),
                    currentSpan.getSpanContext().isSampled());
        } else {
            logger.debug("No parent span found, or invalid parent span context");
        }

        return hasParentSpan;
    }

    /**
     * Processes operation results and extracts attributes.
     *
     * @param span           The span to add attributes to
     * @param result         The result of the method call
     * @param metadata       The method metadata
     * @param agent          The telemetry agent
     * @param query          The query parameter (if available)
     * @param method         The method being traced
     */
    private void processOperationResult(Span span, Object result, MethodMetadata metadata,
                                        TelemetryAgent agent, String query, Method method) {
        if (result == null) {
            return;
        }

        // Extract specific response class from annotation if available
        Class<?> responseClass = metadata.responseType;

        // If no specific response class from annotation, use the actual result class
        if (responseClass == null) {
            responseClass = result.getClass();
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Processing operation result of type: {} for operation: {}",
                    result.getClass().getName(), metadata.operationType);
            logger.debug("Using response class: {} (from annotation: {})",
                    responseClass.getName(), metadata.responseType != null);
        }

        // Extract information from the response based on operation type
        try {
            // Create a context for the operation
            String endpoint = getEndpointFromClass(method.getDeclaringClass());
            OperationContext context = createOperationContext(metadata.operationType, query, endpoint);

            // Add attributes using the agent and the appropriate extractor based on type
            agent.addTypedAttributes(span, result, context, metadata.operationType);

            if (logger.isDebugEnabled()) {
                logger.debug("Finished processing result with context: {}", context);
            }
        } catch (Exception e) {
            logger.warn("Error processing operation result: {}", e.getMessage(), e);
        }
    }

    /**
     * Creates an appropriate operation context based on the operation type.
     *
     * @param operationType The type of operation being performed
     * @param query         The query being processed (if applicable)
     * @param endpoint      The endpoint being called
     * @return An OperationContext appropriate for the operation type
     */
    private OperationContext createOperationContext(OperationType operationType, String query, String endpoint) {
        if (operationType == OperationType.SEARCH) {
            return SearchOperationContext.builder()
                    .searchSystem("custom")
                    .query(query)
                    .endpoint(endpoint)
                    .build();
        } else if (operationType == OperationType.EMBEDDING) {
            // Use LLMOperationContext for EMBEDDING operations
            return LLMOperationContext.builder()
                    .query(query)
                    .endpoint(endpoint)
                    .build();
        } else {
            // For all other types, use a generic operation context
            return GenericOperationContext.builder()
                    .operationType(operationType.toString())
                    .query(query)
                    .endpoint(endpoint)
                    .build();
        }
    }

    /**
     * Extracts an endpoint name from a class name.
     *
     * @param clazz The class to get an endpoint name for
     * @return A string representing the endpoint
     */
    private String getEndpointFromClass(Class<?> clazz) {
        if (clazz == null) {
            return "unknown";
        }
        return clazz.getSimpleName().toLowerCase();
    }

    /**
     * Gets a representation of the class hierarchy for debugging.
     *
     * @param clazz The class to analyze
     * @return A string representation of the class hierarchy
     */
    private String getClassHierarchy(Class<?> clazz) {
        if (clazz == null) {
            return "null";
        }

        StringBuilder hierarchy = new StringBuilder();
        hierarchy.append(clazz.getName());

        Class<?> superclass = clazz.getSuperclass();
        while (superclass != null && !superclass.equals(Object.class)) {
            hierarchy.append(" -> ").append(superclass.getName());
            superclass = superclass.getSuperclass();
        }

        return hierarchy.toString();
    }
} 
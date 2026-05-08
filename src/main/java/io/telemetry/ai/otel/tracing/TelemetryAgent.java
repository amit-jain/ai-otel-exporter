package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.config.TelemetryConfig;
import io.telemetry.ai.otel.config.TracingLimits;
import io.telemetry.ai.otel.extractor.AttributeExtractor;
import io.telemetry.ai.otel.extractor.DefaultAttributeExtractors;
import io.telemetry.ai.otel.extractor.TypedAttributeExtractor;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.model.response.GenericResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static io.telemetry.ai.otel.common.OpenInferenceAttributes.INPUT_MIME_TYPE;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.INPUT_VALUE;

/**
 * Core telemetry agent responsible for managing OpenTelemetry tracing operations.
 * This class handles span creation, attribute management, and telemetry data collection
 * for both embedding and search operations in the semantic search system.
 */
public class TelemetryAgent {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryAgent.class);
    private final Tracer tracer;
    private final TelemetryConfig config;
    private final TracingLimits tracingLimits;

    private final Map<OperationType, AttributeExtractor<?, ?>> extractors = new ConcurrentHashMap<>();

    // Map to store type-specific extractors, with a nested map for each operation type
    // The outer key is the operation type, the inner key is the response class
    private final Map<OperationType, Map<Class<?>, TypedAttributeExtractor<?, ?>>> typedExtractors = new ConcurrentHashMap<>();

    // Thread-local store for context attributes - REMOVED, using TelemetryContext instead

    /**
     * Creates a new TelemetryAgent with default tracing limits.
     *
     * @param tracer The OpenTelemetry tracer instance to use for span creation
     */
    public TelemetryAgent(Tracer tracer) {
        this.tracer = tracer;
        this.config = TelemetryConfig.fromSystemProperties();
        this.tracingLimits = TracingLimits.DEFAULT;
        DefaultAttributeExtractors.registerDefaults(this);
        if (logger.isDebugEnabled()) {
            logger.debug("Created TelemetryAgent with tracer: {} and limits: {}", tracer, tracingLimits);
        }
    }

    /**
     * Creates a new TelemetryAgent with custom tracing limits.
     *
     * @param tracer        The OpenTelemetry tracer instance to use for span creation
     * @param tracingLimits Custom limits for tracing operations
     */
    public TelemetryAgent(Tracer tracer, TracingLimits tracingLimits) {
        this.tracer = tracer;
        this.config = TelemetryConfig.fromSystemProperties();
        this.tracingLimits = tracingLimits;
        DefaultAttributeExtractors.registerDefaults(this);
        if (logger.isDebugEnabled()) {
            logger.debug("Created TelemetryAgent with tracer: {} and limits: {}", tracer, tracingLimits);
        }
    }

    /**
     * Creates a new span with the specified parameters.
     *
     * @param operationName Name of the operation being traced
     * @param spanKind      Kind of span (CLIENT, SERVER, etc.)
     * @param serviceName   Name of the service
     * @param tenantId      ID of the tenant for multi-tenancy support
     * @param query         Query being processed (if applicable)
     * @return A new Span instance
     * @throws IllegalArgumentException if operationName is null or empty
     */
    public Span startSpan(String operationName, SpanKind spanKind, String serviceName, String tenantId, String query) {
        if (operationName == null || operationName.isEmpty()) {
            throw new IllegalArgumentException("Operation name cannot be null or empty");
        }

        return new SpanBuilder(tracer, operationName, tracingLimits)
                .setSpanKind(spanKind)
                .setServiceName(serviceName)
                .setTenantId(tenantId)
                .setQuery(query)
                .build();
    }

    /**
     * Starts a new span with the specified operation name and span kind.
     *
     * @param operationName The name of the operation being traced
     * @param spanKind The kind of span (CLIENT, SERVER, etc.)
     * @return The newly created span
     */
    public Span startSpan(String operationName, SpanKind spanKind) {
        return startSpan(operationName, spanKind, null, null, null);
    }

    /**
     * Starts a new span with the specified operation name, span kind, service name, and tenant ID.
     *
     * @param operationName The name of the operation being traced
     * @param spanKind The kind of span (CLIENT, SERVER, etc.)
     * @param serviceName The name of the service initiating the span
     * @param tenantId The tenant identifier for the current request context
     * @return The newly created span
     */
    public Span startSpan(String operationName, SpanKind spanKind, String serviceName, String tenantId) {
        return startSpan(operationName, spanKind, serviceName, tenantId, null);
    }

    /**
     * Registers an attribute extractor for a specific operation type.
     *
     * @param <T>       Type of response
     * @param <C>       Type of operation context
     * @param type      Operation type to register extractor for
     * @param extractor Attribute extractor implementation
     * @throws IllegalArgumentException if type or extractor is null
     */
    public <T extends GenericResponse, C extends OperationContext> void registerExtractor(
            OperationType type,
            AttributeExtractor<T, C> extractor) {
        if (type == null) throw new IllegalArgumentException("Operation type cannot be null");
        if (extractor == null) throw new IllegalArgumentException("Attribute extractor cannot be null");
        extractors.put(type, extractor);
    }

    /**
     * Registers a type-specific attribute extractor for a specific operation type and response class.
     *
     * @param <T>           Type of response
     * @param <C>           Type of operation context
     * @param type          Operation type to register extractor for
     * @param responseClass The specific class of response this extractor handles
     * @param extractor     Type-specific attribute extractor implementation
     * @throws IllegalArgumentException if any parameter is null
     */
    public <T, C extends OperationContext> void registerTypedExtractor(
            OperationType type,
            Class<T> responseClass,
            TypedAttributeExtractor<T, C> extractor) {
        if (type == null) throw new IllegalArgumentException("Operation type cannot be null");
        if (responseClass == null) throw new IllegalArgumentException("Response class cannot be null");
        if (extractor == null) throw new IllegalArgumentException("Attribute extractor cannot be null");

        logger.debug("Registering typed extractor for operation type {} and response class {}",
                type, responseClass.getName());

        // Ensure the map for this operation type exists
        if (!typedExtractors.containsKey(type)) {
            logger.debug("Creating new map for operation type: {}", type);
            typedExtractors.put(type, new ConcurrentHashMap<>());
        }

        // Add the extractor to the map
        typedExtractors.get(type).put(responseClass, extractor);

        logger.debug("Successfully registered typed extractor for operation type {} and response class {}",
                type, responseClass.getName());
        logger.debug("Current typedExtractors map: {}", typedExtractors.keySet());
        Map<Class<?>, TypedAttributeExtractor<?, ?>> extractorsForType = typedExtractors.get(type);
        if (extractorsForType != null) {
            logger.debug("Extractors for type {}: {}", type,
                    extractorsForType.keySet().stream().map(Class::getName).collect(java.util.stream.Collectors.toList()));
        }
    }

    /**
     * Adds operation-specific attributes to a span using the registered extractor.
     *
     * @param <T>      Type of response
     * @param <C>      Type of operation context
     * @param span     Target span to add attributes to
     * @param context  Operation context
     * @param response Operation response
     * @param type     Operation type
     * @throws IllegalArgumentException if span, context, response, or type is null
     */
    @SuppressWarnings("unchecked")
    public <T extends GenericResponse, C extends OperationContext> void addAttributes(
            Span span,
            C context,
            T response,
            OperationType type) {
        if (span == null) throw new IllegalArgumentException("Span cannot be null");
        if (context == null) throw new IllegalArgumentException("Context cannot be null");
        if (response == null) throw new IllegalArgumentException("Response cannot be null");
        if (type == null) throw new IllegalArgumentException("Operation type cannot be null");

        // Skip attribute collection if OTEL export is disabled
        if (!config.isOtlpEnabled()) {
            logger.debug("OTEL export is disabled, skipping attribute collection");
            return;
        }

        // Add common attributes
        span.setAttribute(INPUT_VALUE, response.getInput());
        span.setAttribute(INPUT_MIME_TYPE, response.getInputMimeType());

        // Get registered extractor for this operation type
        AttributeExtractor<T, C> extractor = (AttributeExtractor<T, C>) extractors.get(type);
        if (extractor != null) {
            extractor.extractAttributes(span, context, response, type);
        }
    }

    /**
     * Adds attributes to a span using a type-specific extractor.
     * This method is used when you have a specific response type that isn't a GenericResponse.
     *
     * @param <T>      Type of response
     * @param span     Target span to add attributes to
     * @param response Operation response
     * @param type     Operation type
     * @throws IllegalArgumentException if span, response, or type is null
     */
    public <T> void addTypedAttributes(
            Span span,
            T response,
            OperationType type) {
        addTypedAttributes(span, response, null, type);
    }

    /**
     * Adds attributes to a span using a type-specific extractor with context.
     * This method is used when you have a specific response type that isn't a GenericResponse,
     * but you still need to provide operation context.
     *
     * @param <T>      Type of response
     * @param <C>      Type of operation context
     * @param span     Target span to add attributes to
     * @param response Operation response
     * @param context  Operation context (may be null)
     * @param type     Operation type
     * @throws IllegalArgumentException if span, response, or type is null
     */
    @SuppressWarnings("unchecked")
    public <T, C extends OperationContext> void addTypedAttributes(
            Span span,
            T response,
            C context,
            OperationType type) {
        // Skip attribute collection if OTEL export is disabled
        if (!config.isOtlpEnabled()) {
            if (logger.isDebugEnabled()) {
                logger.debug("OTEL export is disabled, skipping attribute collection");
            }
            return;
        }

        // Validate required parameters
        if (span == null) {
            logger.warn("Cannot add attributes to null span");
            return;
        }
        
        if (response == null) {
            logger.warn("Cannot extract attributes from null response");
            return;
        }
        
        if (type == null) {
            logger.warn("Cannot extract attributes without an operation type");
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("addTypedAttributes called with span: {}, response: {}, context: {}, type: {}",
                    span, response, context, type);

            if (context != null) {
                logger.debug("Context details - query: {}, endpoint: {}",
                        context.getQuery(), context.getEndpoint());

                if (context instanceof SearchOperationContext) {
                    logger.debug("SearchOperationContext details - searchSystem: {}",
                            ((SearchOperationContext) context).getSearchSystem());
                }
            } else {
                logger.debug("Context is NULL!");
            }

            Class<?> responseClass = response.getClass();
            logger.debug("Response class: {}", responseClass.getName());
            logger.debug("All registered operation types: {}", typedExtractors.keySet());
            logger.debug("typedExtractors map content: {}", typedExtractors);
        }

        // Find extractors for this operation type
        Class<?> responseClass = response.getClass();
        
        // Add basic operation type attribute even if no extractors are found
        span.setAttribute("operation.type", type.name());

        Map<Class<?>, TypedAttributeExtractor<?, ?>> extractorsForType = typedExtractors.get(type);
        if (extractorsForType == null || extractorsForType.isEmpty()) {
            logger.warn("No extractors registered for operation type: {}. Adding minimal attributes only.", type);
            
            // Add minimal information from the response when no extractors are available
            addMinimalAttributes(span, response, type);
            return;
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Found extractors for type: {}, available extractors: {}",
                    type, extractorsForType.keySet().stream().map(Class::getName).collect(java.util.stream.Collectors.toList()));
        }

        // First try to find an exact match for the response class
        TypedAttributeExtractor<?, ?> extractor = extractorsForType.get(responseClass);

        if (extractor != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Found exact extractor match for response class: {}", responseClass.getName());
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("No exact extractor match, trying to find compatible extractor");
            }

            // If no exact match, try to find a compatible extractor
            for (Map.Entry<Class<?>, TypedAttributeExtractor<?, ?>> entry : extractorsForType.entrySet()) {
                if (entry.getKey() == null) {
                    logger.warn("Found null key in extractors map. Skipping.");
                    continue;
                }
                
                if (logger.isDebugEnabled()) {
                    logger.debug("Checking if {} is assignable from {}", entry.getKey().getName(), responseClass.getName());
                }
                
                if (entry.getKey().isAssignableFrom(responseClass)) {
                    extractor = entry.getValue();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Found compatible extractor for response class: {} using extractor for: {}",
                                responseClass.getName(), entry.getKey().getName());
                    }
                    break;
                }
            }
        }

        if (extractor != null) {
            try {
                // Cast and apply the extractor
                TypedAttributeExtractor<T, C> typedExtractor = (TypedAttributeExtractor<T, C>) extractor;

                // If context is null, try to get a default context from the extractor
                C extractorContext = context;
                if (extractorContext == null) {
                    try {
                        extractorContext = typedExtractor.getContext();
                        if (logger.isDebugEnabled()) {
                            logger.debug("Using default context from extractor: {}", extractorContext);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to get default context from extractor. Continuing without context.", e);
                    }
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Calling extractor.extractAttributes");
                }
                
                try {
                    typedExtractor.extractAttributes(span, response, extractorContext, type);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Successfully extracted attributes");
                    }
                } catch (Exception e) {
                    logger.error("Error in extractor.extractAttributes. Adding minimal attributes instead.", e);
                    addMinimalAttributes(span, response, type);
                }
            } catch (ClassCastException e) {
                logger.error("Failed to cast extractor or response for attribute extraction. Adding minimal attributes instead.", e);
                addMinimalAttributes(span, response, type);
            } catch (Exception e) {
                logger.error("Error extracting attributes from response. Adding minimal attributes instead.", e);
                addMinimalAttributes(span, response, type);
            }
        } else {
            logger.warn("No compatible extractor found for response class: {}. Adding minimal attributes only.", responseClass.getName());
            addMinimalAttributes(span, response, type);
        }
    }
    
    /**
     * Adds minimal attributes to the span when no proper extractor is available.
     * This ensures some basic information is always captured even when extractors are missing.
     * 
     * @param span The span to add attributes to
     * @param response The response object
     * @param type The operation type
     */
    private <T> void addMinimalAttributes(Span span, T response, OperationType type) {
        try {
            // Always add the class name of the response
            span.setAttribute(type.getAttributeKey("response_class"), response.getClass().getName());
            
            // Try to add toString representation (with length limit)
            String responseStr = response.toString();
            if (responseStr != null && responseStr.length() > 500) {
                responseStr = responseStr.substring(0, 497) + "...";
            }
            span.setAttribute(type.getAttributeKey("response_summary"), responseStr);
            
            // For specific types, try to extract basic information using reflection
            if (type == OperationType.SEARCH) {
                extractBasicSearchInfo(span, response);
            } else if (type == OperationType.EMBEDDING) {
                extractBasicEmbeddingInfo(span, response);
            }
            
            logger.debug("Added minimal attributes for {}", type);
        } catch (Exception e) {
            logger.warn("Failed to add even minimal attributes", e);
        }
    }
    
    /**
     * Attempts to extract basic search information using reflection.
     * This is a fallback when no proper extractor is available.
     */
    private <T> void extractBasicSearchInfo(Span span, T response) {
        try {
            // Try to get query or input field via reflection
            tryGetFieldValue(response, "getQuery", "getInput", "query", "input")
                .ifPresent(value -> span.setAttribute("search.query", value.toString()));
            
            // Try to get count or size field via reflection
            tryGetFieldValue(response, "getSize", "getCount", "getDocumentsCount", "size", "count")
                .ifPresent(value -> {
                    if (value instanceof Number) {
                        span.setAttribute("search.count", ((Number)value).intValue());
                    } else {
                        span.setAttribute("search.count", value.toString()); 
                    }
                });
        } catch (Exception e) {
            logger.debug("Failed to extract basic search info via reflection", e);
        }
    }
    
    /**
     * Attempts to extract basic embedding information using reflection.
     * This is a fallback when no proper extractor is available.
     */
    private <T> void extractBasicEmbeddingInfo(Span span, T response) {
        try {
            // Try to get model name via reflection
            tryGetFieldValue(response, "getModel", "getModelName", "model", "modelName")
                .ifPresent(value -> span.setAttribute("embedding.model", value.toString()));
            
            // Try to get input text via reflection
            tryGetFieldValue(response, "getInput", "getText", "input", "text")
                .ifPresent(value -> span.setAttribute("input.text", value.toString()));
        } catch (Exception e) {
            logger.debug("Failed to extract basic embedding info via reflection", e);
        }
    }
    
    /**
     * Utility method to try getting a value from an object using various getter methods or field names.
     * 
     * @param object The object to extract from
     * @param methodsOrFields Various getter method names or field names to try
     * @return Optional containing the value if found, empty otherwise
     */
    private <T> Optional<Object> tryGetFieldValue(T object, String... methodsOrFields) {
        if (object == null || methodsOrFields == null) {
            return Optional.empty();
        }
        
        Class<?> clazz = object.getClass();
        
        // First try getter methods
        for (String methodName : methodsOrFields) {
            if (methodName.startsWith("get")) {
                try {
                    Method method = clazz.getMethod(methodName);
                    Object result = method.invoke(object);
                    if (result != null) {
                        return Optional.of(result);
                    }
                } catch (Exception e) {
                    // Just try the next method
                }
            }
        }
        
        // Then try fields directly
        for (String fieldName : methodsOrFields) {
            if (!fieldName.startsWith("get")) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object result = field.get(object);
                    if (result != null) {
                        return Optional.of(result);
                    }
                } catch (Exception e) {
                    // Just try the next field
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Ends a span, setting its status based on whether an exception occurred.
     * If an exception is provided, it will be recorded on the span and the span
     * will be marked as ERROR. Otherwise, the span will be marked as OK.
     *
     * @param span      The span to end
     * @param throwable The exception that occurred, or null if no exception
     * @throws IllegalArgumentException if span is null
     */
    public void endSpan(Span span, Throwable throwable) {
        if (span == null) throw new IllegalArgumentException("Span cannot be null");

        if (!span.getSpanContext().isValid()) {
            return;
        }

        try {
            // Apply any context attributes to the span before ending it
            TelemetryContext.applyAttributes(span);

            if (throwable != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Ending span with error: {}", throwable.getMessage());
                }
                span.recordException(throwable);
                span.setStatus(StatusCode.ERROR, throwable.getMessage());

                // Add exception attributes according to OpenInference conventions
                span.setAttribute("exception.type", throwable.getClass().getName());
                span.setAttribute("exception.message", throwable.getMessage());
                span.setAttribute("exception.stacktrace", Arrays.toString(throwable.getStackTrace()));
            } else {
                span.setStatus(StatusCode.OK);
                if (logger.isDebugEnabled()) {
                    logger.debug("Ending span successfully");
                }
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Span details at end:");
                logger.debug("  - Trace ID: {}", span.getSpanContext().getTraceId());
                logger.debug("  - Span ID: {}", span.getSpanContext().getSpanId());
            }
            span.end();
        } catch (Exception e) {
            logger.error("Error while ending span", e);
            span.recordException(e);
            span.end();
        }
    }

    /**
     * Creates a new span for the given operation.
     *
     * @param operationName The name of the operation
     * @param operationType The type of operation
     * @return A new span
     */
    public Span createSpan(String operationName, OperationType operationType) {
        return createSpan(operationName, operationType, SpanKind.INTERNAL);
    }

    /**
     * Creates a new span for the given operation with the specified kind.
     *
     * @param operationName The name of the operation
     * @param operationType The type of operation
     * @param spanKind      The kind of span
     * @return A new span
     */
    public Span createSpan(String operationName, OperationType operationType, SpanKind spanKind) {
        SpanBuilder spanBuilder = new SpanBuilder(tracer, operationName, tracingLimits)
                .setSpanKind(spanKind);

        Span span = spanBuilder.build();

        // Set operation type as a span attribute
        span.setAttribute("operation.type", operationType.name());

        // Store operation type in context for child spans
        TelemetryContext.addAttribute("operation.type", operationType.name());

        return span;
    }

    /**
     * Executes a function within the context of a span.
     *
     * @param operationName The name of the operation
     * @param operationType The type of operation
     * @param function      The function to execute
     * @param <T>           The return type of the function
     * @return The result of the function
     */
    public <T> T executeWithSpan(String operationName, OperationType operationType, Supplier<T> function) {
        return executeWithSpan(operationName, operationType, SpanKind.INTERNAL, function);
    }

    /**
     * Executes a function within the context of a span with the specified kind.
     *
     * @param operationName The name of the operation
     * @param operationType The type of operation
     * @param spanKind      The kind of span
     * @param function      The function to execute
     * @param <T>           The return type of the function
     * @return The result of the function
     */
    public <T> T executeWithSpan(String operationName, OperationType operationType, SpanKind spanKind, Supplier<T> function) {
        // Store the original context before creating the span
        io.opentelemetry.context.Context originalContext = io.opentelemetry.context.Context.current();
        Span currentSpan = Span.fromContext(originalContext);
        boolean hasParentSpan = currentSpan.getSpanContext().isValid();

        if (hasParentSpan) {
            if (logger.isDebugEnabled()) {
                logger.debug("executeWithSpan: Found parent span: Trace ID: {}, Span ID: {}",
                        currentSpan.getSpanContext().getTraceId(),
                        currentSpan.getSpanContext().getSpanId());
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("executeWithSpan: No parent span found, creating root span");
            }
        }

        Span span = createSpan(operationName, operationType, spanKind);

        // Log the created span details
        if (logger.isDebugEnabled()) {
            logger.debug("executeWithSpan: Created span: {}, Trace ID: {}, Span ID: {}",
                    operationName, span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
        }

        if (hasParentSpan) {
            if (logger.isDebugEnabled()) {
                logger.debug("executeWithSpan: Parent-child relationship: Parent span ID: {}, Child span ID: {}",
                        currentSpan.getSpanContext().getSpanId(), span.getSpanContext().getSpanId());
            }
        }

        T result = null; // Initialize result to null
        try (Scope scope = span.makeCurrent()) {
            // Add operation type to context for child spans
            TelemetryContext.addAttribute("operation.type", operationType.name());

            // Execute the function
            result = function.get();
        } catch (Exception e) {
            span.recordException(e);
            endSpan(span, e);
            throw e;
        } finally {
            // End the span with the result
            endSpan(span, result);

            // Restore original context
            if (originalContext != io.opentelemetry.context.Context.root()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("executeWithSpan: Restoring original context after span completion");
                }
                originalContext.makeCurrent();
            }
        }

        return result;
    }

    /**
     * Executes a function within the context of a span, with no return value.
     *
     * @param operationName The name of the operation
     * @param operationType The type of operation
     * @param runnable      The function to execute
     */
    public void executeWithSpan(String operationName, OperationType operationType, Runnable runnable) {
        executeWithSpan(operationName, operationType, SpanKind.INTERNAL, runnable);
    }

    /**
     * Executes a function within the context of a span with the specified kind, with no return value.
     *
     * @param operationName The name of the operation
     * @param operationType The type of operation
     * @param spanKind      The kind of span
     * @param runnable      The function to execute
     */
    public void executeWithSpan(String operationName, OperationType operationType, SpanKind spanKind, Runnable runnable) {
        // Store the original context before creating the span
        io.opentelemetry.context.Context originalContext = io.opentelemetry.context.Context.current();
        Span currentSpan = Span.fromContext(originalContext);
        boolean hasParentSpan = currentSpan.getSpanContext().isValid();

        if (hasParentSpan) {
            if (logger.isDebugEnabled()) {
                logger.debug("executeWithSpan: Found parent span: Trace ID: {}, Span ID: {}",
                        currentSpan.getSpanContext().getTraceId(),
                        currentSpan.getSpanContext().getSpanId());
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("executeWithSpan: No parent span found, creating root span");
            }
        }

        Span span = createSpan(operationName, operationType, spanKind);

        // Log the created span details
        if (logger.isDebugEnabled()) {
            logger.debug("executeWithSpan: Created span: {}, Trace ID: {}, Span ID: {}",
                    operationName, span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId());
        }

        if (hasParentSpan) {
            if (logger.isDebugEnabled()) {
                logger.debug("executeWithSpan: Parent-child relationship: Parent span ID: {}, Child span ID: {}",
                        currentSpan.getSpanContext().getSpanId(), span.getSpanContext().getSpanId());
            }
        }

        try (Scope scope = span.makeCurrent()) {
            // Add operation type to context for child spans
            TelemetryContext.addAttribute("operation.type", operationType.name());

            // Execute the runnable
            runnable.run();
        } catch (Exception e) {
            span.recordException(e);
            endSpan(span, e);
            throw e;
        } finally {
            // End the span
            endSpan(span, null);

            // Restore original context
            if (originalContext != io.opentelemetry.context.Context.root()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("executeWithSpan: Restoring original context after span completion");
                }
                originalContext.makeCurrent();
            }
        }
    }

    /**
     * Ends a span, optionally extracting attributes from a response.
     *
     * @param span     The span to end
     * @param response The response to extract attributes from (may be null)
     * @param <T>      The type of the response
     */
    public <T> void endSpan(Span span, T response) {
        if (span == null) throw new IllegalArgumentException("Span cannot be null");

        if (!span.getSpanContext().isValid()) {
            return;
        }

        try {
            // Apply any context attributes to the span before ending it
            TelemetryContext.applyAttributes(span);

            // Extract attributes from response if available
            if (response != null) {
                OperationType type = getOperationType(span);
                if (type != null) {
                    // Just add the typed attributes without a context
                    addTypedAttributes(span, response, type);
                }
            }

            // Set status to OK for normal completion
            span.setStatus(StatusCode.OK);

            if (logger.isDebugEnabled()) {
                logger.debug("Ending span successfully");
                logger.debug("Span details at end:");
                logger.debug("  - Trace ID: {}", span.getSpanContext().getTraceId());
                logger.debug("  - Span ID: {}", span.getSpanContext().getSpanId());
            }
            span.end();
        } catch (Exception e) {
            logger.error("Error ending span", e);
            span.recordException(e);
            span.end();
        }
    }

    /**
     * Add a dynamic attribute for the current context.
     * This attribute will be propagated with the OpenTelemetry context
     * across async boundaries (e.g., CompletableFuture, thread pools).
     *
     * @param key   The attribute key
     * @param value The attribute value
     */
    public void addContextAttribute(String key, Object value) {
        TelemetryContext.addAttribute(key, value);
    }

    /**
     * Get all dynamic attributes for the current context.
     *
     * @return Map of attribute keys to values
     */
    public Map<String, Object> getContextAttributes() {
        return TelemetryContext.getAttributes();
    }

    /**
     * Get a specific context attribute.
     *
     * @param key The attribute key
     * @return The attribute value or null if not found
     */
    public Object getContextAttribute(String key) {
        return TelemetryContext.getAttribute(key);
    }

    /**
     * Clear all dynamic attributes for the current context.
     * This should be called when finished with the current context
     * to avoid attribute leakage, especially in thread pools.
     */
    public void clearContextAttributes() {
        TelemetryContext.clearAttributes();
    }

    /**
     * Get the operation type from the current context.
     *
     * @return The operation type or null if not found
     */
    public OperationType getOperationType() {
        return TelemetryContext.getOperationType();
    }

    /**
     * Get the operation type from a span.
     *
     * @param span The span to get the operation type from
     * @return The operation type or null if not found
     */
    public OperationType getOperationType(Span span) {
        // In OpenTelemetry, spans are write-only by design
        // We need to use the context to retrieve the operation type
        return TelemetryContext.getOperationType();
    }

    /**
     * Gets all registered typed extractors for a specific operation type.
     * 
     * @param type The operation type to get extractors for
     * @return A map of response classes to extractors, or null if none registered
     */
    public Map<Class<?>, TypedAttributeExtractor<?, ?>> getExtractorsForOperationType(OperationType type) {
        if (type == null) {
            return null;
        }
        
        logger.debug("Getting extractors for operation type: {}", type);
        Map<Class<?>, TypedAttributeExtractor<?, ?>> extractorsForType = typedExtractors.get(type);
        
        if (extractorsForType != null) {
            logger.debug("Found {} extractors for type {}", extractorsForType.size(), type);
            if (logger.isDebugEnabled()) {
                extractorsForType.keySet().forEach(clazz -> 
                    logger.debug("  - Extractor for class: {}", clazz.getName())
                );
            }
        } else {
            logger.debug("No extractors found for operation type: {}", type);
        }
        
        return extractorsForType;
    }
}
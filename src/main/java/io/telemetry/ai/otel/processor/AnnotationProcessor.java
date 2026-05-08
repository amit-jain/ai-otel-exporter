package io.telemetry.ai.otel.processor;

import io.telemetry.ai.otel.annotation.AttributeList;
import io.telemetry.ai.otel.annotation.ExtractAttributes;
import io.telemetry.ai.otel.annotation.TenantId;
import io.telemetry.ai.otel.extractor.TypedAttributeExtractor;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.telemetry.ai.otel.tracing.TelemetryAgentProducer;
import io.opentelemetry.api.trace.Span;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processes telemetry annotations and applies the appropriate extractors.
 * This class serves as a bridge between annotation-based declarations and imperative telemetry code.
 * Uses aggressive caching to minimize reflection overhead.
 */
@ApplicationScoped
public class AnnotationProcessor {
    
    /**
     * Creates a new instance of the AnnotationProcessor.
     * This processor is application-scoped and will be injected where needed.
     */
    public AnnotationProcessor() {}
    
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationProcessor.class);
    
    @Inject
    TelemetryAgentProducer telemetryAgentProducer;
    
    // Cache for method parameter annotations to avoid repeated reflection
    private final ConcurrentHashMap<Method, MethodParameterMetadata> methodParameterCache = new ConcurrentHashMap<>();
    
    // Cache for extractors by parameter type and operation type
    private final ConcurrentHashMap<CacheKey, TypedAttributeExtractor<?, ?>> extractorCache = new ConcurrentHashMap<>();

        /**
         * Key for caching extractors by operation type and parameter type
         */
        private record CacheKey(OperationType operationType, Class<?> parameterType) {

        @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                CacheKey cacheKey = (CacheKey) o;
                return operationType == cacheKey.operationType &&
                        parameterType.equals(cacheKey.parameterType);
            }

            @Override
            public int hashCode() {
                int result = operationType != null ? operationType.hashCode() : 0;
                result = 31 * result + (parameterType != null ? parameterType.hashCode() : 0);
                return result;
            }
    }

    /**
         * Metadata about a method's parameters for annotation processing
         */
        private record MethodParameterMetadata(int tenantIdIndex, List<Integer> extractAttributesIndexes,
                                               List<Integer> attributeListIndexes,
                                               Map<Integer, AttributeList> attributeListAnnotations) {
    }
    
    /**
     * Process annotations on a method's parameters to enhance the current span.
     * Uses caching to minimize reflection overhead on repeated calls.
     * 
     * @param method The method being traced
     * @param args The arguments passed to the method
     * @param span The span to enhance
     * @param operationType The operation type (SEARCH, EMBEDDING, etc.)
     */
    public void processParameterAnnotations(Method method, Object[] args, Span span, OperationType operationType) {
        if (span == null || method == null || args == null) {
            return;
        }
        
        LOG.debug("Processing telemetry annotations for method: {}", method.getName());
        
        // Get cached method parameter metadata or compute it
        MethodParameterMetadata metadata = methodParameterCache.computeIfAbsent(method, this::extractMethodParameterMetadata);
        
        // Extract tenant ID
        String tenantId = extractTenantId(metadata, args);
        
        // Apply core attributes
        enhanceSpanWithCoreAttributes(span, tenantId);
        
        // Process @ExtractAttributes parameters
        processExtractAttributesParameters(metadata, args, span, operationType);
        
        // Process @AttributeList parameters
        processAttributeListParameters(metadata, args, span);
    }
    
    /**
     * Extracts metadata about a method's parameters for caching
     */
    private MethodParameterMetadata extractMethodParameterMetadata(Method method) {
        int tenantIdIndex = -1;
        List<Integer> extractAttributesIndexes = new ArrayList<>();
        List<Integer> attributeListIndexes = new ArrayList<>();
        Map<Integer, AttributeList> attributeListAnnotations = new HashMap<>();
        
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            
            if (param.isAnnotationPresent(TenantId.class)) {
                tenantIdIndex = i;
            }
            
            if (param.isAnnotationPresent(ExtractAttributes.class)) {
                extractAttributesIndexes.add(i);
            }
            
            if (param.isAnnotationPresent(AttributeList.class)) {
                attributeListIndexes.add(i);
                attributeListAnnotations.put(i, param.getAnnotation(AttributeList.class));
            }
        }
        
        return new MethodParameterMetadata(tenantIdIndex, 
                                         extractAttributesIndexes, 
                                         attributeListIndexes,
                                         attributeListAnnotations);
    }
    
    /**
     * Extract tenant ID from parameter metadata
     */
    private String extractTenantId(MethodParameterMetadata metadata, Object[] args) {
        if (metadata.tenantIdIndex >= 0 && metadata.tenantIdIndex < args.length) {
            Object arg = args[metadata.tenantIdIndex];
            if (arg instanceof String) {
                return (String) arg;
            }
        }
        return null;
    }
    
    /**
     * Process all parameters with ExtractAttributes annotation
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processExtractAttributesParameters(MethodParameterMetadata metadata, 
                                                   Object[] args,
                                                   Span span, 
                                                   OperationType operationType) {
        // Get the default agent
        TelemetryAgent agent = telemetryAgentProducer.produceDefaultAgent();
        
        // Process each parameter with @ExtractAttributes
        for (Integer index : metadata.extractAttributesIndexes) {
            if (index < 0 || index >= args.length) {
                continue;
            }
            
            Object arg = args[index];
            if (arg == null) {
                continue;
            }
            
            // Get parameter type and find extractor
            Class<?> paramType = arg.getClass();
            TypedAttributeExtractor extractor = findExtractorForType(paramType, operationType, agent);
            
            if (extractor != null) {
                LOG.debug("Found extractor for parameter type {}: {}", 
                        paramType.getName(), extractor.getClass().getName());
                
                // Get context from extractor
                OperationContext context = null;
                if (extractor.getContext() != null) {
                    context = extractor.getContext();
                }
                
                // Extract attributes using the found extractor
                try {
                    extractor.extractAttributes(span, arg, context, operationType);
                } catch (Exception e) {
                    LOG.warn("Error extracting attributes for parameter of type {}: {}", 
                            paramType.getName(), e.getMessage(), e);
                }
            } else {
                LOG.debug("No extractor found for parameter type: {}", paramType.getName());
            }
        }
    }
    
    /**
     * Finds an appropriate extractor for the given parameter type, using caching for performance
     */
    @SuppressWarnings({"rawtypes"})
    private TypedAttributeExtractor findExtractorForType(Class<?> paramType, 
                                                        OperationType operationType, 
                                                        TelemetryAgent agent) {
        // Safety checks with detailed logging
        LOG.debug("findExtractorForType called with paramType={}, operationType={}, agent={}", 
                 paramType, operationType, agent);
        
        if (paramType == null) {
            LOG.warn("Cannot find extractor for null parameter type");
            return null;
        }
        
        if (operationType == null) {
            LOG.warn("Cannot find extractor for null operation type");
            return null;
        }
        
        if (agent == null) {
            LOG.warn("Cannot find extractor for null agent");
            return null;
        }
        
        try {
            // Create cache key
            CacheKey key = new CacheKey(operationType, paramType);
            LOG.debug("Created cache key: {}", key);
            
            // Check cache first
            TypedAttributeExtractor extractor = extractorCache.get(key);
            if (extractor != null) {
                LOG.debug("Found extractor in cache: {}", extractor.getClass().getName());
                return extractor;
            }
            
            // Get extractors from agent
            Map<Class<?>, TypedAttributeExtractor<?, ?>> extractors = 
                    agent.getExtractorsForOperationType(operationType);
            
            // Handle no extractors case
            if (extractors == null) {
                LOG.warn("No extractors map found for operation type: {}", operationType);
                return null;
            }
            
            if (extractors.isEmpty()) {
                LOG.debug("Extractors map is empty for operation type: {}", operationType);
                return null;
            }
            
            LOG.debug("Found {} extractors for operation type: {}", extractors.size(), operationType);
            
            // Try direct lookup first
            extractor = extractors.get(paramType);
            if (extractor != null) {
                LOG.debug("Found direct match extractor: {}", extractor.getClass().getName());
            }
            
            // If no direct match, look for compatible types (assignable from)
            if (extractor == null) {
                LOG.debug("No direct match found, looking for assignable types for: {}", paramType.getName());
                for (Map.Entry<Class<?>, TypedAttributeExtractor<?, ?>> entry : extractors.entrySet()) {
                    // Skip null keys or values
                    if (entry == null) {
                        LOG.warn("Null entry in extractors map");
                        continue;
                    }
                    
                    if (entry.getKey() == null) {
                        LOG.warn("Null key in extractors map entry");
                        continue;
                    }
                    
                    if (entry.getValue() == null) {
                        LOG.warn("Null value in extractors map for key: {}", entry.getKey().getName());
                        continue;
                    }
                    
                    try {
                        LOG.debug("Checking assignability: {} isAssignableFrom {}", 
                                entry.getKey().getName(), paramType.getName());
                        if (entry.getKey().isAssignableFrom(paramType)) {
                            extractor = entry.getValue();
                            LOG.debug("Found assignable extractor: {}", extractor.getClass().getName());
                            break;
                        }
                    } catch (Exception e) {
                        LOG.warn("Error checking assignability between {} and {}: {}", 
                                entry.getKey().getName(), paramType.getName(), e.getMessage());
                    }
                }
            }
            
            // Cache the result only if we found an extractor
            if (extractor != null) {
                LOG.debug("Caching extractor {} for key {}", extractor.getClass().getName(), key);
                extractorCache.put(key, extractor);
            } else {
                LOG.debug("No extractor found for parameter type: {}", paramType.getName());
            }
            
            return extractor;
        } catch (Exception e) {
            LOG.warn("Error finding extractor for type {}: {}",
                    paramType.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Process all parameters with AttributeList annotation
     */
    private void processAttributeListParameters(MethodParameterMetadata metadata, Object[] args, Span span) {
        for (Integer index : metadata.attributeListIndexes) {
            if (index < 0 || index >= args.length) {
                continue;
            }
            
            Object arg = args[index];
            if (arg == null) {
                continue;
            }
            
            AttributeList annotation = metadata.attributeListAnnotations.get(index);
            if (annotation == null) {
                continue;
            }
            
            String attributeName = annotation.attributeName();
            String delimiter = annotation.delimiter();
            int maxElements = annotation.maxElements();
            
            try {
                if (arg instanceof Collection) {
                    List<String> elements = new ArrayList<>();
                    Collection<?> collection = (Collection<?>) arg;

                    // Convert all elements to strings
                    for (Object item : collection) {
                        if (item != null) {
                            elements.add(item.toString());
                        }
                    }
                    
                    if (!elements.isEmpty()) {
                        // Apply max elements limit if needed
                        if (maxElements > 0 && elements.size() > maxElements) {
                            elements = elements.subList(0, maxElements);
                        }
                        
                        // Join elements with delimiter
                        String value = String.join(delimiter, elements);
                        span.setAttribute(attributeName, value);
                        
                        LOG.debug("Set attribute {} to {}", attributeName, value);
                    }
                } else {
                    LOG.warn("AttributeList parameter is not a Collection: {}", 
                            arg.getClass().getName());
                }
            } catch (Exception e) {
                LOG.warn("Error processing AttributeList parameter: {}", 
                        e.getMessage(), e);
            }
        }
    }
    
    /**
     * Enhance a span with core attributes such as tenant ID.
     */
    private void enhanceSpanWithCoreAttributes(Span span, String tenantId) {
        if (span == null) {
            return;
        }
        
        // Set tenant ID if available
        if (tenantId != null && !tenantId.isEmpty()) {
            span.setAttribute("tenant.id", tenantId);
        }
    }
} 
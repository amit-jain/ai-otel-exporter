package io.telemetry.ai.otel.metrics;

import io.telemetry.ai.otel.config.MetricsConfig;
import io.telemetry.ai.otel.config.MetricsConstants;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.tracing.TelemetryContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of MetricsExporter that records metrics using Micrometer.
 */
public class DefaultMetricsExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(DefaultMetricsExporter.class);
    
    private final MeterRegistry registry;
    private final MetricsConfig config;
    
    /**
     * Creates a new DefaultMetricsExporter.
     *
     * @param registry The meter registry to use
     * @param config The metrics configuration
     */
    public DefaultMetricsExporter(MeterRegistry registry, MetricsConfig config) {
        this.registry = registry;
        this.config = config;
        
        // Keep critical initialization logs at INFO level
        logger.info("Created DefaultMetricsExporter with registry: {}, service: {}, prefix: {}", 
                   registry.getClass().getSimpleName(), config.getServiceName(), config.getMetricsPrefix());
                   
        if (registry == null) {
            logger.warn("Metrics registry is null - metrics will not be recorded");
        }
    }
    
    @Override
    public void recordTimer(String name, String[] tags, long timeInMillis) {
        logger.debug("=== DefaultMetricsExporter.recordTimer CALLED === name={}, duration={}ms", name, timeInMillis);
                  
        if (registry == null) {
            logger.warn("Null registry, not recording timer: {}", name);
            return;
        }
        
        try {
            String metricName = getMetricName(name);
            logger.debug("Recording timer: {} -> {} with duration={}ms", name, metricName, timeInMillis);
            
            // Convert tag array to Micrometer Tags format
            Tags meterTags = Tags.empty();
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length) {
                    meterTags = meterTags.and(tags[i], tags[i + 1]);
                    logger.debug("  Adding tag: {}={}", tags[i], tags[i + 1]);
                }
            }
            
            // Add service tag if not already present
            boolean hasServiceTag = false;
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length && "service".equals(tags[i])) {
                    hasServiceTag = true;
                    break;
                }
            }
            
            if (!hasServiceTag) {
                meterTags = meterTags.and("service", config.getServiceName());
                logger.debug("  Adding default service tag: service={}", config.getServiceName());
            }
            
            logger.debug("RECORDING TIMER: metric={}, tags={}, duration={}ms", metricName, meterTags, timeInMillis);
            
            // Create and record timer
            Timer timer = Timer.builder(metricName)
                         .tags(meterTags)
                         .register(registry);
            
            timer.record(timeInMillis, TimeUnit.MILLISECONDS);
            
            logger.debug("TIMER RECORDED SUCCESSFULLY: metric={}, count={}, totalTime={}ms", 
                      metricName, timer.count(), timer.totalTime(TimeUnit.MILLISECONDS));
            
        } catch (Exception e) {
            logger.error("Error recording timer: {} - {}", name, e.getMessage(), e);
            // Don't throw here, just log
        }
    }
    
    @Override
    public void incrementCounter(String name, String[] tags, double amount) {
                  
        if (registry == null) {
            logger.debug("Null registry, not incrementing counter: {}", name);
            return;
        }
        
        try {
            String metricName = getMetricName(name);
                     
            // Convert tag array to Micrometer Tags format
            Tags meterTags = Tags.empty();
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length) {
                    meterTags = meterTags.and(tags[i], tags[i + 1]);
                }
            }
            
            // Add service tag if not already present
            boolean hasServiceTag = false;
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length && "service".equals(tags[i])) {
                    hasServiceTag = true;
                    break;
                }
            }
            
            if (!hasServiceTag) {
                meterTags = meterTags.and("service", config.getServiceName());
            }
            
            // Create and increment counter
            Counter counter = Counter.builder(metricName)
                           .tags(meterTags)
                           .register(registry);
            
            counter.increment(amount);
            
        } catch (Exception e) {
            logger.error("Error incrementing counter: {}", e.getMessage(), e);
            // Don't throw here, just log
        }
    }
    
    @Override
    public void recordGauge(String name, String[] tags, double value) {
        if (registry == null) {
            logger.debug("Null registry, not recording gauge: {}", name);
            return;
        }
        
        try {
            String metricName = getMetricName(name);
            Map<String, String> tagMap = formatTagsAsMap(tags);
            
            // Convert the tag map to micrometer Tags
            Tags meterTags = Tags.of(tagMap.entrySet().stream()
                .map(entry -> io.micrometer.core.instrument.Tag.of(entry.getKey(), entry.getValue()))
                .toArray(io.micrometer.core.instrument.Tag[]::new));
            
            // Use AtomicReference with a Double instead of AtomicDouble
            java.util.concurrent.atomic.AtomicReference<Double> atomicValue = 
                new java.util.concurrent.atomic.AtomicReference<>(value);
            
            registry.gauge(metricName, meterTags, atomicValue, ref -> ref.get());
            
            // Record to span if available
            Span currentSpan = Span.current();
            if (currentSpan != null && !currentSpan.equals(Span.getInvalid())) {
                currentSpan.setAttribute(name, value);
                logger.debug("Recorded gauge attribute to span: {}={}", name, value);
            }
        } catch (Exception e) {
            logger.error("Failed to record gauge metric: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void recordOperationMetrics(OperationType operationType) {
        logger.debug("Recording operation metrics: {}", operationType);
                  
        if (registry == null) {
            logger.debug("Registry null, skipping operation metrics recording");
            return;
        }
        
        try {
            // Get tenant ID from context
            String tenantId = (String) TelemetryContext.getAttribute("tenant.id");
            logger.debug("Recording metrics from TelemetryContext: operation={}, tenant={}", 
                      operationType, tenantId);
            
            // Get operation name
            String operation = operationType != null ? 
                             operationType.name().toLowerCase() : 
                             MetricsConstants.UNKNOWN_OPERATION;
            
            // Build basic tags
            String[] tags = {
                MetricsConstants.TAG_OPERATION, operation
            };
            String[] enhancedTags = ensureServiceAndTenantTags(tags, tenantId);
            
            // Get duration from context
            Long durationMs = null;
            Object durationObj = TelemetryContext.getAttribute("search.latency_ms");
            if (durationObj instanceof Number) {
                durationMs = ((Number) durationObj).longValue();
            } else {
                // Check for operation-specific duration
                durationObj = TelemetryContext.getAttribute(operation + ".duration_ms");
                if (durationObj instanceof Number) {
                    durationMs = ((Number) durationObj).longValue();
                } else {
                    // Check for generic duration
                    durationObj = TelemetryContext.getAttribute("duration_ms");
                    if (durationObj instanceof Number) {
                        durationMs = ((Number) durationObj).longValue();
                    } else {
                        // Default to 0 if no duration found
                        durationMs = 0L;
                    }
                }
            }
            
            // Record duration metric
            recordTimer(MetricsConstants.METRIC_OPERATION_DURATION, enhancedTags, durationMs);
            
            // Get result count
            Long resultCount = null;
            Object resultCountObj = TelemetryContext.getAttribute("openinference.retrieval.documents.count");
            if (resultCountObj instanceof Number) {
                resultCount = ((Number) resultCountObj).longValue();
                incrementCounter(MetricsConstants.METRIC_OPERATION_RESULT_COUNT, enhancedTags, resultCount);
            }
            
            // Get success count - always 1 (successful operation)
            incrementCounter(MetricsConstants.METRIC_OPERATION_SUCCESS_COUNT, enhancedTags, 1);
        } catch (Exception e) {
            logger.error("Error in recordOperationMetrics", e);
            // Print stack trace to log for critical errors
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            logger.error("Stack trace: {}", sw.toString());
        }
    }
    
    @Override
    public void recordOperationMetrics(OperationType operationType, String tenantId, 
                                 long durationMs, Long resultCount, 
                                 Long successCount, Long failureCount) {
        logger.debug("Recording detailed operation metrics: type={}, tenant={}, duration={}ms", 
                  operationType, tenantId, durationMs);
        
        if (registry == null) {
            logger.error("Null registry, not recording operation metrics: {}", operationType);
            return;
        }
        
        try {
            // Get operation name (lowercase) for use in metrics tags
            String operationName = operationType.name().toLowerCase();
            
            // Create tags array with tenant and operation
            String[] tags = ensureServiceAndTenantTags(new String[]{
                "operation", operationName,
                "tenant", tenantId
            }, tenantId);
            
            // Record operation duration
            recordTimer(MetricsConstants.METRIC_OPERATION_DURATION, tags, durationMs);
            
            // Record result count metric if available
            if (resultCount != null) {
                incrementCounter(MetricsConstants.METRIC_OPERATION_RESULT_COUNT, tags, resultCount);
            }
            
            // Record success count metric if available
            if (successCount != null) {
                incrementCounter(MetricsConstants.METRIC_OPERATION_SUCCESS_COUNT, tags, successCount);
            }
            
            // Record failure count metric if available
            if (failureCount != null && failureCount > 0) {
                incrementCounter(MetricsConstants.METRIC_OPERATION_FAILURE_COUNT, tags, failureCount);
            }
        } catch (Exception e) {
            logger.error("Error recording operation metrics", e);
        }
    }
    
    /**
     * Ensures that service and tenant tags are included in the tags array.
     * Modifies the input tags array if necessary and returns a new array with all required tags.
     * 
     * @param tags The original tags array
     * @param tenantId The tenant ID
     * @return A new tags array with service and tenant tags included
     */
    private String[] ensureServiceAndTenantTags(String[] tags, String tenantId) {
        // First convert tags to a map for easier manipulation
        Map<String, String> tagMap = formatTagsAsMap(tags);
        
        // Check if we have essential tags
        boolean hasOperationTag = tagMap.containsKey(MetricsConstants.TAG_OPERATION);
        boolean hasTenantTag = tagMap.containsKey(MetricsConstants.TAG_TENANT);
        boolean hasServiceTag = tagMap.containsKey(MetricsConstants.TAG_SERVICE);
        
        // Add missing tags with appropriate defaults
        if (!hasOperationTag) {
            logger.warn("Operation tag is missing - this should not happen!");
            tagMap.put(MetricsConstants.TAG_OPERATION, MetricsConstants.UNKNOWN_OPERATION);
        }
        
        if (!hasTenantTag && tenantId != null) {
            tagMap.put(MetricsConstants.TAG_TENANT, tenantId);
        } else if (!hasTenantTag) {
            tagMap.put(MetricsConstants.TAG_TENANT, MetricsConstants.UNKNOWN_TENANT);
        }
        
        if (!hasServiceTag) {
            String serviceName = config.getServiceName();
            tagMap.put(MetricsConstants.TAG_SERVICE, serviceName);
        }
        
        // Convert map back to array
        String[] result = new String[tagMap.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : tagMap.entrySet()) {
            result[i++] = entry.getKey();
            result[i++] = entry.getValue();
        }
        
        return result;
    }
    
    /**
     * Helper method to convert a tags array to a string for logging.
     *
     * @param tags The tags array to convert to string
     * @return A string representation of the tags
     */
    private String tagsToString(String[] tags) {
        if (tags == null || tags.length == 0) {
            return "[]";
        }
        
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tags.length; i += 2) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(tags[i]).append("=");
            if (i + 1 < tags.length) {
                sb.append(tags[i + 1]);
            } else {
                sb.append("<MISSING VALUE>");
            }
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Formats an array of tags in the format ["key1", "value1", "key2", "value2"] 
     * into a map for gauge metrics.
     *
     * @param tags The tags to format
     * @return The formatted tags as a map
     */
    private Map<String, String> formatTagsAsMap(String[] tags) {
        Map<String, String> tagMap = new HashMap<>();
        
        if (tags != null && tags.length > 0) {
            for (int i = 0; i < tags.length; i += 2) {
                if (i + 1 < tags.length) {
                    tagMap.put(tags[i], tags[i + 1]);
                }
            }
        }
        
        // Add service name tag
        tagMap.put("service", config.getServiceName());
        
        return tagMap;
    }
    
    /**
     * Gets the full metric name with prefix.
     *
     * @param name The metric name
     * @return The full metric name with prefix
     */
    private String getMetricName(String name) {
        String prefix = config.getMetricsPrefix();
        if (prefix == null || prefix.isEmpty()) {
            return name;
        }
        
        // Ensure prefix ends with underscore for proper Prometheus naming
        if (!prefix.endsWith("_")) {
            prefix = prefix + "_";
        }
        
        return prefix + name;
    }
    
    /**
     * Gets the meter registry used by this exporter.
     *
     * @return The MeterRegistry instance
     */
    public MeterRegistry getRegistry() {
        return this.registry;
    }
} 
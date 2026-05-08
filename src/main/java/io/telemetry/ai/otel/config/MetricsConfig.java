package io.telemetry.ai.otel.config;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration for metrics collection and export.
 * Controls whether and how metrics are collected and exported.
 * 
 * <p>When enabled, the following metrics will be emitted to Prometheus:</p>
 * <ul>
 *   <li>{prefix}operation_duration - Duration of operations in milliseconds</li>
 *   <li>{prefix}operation_result_count - Count of results from operations</li>
 *   <li>{prefix}operation_success_count - Count of successful operations</li>
 *   <li>{prefix}operation_failure_count - Count of failed operations</li>
 * </ul>
 * 
 * <p>Each metric includes the following labels/tags:</p>
 * <ul>
 *   <li>operation - The operation type (search, embedding, enrichment)</li>
 *   <li>service - The service name from configuration</li>
 *   <li>tenant - The tenant ID if available, "unknown" otherwise</li>
 * </ul>
 * 
 * <p>To enable metrics collection for a method, use the @Trace annotation with collectMetrics=true:</p>
 * <pre>
 * &#64;Trace(
 *     spanName = "my_operation",
 *     operationType = OperationType.SEARCH,
 *     collectMetrics = true
 * )
 * </pre>
 */
@ApplicationScoped
@Getter
public class MetricsConfig {
    private static final Logger logger = LoggerFactory.getLogger(MetricsConfig.class);
    
    private static final String METRICS_ENABLED_PROPERTY = "AI_OTEL_METRICS_ENABLED";
    private static final String METRICS_ENABLED_DEFAULT = "false";
    
    private static final String METRICS_PREFIX_PROPERTY = "AI_OTEL_METRICS_PREFIX";
    private static final String METRICS_PREFIX_DEFAULT = "ai_";
    
    private static final String SERVICE_NAME_PROPERTY = "SERVICE_NAME";
    private static final String SERVICE_NAME_DEFAULT = "unknown_service";
    
    private final boolean metricsEnabled;
    private final String metricsPrefix;
    private final String serviceName;
    
    /**
     * Default constructor for CDI.
     * Creates a MetricsConfig with settings from system properties.
     */
    public MetricsConfig() {
        String metricsEnabledValue = getConfigValue(METRICS_ENABLED_PROPERTY, METRICS_ENABLED_DEFAULT);
        this.metricsEnabled = Boolean.parseBoolean(metricsEnabledValue);
        
        // Get metrics prefix from configuration
        this.metricsPrefix = getConfigValue(METRICS_PREFIX_PROPERTY, METRICS_PREFIX_DEFAULT);
        
        // Get service name from the standard SERVICE_NAME property
        this.serviceName = getConfigValue(SERVICE_NAME_PROPERTY, SERVICE_NAME_DEFAULT);
        
        logger.info("Metrics configuration: enabled={}, prefix={}, service={}", 
                  metricsEnabled, metricsPrefix, serviceName);
    }
    
    /**
     * Creates a new MetricsConfig with the specified settings.
     *
     * @param metricsEnabled Whether metrics collection is enabled
     * @param metricsPrefix The prefix to apply to all metrics
     * @param serviceName The service name to include in metric labels
     */
    public MetricsConfig(boolean metricsEnabled, String metricsPrefix, String serviceName) {
        this.metricsEnabled = metricsEnabled;
        this.metricsPrefix = metricsPrefix != null ? metricsPrefix : METRICS_PREFIX_DEFAULT;
        this.serviceName = serviceName != null ? serviceName : SERVICE_NAME_DEFAULT;
    }
    
    /**
     * Gets a configuration value from environment or system properties.
     * Prioritizes environment variables over system properties.
     *
     * @param key          The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    private static String getConfigValue(String key, String defaultValue) {
        // First try environment variable
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            logger.debug("Found configuration in environment variable: {}={}", key, value);
            return value;
        }
        
        // Then try system property
        value = System.getProperty(key);
        if (value != null && !value.isEmpty()) {
            logger.debug("Found configuration in system property: {}={}", key, value);
            return value;
        }
        
        // Fall back to default
        logger.debug("Using default configuration value: {}={}", key, defaultValue);
        return defaultValue;
    }
} 
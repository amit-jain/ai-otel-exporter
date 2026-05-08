package io.telemetry.ai.otel.config;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_BATCH_SIZE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_EXPORT_TIMEOUT_SECONDS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_LOG_EMBEDDINGS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_MAX_ATTRIBUTES_PER_SPAN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_MAX_EVENTS_PER_SPAN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_MAX_SPANS_PER_TRACE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_MAX_SPAN_SIZE_BYTES;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_MAX_TRACES_PER_SECOND;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_OTLP_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_OTLP_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_PII_DETECTION_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_QUEUE_SIZE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SAMPLE_ERRORS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SAMPLING_RATE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SCHEDULE_DELAY_MS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SEARCH_SYSTEM;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SERVICE_NAME;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.LOG_EMBEDDINGS_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTEL_BSP_MAX_EXPORT_BATCH_SIZE_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTEL_BSP_MAX_QUEUE_SIZE_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTEL_BSP_SCHEDULE_DELAY_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTEL_EXPORTER_OTLP_TIMEOUT_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTLP_EXPORTER_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.OTLP_EXPORT_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.SEARCH_SYSTEM_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.SERVICE_NAME_PROPERTY;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_MAX_ATTRIBUTES_PER_SPAN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_MAX_EVENTS_PER_SPAN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_MAX_SPANS_PER_TRACE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_MAX_SPAN_SIZE_BYTES;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_MAX_TRACES_PER_SECOND;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_SAMPLE_ERRORS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.TRACING_SAMPLING_RATE;

/**
 * Configuration class for telemetry settings.
 * Provides a centralized configuration for OpenTelemetry settings including
 * service endpoints, batch processing parameters, tracing limits, and Quarkus integration.
 * Uses environment variables and system properties for configuration with sensible defaults.
 * Environment variables take precedence over system properties.
 */
@Data
@Builder
public class TelemetryConfig {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryConfig.class);

    // Static cache for the configuration to prevent repeated loading
    private static final AtomicReference<TelemetryConfig> cachedConfig = new AtomicReference<>();

    // No longer need to define default values here as they come from TelemetryConfigConstants

    /**
     * The name of the service for resource attribution
     */
    private final String serviceName;
    /**
     * The OTLP endpoint URL for exporting telemetry data
     */
    private final String otlpEndpoint;
    /**
     * Whether OTLP export is enabled.
     * When set to false, the system will:
     * 1. Not create or export any spans
     * 2. Skip attribute collection
     * 3. Use no-op implementations for exporters
     * This provides a way to completely disable telemetry without changing code.
     */
    private final boolean otlpEnabled;

    /**
     * The identifier for the search system being used
     */
    private final String searchSystem;

    /**
     * Whether to log embedding vectors (useful for debugging)
     */
    private final boolean logEmbeddings;

    /**
     * Whether PII detection and anonymization is enabled
     * -- GETTER --
     *  Gets whether PII detection and anonymization is enabled.
     *
     * @return Whether PII detection is enabled

     */
    private final boolean piiDetectionEnabled;

    /**
     * Maximum number of spans to include in each export batch
     */
    private final int batchSize;
    /**
     * Maximum number of spans that can be queued for export
     */
    private final int queueSize;
    /**
     * Delay between scheduled batch exports in milliseconds
     */
    private final int scheduleDelayMs;
    /**
     * Timeout for export operations in seconds
     */
    private final int exportTimeoutSeconds;

    /**
     * Configuration for tracing limits and sampling
     */
    private final TracingLimits tracingLimits;

    /**
     * Helper method to get configuration value from environment variables or system properties.
     * Environment variables take precedence over system properties.
     *
     * @param propertyName The system property name
     * @param envVarName   The environment variable name
     * @param defaultValue The default value if neither is set
     * @return The configuration value
     */
    private static String getConfigValue(String propertyName, String envVarName, String defaultValue) {
        // First check environment variable (higher priority)
        String value = System.getenv(envVarName);

        // If not found, check system property
        if (value == null || value.isEmpty()) {
            value = System.getProperty(propertyName);
        }

        // If still not found, use default
        if (value == null || value.isEmpty()) {
            value = defaultValue;
        }

        return value;
    }

    /**
     * Helper method to get boolean configuration value from environment variables or system properties.
     * Environment variables take precedence over system properties.
     *
     * @param propertyName The system property name
     * @param envVarName   The environment variable name
     * @param defaultValue The default value if neither is set
     * @return The boolean configuration value
     */
    private static boolean getBooleanConfigValue(String propertyName, String envVarName, boolean defaultValue) {
        // First check environment variable (higher priority)
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            return Boolean.parseBoolean(envValue);
        }

        // If not found, check system property
        String propValue = System.getProperty(propertyName);
        if (propValue != null && !propValue.isEmpty()) {
            return Boolean.parseBoolean(propValue);
        }

        // If still not found, use default
        return defaultValue;
    }

    /**
     * Helper method to get integer configuration value from environment variables or system properties.
     * Environment variables take precedence over system properties.
     *
     * @param propertyName The system property name
     * @param envVarName   The environment variable name
     * @param defaultValue The default value if neither is set
     * @return The integer configuration value
     */
    private static int getIntConfigValue(String propertyName, String envVarName, int defaultValue) {
        // First check environment variable (higher priority)
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for environment variable {}: {}", envVarName, envValue);
            }
        }

        // If not found or invalid, check system property
        String propValue = System.getProperty(propertyName);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for system property {}: {}", propertyName, propValue);
            }
        }

        // If still not found or invalid, use default
        return defaultValue;
    }

    /**
     * Helper method to get double configuration value from environment variables or system properties.
     * Environment variables take precedence over system properties.
     *
     * @param propertyName The system property name
     * @param envVarName   The environment variable name
     * @param defaultValue The default value if neither is set
     * @return The double configuration value
     */
    private static double getDoubleConfigValue(String propertyName, String envVarName, double defaultValue) {
        // First check environment variable (higher priority)
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Double.parseDouble(envValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid double value for environment variable {}: {}", envVarName, envValue);
            }
        }

        // If not found or invalid, check system property
        String propValue = System.getProperty(propertyName);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Double.parseDouble(propValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid double value for system property {}: {}", propertyName, propValue);
            }
        }

        // If still not found or invalid, use default
        return defaultValue;
    }

    /**
     * Helper method to get long configuration value from environment variables or system properties.
     * Environment variables take precedence over system properties.
     *
     * @param propertyName The system property name
     * @param envVarName   The environment variable name
     * @param defaultValue The default value if neither is set
     * @return The long configuration value
     */
    private static long getLongConfigValue(String propertyName, String envVarName, long defaultValue) {
        // First check environment variable (higher priority)
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Long.parseLong(envValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid long value for environment variable {}: {}", envVarName, envValue);
            }
        }

        // If not found or invalid, check system property
        String propValue = System.getProperty(propertyName);
        if (propValue != null && !propValue.isEmpty()) {
            try {
                return Long.parseLong(propValue);
            } catch (NumberFormatException e) {
                logger.warn("Invalid long value for system property {}: {}", propertyName, propValue);
            }
        }

        // If still not found or invalid, use default
        return defaultValue;
    }

    /**
     * Creates a TelemetryConfig instance from system properties and environment variables.
     * This is the primary factory method for creating a configuration instance.
     * The configuration is cached on first load to prevent repeated loading and logging.
     * Uses AtomicReference for thread-safe caching.
     *
     * @return A cached TelemetryConfig instance with values from system properties
     */
    public static TelemetryConfig fromSystemProperties() {
        TelemetryConfig config = cachedConfig.get();
        if (config == null) {
            logger.info("Loading telemetry configuration from system properties and environment variables");
            
            // Build tracing limits configuration
            TracingLimits tracingLimits = TracingLimits.builder()
                    .samplingRate(getDoubleConfigValue(TRACING_SAMPLING_RATE, TRACING_SAMPLING_RATE, DEFAULT_SAMPLING_RATE))
                    .sampleErrors(getBooleanConfigValue(TRACING_SAMPLE_ERRORS, TRACING_SAMPLE_ERRORS, DEFAULT_SAMPLE_ERRORS))
                    .maxTracesPerSecond(getIntConfigValue(TRACING_MAX_TRACES_PER_SECOND, TRACING_MAX_TRACES_PER_SECOND, DEFAULT_MAX_TRACES_PER_SECOND))
                    .maxSpansPerTrace(getIntConfigValue(TRACING_MAX_SPANS_PER_TRACE, TRACING_MAX_SPANS_PER_TRACE, DEFAULT_MAX_SPANS_PER_TRACE))
                    .maxAttributesPerSpan(getIntConfigValue(TRACING_MAX_ATTRIBUTES_PER_SPAN, TRACING_MAX_ATTRIBUTES_PER_SPAN, DEFAULT_MAX_ATTRIBUTES_PER_SPAN))
                    .maxEventsPerSpan(getIntConfigValue(TRACING_MAX_EVENTS_PER_SPAN, TRACING_MAX_EVENTS_PER_SPAN, DEFAULT_MAX_EVENTS_PER_SPAN))
                    .maxSpanSizeBytes(getLongConfigValue(TRACING_MAX_SPAN_SIZE_BYTES, TRACING_MAX_SPAN_SIZE_BYTES, DEFAULT_MAX_SPAN_SIZE_BYTES))
                    .build();
            
            // Build the main configuration
            config = TelemetryConfig.builder()
                    .serviceName(getConfigValue(SERVICE_NAME_PROPERTY, SERVICE_NAME_PROPERTY, DEFAULT_SERVICE_NAME))
                    .otlpEndpoint(getConfigValue(OTLP_EXPORTER_PROPERTY, OTLP_EXPORTER_PROPERTY, DEFAULT_OTLP_ENDPOINT))
                    .otlpEnabled(getBooleanConfigValue(OTLP_EXPORT_PROPERTY, OTLP_EXPORT_PROPERTY, DEFAULT_OTLP_ENABLED))
                    .searchSystem(getConfigValue(SEARCH_SYSTEM_PROPERTY, SEARCH_SYSTEM_PROPERTY, DEFAULT_SEARCH_SYSTEM))
                    .logEmbeddings(getBooleanConfigValue(LOG_EMBEDDINGS_PROPERTY, LOG_EMBEDDINGS_PROPERTY, DEFAULT_LOG_EMBEDDINGS))
                    .piiDetectionEnabled(getBooleanConfigValue(PII_DETECTOR_ENABLED, PII_DETECTOR_ENABLED, DEFAULT_PII_DETECTION_ENABLED))
                    
                    // Batch processor settings
                    .batchSize(getIntConfigValue(OTEL_BSP_MAX_EXPORT_BATCH_SIZE_PROPERTY, OTEL_BSP_MAX_EXPORT_BATCH_SIZE_PROPERTY, DEFAULT_BATCH_SIZE))
                    .queueSize(getIntConfigValue(OTEL_BSP_MAX_QUEUE_SIZE_PROPERTY, OTEL_BSP_MAX_QUEUE_SIZE_PROPERTY, DEFAULT_QUEUE_SIZE))
                    .scheduleDelayMs(getIntConfigValue(OTEL_BSP_SCHEDULE_DELAY_PROPERTY, OTEL_BSP_SCHEDULE_DELAY_PROPERTY, DEFAULT_SCHEDULE_DELAY_MS))
                    .exportTimeoutSeconds(getIntConfigValue(OTEL_EXPORTER_OTLP_TIMEOUT_PROPERTY, OTEL_EXPORTER_OTLP_TIMEOUT_PROPERTY, DEFAULT_EXPORT_TIMEOUT_SECONDS))
                    
                    // Tracing limits
                    .tracingLimits(tracingLimits)
                    .build();
            
            logger.info("Telemetry configuration loaded: serviceName={}, otlpEnabled={}, endpoint={}",
                    config.getServiceName(), config.isOtlpEnabled(), config.getOtlpEndpoint());
            
            // Atomically set the config if it's still null, otherwise use the one set by another thread
            if (!cachedConfig.compareAndSet(null, config)) {
                // Another thread set it first, use that one instead
                config = cachedConfig.get();
            }
        }
        
        return config;
    }

    /**
     * Clears the cached configuration, forcing it to be reloaded on the next call to fromSystemProperties().
     * This method is primarily intended for testing purposes or when configuration changes at runtime.
     */
    public static void clearCache() {
        cachedConfig.set(null);
        logger.debug("Telemetry configuration cache cleared");
    }

    /**
     * Checks if the configuration is currently cached.
     * This method is primarily intended for testing purposes.
     * 
     * @return true if configuration is cached, false otherwise
     */
    public static boolean isCached() {
        return cachedConfig.get() != null;
    }

}
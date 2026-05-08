package io.telemetry.ai.otel.config;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Configuration and runtime management of tracing limits and sampling.
 * Provides controls for sampling rates, resource limits, and runtime state tracking
 * to prevent overwhelming the telemetry system while ensuring critical data is captured.
 * Configuration can be provided via environment variables or system properties.
 * Environment variables take precedence over system properties.
 */
@Data
@Builder
public class TracingLimits {
    private static final Logger logger = LoggerFactory.getLogger(TracingLimits.class);

    // Sampling configuration
    /**
     * Sampling rate between 0.0 and 1.0
     */
    @Builder.Default
    private double samplingRate = 1.0;
    /**
     * Whether to always sample error spans regardless of sampling rate
     */
    @Builder.Default
    private boolean sampleErrors = true;
    /**
     * Maximum number of traces to collect per second
     */
    @Builder.Default
    private int maxTracesPerSecond = 1000;

    // Resource limits
    /**
     * Maximum number of spans allowed per trace
     */
    @Builder.Default
    private int maxSpansPerTrace = 100;
    /**
     * Maximum number of attributes allowed per span
     */
    @Builder.Default
    private int maxAttributesPerSpan = 32;
    /**
     * Maximum number of events allowed per span
     */
    @Builder.Default
    private int maxEventsPerSpan = 128;
    /**
     * Maximum size of a span in bytes
     */
    @Builder.Default
    private long maxSpanSizeBytes = 5 * 1024;

    // Runtime state
    /**
     * Counter for spans dropped due to limits
     */
    @Builder.Default
    private final AtomicLong droppedSpans = new AtomicLong(0);
    /**
     * Counter for traces in the current second
     */
    @Builder.Default
    private final AtomicLong traceCount = new AtomicLong(0);
    /**
     * Current second for rate limiting
     */
    @Builder.Default
    private volatile long currentSecond = System.currentTimeMillis() / 1000;
    /**
     * Map tracking number of spans per trace
     */
    @Builder.Default
    private final ConcurrentHashMap<String, AtomicLong> spansPerTrace = new ConcurrentHashMap<>();

    /**
     * Creates a new TracingLimits instance with default settings.
     * This constructor is required for frameworks that use reflection.
     */
    public TracingLimits() {
        this.samplingRate = 1.0;
        this.sampleErrors = true;
        this.maxTracesPerSecond = 1000;
        this.maxSpansPerTrace = 100;
        this.maxAttributesPerSpan = 32;
        this.maxEventsPerSpan = 128;
        this.maxSpanSizeBytes = 5 * 1024;
        this.droppedSpans = new AtomicLong(0);
        this.traceCount = new AtomicLong(0);
        this.currentSecond = System.currentTimeMillis() / 1000;
        this.spansPerTrace = new ConcurrentHashMap<>();
    }

    /**
     * All-args constructor required for Lombok @Builder
     */
    public TracingLimits(double samplingRate, boolean sampleErrors, int maxTracesPerSecond, 
                         int maxSpansPerTrace, int maxAttributesPerSpan, int maxEventsPerSpan, 
                         long maxSpanSizeBytes, AtomicLong droppedSpans, AtomicLong traceCount, 
                         long currentSecond, ConcurrentHashMap<String, AtomicLong> spansPerTrace) {
        this.samplingRate = samplingRate;
        this.sampleErrors = sampleErrors;
        this.maxTracesPerSecond = maxTracesPerSecond;
        this.maxSpansPerTrace = maxSpansPerTrace;
        this.maxAttributesPerSpan = maxAttributesPerSpan;
        this.maxEventsPerSpan = maxEventsPerSpan;
        this.maxSpanSizeBytes = maxSpanSizeBytes;
        this.droppedSpans = droppedSpans;
        this.traceCount = traceCount;
        this.currentSecond = currentSecond;
        this.spansPerTrace = spansPerTrace;
    }

    /**
     * Helper method to get configuration value from environment variables or system properties.
     * Environment variables take precedence over system properties.
     *
     * @param propertyName The system property name
     * @param envVarName   The environment variable name
     * @param defaultValue The default value if neither is set
     * @return The configuration value
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
     * Default configuration with reasonable limits, configurable via environment variables or system properties
     */
    public static final TracingLimits DEFAULT = TracingLimits.builder()
            .samplingRate(getDoubleConfigValue("TRACING_SAMPLING_RATE", "TRACING_SAMPLING_RATE", 1.0))
            .sampleErrors(getBooleanConfigValue("TRACING_SAMPLE_ERRORS", "TRACING_SAMPLE_ERRORS", true))
            .maxTracesPerSecond(getIntConfigValue("TRACING_MAX_TRACES_PER_SECOND", "TRACING_MAX_TRACES_PER_SECOND", 1000))
            .maxSpansPerTrace(getIntConfigValue("TRACING_MAX_SPANS_PER_TRACE", "TRACING_MAX_SPANS_PER_TRACE", 100))
            .maxAttributesPerSpan(getIntConfigValue("TRACING_MAX_ATTRIBUTES_PER_SPAN", "TRACING_MAX_ATTRIBUTES_PER_SPAN", 32))
            .maxEventsPerSpan(getIntConfigValue("TRACING_MAX_EVENTS_PER_SPAN", "TRACING_MAX_EVENTS_PER_SPAN", 128))
            .maxSpanSizeBytes(getLongConfigValue("TRACING_MAX_SPAN_SIZE_BYTES", "TRACING_MAX_SPAN_SIZE_BYTES", 5 * 1024))
            .build();

    /**
     * Determines if a span should be sampled based on configured limits.
     * Applies sampling rate and rate limiting logic to prevent overwhelming
     * the telemetry system while maintaining data quality.
     *
     * @return true if the span should be sampled, false otherwise
     */
    public boolean shouldSample() {
        // Check rate limits
        long currentTimeSecond = System.currentTimeMillis() / 1000;
        if (currentTimeSecond > currentSecond) {
            synchronized (this) {
                if (currentTimeSecond > currentSecond) {
                    currentSecond = currentTimeSecond;
                    traceCount.set(0);
                    spansPerTrace.clear();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Reset rate limiting state for new second: {}", currentTimeSecond);
                    }
                    return true; // Always allow the first span in a new second
                }
            }
        }

        // Apply sampling rate first
        if (samplingRate < 1.0) {
            if (samplingRate <= 0.0 || Math.random() >= samplingRate) {
                droppedSpans.incrementAndGet();
                return false;
            }
        }

        // Check if we've exceeded traces per second
        long count = traceCount.get();
        if (count >= maxTracesPerSecond) {
            // If we're way over the limit, drop the span
            if (count >= maxTracesPerSecond * 2L) {
                droppedSpans.incrementAndGet();
                return false;
            }
            // Otherwise, let some spans through to prevent complete starvation
            if (count % 2 == 0) {
                droppedSpans.incrementAndGet();
                return false;
            }
        }

        // Only increment the count if we're going to accept the span
        traceCount.incrementAndGet();
        return true;
    }

    /**
     * Determines if a new span should be created for a trace.
     * Enforces the maximum spans per trace limit to prevent resource exhaustion.
     *
     * @param traceId The ID of the trace to check
     * @return true if a new span can be created, false if the limit is exceeded
     */
    public boolean shouldCreateSpan(String traceId) {
        if (traceId == null) {
            return true; // This is a root span
        }

        // Check spans per trace limit
        AtomicLong spanCount = spansPerTrace.computeIfAbsent(traceId, k -> new AtomicLong(0));
        return spanCount.incrementAndGet() <= maxSpansPerTrace;
    }

    /**
     * Creates an OpenTelemetry sampler based on the configured sampling rate.
     * Provides appropriate sampling behavior based on the configured rate.
     *
     * @return A configured Sampler instance
     */
    public Sampler createSampler() {
        if (samplingRate >= 1.0) {
            return Sampler.alwaysOn();
        } else if (samplingRate <= 0.0) {
            return Sampler.alwaysOff();
        } else {
            return Sampler.traceIdRatioBased(samplingRate);
        }
    }

    /**
     * Handles a dropped span by logging appropriate information.
     * Provides detailed logging for debugging and monitoring purposes
     * when spans are dropped due to various limits.
     *
     * @param span   The span that was dropped
     * @param reason The reason why the span was dropped
     */
    public void handleDroppedSpan(SpanData span, String reason) {
        droppedSpans.incrementAndGet();

        String traceId;
        String spanId;

        if (span != null) {
            SpanContext spanContext = span.getSpanContext();
            traceId = spanContext.getTraceId();
            spanId = spanContext.getSpanId();
        } else if ("max_spans_per_trace_exceeded".equals(reason) && !spansPerTrace.isEmpty()) {
            traceId = spansPerTrace.keySet().iterator().next(); // Get the current trace ID
            spanId = String.format("%016x", System.nanoTime()); // Generate a unique span ID
        } else {
            // For sampling or other drops, generate IDs
            traceId = String.format("%032x", System.nanoTime());
            spanId = String.format("%016x", System.nanoTime());
        }

        // Basic drop message
        logger.warn("Span dropped due to {}, traceId={}, spanId={}",
                reason, traceId, spanId);

        // Log detailed info for all dropped spans
        if (logger.isDebugEnabled()) {
            logger.debug("Detailed dropped span info");
            logger.debug("TraceId: {}", traceId);
            logger.debug("SpanId: {}", spanId);
            logger.debug("Attributes: {}", span != null ? span.getAttributes() : "{}");
        }
    }

    /**
     * Gets the total number of spans that have been dropped.
     *
     * @return The count of dropped spans
     */
    public long getDroppedSpanCount() {
        return droppedSpans.get();
    }
} 
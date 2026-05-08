package io.telemetry.ai.otel.config;

/**
 * Constants for metrics collection and export.
 * Centralizes metric names and tags to ensure consistency.
 * 
 * <p>All metrics use a consistent naming approach:</p>
 * <ul>
 *   <li>operation_duration - Duration of operations in milliseconds</li>
 *   <li>operation_result_count - Count of results from operations</li>
 *   <li>operation_success_count - Count of successful operations</li>
 *   <li>operation_failure_count - Count of failed operations</li>
 * </ul>
 *
 * <p>All metrics include the following standard tags:</p>
 * <ul>
 *   <li>operation - The operation type (search, embedding, enrichment)</li>
 *   <li>service - The service name</li>
 *   <li>tenant - The tenant ID</li>
 * </ul>
 */
public final class MetricsConstants {
    
    // Metric names (without prefix)
    public static final String METRIC_OPERATION_DURATION = "operation_duration";
    public static final String METRIC_OPERATION_RESULT_COUNT = "operation_result_count";
    public static final String METRIC_OPERATION_SUCCESS_COUNT = "operation_success_count";
    public static final String METRIC_OPERATION_FAILURE_COUNT = "operation_failure_count";
    
    // Standard tag names
    public static final String TAG_OPERATION = "operation";
    public static final String TAG_TENANT = "tenant";
    public static final String TAG_SERVICE = "service";
    
    // Operation values for TAG_OPERATION
    public static final String OPERATION_SEARCH = "search";
    public static final String OPERATION_EMBEDDING = "embedding";
    public static final String OPERATION_ENRICHMENT = "enrichment";
    
    // Default values
    public static final String UNKNOWN_TENANT = "unknown";
    public static final String UNKNOWN_OPERATION = "unknown";
    
    private MetricsConstants() {
        // Private constructor to prevent instantiation
    }
} 
package io.telemetry.ai.otel.metrics;

import io.telemetry.ai.otel.model.OperationType;

/**
 * Interface for exporting metrics to monitoring systems.
 * Provides methods to record various types of metrics like timers and counters.
 * 
 * <p>Example usage with {@code @Trace} annotation for automatic metrics collection:</p>
 * <pre>{@code
 * @Trace(
 *     spanName = "enrich_document",
 *     operationType = OperationType.ENRICHMENT,
 *     collectMetrics = true
 * )
 * public ElasticClusterEnrichResult run() {
 *     // Implementation that returns a result with success/failure counts
 *     // The @Trace annotation will automatically collect metrics
 * }
 * }</pre>
 * 
 * <p>Example for manually recording metrics with success/failure counts:</p>
 * <pre>{@code
 * // At the end of an enrichment process
 * Long durationMs = System.currentTimeMillis() - startTime;
 * Long resultCount = (long) processedDocs.size();
 * Long successCount = (long) successfulDocs.size();
 * Long failureCount = (long) failedDocs.size();
 * 
 * metricsExporter.recordOperationMetrics(
 *     OperationType.ENRICHMENT,
 *     tenantId,
 *     durationMs,
 *     resultCount,
 *     successCount,
 *     failureCount
 * );
 * }</pre>
 */
public interface MetricsExporter {
    
    /**
     * Records a timing metric.
     * 
     * @param name The name of the timer
     * @param tags Array of tag key-value pairs (must be even length)
     * @param durationMs The duration in milliseconds
     */
    void recordTimer(String name, String[] tags, long durationMs);
    
    /**
     * Increments a counter metric.
     * 
     * @param name The name of the counter
     * @param tags Array of tag key-value pairs (must be even length)
     * @param amount The amount to increment by
     */
    void incrementCounter(String name, String[] tags, double amount);
    
    /**
     * Records a gauge metric.
     * 
     * @param name The name of the gauge
     * @param tags Array of tag key-value pairs (must be even length)
     * @param value The gauge value
     */
    void recordGauge(String name, String[] tags, double value);
    
    /**
     * Records metrics from the current context for the specified operation type.
     * This method should extract metrics data from the OpenTelemetry context
     * and record it using the other methods in this interface.
     * 
     * @param operationType The type of operation that was performed
     */
    void recordOperationMetrics(OperationType operationType);
    
    /**
     * Records metrics for an operation with result count.
     * 
     * @param operationType The type of operation that was performed
     * @param tenantId The tenant ID (optional)
     * @param durationMs The duration in milliseconds
     * @param resultCount The number of results processed (optional)
     */
    default void recordOperationMetrics(OperationType operationType, String tenantId, 
                                   long durationMs, Long resultCount) {
        recordOperationMetrics(operationType, tenantId, durationMs, resultCount, null, null);
    }
    
    /**
     * Records metrics for an operation with success and failure counts.
     * 
     * @param operationType The type of operation that was performed
     * @param tenantId The tenant ID (optional)
     * @param durationMs The duration in milliseconds
     * @param resultCount The number of results processed (optional)
     * @param successCount The number of successful results (optional)
     * @param failureCount The number of failed results (optional)
     */
    void recordOperationMetrics(OperationType operationType, String tenantId, 
                           long durationMs, Long resultCount, 
                           Long successCount, Long failureCount);
} 
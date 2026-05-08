package io.telemetry.ai.otel.metrics;

import io.telemetry.ai.otel.config.MetricsConstants;
import io.telemetry.ai.otel.model.OperationType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive metrics recorder for collecting and exporting various metrics.
 * This class provides a flexible API for timing operations, recording results,
 * and exporting different metric types with dimensional tags.
 * 
 * <p>Example usage for simple timing:</p>
 * <pre>{@code
 * // Start timer for a search operation
 * TimerContext timer = metricsRecorder.startTimer("search")
 *     .withTenant("tenant1")
 *     .withTag("model", "gpt4")
 *     .start();
 * 
 * try {
 *     // Perform operation...
 *     List<Result> results = performSearch(...);
 *     
 *     // End timer and record results
 *     timer.success(results.size());
 * } catch (Exception e) {
 *     // Record failure
 *     timer.failure(e.getClass().getSimpleName());
 *     throw e;
 * }
 * }</pre>
 * 
 * <p>Example usage with try-with-resources:</p>
 * <pre>{@code
 * try (TimerContext timer = metricsRecorder.startTimer(OperationType.EMBEDDING)
 *     .withTenant("tenant1")
 *     .start()) {
 *     
 *     // Perform operation...
 *     EmbeddingResult result = createEmbedding(...);
 *     
 *     // Success is automatically recorded when the block exits
 *     timer.setResultCount(1);
 * }
 * }</pre>
 */
@ApplicationScoped
public class MetricsRecorder {
    private static final Logger logger = LoggerFactory.getLogger(MetricsRecorder.class);

    private final MetricsExporter metricsExporter;
    private final ConcurrentHashMap<String, Long> gaugeValues = new ConcurrentHashMap<>();

    @Inject
    public MetricsRecorder(MetricsExporter metricsExporter) {
        this.metricsExporter = metricsExporter;
        logger.info("Created MetricsRecorder with exporter: {}", 
                  metricsExporter.getClass().getSimpleName());
    }

    /**
     * Starts a timer builder for an operation type.
     * 
     * @param operationType The operation type to time
     * @return A TimerBuilder for further configuration
     */
    public TimerBuilder startTimer(OperationType operationType) {
        return new TimerBuilder(operationType.name().toLowerCase());
    }

    /**
     * Starts a timer builder for a custom operation name.
     * 
     * @param operationName The name of the operation to time
     * @return A TimerBuilder for further configuration
     */
    public TimerBuilder startTimer(String operationName) {
        return new TimerBuilder(operationName.toLowerCase());
    }

    /**
     * Records a custom timer with the specified duration.
     * 
     * @param name The name of the timer metric
     * @param durationMs The duration in milliseconds
     * @param tags Key-value pairs of tags (must be even length)
     */
    public void recordTimer(String name, long durationMs, String... tags) {
        try {
            metricsExporter.recordTimer(name, tags, durationMs);
            logger.debug("Recorded timer: {}={}ms with {} tags", 
                      name, durationMs, tags.length / 2);
        } catch (Exception e) {
            logger.error("Failed to record timer: {}", name, e);
        }
    }

    /**
     * Increments a counter metric.
     * 
     * @param name The name of the counter metric
     * @param amount The amount to increment by
     * @param tags Key-value pairs of tags (must be even length)
     */
    public void incrementCounter(String name, double amount, String... tags) {
        try {
            metricsExporter.incrementCounter(name, tags, amount);
            logger.debug("Incremented counter: {}={} with {} tags", 
                      name, amount, tags.length / 2);
        } catch (Exception e) {
            logger.error("Failed to increment counter: {}", name, e);
        }
    }

    /**
     * Records a gauge metric with the specified value.
     * Gauges represent a point-in-time value that can go up or down.
     * 
     * @param name The name of the gauge metric
     * @param value The gauge value
     * @param tags Key-value pairs of tags (must be even length)
     */
    public void recordGauge(String name, double value, String... tags) {
        try {
            metricsExporter.recordGauge(name, tags, value);
            
            // Store the value for tracking
            String metricKey = name + String.join("", tags);
            gaugeValues.put(metricKey, Math.round(value));
            
            logger.debug("Recorded gauge: {}={} with {} tags", 
                      name, value, tags.length / 2);
        } catch (Exception e) {
            logger.error("Failed to record gauge: {}", name, e);
        }
    }

    /**
     * Functional interface for operations that may throw checked exceptions.
     * @param <T> The type of the result returned by the operation
     */
    @FunctionalInterface
    public interface CheckedSupplier<T> {
        /**
         * Gets a result, potentially throwing a checked exception.
         *
         * @return The result
         * @throws Exception if an error occurs during the operation
         */
        T get() throws Exception;
    }

    /**
     * Container class that holds both the result of an operation and its timing information.
     *
     * @param <T> The type of the result
     */
    @Getter
    public static class TimedResult<T> {
        private final T result;
        private final long durationMs;
        private final boolean success;
        private final String errorType;
        private final Long resultCount;
        
        /**
         * Creates a new TimedResult.
         *
         * @param result The result of the operation
         * @param durationMs The duration of the operation in milliseconds
         * @param success Whether the operation was successful
         * @param errorType The type of error if the operation failed
         * @param resultCount The number of results if applicable
         */
        public TimedResult(T result, long durationMs, boolean success, String errorType, Long resultCount) {
            this.result = result;
            this.durationMs = durationMs;
            this.success = success;
            this.errorType = errorType;
            this.resultCount = resultCount;
        }

    }

    /**
     * Measures the duration of an operation and records metrics.
     * Returns a TimedResult containing both the operation result and timing information.
     *
     * @param <T> The type of the result
     * @param operationName The name of the operation
     * @param tenantId The tenant ID
     * @param tags Additional tags for metrics
     * @param task The operation to measure
     * @return A TimedResult containing both the operation result and timing information
     * @throws Exception Any exception thrown by the code block
     */
    public <T> TimedResult<T> measureOperation(String operationName, String tenantId,
                                                    Map<String, String> tags, CheckedSupplier<T> task)
            throws Exception {
        if (tenantId == null) {
            logger.warn("tenantId is null; defaulting to 'unknown'. This may indicate a misconfiguration.");
        }
        TimerBuilder timerBuilder = startTimer(operationName)
            .withTenant(tenantId != null ? tenantId : "unknown");
        
        if (tags != null) {
            timerBuilder.withTags(tags);
        }
        
        TimerBuilder.TimerContext timer = timerBuilder.start();
        
        try {
            T result = task.get();
            
            // If the result has a size or length method, try to record count
            Long resultCount = null;
            try {
                if (result instanceof java.util.Collection) {
                    resultCount = (long)((java.util.Collection<?>) result).size();
                    timer.setResultCount(resultCount);
                } else if (result instanceof java.util.Map) {
                    resultCount = (long)((java.util.Map<?, ?>) result).size();
                    timer.setResultCount(resultCount);
                } else if (result.getClass().isArray()) {
                    resultCount = (long)java.lang.reflect.Array.getLength(result);
                    timer.setResultCount(resultCount);
                }
            } catch (Exception e) {
                // Ignore reflection errors
                logger.debug("Could not determine result count: {}", e.getMessage());
            }
            
            timer.success();
            return new TimedResult<>(result, timer.getElapsedTimeMs(), true, null, resultCount);
        } catch (Exception e) {
            // Record the failure but preserve the original exception type
            String errorType = e.getClass().getSimpleName();
            timer.failure(errorType);
            throw e;
        }
    }

    /**
     * Builder for creating and configuring timer contexts.
     */
    public class TimerBuilder {
        private final String operationName;
        private String tenantId;
        private final Map<String, String> tags = new HashMap<>();
        private long startTimeNanos;

        private TimerBuilder(String operationName) {
            this.operationName = operationName;
        }

        /**
         * Sets the tenant ID for the timer.
         * 
         * @param tenantId The tenant ID
         * @return This builder for chaining
         */
        public TimerBuilder withTenant(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        /**
         * Adds a tag to the timer.
         * 
         * @param key The tag key
         * @param value The tag value
         * @return This builder for chaining
         */
        public TimerBuilder withTag(String key, String value) {
            if (key != null && value != null) {
                tags.put(key, value);
            }
            return this;
        }

        /**
         * Adds multiple tags to the timer.
         * 
         * @param tags Map of tag keys and values
         * @return This builder for chaining
         */
        public TimerBuilder withTags(Map<String, String> tags) {
            if (tags != null) {
                this.tags.putAll(tags);
            }
            return this;
        }

        /**
         * Starts the timer and returns a timer context.
         * 
         * @return A TimerContext that can be used to stop the timer and record results
         */
        public TimerContext start() {
            startTimeNanos = System.nanoTime();
            return new TimerContext();
        }

        /**
         * Context for a running timer that can be used to stop and record results.
         */
        public class TimerContext implements AutoCloseable {
            private boolean stopped = false;
            private Long resultCount;
            private boolean success = true;
            private String errorType = null;
            private long endTimeNanos;

            /**
             * Sets the result count for the operation.
             * 
             * @param count The number of results produced
             * @return This context for chaining
             */
            public TimerContext setResultCount(long count) {
                this.resultCount = count;
                return this;
            }

            /**
             * Returns the duration of the timer since it was started.
             * 
             * @return The elapsed time in milliseconds
             */
            public long getElapsedTimeMs() {
                return getDurationMs();
            }

            /**
             * Marks the operation as successful and records metrics.
             * 
             * @return This context for chaining
             */
            public TimerContext success() {
                if (!stopped) {
                    endTimeNanos = System.nanoTime();
                    long durationMs = getDurationMs();
                    recordOperationMetrics(durationMs, resultCount, 1L, null);
                    stopped = true;
                }
                return this;
            }

            /**
             * Marks the operation as successful with a result count and records metrics.
             * 
             * @param resultCount The number of results produced
             * @return This context for chaining
             */
            public TimerContext success(long resultCount) {
                setResultCount(resultCount);
                return success();
            }

            /**
             * Marks the operation as failed and records metrics.
             * 
             * @param errorType The type of error that occurred
             * @return This context for chaining
             */
            public TimerContext failure(String errorType) {
                if (!stopped) {
                    endTimeNanos = System.nanoTime();
                    long durationMs = getDurationMs();
                    this.success = false;
                    this.errorType = errorType;
                    recordOperationMetrics(durationMs, resultCount, null, 1L);
                    stopped = true;
                }
                return this;
            }

            /**
             * Records metrics for the operation based on the current state.
             */
            private void recordOperationMetrics(long durationMs, Long resultCount, 
                                            Long successCount, Long failureCount) {
                // Create tag array from maps
                String[] tagArray = createTagArray();
                
                // Record operation duration timer
                recordTimer(MetricsConstants.METRIC_OPERATION_DURATION, durationMs, tagArray);
                
                // Record result count if available
                if (resultCount != null) {
                    incrementCounter(MetricsConstants.METRIC_OPERATION_RESULT_COUNT, 
                                    resultCount, tagArray);
                }
                
                // Record success count if successful
                if (successCount != null) {
                    incrementCounter(MetricsConstants.METRIC_OPERATION_SUCCESS_COUNT, 
                                    successCount, tagArray);
                }
                
                // Record failure count if failed
                if (failureCount != null) {
                    // Add error type tag if available
                    String[] tagsWithError = errorType != null 
                            ? addTag(tagArray, "error_type", errorType) 
                            : tagArray;
                    incrementCounter(MetricsConstants.METRIC_OPERATION_FAILURE_COUNT, 
                                    failureCount, tagsWithError);
                }
            }

            /**
             * Calculates the duration since the timer was started.
             * 
             * @return The duration in milliseconds
             */
            private long getDurationMs() {
                if (stopped) {
                    return TimeUnit.NANOSECONDS.toMillis(endTimeNanos - startTimeNanos);
                } else {
                    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
                }
            }

            /**
             * Creates an array of tag key-value pairs from the maps.
             * 
             * @return Array of tag key-value pairs
             */
            private String[] createTagArray() {
                int capacity = 2 + (tenantId != null ? 2 : 0) + (tags.size() * 2);
                String[] tagArray = new String[capacity];
                
                int index = 0;
                
                // Add operation tag
                tagArray[index++] = MetricsConstants.TAG_OPERATION;
                tagArray[index++] = operationName;
                
                // Add tenant tag if available
                if (tenantId != null) {
                    tagArray[index++] = "tenant";
                    tagArray[index++] = tenantId;
                }
                
                // Add custom tags
                for (Map.Entry<String, String> entry : tags.entrySet()) {
                    tagArray[index++] = entry.getKey();
                    tagArray[index++] = entry.getValue();
                }
                
                return tagArray;
            }

            /**
             * Adds a tag to an existing tag array.
             * 
             * @param tags The existing tag array
             * @param key The tag key to add
             * @param value The tag value to add
             * @return A new array with the additional tag
             */
            private String[] addTag(String[] tags, String key, String value) {
                String[] newTags = new String[tags.length + 2];
                System.arraycopy(tags, 0, newTags, 0, tags.length);
                newTags[tags.length] = key;
                newTags[tags.length + 1] = value;
                return newTags;
            }

            /**
             * Automatically stops the timer if it hasn't been stopped yet.
             * This allows using the timer with try-with-resources.
             */
            @Override
            public void close() {
                if (!stopped) {
                    if (success) {
                        success();
                    } else {
                        failure(errorType);
                    }
                }
            }
        }
    }
} 
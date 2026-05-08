package io.telemetry.ai.otel.metrics;

import io.telemetry.ai.otel.model.OperationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-operation implementation of MetricsExporter.
 * Used when metrics collection is disabled.
 */
public class NoopMetricsExporter implements MetricsExporter {
    private static final Logger logger = LoggerFactory.getLogger(NoopMetricsExporter.class);
    private static final boolean DEBUG_ENABLED = false;
    
    public NoopMetricsExporter() {
        if (DEBUG_ENABLED) {
            logger.debug("Created NoopMetricsExporter");
        }
    }
    
    @Override
    public void recordTimer(String name, String[] tags, long durationMs) {
        if (DEBUG_ENABLED) {
            logger.debug("NoopMetricsExporter ignoring timer: {}", name);
        }
    }
    
    @Override
    public void incrementCounter(String name, String[] tags, double amount) {
        if (DEBUG_ENABLED) {
            logger.debug("NoopMetricsExporter ignoring counter: {}", name);
        }
    }
    
    @Override
    public void recordGauge(String name, String[] tags, double value) {
        if (DEBUG_ENABLED) {
            logger.debug("NoopMetricsExporter ignoring gauge: {}", name);
        }
    }
    
    @Override
    public void recordOperationMetrics(OperationType operationType) {
        if (DEBUG_ENABLED) {
            logger.debug("NoopMetricsExporter ignoring operation metrics from context for: {}", 
                       operationType != null ? operationType.name() : "null");
        }
    }
    
    @Override
    public void recordOperationMetrics(OperationType operationType, String tenantId, 
                                 long durationMs, Long resultCount, 
                                 Long successCount, Long failureCount) {
        if (DEBUG_ENABLED) {
            logger.debug("NoopMetricsExporter ignoring operation metrics for: {}", 
                       operationType != null ? operationType.name() : "null");
        }
    }
}
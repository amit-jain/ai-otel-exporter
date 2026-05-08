package io.telemetry.ai.otel.metrics;

import io.telemetry.ai.otel.config.MetricsConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * Producer for metrics-related components.
 * Ensures proper initialization and lifecycle management of metrics components.
 * Simplified to remove push gateway dependencies.
 * Now integrates with Quarkus default MeterRegistry.
 */
@ApplicationScoped
public class MetricsProducer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsProducer.class);
    
    @Inject
    MetricsConfig metricsConfig;

    /**
     * Produces a MeterFilter to add common tags to all registries.
     * This will be automatically applied by Quarkus to the default MeterRegistry.
     * @return A MeterFilter instance.
     */
    @Produces
    @Singleton
    public MeterFilter commonTagsMeterFilter() {
        logger.info("Producing MeterFilter to add common service tag: {}", metricsConfig.getServiceName());
        return MeterFilter.commonTags(Collections.singletonList(Tag.of("service", metricsConfig.getServiceName())));
    }
    
    /**
     * Produces a metrics exporter.
     * This now uses the default MeterRegistry injected by Quarkus.
     * @param meterRegistry The default MeterRegistry provided by Quarkus.
     * @return The metrics exporter to use (real or NOOP depending on config)
     */
    @Produces
    @Singleton
    public MetricsExporter produceMetricsExporter(MeterRegistry meterRegistry) {
        // Debug the current metrics configuration state
        logger.info("Metrics configuration state:");
        logger.info("  metricsConfig.isMetricsEnabled() = {}", metricsConfig.isMetricsEnabled());
        logger.info("  metricsConfig.getMetricsPrefix() = {}", metricsConfig.getMetricsPrefix());
        logger.info("  metricsConfig.getServiceName() = {}", metricsConfig.getServiceName());
        
        String systemPropMetricsEnabled = System.getProperty("AI_OTEL_METRICS_ENABLED");
        logger.info("  System property AI_OTEL_METRICS_ENABLED = {}", systemPropMetricsEnabled);

        boolean effectivelyEnabled = metricsConfig.isMetricsEnabled();
        MetricsConfig configToUse = metricsConfig; // Default to the injected config

        if (!effectivelyEnabled && "true".equalsIgnoreCase(systemPropMetricsEnabled)) {
            effectivelyEnabled = true;
            // Create a new MetricsConfig instance that will pick up current system properties
            logger.info("Re-evaluating MetricsConfig from system properties for DefaultMetricsExporter due to override.");
            configToUse = new MetricsConfig(); 
            logger.info("  New MetricsConfig for exporter: enabled={}, prefix={}, service={}",
                        configToUse.isMetricsEnabled(), configToUse.getMetricsPrefix(), configToUse.getServiceName());
        }
        
        if (!effectivelyEnabled) {
            logger.info("Metrics effectively disabled, using NoopMetricsExporter");
            return new NoopMetricsExporter();
        }
        
        logger.info("Creating DefaultMetricsExporter with service={}, prefix={}, using injected MeterRegistry: {}", 
                  configToUse.getServiceName(), 
                  configToUse.getMetricsPrefix() != null ? configToUse.getMetricsPrefix() : "null",
                  meterRegistry.getClass().getName());
        
        return new DefaultMetricsExporter(meterRegistry, configToUse);
    }
} 
package io.telemetry.ai.otel.system;

import io.telemetry.ai.otel.config.TelemetryConfig;
import io.telemetry.ai.otel.pii.PIIAnonymizingSpanExporter;
import io.telemetry.ai.otel.pii.PIIDetectorConfig;
import io.telemetry.ai.otel.tracing.processor.QueueAwareSpanProcessor;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.telemetry.ai.otel.common.OpenInferenceAttributes.OPENINFERENCE_PROJECT_NAME;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.PROJECT_NAME_ATTRIBUTE;
import static io.telemetry.ai.otel.common.OpenInferenceAttributes.SERVICE_NAME_ATTRIBUTE;

/**
 * Configuration class for OpenTelemetry telemetry setup.
 * Manages tracer providers, exporters, and span processors for collecting
 * and exporting telemetry data. Supports both OTLP and logging exporters
 * with configurable sampling, limits, and queue management.
 */
public class TelemetrySystem {
    private static final Logger logger = LoggerFactory.getLogger(TelemetrySystem.class);

    /**
     *  Gets the configured tracer provider.
     *
     * @return The SdkTracerProvider instance
     */
    @Getter
    private final SdkTracerProvider tracerProvider;
    /**
     *  Gets the configured tracer for creating spans.
     *
     * @return The Tracer instance
     */
    @Getter
    private final Tracer tracer;
    private final String serviceName;
    private final String appName;
    private final TelemetryConfig config;

    // Static list to hold registered exporters in order
    private static final List<io.telemetry.ai.otel.system.DelegatingSpanExporter> registeredExporters = new ArrayList<>();

    /**
     * Registers a DelegatingSpanExporter to be included in the exporter chain.
     * Exporters are registered in order from first to last in the chain.
     *
     * @param <T> The type of DelegatingSpanExporter being registered
     * @param exporter The exporter to register
     * @return The registered exporter instance
     */
    public static <T extends io.telemetry.ai.otel.system.DelegatingSpanExporter> T registerExporter(T exporter) {
        if (exporter == null) {
            throw new IllegalArgumentException("Exporter cannot be null");
        }

        // Add to the list of registered exporters
        registeredExporters.add(exporter);
        logger.info("Registered exporter: {}", exporter.getClass().getSimpleName());
        return exporter;
    }

    /**
     * Clears all registered exporters.
     */
    public static void clearRegisteredExporters() {
        int size = registeredExporters.size();
        registeredExporters.clear();
        logger.info("Cleared all registered exporters (was: {})", size);
    }

    /**
     * Creates a chain of exporters with the specified final exporter.
     * This method does not use the static registration mechanism.
     *
     * @param finalExporter The final exporter in the chain
     * @param exporters     The exporters to add to the chain, in order from first to last
     * @return The first exporter in the chain
     */
    public static SpanExporter createExporterChain(SpanExporter finalExporter, io.telemetry.ai.otel.system.DelegatingSpanExporter... exporters) {
        if (exporters == null || exporters.length == 0) {
            return finalExporter;
        }

        SpanExporter current = finalExporter;

        // Chain the exporters in reverse order (last to first)
        for (int i = exporters.length - 1; i >= 0; i--) {
            io.telemetry.ai.otel.system.DelegatingSpanExporter exporter = exporters[i];
            exporter.setDelegate(current);
            current = exporter;
            logger.info("Added {} to exporter chain", exporter.getClass().getSimpleName());
        }

        return current;
    }

    /**
     * Custom span exporter that uses SLF4J for logging spans.
     * Provides a fallback mechanism when the primary exporter is unavailable.
     */
    private static class Slf4jSpanExporter implements SpanExporter {
        private static final Logger exportLogger = LoggerFactory.getLogger("io.opentelemetry.exporter.logging.LoggingSpanExporter");

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            for (SpanData span : spans) {
                exportLogger.info("LoggingSpanExporter export - '{}' : {} {} {} [tracer: {}:] {}",
                        span.getName(),
                        span.getTraceId(),
                        span.getSpanId(),
                        span.getKind(),
                        span.getInstrumentationScopeInfo().getName(),
                        span.getAttributes()
                );
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    /**
     * Creates a new TelemetryConfiguration for the specified service and application.
     * Initializes the tracer provider, configures exporters, and sets up span processors
     * based on the provided configuration.
     *
     * @param serviceName The name of the service for resource attribution
     * @param appName     The name of the application or component
     */
    public TelemetrySystem(String serviceName, String appName) {
        this.serviceName = serviceName;
        this.appName = appName;
        this.config = TelemetryConfig.fromSystemProperties();

        // Create resource attributes
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.builder()
                        .put(SERVICE_NAME_ATTRIBUTE, serviceName)
                        .put(PROJECT_NAME_ATTRIBUTE, String.format("%s/%s", serviceName, appName))
                        .put(OPENINFERENCE_PROJECT_NAME, String.format("%s/%s", serviceName, appName))
                        .build()));

        // Build tracer provider with sampling and limits
        SdkTracerProviderBuilder tracerProviderBuilder = SdkTracerProvider.builder()
                .setResource(resource)
                .setSampler(config.getTracingLimits().createSampler());

        // Set span limits
        SpanLimits spanLimits = SpanLimits.builder()
                .setMaxNumberOfAttributes(config.getTracingLimits().getMaxAttributesPerSpan())
                .setMaxNumberOfEvents(config.getTracingLimits().getMaxEventsPerSpan())
                .setMaxNumberOfLinks(config.getTracingLimits().getMaxSpansPerTrace())
                .setMaxAttributeValueLength((int) config.getTracingLimits().getMaxSpanSizeBytes())
                .build();
        tracerProviderBuilder.setSpanLimits(spanLimits);

        if (config.isOtlpEnabled()) {
            if (config.getOtlpEndpoint() != null && !config.getOtlpEndpoint().isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Configuring OTLP exporter with endpoint: {}", config.getOtlpEndpoint());
                }

                SpanExporter otlpExporter = createOtlpExporter(config.getOtlpEndpoint());

                // Create batch processor with OTLP exporter and rate limits
                SpanProcessor batchProcessor = createBatchProcessor(otlpExporter);

                tracerProviderBuilder.addSpanProcessor(batchProcessor);
            } else {
                // Use logging exporter when OTLP is not configured
                if (logger.isDebugEnabled()) {
                    logger.debug("Using logging exporter as no OTLP endpoint specified");
                }
                tracerProviderBuilder.addSpanProcessor(
                        SimpleSpanProcessor.create(new Slf4jSpanExporter())
                );
            }
        } else {
            // When OTLP export is disabled, use a no-op span processor
            logger.info("Telemetry collection disabled by OTLP_EXPORT=false setting, no spans will be collected");
            // We still need a minimal processor to avoid NPEs, but it won't do anything
            tracerProviderBuilder.addSpanProcessor(SimpleSpanProcessor.create(new NoOpSpanExporter()));
        }

        this.tracerProvider = tracerProviderBuilder.build();
        this.tracer = tracerProvider.get(appName);
    }

    /**
     * Shuts down the telemetry configuration and releases resources.
     * This should be called when the configuration is no longer needed.
     */
    public void shutdown() {
        logger.info("Shutting down TelemetrySystem for service: {}, app: {}", serviceName, appName);

        if (tracerProvider != null) {
            try {
                // Flush any pending spans before closing
                CompletableResultCode flushResult = tracerProvider.forceFlush();
                flushResult.join(5, TimeUnit.SECONDS);
                if (flushResult.isSuccess()) {
                    logger.info("Successfully flushed pending spans for service: {}, app: {}", serviceName, appName);
                } else {
                    logger.warn("Failed to flush pending spans for service: {}, app: {}", serviceName, appName);
                }

                // Close the tracer provider, which will also close all associated span processors and exporters
                tracerProvider.close();
                logger.info("Successfully closed TracerProvider for service: {}, app: {}", serviceName, appName);
            } catch (Exception e) {
                logger.error("Error closing TracerProvider for service: {}, app: {}", serviceName, appName, e);
            }
        } else {
            logger.warn("TracerProvider was null during shutdown for service: {}, app: {}", serviceName, appName);
        }

        logger.info("TelemetrySystem shutdown complete for service: {}, app: {}", serviceName, appName);
    }

    /**
     * Creates an OTLP gRPC span exporter with the specified endpoint.
     *
     * @param endpoint The OTLP endpoint URL
     * @return Configured OtlpGrpcSpanExporter instance
     */
    private SpanExporter createOtlpExporter(String endpoint) {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating OTLP exporter with endpoint: {}", endpoint);
        }

        // Start with the OTLP exporter
        SpanExporter finalExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(config.getExportTimeoutSeconds()))
                .build();

        // Add registered exporters to the chain
        if (!registeredExporters.isEmpty()) {
            logger.info("Adding {} registered exporters to the chain", registeredExporters.size());
            finalExporter = createExporterChain(finalExporter,
                    registeredExporters.toArray(new io.telemetry.ai.otel.system.DelegatingSpanExporter[0]));
        }

        // If PII detection is enabled, add it as the FIRST exporter in the chain
        // This ensures PII is anonymized before any other processing
        if (config.isPiiDetectionEnabled()) {
            PIIDetectorConfig piiConfig = PIIDetectorConfig.fromSystemProperties();
            if (piiConfig.isEnabled()) {
                logger.info("Adding PIIAnonymizingSpanExporter as first exporter in the chain");
                finalExporter = new PIIAnonymizingSpanExporter(finalExporter, piiConfig);
            }
        }

        return finalExporter;
    }

    /**
     * Creates a batch processor with queue awareness and fallback behavior.
     *
     * @param exporter The primary span exporter
     * @return A SpanProcessor that handles queuing and fallback
     */
    private SpanProcessor createBatchProcessor(SpanExporter exporter) {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating robust batch processor with exporter: {}", exporter.getClass().getSimpleName());
        }

        // Create a wrapper around LoggingSpanExporter that logs at WARN level when fallback occurs
        SpanExporter fallbackExporter = new SpanExporter() {
            private final SpanExporter delegate = LoggingSpanExporter.create();

            @Override
            public CompletableResultCode export(Collection<SpanData> spans) {
                long timestamp = System.currentTimeMillis();
                logger.warn("[{}] Fallback triggered - batch queue full or export failed. Falling back to logging {} spans. First span: {}",
                        timestamp,
                        spans.size(),
                        spans.stream().findFirst().map(s -> s.getName() + " [trace: " + s.getTraceId() + "]").orElse("none"));
                return delegate.export(spans);
            }

            @Override
            public CompletableResultCode flush() {
                return delegate.flush();
            }

            @Override
            public CompletableResultCode shutdown() {
                return delegate.shutdown();
            }
        };

        // Create batch processor with just the OTLP exporter
        BatchSpanProcessor batchProcessor = BatchSpanProcessor.builder(exporter)
                .setMaxQueueSize(config.getQueueSize())
                .setMaxExportBatchSize(config.getBatchSize())
                .setScheduleDelay(Duration.ofMillis(config.getScheduleDelayMs()))
                .setExporterTimeout(Duration.ofSeconds(config.getExportTimeoutSeconds()))
                .build();

        return new QueueAwareSpanProcessor(batchProcessor, fallbackExporter, config.getQueueSize());
    }

    /**
     * No-op span exporter that does nothing when export is disabled
     */
    private static class NoOpSpanExporter implements SpanExporter {
        @Override
        public CompletableResultCode export(@Nonnull Collection<SpanData> spans) {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}




package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.system.TelemetrySystemFactory;
import io.opentelemetry.api.trace.Tracer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SERVICE_NAME;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_TENANT_ID;

/**
 * CDI producer for OpenTelemetry Tracer instances.
 * Provides dependency injection support for tracers in a CDI environment,
 * ensuring proper configuration and lifecycle management.
 */
@ApplicationScoped
public class TracerProducer {
    private static final Logger logger = LoggerFactory.getLogger(TracerProducer.class);

    /**
     * Creates a new TracerProducer with default settings.
     * This class is typically used as a CDI producer and injected where needed.
     */
    public TracerProducer() {}

    /**
     * Produces a Tracer instance for injection.
     * Creates a new tracer with the default configuration.
     *
     * @return A configured Tracer instance
     */
    @Produces
    @ApplicationScoped
    public Tracer produceTracer() {
        if (logger.isDebugEnabled()) {
            logger.debug("Creating application-scoped Tracer for service: {}", DEFAULT_SERVICE_NAME);
        }
        TelemetrySystem config = TelemetrySystemFactory.getConfiguration(DEFAULT_SERVICE_NAME, DEFAULT_TENANT_ID);
        return config.getTracer();
    }

    /**
     * Disposes of a Tracer instance.
     * Ensures proper cleanup when the tracer is no longer needed.
     *
     * @param tracer The tracer to dispose
     */
    public void disposeTracer(@Disposes Tracer tracer) {
        logger.info("Disposing Tracer");
        // No cleanup needed for now, but method is required for CDI
    }
} 
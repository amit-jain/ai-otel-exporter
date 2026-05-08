package io.telemetry.ai.otel.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.system.TelemetrySystemFactory;
import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for TelemetryConfig functionality.
 * Verifies the configuration loading, validation, and default value handling
 * for various telemetry settings.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SystemStubsExtension.class)
public class TelemetryConfigTest {
    private static final Logger logger = (Logger) LoggerFactory.getLogger(TelemetryConfigTest.class);

    private ListAppender<ILoggingEvent> logAppender;
    private Logger tracingLimitsLogger;

    @SystemStub
    private EnvironmentVariables environmentVariables;

    /**
     * Sets up the test environment before each test.
     * Clears system properties to ensure a clean test state.
     */
    @BeforeEach
    public void setup() {
        // Clear any existing system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE");
        System.clearProperty("OTEL_BSP_MAX_QUEUE_SIZE");
        System.clearProperty("OTEL_BSP_SCHEDULE_DELAY");
        System.clearProperty("OTEL_EXPORTER_OTLP_TIMEOUT");
        System.clearProperty("TRACING_SAMPLING_RATE");
        System.clearProperty("TRACING_MAX_TRACES_PER_SECOND");
        System.clearProperty("TRACING_MAX_SPANS_PER_TRACE");
        System.clearProperty("TRACING_MAX_SPAN_SIZE_BYTES");
        System.clearProperty("TRACING_MAX_ATTRIBUTES_PER_SPAN");
        System.clearProperty("TRACING_MAX_EVENTS_PER_SPAN");
        System.clearProperty("OTEL_EXPORTER_OTLP_ENDPOINT");
        System.clearProperty("OTEL_EXPORTER_OTLP_PROTOCOL");

        // Clear the TelemetryConfig cache to ensure fresh configuration for each test
        TelemetryConfig.clearCache();

        // Set up log capture
        logAppender = new ListAppender<>();
        logAppender.start();

        // Bridge JUL to SLF4J
        java.util.logging.LogManager.getLogManager().reset();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Capture TracingLimits logs
        tracingLimitsLogger = (Logger) LoggerFactory.getLogger(TracingLimits.class);
        tracingLimitsLogger.addAppender(logAppender);
        tracingLimitsLogger.setLevel(ch.qos.logback.classic.Level.ALL);

        // Capture OpenTelemetry logging exporter logs
        Logger otelLogger = (Logger) LoggerFactory.getLogger("io.opentelemetry.exporter.logging.LoggingSpanExporter");
        otelLogger.addAppender(logAppender);
        otelLogger.setLevel(ch.qos.logback.classic.Level.ALL);

        // Also capture the Slf4jSpanExporter logs
        Logger slf4jExporterLogger = (Logger) LoggerFactory.getLogger("io.telemetry.ai.otel.tracing.system.TelemetrySystem$Slf4jSpanExporter");
        slf4jExporterLogger.addAppender(logAppender);
        slf4jExporterLogger.setLevel(ch.qos.logback.classic.Level.ALL);

        // Capture QueueAwareSpanProcessor logs
        Logger queueLogger = (Logger) LoggerFactory.getLogger("io.telemetry.ai.otel.tracing.processor.QueueAwareSpanProcessor");
        queueLogger.addAppender(logAppender);
        queueLogger.setLevel(ch.qos.logback.classic.Level.ALL);

        // Capture TelemetryConfiguration logs
        Logger telemetryLogger = (Logger) LoggerFactory.getLogger("io.telemetry.ai.otel.tracing.system.TelemetrySystem");
        telemetryLogger.addAppender(logAppender);
        telemetryLogger.setLevel(ch.qos.logback.classic.Level.ALL);

        // Set root logger to capture all logs
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logAppender);
        rootLogger.setLevel(ch.qos.logback.classic.Level.ALL);
    }

    /**
     * Cleans up resources after each test.
     * Restores system properties to their original state.
     */
    @AfterEach
    public void cleanup() {
        // Properly shutdown all telemetry systems
        try {
            io.telemetry.ai.otel.system.TelemetrySystemFactory.shutdownAll();
            logger.info("Shutdown all telemetry systems");
        } catch (Exception e) {
            logger.warn("Failed to shutdown telemetry systems", e);
        }

        // Reset to root context
        io.opentelemetry.context.Context.root().makeCurrent();

        // Clear the TelemetryConfig cache to ensure clean state after test
        TelemetryConfig.clearCache();

        // Clean up system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE");
        System.clearProperty("OTEL_BSP_MAX_QUEUE_SIZE");
        System.clearProperty("OTEL_BSP_SCHEDULE_DELAY");
        System.clearProperty("OTEL_EXPORTER_OTLP_TIMEOUT");
        System.clearProperty("TRACING_SAMPLING_RATE");
        System.clearProperty("TRACING_MAX_TRACES_PER_SECOND");
        System.clearProperty("TRACING_MAX_SPANS_PER_TRACE");
        System.clearProperty("TRACING_MAX_SPAN_SIZE_BYTES");
        System.clearProperty("TRACING_MAX_ATTRIBUTES_PER_SPAN");
        System.clearProperty("TRACING_MAX_EVENTS_PER_SPAN");
        System.clearProperty("OTEL_EXPORTER_OTLP_ENDPOINT");
        System.clearProperty("OTEL_EXPORTER_OTLP_PROTOCOL");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");
        System.clearProperty("otel.exporter.otlp.endpoint");
        System.clearProperty("otel.exporter.otlp.insecure");

        // Remove log appender from all loggers
        if (tracingLimitsLogger != null && logAppender != null) {
            try {
                tracingLimitsLogger.detachAppender(logAppender);
                Logger otelLogger = (Logger) LoggerFactory.getLogger("io.opentelemetry.exporter.logging.LoggingSpanExporter");
                otelLogger.detachAppender(logAppender);
                Logger slf4jExporterLogger = (Logger) LoggerFactory.getLogger("io.telemetry.ai.otel.tracing.system.TelemetrySystem$Slf4jSpanExporter");
                slf4jExporterLogger.detachAppender(logAppender);
                Logger queueLogger = (Logger) LoggerFactory.getLogger("io.telemetry.ai.otel.tracing.processor.QueueAwareSpanProcessor");
                queueLogger.detachAppender(logAppender);
                Logger telemetryLogger = (Logger) LoggerFactory.getLogger("io.telemetry.ai.otel.tracing.system.TelemetrySystem");
                telemetryLogger.detachAppender(logAppender);
                Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
                rootLogger.detachAppender(logAppender);

                // Uninstall JUL bridge
                SLF4JBridgeHandler.uninstall();
            } catch (Exception e) {
                logger.warn("Error detaching log appenders", e);
            }
        }

        logger.info("Cleanup completed");
    }

    /**
     * Tests default configuration values.
     * Verifies that default values are used when properties are not set.
     */
    @Test
    public void testDefaultValues() {
        // Enable OTLP export for this test to ensure proper tracing functionality
        System.setProperty("OTLP_EXPORT", "true");

        TelemetryConfig config = TelemetryConfig.fromSystemProperties();

        // Verify batch processor settings
        assertEquals(8192, config.getBatchSize(), "Batch size should be 8192 (128MB per batch)");
        assertEquals(32768, config.getQueueSize(), "Queue size should be 32768 (512MB queue)");
        assertEquals(10000, config.getScheduleDelayMs(), "Schedule delay should be 10000ms (10 seconds)");
        assertEquals(30, config.getExportTimeoutSeconds(), "Export timeout should be 30 seconds for reliability");

        // Verify we can handle the span volume between exports
        TracingLimits limits = config.getTracingLimits();
        long spansPerSecond = (long) limits.getMaxTracesPerSecond() * limits.getMaxSpansPerTrace(); // 10,000
        long spansPerExport = spansPerSecond * (config.getScheduleDelayMs() / 1000); // 100,000
        int batchesNeeded = (int) Math.ceil((double) spansPerExport / config.getBatchSize());
        assertTrue(batchesNeeded <= 13,
                "Should need 13 or fewer batches per export, actual: " + batchesNeeded);
    }

    /**
     * Tests custom configuration values.
     * Verifies that custom values from system properties override defaults.
     */
    @Test
    public void testCustomValues() {
        // Enable OTLP export for this test to ensure proper tracing functionality
        System.setProperty("OTLP_EXPORT", "true");

        // Set custom properties
        System.setProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "4096");
        System.setProperty("OTEL_BSP_MAX_QUEUE_SIZE", "40000");
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", "1000");
        System.setProperty("TRACING_SAMPLING_RATE", "0.5");
        System.setProperty("TRACING_MAX_TRACES_PER_SECOND", "500");

        TelemetryConfig config = TelemetryConfig.fromSystemProperties();
        TracingLimits limits = config.getTracingLimits();

        // Verify custom settings
        assertEquals(4096, config.getBatchSize(), "Custom batch size should be applied");
        assertEquals(40000, config.getQueueSize(), "Custom queue size should be applied");
        assertEquals(1000, config.getScheduleDelayMs(), "Custom schedule delay should be applied");
        assertEquals(0.5, limits.getSamplingRate(), "Custom sampling rate should be applied");
        assertEquals(500, limits.getMaxTracesPerSecond(), "Custom traces per second should be applied");
    }

    /**
     * Tests queue capacity configuration and validation.
     * Verifies that the queue can hold an appropriate amount of spans
     * based on the configured tracing limits and memory constraints.
     */
    @Test
    public void testQueueCapacity() {
        // Enable OTLP export for this test to ensure proper tracing functionality
        System.setProperty("OTLP_EXPORT", "true");

        TelemetryConfig config = TelemetryConfig.fromSystemProperties();
        TracingLimits limits = config.getTracingLimits();

        // Calculate how many seconds of spans the queue can hold
        double spansPerSecond = limits.getMaxTracesPerSecond() * limits.getMaxSpansPerTrace();
        double queueCapacitySeconds = config.getQueueSize() / spansPerSecond;

        // Queue should hold at least 0.5 seconds worth of spans
        assertTrue(queueCapacitySeconds >= 0.5,
                "Queue should hold at least 0.5 seconds of spans, actual: " + queueCapacitySeconds + " seconds");

        // Queue shouldn't be too large (memory constraint)
        assertTrue(queueCapacitySeconds <= 4.0,
                "Queue should not hold more than 4 seconds of spans, actual: " + queueCapacitySeconds + " seconds");
    }

    /**
     * Tests export timing configuration.
     * Verifies that export timing settings are properly configured,
     * including exports per second, spans per export, and batch timing.
     */
    @Test
    public void testExportTiming() {
        // Enable OTLP export for this test to ensure proper tracing functionality
        System.setProperty("OTLP_EXPORT", "true");

        TelemetryConfig config = TelemetryConfig.fromSystemProperties();

        // Calculate exports per second
        double exportsPerSecond = 1000.0 / config.getScheduleDelayMs();

        // With 10000ms delay, should be 0.1 exports per second
        assertEquals(0.1, exportsPerSecond, 0.01,
                "Should have 0.1 exports per second with 10-second delay");

        // Calculate spans per export
        TracingLimits limits = config.getTracingLimits();
        double spansPerSecond = limits.getMaxTracesPerSecond() * limits.getMaxSpansPerTrace();
        double spansPerExport = spansPerSecond / exportsPerSecond;

        // Verify batch size can handle spans per export
        int batchesNeeded = (int) Math.ceil(spansPerExport / config.getBatchSize());
        assertTrue(batchesNeeded <= 13,
                "Should not need more than 13 batches per export, actual: " + batchesNeeded);

        // Verify export timeout gives enough time per batch
        double timePerBatch = (config.getExportTimeoutSeconds() * 1000.0) / batchesNeeded;
        assertTrue(timePerBatch >= 2000.0,
                "Should have at least 2 seconds per batch, actual: " + timePerBatch + "ms");
    }

    /**
     * Checks if the OTLP collector is running at the specified endpoint.
     *
     * @param host The collector host
     * @param port The collector port
     * @return true if the collector is running, false otherwise
     */
    private boolean isCollectorRunning(String host, int port) {
        try (Socket socket = new Socket()) {
            // Set a short timeout to avoid hanging the test
            socket.connect(new InetSocketAddress(host, port), 1000);
            return true;
        } catch (IOException e) {
            logger.info("OTLP collector not available at {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the OTLP collector is available at the default endpoint.
     *
     * @return true if the collector is available, false otherwise
     */
    private boolean isCollectorAvailable() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", 4317), 1000);
            socket.close();
            return true;
        } catch (IOException e) {
            logger.warn("OTLP collector not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tests batch queue full fallback behavior.
     * Verifies that the system properly handles situations where the batch queue
     * becomes full, including fallback behavior and error handling.
     * <p>
     * This test is skipped if the OTLP collector is not running.
     *
     * @throws Exception if an error occurs during test execution
     */
    @Test
    void testBatchQueueFullFallback() throws Exception {
        // Skip test if collector is not running
        Assumptions.assumeTrue(isCollectorRunning("localhost", 4317),
                "Skipping testBatchQueueFullFallback because OTLP collector is not running");

        // Set very small queue size and batch size to force fallback
        System.setProperty("OTEL_BSP_MAX_QUEUE_SIZE", "2");        // Tiny queue to force fallback
        System.setProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "1"); // Small batch size
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", "1000");     // Slower exports to help fill queue
        System.setProperty("OTEL_EXPORTER_OTLP_TIMEOUT", "1");     // 1 second timeout to fail quickly

        // Enable OTLP with default endpoint
        System.setProperty("OTLP_EXPORT", "true");
        System.setProperty("OTLP_EXPORTER", "http://localhost:4317");
        System.setProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317");
        System.setProperty("OTEL_EXPORTER_OTLP_PROTOCOL", "grpc");

        TelemetryConfig config = TelemetryConfig.fromSystemProperties();

        // Create a test agent with the configured tracing limits
        TelemetrySystem telemetryConfig = TelemetrySystemFactory.getConfiguration("config-test", "config-test-tenant");
        TelemetryAgent agent = new TelemetryAgent(telemetryConfig.getTracer());

        // Create a latch to track span completion
        CountDownLatch spanLatch = new CountDownLatch(40); // Track 40 spans (increased from 20)

        // Create multiple threads to generate spans concurrently
        Thread[] threads = new Thread[8]; // Increased from 4 to 8 threads
        for (int t = 0; t < threads.length; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    // Each thread creates 5 spans
                    for (int i = 0; i < 5; i++) {
                        String spanName = String.format("test-batch-t%d-%d", threadId, i);
                        io.opentelemetry.api.trace.Span span = agent.startSpan(
                                spanName,
                                io.opentelemetry.api.trace.SpanKind.INTERNAL,
                                "config-test",
                                "test-instance",
                                "test query"
                        );

                        if (span != io.opentelemetry.api.trace.Span.getInvalid()) {
                            try {
                                span.setAttribute("test.thread", threadId);
                                span.setAttribute("test.index", i);
                                // Add some artificial work to slow down processing
                                Thread.sleep(50);
                            } finally {
                                agent.endSpan(span, null);
                                spanLatch.countDown();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[t].start();
        }

        // Wait for all spans to be processed
        assertTrue(spanLatch.await(10, TimeUnit.SECONDS), "Failed to process all spans within timeout");

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000);
        }

        // Force flush to ensure all spans are processed
        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(5, TimeUnit.SECONDS);

        // Get captured log messages
        java.util.List<ILoggingEvent> logEvents = logAppender.list;

        // Count queue full warnings from QueueAwareSpanProcessor
        long queueFullWarnings = logEvents.stream()
                .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                .filter(event -> event.getLoggerName().equals("io.telemetry.ai.otel.tracing.processor.QueueAwareSpanProcessor"))
                .filter(event -> event.getFormattedMessage().contains("Span queue full"))
                .map(event -> event.getTimeStamp() + "|" + event.getFormattedMessage()) // Combine timestamp and message
                .distinct() // Remove duplicates
                .count();

        // Count actual fallback exports from LoggingSpanExporter
        long fallbackExports = logEvents.stream()
                .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.INFO)
                .filter(event -> event.getLoggerName().equals("io.opentelemetry.exporter.logging.LoggingSpanExporter"))
                .filter(event -> event.getMessage().contains("'test-batch-"))
                .count();

        // Verify that we got queue full warnings and matching fallback exports
        assertTrue(queueFullWarnings >= 3, "Expected at least 3 queue full warnings, got: " + queueFullWarnings);
        assertTrue(fallbackExports >= queueFullWarnings,
                String.format("Expected fallback exports (%d) to be >= queue full warnings (%d)",
                        fallbackExports, queueFullWarnings));

        // Verify we can still create and process spans after queue overflow
        io.opentelemetry.api.trace.Span finalSpan = agent.startSpan(
                "test-after-overflow",
                io.opentelemetry.api.trace.SpanKind.INTERNAL,
                "test-tenant",
                "test-instance"
        );

        assertNotEquals(io.opentelemetry.api.trace.Span.getInvalid(), finalSpan,
                "Should be able to create new spans after queue overflow");
        agent.endSpan(finalSpan, null);
    }

    @Test
    public void testTracingLimits() {
        // Enable OTLP export for this test to ensure proper tracing functionality
        System.setProperty("OTLP_EXPORT", "true");

        System.setProperty("TRACING_SAMPLING_RATE", "0.5");
        System.setProperty("TRACING_MAX_TRACES_PER_SECOND", "500");
        System.setProperty("TRACING_MAX_SPANS_PER_TRACE", "5");
        System.setProperty("TRACING_MAX_ATTRIBUTES_PER_SPAN", "16");
        System.setProperty("TRACING_MAX_EVENTS_PER_SPAN", "32");
        System.setProperty("TRACING_MAX_SPAN_SIZE_BYTES", "8192");

        // Set schedule delay explicitly
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", "1000");

        TelemetryConfig config = TelemetryConfig.fromSystemProperties();

        // Verify custom settings
        assertEquals(8192, config.getBatchSize(), "Custom batch size should be applied");
        assertEquals(32768, config.getQueueSize(), "Custom queue size should be applied");
        assertEquals(1000, config.getScheduleDelayMs(), "Custom schedule delay should be applied");
        assertEquals(0.5, config.getTracingLimits().getSamplingRate(), "Custom sampling rate should be applied");
        assertEquals(500, config.getTracingLimits().getMaxTracesPerSecond(), "Custom traces per second should be applied");
        assertEquals(5, config.getTracingLimits().getMaxSpansPerTrace(), "Custom spans per trace should be applied");
        assertEquals(16, config.getTracingLimits().getMaxAttributesPerSpan(), "Custom attributes per span should be applied");
        assertEquals(32, config.getTracingLimits().getMaxEventsPerSpan(), "Custom events per span should be applied");
        assertEquals(8192, config.getTracingLimits().getMaxSpanSizeBytes(), "Custom span size bytes should be applied");
    }

    /**
     * Test that environment variables are properly read by the configuration system.
     * This test uses System Stubs to set environment variables for testing.
     */
    @Test
    public void testEnvironmentVariableReading() {
        // Set environment variable for the test
        environmentVariables.set("SERVICE_NAME", "test-env-var-service");

        // Create config and verify it uses the environment variable
        TelemetryConfig config = TelemetryConfig.fromSystemProperties();

        // Verify the environment variable value is used
        assertEquals("test-env-var-service", config.getServiceName(),
                "TelemetryConfig should use the SERVICE_NAME environment variable");
    }

    /**
     * Test that environment variables take precedence over system properties.
     */
    @Test
    public void testEnvironmentVariablePrecedence() {
        String originalSysPropValue = System.getProperty("SERVICE_NAME");

        try {
            // Set system property
            System.setProperty("SERVICE_NAME", "system-property-service");

            // Set environment variable with different value
            environmentVariables.set("SERVICE_NAME", "environment-variable-service");

            // Create the config and check which value was used
            TelemetryConfig config = TelemetryConfig.fromSystemProperties();

            // Environment variable should take precedence
            assertEquals("environment-variable-service", config.getServiceName(),
                    "Environment variable should take precedence over system property");

        } finally {
            // Restore original system property value
            if (originalSysPropValue != null) {
                System.setProperty("SERVICE_NAME", originalSysPropValue);
            } else {
                System.clearProperty("SERVICE_NAME");
            }
        }
    }
} 
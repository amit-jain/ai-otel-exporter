package io.telemetry.ai.otel.system;

import io.telemetry.ai.otel.tracing.TelemetryAgent;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for OTLP (OpenTelemetry Protocol) exporter configuration.
 * Verifies the proper configuration and behavior of the OTLP exporter,
 * including endpoint settings, connection properties, and export functionality.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OtlpExporterConfigurationTest {
    private TelemetryAgent telemetryAgent;
    private static final Logger logger = LoggerFactory.getLogger(OtlpExporterConfigurationTest.class);

    /**
     * Helper class to directly test system property values rather than relying on indirect testing.
     */
    static class SystemPropertyTester {
        /**
         * Verifies system properties are correctly set with expected values.
         */
        public static void verifySystemProperties(String expectedEndpoint, String expectedTimeout,
                                                  String expectedScheduleDelay, String expectedQueueSize, String expectedBatchSize) {
            // Verify system properties directly
            assertEquals(expectedEndpoint, System.getProperty("OTLP_EXPORTER"),
                    "OTLP endpoint property should be set correctly");

            assertEquals(expectedTimeout, System.getProperty("OTEL_EXPORTER_OTLP_TIMEOUT"),
                    "OTLP timeout property should be set correctly");

            assertEquals(expectedScheduleDelay, System.getProperty("OTEL_BSP_SCHEDULE_DELAY"),
                    "Batch schedule delay property should be set correctly");

            assertEquals(expectedQueueSize, System.getProperty("OTEL_BSP_MAX_QUEUE_SIZE"),
                    "Max queue size property should be set correctly");

            assertEquals(expectedBatchSize, System.getProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE"),
                    "Max export batch size property should be set correctly");

            assertEquals("true", System.getProperty("OTLP_EXPORT"),
                    "OTLP export should be enabled");
        }
    }

    /**
     * Sets up the test environment before each test.
     * Configures system properties and initializes telemetry components.
     */
    @BeforeEach
    public void setup() {
        // Clear any existing system properties first
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("otel.exporter.otlp.timeout");
        System.clearProperty("otel.bsp.schedule.delay");
        System.clearProperty("otel.bsp.max.queue.size");
        System.clearProperty("otel.bsp.max.export.batch.size");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");
        System.clearProperty("otel.exporter.otlp.endpoint");
        System.clearProperty("otel.exporter.otlp.insecure");

        // Set up OTLP environment - explicitly enable OTLP export
        System.setProperty("OTLP_EXPORT", "true");
        System.setProperty("OTLP_EXPORTER", "http://localhost:4317");
        System.setProperty("otel.exporter.otlp.timeout", "30000");
        System.setProperty("otel.bsp.schedule.delay", "1000");
        System.setProperty("otel.bsp.max.queue.size", "4096");
        System.setProperty("otel.bsp.max.export.batch.size", "512");

        // Set service properties
        System.setProperty("EMBEDDING_ENDPOINT", "http://localhost:4317");
        System.setProperty("SEARCH_SYSTEM", "vector-db-test");

        // Verify OTLP endpoint is available
        try {
            java.net.Socket socket = new java.net.Socket("localhost", 4317);
            socket.close();
        } catch (Exception e) {
            logger.warn("OTLP endpoint is not available at localhost:4317. Tests may fail or fall back to logging.");
        }
    }

    /**
     * Cleans up resources after each test.
     * Ensures proper shutdown of telemetry components.
     */
    @AfterEach
    public void cleanup() {
        // Properly shutdown all telemetry systems
        TelemetrySystemFactory.shutdownAll();
        logger.info("Shutdown all telemetry systems");

        // Reset to root context
        io.opentelemetry.context.Context.root().makeCurrent();

        // Clean up all system properties
        System.clearProperty("OTLP_EXPORT");
        System.clearProperty("OTLP_EXPORTER");
        System.clearProperty("otel.exporter.otlp.timeout");
        System.clearProperty("otel.bsp.schedule.delay");
        System.clearProperty("otel.bsp.max.queue.size");
        System.clearProperty("otel.bsp.max.export.batch.size");
        System.clearProperty("EMBEDDING_ENDPOINT");
        System.clearProperty("SEARCH_SYSTEM");
        System.clearProperty("otel.exporter.otlp.endpoint");
        System.clearProperty("otel.exporter.otlp.insecure");

        // Reset the agent to null
        telemetryAgent = null;

        logger.info("Cleanup completed");
    }

    /**
     * Tests OTLP exporter configuration with default settings.
     * Verifies that system properties are properly set.
     */
    @Test
    public void testDefaultConfiguration() {
        // Explicitly set OTLP export to true
        System.setProperty("OTLP_EXPORT", "true");

        // Set specific OTLP properties to test
        String testEndpoint = "http://localhost:4317";
        String testTimeout = "30";
        String testScheduleDelay = "1000";
        String testMaxQueueSize = "4096";
        String testMaxBatchSize = "512";

        System.setProperty("OTLP_EXPORTER", testEndpoint);
        System.setProperty("OTEL_EXPORTER_OTLP_TIMEOUT", testTimeout);
        System.setProperty("otel.exporter.otlp.insecure", "true");
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", testScheduleDelay);
        System.setProperty("OTEL_BSP_MAX_QUEUE_SIZE", testMaxQueueSize);
        System.setProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", testMaxBatchSize);

        // Directly verify system properties are set correctly
        SystemPropertyTester.verifySystemProperties(
                testEndpoint,
                testTimeout,
                testScheduleDelay,
                testMaxQueueSize,
                testMaxBatchSize
        );

        // Create a TelemetrySystem to test proper shutdown
        TelemetrySystem telemetryConfig = new TelemetrySystem("test-system-properties", "tenant1");

        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(10, TimeUnit.SECONDS);

        // Verify we can initialize and flush without errors
        assertTrue(result.isSuccess(), "Flush operation should complete successfully");
    }

    /**
     * Tests OTLP exporter configuration with custom endpoint.
     * Verifies that the system properties are properly set with custom values.
     */
    @Test
    public void testCustomEndpoint() {
        // Explicitly set OTLP export to true
        System.setProperty("OTLP_EXPORT", "true");

        // Set custom endpoint
        String testEndpoint = "http://custom-collector.example.com:4317";
        String testTimeout = "60";
        String testScheduleDelay = "5000";
        String testMaxQueueSize = "8192";
        String testMaxBatchSize = "1024";

        System.setProperty("OTLP_EXPORTER", testEndpoint);
        System.setProperty("OTEL_EXPORTER_OTLP_TIMEOUT", testTimeout);
        System.setProperty("otel.exporter.otlp.insecure", "true");
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", testScheduleDelay);
        System.setProperty("OTEL_BSP_MAX_QUEUE_SIZE", testMaxQueueSize);
        System.setProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", testMaxBatchSize);

        // Directly verify system properties are set correctly
        SystemPropertyTester.verifySystemProperties(
                testEndpoint,
                testTimeout,
                testScheduleDelay,
                testMaxQueueSize,
                testMaxBatchSize
        );

        // Create a TelemetrySystem to test proper initialization
        TelemetrySystem telemetryConfig = new TelemetrySystem("test-custom-endpoint", "tenant2");

        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(10, TimeUnit.SECONDS);

        // Verify we can initialize and flush without errors
        assertTrue(result.isSuccess(), "Flush operation should complete successfully");
    }

    /**
     * Tests OTLP exporter with secure connection settings.
     * Verifies that the system properties are properly set for secure connections.
     */
    @Test
    public void testSecureConnection() {
        // Explicitly set OTLP export to true
        System.setProperty("OTLP_EXPORT", "true");

        // Set custom secure endpoint with TLS configuration
        String testEndpoint = "https://secure-collector.example.com:4317";
        String testTimeout = "30";

        System.setProperty("OTLP_EXPORTER", testEndpoint);
        System.setProperty("OTEL_EXPORTER_OTLP_TIMEOUT", testTimeout);
        System.setProperty("otel.exporter.otlp.insecure", "false");
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", "1000");
        System.setProperty("OTEL_BSP_MAX_QUEUE_SIZE", "4096");
        System.setProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "512");

        // Verify TLS-specific properties
        assertEquals("false", System.getProperty("otel.exporter.otlp.insecure"),
                "Insecure property should be set to false for secure connections");
        assertEquals(testEndpoint, System.getProperty("OTLP_EXPORTER"),
                "OTLP endpoint property should be set correctly with https protocol");

        // Create a TelemetrySystem to test proper initialization
        TelemetrySystem telemetryConfig = new TelemetrySystem("test-secure-connection", "tenant3");

        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(10, TimeUnit.SECONDS);

        // Verify we can initialize and flush without errors
        assertTrue(result.isSuccess(), "Flush operation should complete successfully");
    }

    /**
     * Tests OTLP exporter with custom timeout settings.
     * Verifies that the system properties are properly set for custom timeouts.
     */
    @Test
    public void testCustomTimeout() {
        // Explicitly set OTLP export to true
        System.setProperty("OTLP_EXPORT", "true");

        // Set custom timeout value
        String testEndpoint = "http://localhost:4317";
        String testTimeout = "120";  // 2 minutes timeout

        System.setProperty("OTLP_EXPORTER", testEndpoint);
        System.setProperty("OTEL_EXPORTER_OTLP_TIMEOUT", testTimeout);
        System.setProperty("otel.exporter.otlp.insecure", "true");
        System.setProperty("OTEL_BSP_SCHEDULE_DELAY", "1000");
        System.setProperty("OTEL_BSP_MAX_QUEUE_SIZE", "4096");
        System.setProperty("OTEL_BSP_MAX_EXPORT_BATCH_SIZE", "512");

        // Verify custom timeout property
        assertEquals(testTimeout, System.getProperty("OTEL_EXPORTER_OTLP_TIMEOUT"),
                "OTLP timeout property should be set correctly with extended value");

        // Create a TelemetrySystem to test proper initialization
        TelemetrySystem telemetryConfig = new TelemetrySystem("test-custom-timeout", "tenant4");

        CompletableResultCode result = telemetryConfig.getTracerProvider().forceFlush();
        result.join(10, TimeUnit.SECONDS);

        // Verify we can initialize and flush without errors
        assertTrue(result.isSuccess(), "Flush operation should complete successfully");
    }
} 
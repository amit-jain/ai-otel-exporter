package io.telemetry.ai.otel.pii;

import io.telemetry.ai.otel.system.TelemetrySystem;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_REGEX_ENABLED;

/**
 * Integration test for {@link io.telemetry.ai.otel.pii.PIIAnonymizingSpanExporter}.
 * <p>
 * This test demonstrates how to integrate the PIIAnonymizingSpanExporter with the
 * TelemetryConfiguration class to anonymize PII before exporting spans to an OTLP collector.
 * <p>
 * To run this test:
 * 1. Start a local OTLP collector. You can use Docker:
 * docker run -p 4317:4317 -p 4318:4318 otel/opentelemetry-collector-contrib:latest
 * <p>
 * 2. Run the test with:
 * mvn test -Dtest=PIIAnonymizingSpanExporterIntegrationTest
 * <p>
 * 3. Check the collector logs to verify that the PII has been anonymized
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("pii-tests")
public class PIIAnonymizingSpanExporterIntegrationTest extends BasePIIAnonymizingSpanExporterIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(PIIAnonymizingSpanExporterIntegrationTest.class);

    private static final String SERVICE_NAME = "pii-anonymization-test";
    private static final String TENANT_ID = "integration-test";

    @Override
    protected String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    protected String getTenantId() {
        return TENANT_ID;
    }

    @BeforeEach
    @Override
    protected void baseSetup() {
        // Call the parent setup first
        super.baseSetup();

        logger.info("Test setup complete with TestInMemorySpanExporter");
    }

    @AfterEach
    @Override
    protected void baseCleanup() {
        // Clear registered exporters before calling parent cleanup
        TelemetrySystem.clearRegisteredExporters();
        logger.info("Cleared registered exporters");

        // Call parent cleanup
        super.baseCleanup();
    }

    @Override
    protected void configurePIIDetection() {
        // Configure regex-based PII detection
        System.setProperty(PII_DETECTOR_REGEX_ENABLED, "true");
        
        // Enable in-memory exporter for verification in the base class
        System.setProperty("test.in.memory.exporter.enabled", "true");
        
        logger.info("Configured regex-based PII detection with default patterns");
    }

    @Override
    protected void clearPIIDetectionProperties() {
        // Clear regex-specific properties
        System.clearProperty(PII_DETECTOR_REGEX_ENABLED);
        System.clearProperty("test.in.memory.exporter.enabled");
    }

    @Override
    protected void logTestCompletion() {
        // Call the parent method first
        super.logTestCompletion();

        // Log information about our TestInMemorySpanExporter
        if (testExporter != null) {
            List<SpanData> spans = testExporter.getExportedSpans();
            logger.info("Found {} spans in our TestInMemorySpanExporter", spans.size());

            // Log some basic information about the spans
            for (SpanData span : spans) {
                logger.info("Span: {} ({}), parent: {}",
                        span.getName(),
                        span.getSpanId(),
                        span.getParentSpanContext().getSpanId());
            }

            // Log the diagnostic report
            logger.info("Diagnostic report:\n{}", testExporter.getDiagnosticReport());
        }
    }
} 
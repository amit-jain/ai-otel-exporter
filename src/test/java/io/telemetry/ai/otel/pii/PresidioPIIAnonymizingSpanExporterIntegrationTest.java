package io.telemetry.ai.otel.pii;

import io.telemetry.ai.otel.config.TelemetryConfigConstants;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
/**
 * Integration test for PIIAnonymizingSpanExporter with Presidio service.
 * <p>
 * This test checks if the Presidio service is running and only executes if Presidio is available.
 * The test will be skipped if Presidio services are not available.
 * <p>
 * To run this test:
 * 1. Start the Presidio services using Docker:
 * docker run -d -p 5002:3000 --name presidio-analyzer mcr.microsoft.com/presidio-analyzer:latest
 * docker run -d -p 5001:3000 --name presidio-anonymizer mcr.microsoft.com/presidio-anonymizer:latest
 * <p>
 * 2. Run the test with:
 * mvn test -Dtest=PresidioPIIAnonymizingSpanExporterIntegrationTest
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("pii-tests")
public class PresidioPIIAnonymizingSpanExporterIntegrationTest extends BasePIIAnonymizingSpanExporterIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(PresidioPIIAnonymizingSpanExporterIntegrationTest.class);

    private static final String SERVICE_NAME = "presidio-pii-anonymization-test";
    private static final String TENANT_ID = "presidio-integration-test";

    // Presidio service endpoints
    private static final String PRESIDIO_ANALYZER_ENDPOINT = "http://localhost:5002/analyze";
    private static final String PRESIDIO_ANONYMIZER_ENDPOINT = "http://localhost:5001/anonymize";

    /**
     * Check if Presidio is available before running the test.
     * If Presidio is not available, the test will be skipped.
     */
    @BeforeEach
    public void checkPresidioBeforeTest() {
        boolean presidioAvailable = checkPresidioAvailability();
        logger.info("Presidio services available: {}", presidioAvailable);
        // Skip the test if Presidio is not available
        Assumptions.assumeTrue(presidioAvailable, "Presidio services are not available. Test will be skipped.");
    }

    /**
     * Checks if the Presidio services are available.
     *
     * @return true if both analyzer and anonymizer services are available, false otherwise
     */
    private boolean checkPresidioAvailability() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        try {
            // Check analyzer service
            HttpRequest analyzerRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PRESIDIO_ANALYZER_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"text\": \"test\", \"language\": \"en\"}"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> analyzerResponse = client.send(analyzerRequest, HttpResponse.BodyHandlers.ofString());
            boolean analyzerAvailable = analyzerResponse.statusCode() == 200;

            // Check anonymizer service
            HttpRequest anonymizerRequest = HttpRequest.newBuilder()
                    .uri(URI.create(PRESIDIO_ANONYMIZER_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"text\": \"test\", \"analyzer_results\": [], \"anonymizers\": {\"DEFAULT\": {\"type\": \"replace\", \"new_value\": \"[REDACTED]\"}}}"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> anonymizerResponse = client.send(anonymizerRequest, HttpResponse.BodyHandlers.ofString());
            boolean anonymizerAvailable = anonymizerResponse.statusCode() == 200;

            return analyzerAvailable && anonymizerAvailable;
        } catch (IOException | InterruptedException e) {
            logger.warn("Error checking Presidio availability: {}", e.getMessage());
            return false;
        }
    }

    @Override
    protected String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    protected String getTenantId() {
        return TENANT_ID;
    }

    @Override
    protected void configurePIIDetection() {
        // Configure Presidio detection
        System.setProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ENABLED, "true");
        System.setProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT, PRESIDIO_ANALYZER_ENDPOINT);
        System.setProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT, PRESIDIO_ANONYMIZER_ENDPOINT);
        System.setProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS, "5");
        logger.info("Configured Presidio PII detection with analyzer endpoint: {} and anonymizer endpoint: {}",
                PRESIDIO_ANALYZER_ENDPOINT, PRESIDIO_ANONYMIZER_ENDPOINT);

        // Explicitly disable regex detection to ensure only Presidio is used
        System.setProperty(TelemetryConfigConstants.PII_DETECTOR_REGEX_ENABLED, "false");
        logger.info("Explicitly disabled regex-based PII detection to ensure only Presidio is used");
    }

    @Override
    protected void clearPIIDetectionProperties() {
        // Clear Presidio-specific properties
        System.clearProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ENABLED);
        System.clearProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT);
        System.clearProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT);
        System.clearProperty(TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS);
    }

    @Override
    protected void logTestCompletion() {
        logger.info("PresidioPIIAnonymizingSpanExporterIntegrationTest complete");
        logger.info("PII detection was performed using Presidio with analyzer endpoint: {} and anonymizer endpoint: {}",
                PRESIDIO_ANALYZER_ENDPOINT, PRESIDIO_ANONYMIZER_ENDPOINT);
        logger.info("Check the collector logs to verify PII anonymization");
    }
} 
package io.telemetry.ai.otel.pii;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

import java.util.Map;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_REGEX_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the PIIDetectorConfig class.
 */
@ExtendWith(SystemStubsExtension.class)
public class PIIDetectorConfigTest {

    @SystemStub
    private SystemProperties systemProperties;

    @SystemStub
    private EnvironmentVariables environmentVariables;

    @BeforeEach
    void setup() {
        clearProperties();
    }

    @AfterEach
    void tearDown() {
        clearProperties();
    }

    private void clearProperties() {
        System.clearProperty(PII_DETECTOR_ENABLED);
        System.clearProperty(PII_DETECTOR_REGEX_ENABLED);
        System.clearProperty(PII_DETECTOR_PRESIDIO_ENABLED);
        System.clearProperty(PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT);
        System.clearProperty(PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT);
        System.clearProperty(PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS);
    }

    @Test
    void testDefaultValues() {
        PIIDetectorConfig config = PIIDetectorConfig.fromSystemProperties();
        
        assertTrue(config.isEnabled(), "PII detection should be enabled by default");
        assertTrue(config.isRegexDetectionEnabled(), "Regex detection should be enabled by default");
        assertFalse(config.isPresidioDetectionEnabled(), "Presidio detection should be disabled by default");
        
        assertEquals("http://presidio-analyzer:3000/analyze", config.getPresidioAnalyzerEndpoint(), 
                "Default analyzer endpoint should be set");
        assertEquals("http://presidio-anonymizer:3001/anonymize", config.getPresidioAnonymizerEndpoint(), 
                "Default anonymizer endpoint should be set");
        assertEquals(5, config.getPresidioTimeoutSeconds(), 
                "Default timeout should be 5 seconds");
        
        // Check default regexPatterns
        Map<String, String> patterns = config.getRegexPatterns();
        assertTrue(patterns.size() >= 5, "Should have at least 5 default patterns");
    }

    @Test
    void testSystemPropertyConfiguration() {
        systemProperties.set(PII_DETECTOR_ENABLED, "false");
        systemProperties.set(PII_DETECTOR_REGEX_ENABLED, "false");
        systemProperties.set(PII_DETECTOR_PRESIDIO_ENABLED, "true");
        systemProperties.set(PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT, "http://custom-analyzer:8080");
        systemProperties.set(PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT, "http://custom-anonymizer:8081");
        systemProperties.set(PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS, "10");
        
        PIIDetectorConfig config = PIIDetectorConfig.fromSystemProperties();
        
        assertFalse(config.isEnabled(), "PII detection should be disabled");
        assertFalse(config.isRegexDetectionEnabled(), "Regex detection should be disabled");
        assertTrue(config.isPresidioDetectionEnabled(), "Presidio detection should be enabled");
        
        assertEquals("http://custom-analyzer:8080", config.getPresidioAnalyzerEndpoint(), 
                "Custom analyzer endpoint should be set");
        assertEquals("http://custom-anonymizer:8081", config.getPresidioAnonymizerEndpoint(), 
                "Custom anonymizer endpoint should be set");
        assertEquals(10, config.getPresidioTimeoutSeconds(), 
                "Custom timeout should be 10 seconds");
    }

    @Test
    void testEnvironmentVariableConfiguration() {
        environmentVariables.set(PII_DETECTOR_ENABLED, "false");
        environmentVariables.set(PII_DETECTOR_REGEX_ENABLED, "false");
        environmentVariables.set(PII_DETECTOR_PRESIDIO_ENABLED, "true");
        environmentVariables.set(PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT, "http://env-analyzer:9090");
        environmentVariables.set(PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT, "http://env-anonymizer:9091");
        environmentVariables.set(PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS, "15");
        
        PIIDetectorConfig config = PIIDetectorConfig.fromSystemProperties();
        
        assertFalse(config.isEnabled(), "PII detection should be disabled");
        assertFalse(config.isRegexDetectionEnabled(), "Regex detection should be disabled");
        assertTrue(config.isPresidioDetectionEnabled(), "Presidio detection should be enabled");
        
        assertEquals("http://env-analyzer:9090", config.getPresidioAnalyzerEndpoint(), 
                "Environment analyzer endpoint should be set");
        assertEquals("http://env-anonymizer:9091", config.getPresidioAnonymizerEndpoint(), 
                "Environment anonymizer endpoint should be set");
        assertEquals(15, config.getPresidioTimeoutSeconds(), 
                "Environment timeout should be 15 seconds");
    }

    @Test
    void testEnvironmentVariableOverridesSystemProperty() {
        // Set system properties
        systemProperties.set(PII_DETECTOR_ENABLED, "false");
        systemProperties.set(PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT, "http://sys-analyzer:8080");
        
        // Set environment variables with different values
        environmentVariables.set(PII_DETECTOR_ENABLED, "true");
        environmentVariables.set(PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT, "http://env-analyzer:9090");
        
        PIIDetectorConfig config = PIIDetectorConfig.fromSystemProperties();
        
        // Environment variables should take precedence
        assertTrue(config.isEnabled(), "Environment variable should override system property");
        assertEquals("http://env-analyzer:9090", config.getPresidioAnalyzerEndpoint(), 
                "Environment variable should override system property");
    }

    @Test
    void testConfigBuilderConstructor() {
        PIIDetectorConfig config = new PIIDetectorConfig(false, false, true, 
                null, "http://custom-analyzer:8080", "http://custom-anonymizer:8081", 20);
        
        assertFalse(config.isEnabled(), "PII detection should be disabled");
        assertFalse(config.isRegexDetectionEnabled(), "Regex detection should be disabled");
        assertTrue(config.isPresidioDetectionEnabled(), "Presidio detection should be enabled");
        
        assertEquals("http://custom-analyzer:8080", config.getPresidioAnalyzerEndpoint());
        assertEquals("http://custom-anonymizer:8081", config.getPresidioAnonymizerEndpoint());
        assertEquals(20, config.getPresidioTimeoutSeconds());
        
        // Should still have default regex patterns
        Map<String, String> patterns = config.getRegexPatterns();
        assertTrue(patterns.size() >= 5, "Should have default patterns when null is passed");
    }

    @Test
    void testInvalidTimeoutValue() {
        systemProperties.set(PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS, "not-a-number");
        
        PIIDetectorConfig config = PIIDetectorConfig.fromSystemProperties();
        
        // Should fall back to default
        assertEquals(5, config.getPresidioTimeoutSeconds(), 
                "Invalid timeout should fall back to default");
    }
} 
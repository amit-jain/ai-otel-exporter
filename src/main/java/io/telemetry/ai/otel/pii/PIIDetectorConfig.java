package io.telemetry.ai.otel.pii;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_PII_PRESIDIO_ANALYZER_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_PII_PRESIDIO_ANONYMIZER_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_PII_PRESIDIO_TIMEOUT_SECONDS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_CREDIT_CARD_PATTERN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_CREDIT_CARD_REPLACEMENT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_DETECTOR_REGEX_ENABLED;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_EMAIL_PATTERN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_EMAIL_REPLACEMENT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_IP_ADDRESS_PATTERN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_IP_ADDRESS_REPLACEMENT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_PHONE_PATTERN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_PHONE_REPLACEMENT;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_SSN_PATTERN;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_SSN_REPLACEMENT;

/**
 * Configuration for the PII detector.
 */
public class PIIDetectorConfig {
    /**
     *  Gets whether the PII detector is enabled.
     *
     *
     *  Sets whether the PII detector is enabled.
     *
     @return Whether the PII detector is enabled
      * @param enabled Whether the PII detector is enabled
     */
    @Setter
    @Getter
    private boolean enabled = true;
    /**
     * -- GETTER --
     *  Gets whether regex-based detection is enabled.
     *
     *
     * -- SETTER --
     *  Sets whether regex-based detection is enabled.
     *
     @return Whether regex-based detection is enabled
      * @param regexDetectionEnabled Whether regex-based detection is enabled
     */
    @Setter
    @Getter
    private boolean regexDetectionEnabled = true;
    /**
     * -- GETTER --
     *  Gets whether Presidio-based detection is enabled.
     *
     *
     * -- SETTER --
     *  Sets whether Presidio-based detection is enabled.
     *
     @return Whether Presidio-based detection is enabled
      * @param presidioDetectionEnabled Whether Presidio-based detection is enabled
     */
    @Setter
    @Getter
    private boolean presidioDetectionEnabled = false;
    private Map<String, String> regexPatterns;
    /**
     * -- GETTER --
     *  Gets the endpoint for the Presidio analyzer service.
     *
     *
     * -- SETTER --
     *  Sets the endpoint for the Presidio analyzer service.
     *
     @return The endpoint for the Presidio analyzer service
      * @param presidioAnalyzerEndpoint The endpoint for the Presidio analyzer service
     */
    @Setter
    @Getter
    private String presidioAnalyzerEndpoint = DEFAULT_PII_PRESIDIO_ANALYZER_ENDPOINT;
    /**
     * -- GETTER --
     *  Gets the endpoint for the Presidio anonymizer service.
     *
     *
     * -- SETTER --
     *  Sets the endpoint for the Presidio anonymizer service.
     *
     @return The endpoint for the Presidio anonymizer service
      * @param presidioAnonymizerEndpoint The endpoint for the Presidio anonymizer service
     */
    @Setter
    @Getter
    private String presidioAnonymizerEndpoint = DEFAULT_PII_PRESIDIO_ANONYMIZER_ENDPOINT;
    /**
     * -- GETTER --
     *  Gets the timeout in seconds for Presidio API calls.
     *
     *
     * -- SETTER --
     *  Sets the timeout in seconds for Presidio API calls.
     *
     @return The timeout in seconds for Presidio API calls
      * @param presidioTimeoutSeconds The timeout in seconds for Presidio API calls
     */
    @Setter
    @Getter
    private int presidioTimeoutSeconds = DEFAULT_PII_PRESIDIO_TIMEOUT_SECONDS;

    /**
     * Creates a new PIIDetectorConfig with default settings.
     */
    public PIIDetectorConfig() {
        this.regexPatterns = getDefaultRegexPatterns();
    }

    /**
     * Creates a new PIIDetectorConfig with custom settings.
     *
     * @param enabled                    Whether the PII detector is enabled
     * @param regexDetectionEnabled      Whether regex-based detection is enabled
     * @param presidioDetectionEnabled   Whether Presidio-based detection is enabled
     * @param regexPatterns              Map of regex patterns to replacement strings
     * @param presidioAnalyzerEndpoint   Endpoint for the Presidio analyzer service
     * @param presidioAnonymizerEndpoint Endpoint for the Presidio anonymizer service
     * @param presidioTimeoutSeconds     Timeout in seconds for Presidio API calls
     */
    public PIIDetectorConfig(boolean enabled, boolean regexDetectionEnabled, boolean presidioDetectionEnabled,
                             Map<String, String> regexPatterns,
                             String presidioAnalyzerEndpoint, String presidioAnonymizerEndpoint, int presidioTimeoutSeconds) {
        this.enabled = enabled;
        this.regexDetectionEnabled = regexDetectionEnabled;
        this.presidioDetectionEnabled = presidioDetectionEnabled;
        this.regexPatterns = regexPatterns != null ? new HashMap<>(regexPatterns) : getDefaultRegexPatterns();
        this.presidioAnalyzerEndpoint = presidioAnalyzerEndpoint;
        this.presidioAnonymizerEndpoint = presidioAnonymizerEndpoint;
        this.presidioTimeoutSeconds = presidioTimeoutSeconds;
    }

    /**
     * Creates a PIIDetectorConfig from system properties and environment variables.
     * Environment variables take precedence over system properties.
     *
     * @return A new PIIDetectorConfig instance
     */
    public static PIIDetectorConfig fromSystemProperties() {
        boolean enabled = getBooleanConfigValue(PII_DETECTOR_ENABLED, true);
        boolean regexEnabled = getBooleanConfigValue(PII_DETECTOR_REGEX_ENABLED, true);
        boolean presidioEnabled = getBooleanConfigValue(PII_DETECTOR_PRESIDIO_ENABLED, false);
        String analyzerEndpoint = getStringConfigValue(PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT, DEFAULT_PII_PRESIDIO_ANALYZER_ENDPOINT);
        String anonymizerEndpoint = getStringConfigValue(PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT, DEFAULT_PII_PRESIDIO_ANONYMIZER_ENDPOINT);
        int timeoutSeconds = getIntConfigValue(PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS, DEFAULT_PII_PRESIDIO_TIMEOUT_SECONDS);

        return new PIIDetectorConfig(enabled, regexEnabled, presidioEnabled, null,
                analyzerEndpoint, anonymizerEndpoint, timeoutSeconds);
    }

    /**
     * Gets a boolean configuration value from system properties or environment variables.
     * Environment variables take precedence over system properties.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    private static boolean getBooleanConfigValue(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key, String.valueOf(defaultValue));
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets a string configuration value from system properties or environment variables.
     * Environment variables take precedence over system properties.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    private static String getStringConfigValue(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key, defaultValue);
        }
        return value;
    }

    /**
     * Gets an integer configuration value from system properties or environment variables.
     * Environment variables take precedence over system properties.
     *
     * @param key The configuration key
     * @param defaultValue The default value
     * @return The configuration value
     */
    private static int getIntConfigValue(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key, String.valueOf(defaultValue));
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets the default regex patterns for PII detection.
     *
     * @return A map of regex patterns to replacement strings
     */
    private static Map<String, String> getDefaultRegexPatterns() {
        Map<String, String> patterns = new HashMap<>();

        // Email addresses
        patterns.put(PII_EMAIL_PATTERN, PII_EMAIL_REPLACEMENT);

        // Phone numbers (US format)
        patterns.put(PII_PHONE_PATTERN, PII_PHONE_REPLACEMENT);

        // Social Security Numbers
        patterns.put(PII_SSN_PATTERN, PII_SSN_REPLACEMENT);

        // Credit card numbers
        patterns.put(PII_CREDIT_CARD_PATTERN, PII_CREDIT_CARD_REPLACEMENT);

        // IP addresses
        patterns.put(PII_IP_ADDRESS_PATTERN, PII_IP_ADDRESS_REPLACEMENT);

        return patterns;
    }

    /**
     * Gets the regex patterns for PII detection.
     *
     * @return A map of regex patterns to replacement strings
     */
    public Map<String, String> getRegexPatterns() {
        return Collections.unmodifiableMap(regexPatterns);
    }

    /**
     * Sets the regex patterns for PII detection.
     *
     * @param regexPatterns A map of regex patterns to replacement strings
     */
    public void setRegexPatterns(Map<String, String> regexPatterns) {
        this.regexPatterns = new HashMap<>(regexPatterns);
    }

    /**
     * Adds a regex pattern for PII detection.
     *
     * @param pattern     The regex pattern
     * @param replacement The replacement string
     */
    public void addRegexPattern(String pattern, String replacement) {
        this.regexPatterns.put(pattern, replacement);
    }

}
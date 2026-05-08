package io.telemetry.ai.otel.pii;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_NEW_VALUE_FIELD;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_REDACTED_VALUE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_REPLACE_TYPE;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_TEXT_FIELD;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.PII_TYPE_FIELD;

/**
 * A PII detector that uses Microsoft Presidio for PII detection and anonymization.
 * This implementation calls a Presidio API endpoint to perform the detection and anonymization.
 */
public class PresidioPIIDetector implements PIIDetector {
    private static final Logger logger = LoggerFactory.getLogger(PresidioPIIDetector.class);

    private final String analyzerEndpoint;
    private final String anonymizerEndpoint;
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    /**
     * Creates a new PresidioPIIDetector with the specified endpoints.
     *
     * @param analyzerEndpoint   The endpoint for the Presidio analyzer service
     * @param anonymizerEndpoint The endpoint for the Presidio anonymizer service
     * @param timeoutSeconds     The timeout in seconds for API calls
     */
    public PresidioPIIDetector(String analyzerEndpoint, String anonymizerEndpoint, int timeoutSeconds) {
        this.analyzerEndpoint = analyzerEndpoint;
        this.anonymizerEndpoint = anonymizerEndpoint;
        this.timeoutSeconds = timeoutSeconds;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        logger.info("Created PresidioPIIDetector with analyzer endpoint: {}, anonymizer endpoint: {}",
                analyzerEndpoint, anonymizerEndpoint);
    }

    /**
     * Anonymizes PII in the given input text using Presidio services.
     * If Presidio services are not available, returns the original input.
     *
     * @param input The text to anonymize
     * @return The anonymized text, or the original text if no PII is found or Presidio services are not available
     */
    @Override
    public String anonymize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        logger.debug("PresidioPIIDetector: Starting PII detection for input: '{}'", input);

        try {
            // First, analyze the text to find PII entities
            String analysisResult = analyzeText(input);

            // If no PII entities were found, return the original text
            if ("[]".equals(analysisResult)) {
                logger.debug("PresidioPIIDetector: No PII entities found in input");
                return input;
            }

            logger.debug("PresidioPIIDetector: Found PII entities in input: {}", analysisResult);

            // Then, anonymize the text using the analysis results
            String anonymizedText = anonymizeText(input, analysisResult);
            logger.debug("PresidioPIIDetector: Text changed after anonymization: {}", !input.equals(anonymizedText));
            return anonymizedText;
        } catch (Exception e) {
            logger.warn("Error anonymizing text with Presidio: {}", e.getMessage());
            return input;
        }
    }

    /**
     * Analyzes the text to find PII entities.
     *
     * @param text The text to analyze
     * @return The analysis results in JSON format
     * @throws Exception If an error occurs during analysis
     */
    private String analyzeText(String text) throws Exception {
        String requestBody = String.format("{\"%s\": \"%s\", \"language\": \"en\"}",
                PII_TEXT_FIELD, text.replace("\"", "\\\""));

        if (logger.isTraceEnabled()) {
            logger.trace("PresidioPIIDetector: Sending request to analyzer: {}", requestBody);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(analyzerEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());

        try {
            HttpResponse<String> response = responseFuture.get(timeoutSeconds, TimeUnit.SECONDS);

            if (logger.isTraceEnabled()) {
                logger.trace("PresidioPIIDetector: Analyzer response status: {}", response.statusCode());
                logger.trace("PresidioPIIDetector: Analyzer response body: {}", response.body());
            }

            if (response.statusCode() != 200) {
                logger.warn("Presidio analyzer returned status code: {}", response.statusCode());
                return "[]";
            }

            return response.body();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error calling Presidio analyzer: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Anonymizes the text using the analysis results.
     *
     * @param text            The text to anonymize
     * @param analysisResults The analysis results in JSON format
     * @return The anonymized text
     * @throws Exception If an error occurs during anonymization
     */
    private String anonymizeText(String text, String analysisResults) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode anonymizerRequest = mapper.createObjectNode();
        anonymizerRequest.put(PII_TEXT_FIELD, text);
        anonymizerRequest.set("analyzer_results", mapper.readTree(analysisResults));

        ObjectNode anonymizersNode = anonymizerRequest.putObject("anonymizers");
        ObjectNode defaultAnonymizer = anonymizersNode.putObject("DEFAULT");
        defaultAnonymizer.put(PII_TYPE_FIELD, PII_REPLACE_TYPE);
        defaultAnonymizer.put(PII_NEW_VALUE_FIELD, PII_REDACTED_VALUE);

        String anonymizerRequestBody = mapper.writeValueAsString(anonymizerRequest);
        if (logger.isTraceEnabled()) {
            logger.trace("PresidioPIIDetector: Anonymizer request body: {}", anonymizerRequestBody);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(anonymizerEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(anonymizerRequestBody))
                .build();

        CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());

        try {
            HttpResponse<String> response = responseFuture.get(timeoutSeconds, TimeUnit.SECONDS);

            if (logger.isTraceEnabled()) {
                logger.trace("PresidioPIIDetector: Anonymizer response status: {}", response.statusCode());
                logger.trace("PresidioPIIDetector: Anonymizer response body: {}", response.body());
            }

            if (response.statusCode() != 200) {
                logger.warn("Presidio anonymizer returned status code: {}", response.statusCode());
                return text;
            }

            // Parse the response to get the anonymized text
            Map<String, Object> anonymizerResult = mapper.readValue(response.body(),
                    new TypeReference<>() {
                    });

            String anonymizedText = (String) anonymizerResult.get("text");
            if (logger.isTraceEnabled()) {
                logger.trace("PresidioPIIDetector: Successfully extracted anonymized text: {}", anonymizedText);
            }

            return anonymizedText;
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.warn("Error calling Presidio anonymizer: {}", e.getMessage());
            throw e;
        }
    }
} 
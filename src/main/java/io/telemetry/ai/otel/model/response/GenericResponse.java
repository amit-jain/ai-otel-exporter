package io.telemetry.ai.otel.model.response;

/**
 * Generic interface for all response types in the semantic operations system.
 * Provides common methods that all response implementations must support
 * for consistent handling of input data and telemetry collection.
 */
public interface GenericResponse {
    /**
     * Gets the MIME type of the input that generated this response.
     * Used for proper content type handling and telemetry attribution.
     *
     * @return The MIME type string (e.g., "text/plain")
     */
    String getInputMimeType();

    /**
     * Gets the original input text or query that generated this response.
     * Used for tracing and debugging purposes.
     *
     * @return The original input text or query
     */
    String getInput();
} 
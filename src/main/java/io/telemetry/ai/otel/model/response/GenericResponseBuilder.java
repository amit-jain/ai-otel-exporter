package io.telemetry.ai.otel.model.response;

import java.util.HashMap;
import java.util.Map;

/**
 * A generic builder for creating response objects that implement the GenericResponse interface.
 * Provides a flexible way to construct responses with arbitrary attributes while maintaining
 * type safety and the required GenericResponse contract.
 *
 * @param <T> The type of response being built, must implement GenericResponse
 */
public class GenericResponseBuilder<T extends GenericResponse> {
    protected final Map<String, Object> attributes = new HashMap<>();

    /**
     * Adds a custom attribute to the response being built.
     *
     * @param key   The attribute key
     * @param value The attribute value
     * @return This builder instance for method chaining
     */
    public GenericResponseBuilder<T> withAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /**
     * Sets the input text for the response.
     * This is a required field for all GenericResponse implementations.
     *
     * @param input The input text that generated this response
     * @return This builder instance for method chaining
     */
    public GenericResponseBuilder<T> withInput(String input) {
        attributes.put("input", input);
        return this;
    }

    /**
     * Sets the MIME type of the input.
     * This is a required field for all GenericResponse implementations.
     *
     * @param mimeType The MIME type of the input (e.g., "text/plain")
     * @return This builder instance for method chaining
     */
    public GenericResponseBuilder<T> withInputMimeType(String mimeType) {
        attributes.put("inputMimeType", mimeType);
        return this;
    }

    /**
     * Builds the response object with all configured attributes.
     *
     * @return A new response object implementing GenericResponse
     */
    @SuppressWarnings("unchecked")
    public T build() {
        return (T) new DynamicResponse(attributes);
    }

    /**
     * A dynamic response implementation that can hold any attributes.
     * This class provides a flexible way to create response objects.
     * @param attributes Map of attribute names to their values that will be included in the response
     */
    public record DynamicResponse(Map<String, Object> attributes) implements GenericResponse {
        /**
         * Creates a new DynamicResponse with the specified attributes.
         *
         * @param attributes Map of attribute key-value pairs
         */
        public DynamicResponse(Map<String, Object> attributes) {
            this.attributes = new HashMap<>(attributes);
        }

        @Override
        public String getInput() {
            return (String) attributes.get("input");
        }

        @Override
        public String getInputMimeType() {
            return (String) attributes.get("inputMimeType");
        }

        /**
         * Gets the value of a custom attribute.
         *
         * @param key The attribute key
         * @return The attribute value, or null if not found
         */
        public Object getAttribute(String key) {
            return attributes.get(key);
        }

        /**
         * Gets all attributes as an unmodifiable map.
         *
         * @return Map of all attribute key-value pairs
         */
        @Override
        public Map<String, Object> attributes() {
            return attributes;
        }
    }
} 
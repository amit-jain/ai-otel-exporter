package io.telemetry.ai.otel.model.context;

/**
 * Generic builder for creating operation context objects.
 * Provides a type-safe way to construct context objects with common fields
 * and operation-specific attributes.
 *
 * @param <T> The type of context being built
 * @param <B> The concrete builder type (for method chaining)
 */
public abstract class GenericContextBuilder<T extends OperationContext, B extends GenericContextBuilder<T, B>> {
    protected String query;
    protected String endpoint;
    protected String searchSystem;

    /**
     * Gets the concrete builder instance for method chaining.
     *
     * @return The concrete builder instance
     */
    protected abstract B self();

    /**
     * Builds the context object with the configured values.
     *
     * @return A new context object
     */
    public abstract T build();

    /**
     * Sets the query for the operation.
     *
     * @param query The query text
     * @return The builder instance for method chaining
     */
    public B query(String query) {
        this.query = query;
        return self();
    }

    /**
     * Sets the endpoint for the operation.
     *
     * @param endpoint The endpoint URL
     * @return The builder instance for method chaining
     */
    public B endpoint(String endpoint) {
        this.endpoint = endpoint;
        return self();
    }

    /**
     * Sets the search system identifier.
     *
     * @param searchSystem The search system identifier
     * @return The builder instance for method chaining
     */
    public B searchSystem(String searchSystem) {
        this.searchSystem = searchSystem;
        return self();
    }
} 
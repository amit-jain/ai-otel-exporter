package io.telemetry.ai.otel.common;

/**
 * Constants for OpenInference semantic attributes.
 * Defines standard attribute keys for telemetry data collection
 * following the OpenInference specification.
 * See: <a href="https://openinference.github.io/">...</a>
 */
public final class OpenInferenceAttributes {
    /**
     * Private constructor to prevent instantiation of utility class
     */
    private OpenInferenceAttributes() {
    }

    // Common attributes
    /**
     * The kind of span (e.g., RETRIEVER, EMBEDDING)
     */
    public static final String SPAN_KIND = "openinference.span.kind";
    /**
     * The input value for the operation
     */
    public static final String INPUT_VALUE = "input.value";
    /**
     * The MIME type of the input (e.g., text/plain)
     */
    public static final String INPUT_MIME_TYPE = "input.mime_type";

    // LLM attributes
    /**
     * The input text for LLM operations
     */
    public static final String LLM_INPUT = "openinference.llm.input";
    /**
     * Openinference project name
     */
    public static final String OPENINFERENCE_PROJECT_NAME = "openinference.project.name";
    /**
     * The endpoint URL for LLM service
     */
    public static final String LLM_ENDPOINT = "openinference.llm.endpoint";
    /**
     * The LLM model identifier
     */
    public static final String LLM_MODEL = "openinference.llm.model";
    /**
     * Number of tokens in the prompt
     */
    public static final String LLM_USAGE_PROMPT_TOKENS = "openinference.llm.usage.prompt_tokens";
    /**
     * Total number of tokens used
     */
    public static final String LLM_USAGE_TOTAL_TOKENS = "openinference.llm.usage.total_tokens";

    // Embedding attributes
    /**
     * The generated embedding vector
     */
    public static final String EMBEDDING_VECTOR = "openinference.embedding.vector";
    /**
     * Number of dimensions in the embedding
     */
    public static final String EMBEDDING_DIMENSIONS = "openinference.embedding.dimensions";
    /**
     * Input text for embedding generation
     */
    public static final String EMBEDDING_INPUT = "openinference.embedding.input";
    /**
     * Endpoint URL for embedding service
     */
    public static final String EMBEDDING_ENDPOINT = "openinference.embedding.endpoint";
    /**
     * Embedding model identifier
     */
    public static final String EMBEDDING_MODEL = "openinference.embedding.model";
    /**
     * Number of tokens in the embedding input
     */
    public static final String EMBEDDING_USAGE_PROMPT_TOKENS = "openinference.embedding.usage.prompt_tokens";
    /**
     * Total number of tokens used for embedding
     */
    public static final String EMBEDDING_USAGE_TOTAL_TOKENS = "openinference.embedding.usage.total_tokens";

    // Retrieval attributes
    /**
     * Identifier for the search/retrieval system
     */
    public static final String RETRIEVAL_SYSTEM = "openinference.retrieval.system";
    /**
     * The search query text
     */
    public static final String RETRIEVAL_QUERY = "openinference.retrieval.query";
    /**
     * Number of documents retrieved
     */
    public static final String RETRIEVAL_DOCUMENTS_COUNT = "openinference.retrieval.documents.count";
    /**
     * Prefix for document-specific attributes
     */
    public static final String RETRIEVAL_DOCUMENT_PREFIX = "openinference.retrieval.document.";

    // Span kinds
    /**
     * Span kind for retrieval operations
     */
    public static final String SPAN_KIND_RETRIEVER = "RETRIEVER";
    /**
     * Span kind for embedding operations
     */
    public static final String SPAN_KIND_EMBEDDING = "EMBEDDING";
    
    // Common system properties and attributes
    /**
     * System property key for service name
     */
    public static final String SERVICE_NAME_PROPERTY = "otel.service.name";
    /**
     * Span attribute name for service name
     */
    public static final String SERVICE_NAME_ATTRIBUTE = "service.name";

    /**
     * System property key for project name
     */
    public static final String PROJECT_NAME_ATTRIBUTE = "project_name";

    /**
     * Span attribute name for tenant ID
     */
    public static final String TENANT_ID_ATTRIBUTE = "tenant.id";
} 
package io.telemetry.ai.otel.config;

/**
 * Constants for telemetry configuration.
 * Centralizes all configuration keys, environment variables, default values,
 * and attribute names used in telemetry configuration.
 */
public final class TelemetryConfigConstants {

    private TelemetryConfigConstants() {
    }

    // Configuration property and environment variable names used in both TelemetryInitializer and TelemetryConfig
    public static final String SERVICE_NAME_PROPERTY = "SERVICE_NAME";
    public static final String OTLP_EXPORT_PROPERTY = "OTLP_EXPORT";
    public static final String OTLP_EXPORTER_PROPERTY = "OTLP_EXPORTER";
    public static final String SEARCH_SYSTEM_PROPERTY = "SEARCH_SYSTEM";
    public static final String LOG_EMBEDDINGS_PROPERTY = "LOG_EMBEDDINGS";

    // Batch processor configuration properties
    public static final String OTEL_BSP_MAX_EXPORT_BATCH_SIZE_PROPERTY = "OTEL_BSP_MAX_EXPORT_BATCH_SIZE";
    public static final String OTEL_BSP_MAX_QUEUE_SIZE_PROPERTY = "OTEL_BSP_MAX_QUEUE_SIZE";
    public static final String OTEL_BSP_SCHEDULE_DELAY_PROPERTY = "OTEL_BSP_SCHEDULE_DELAY";
    public static final String OTEL_EXPORTER_OTLP_TIMEOUT_PROPERTY = "OTEL_EXPORTER_OTLP_TIMEOUT";
    
    // Tracing limits configuration properties
    public static final String TRACING_SAMPLING_RATE = "TRACING_SAMPLING_RATE";
    public static final String TRACING_SAMPLE_ERRORS = "TRACING_SAMPLE_ERRORS";
    public static final String TRACING_MAX_TRACES_PER_SECOND = "TRACING_MAX_TRACES_PER_SECOND";
    public static final String TRACING_MAX_SPANS_PER_TRACE = "TRACING_MAX_SPANS_PER_TRACE";
    public static final String TRACING_MAX_ATTRIBUTES_PER_SPAN = "TRACING_MAX_ATTRIBUTES_PER_SPAN";
    public static final String TRACING_MAX_EVENTS_PER_SPAN = "TRACING_MAX_EVENTS_PER_SPAN";
    public static final String TRACING_MAX_SPAN_SIZE_BYTES = "TRACING_MAX_SPAN_SIZE_BYTES";
    
    /**
     * Default service name used for the application if not specified.
     */
    public static final String DEFAULT_SERVICE_NAME = "content-ai-nexus";
    
    /**
     * Default OTLP endpoint URL for OpenTelemetry exports.
     */
    public static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";
    
    /**
     * Default search system identifier.
     */
    public static final String DEFAULT_SEARCH_SYSTEM = "elasticsearch-prod";

    /**
     * Default setting for logging embeddings (disabled by default).
     */
    public static final boolean DEFAULT_LOG_EMBEDDINGS = false;
    
    /**
     * Default setting for PII detection (enabled by default).
     */
    public static final boolean DEFAULT_PII_DETECTION_ENABLED = true;
    
    /**
     * Default setting for OTLP export (disabled by default).
     */
    public static final boolean DEFAULT_OTLP_ENABLED = false;
    
    // Default batch processor settings
    /**
     * Default maximum batch size for the OTLP span exporter.
     */
    public static final int DEFAULT_BATCH_SIZE = 8192;
    
    /**
     * Default maximum queue size for the OTLP span processor.
     */
    public static final int DEFAULT_QUEUE_SIZE = 32768;
    
    /**
     * Default schedule delay in milliseconds for the batch span processor.
     */
    public static final int DEFAULT_SCHEDULE_DELAY_MS = 10000;
    
    /**
     * Default timeout in seconds for OTLP exporter operations.
     */
    public static final int DEFAULT_EXPORT_TIMEOUT_SECONDS = 30;
    
    // Default tracing limits
    /**
     * Default sampling rate for traces (1.0 means sample all traces).
     */
    public static final double DEFAULT_SAMPLING_RATE = 1.0;
    
    /**
     * Default setting to always sample traces with errors.
     */
    public static final boolean DEFAULT_SAMPLE_ERRORS = true;
    
    /**
     * Default maximum number of traces to sample per second.
     */
    public static final int DEFAULT_MAX_TRACES_PER_SECOND = 1000;
    
    /**
     * Default maximum number of spans per trace.
     */
    public static final int DEFAULT_MAX_SPANS_PER_TRACE = 10;
    
    /**
     * Default maximum number of attributes per span.
     */
    public static final int DEFAULT_MAX_ATTRIBUTES_PER_SPAN = 32;
    
    /**
     * Default maximum number of events per span.
     */
    public static final int DEFAULT_MAX_EVENTS_PER_SPAN = 64;
    
    /**
     * Default maximum size in bytes per span (16KB).
     */
    public static final long DEFAULT_MAX_SPAN_SIZE_BYTES = 16 * 1024; // 16KB per span

    // OTLP exporter configuration properties
    public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "otel.exporter.otlp.endpoint";
    public static final String OTEL_EXPORTER_OTLP_INSECURE = "otel.exporter.otlp.insecure";
    public static final String OTEL_EXPORTER_OTLP_INSECURE_VALUE = "true";

    /**
     * Default tenant ID used for telemetry data.
     */
    public static final String DEFAULT_TENANT_ID = "default";
    
    // Common span attribute keys
    public static final String OPERATION_TYPE_ATTRIBUTE = "operation.type";
    public static final String PARAM_PREFIX = "param.";
    public static final String INPUT_ATTRIBUTE = "input";
    public static final String INPUT_MIME_TYPE_ATTRIBUTE = "inputMimeType";
    public static final String INPUT_TEXT_ATTRIBUTE = "input.text";
    
    // Search attribute keys
    public static final String SEARCH_SYSTEM = "search.system";
    public static final String SEARCH_QUERY = "search.query";
    public static final String SEARCH_COUNT = "search.count";
    public static final String SEARCH_SOURCE = "search.source";
    public static final String SEARCH_LATENCY_MS = "search.latency_ms";
    public static final String SEARCH_TOTAL_HITS = "search.total_hits";
    public static final String SEARCH_TIMED_OUT = "search.timed_out";
    public static final String SEARCH_FILTER = "search.filter";
    public static final String SEARCH_SORT_BY = "search.sort_by";
    public static final String SEARCH_MAX_RESULTS = "search.max_results";
    
    // Embedding attribute keys
    
    // Exception attribute keys
    public static final String EXCEPTION_TYPE = "exception.type";
    public static final String EXCEPTION_MESSAGE = "exception.message";
    public static final String EXCEPTION_STACKTRACE = "exception.stacktrace";
    
    // PII detection constants
    public static final String PII_DETECTOR_ENABLED = "PII_DETECTOR_ENABLED";
    public static final String PII_DETECTOR_REGEX_ENABLED = "PII_DETECTOR_REGEX_ENABLED";
    public static final String PII_DETECTOR_PRESIDIO_ENABLED = "PII_DETECTOR_PRESIDIO_ENABLED";
    public static final String PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT = "PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT";
    public static final String PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT = "PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT";
    public static final String PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS = "PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS";

    // PII anonymization constants
    public static final String PII_TEXT_FIELD = "text";
    public static final String PII_TYPE_FIELD = "type";
    public static final String PII_NEW_VALUE_FIELD = "new_value";
    public static final String PII_REPLACE_TYPE = "replace";
    public static final String PII_REDACTED_VALUE = "[REDACTED]";
    
    // PII pattern replacements
    public static final String PII_EMAIL_PATTERN = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b";
    public static final String PII_EMAIL_REPLACEMENT = "[EMAIL]";
    public static final String PII_PHONE_PATTERN = "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b";
    public static final String PII_PHONE_REPLACEMENT = "[PHONE]";
    public static final String PII_SSN_PATTERN = "\\b\\d{3}[-]?\\d{2}[-]?\\d{4}\\b";
    public static final String PII_SSN_REPLACEMENT = "[SSN]";
    public static final String PII_CREDIT_CARD_PATTERN = "\\b(?:\\d[ -]*?){13,16}\\b";
    public static final String PII_CREDIT_CARD_REPLACEMENT = "[CREDIT_CARD]";
    public static final String PII_IP_ADDRESS_PATTERN = "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b";
    public static final String PII_IP_ADDRESS_REPLACEMENT = "[IP_ADDRESS]";
    
    // Presidio constants
    /*
     * Default endpoints for Presidio analyzer services.
     */
    public static final String DEFAULT_PII_PRESIDIO_ANALYZER_ENDPOINT = "http://presidio-analyzer:3000/analyze";
    /*
     * Default endpoint for Presidio anonymizer service.
     */
    public static final String DEFAULT_PII_PRESIDIO_ANONYMIZER_ENDPOINT = "http://presidio-anonymizer:3001/anonymize";
    /*
     * Default timeout in seconds for Presidio API calls.
     */
    public static final int DEFAULT_PII_PRESIDIO_TIMEOUT_SECONDS = 5;
    
    // Elasticsearch identifiers
    public static final String ELASTICSEARCH_SYSTEM = "elasticsearch";
    public static final String ELASTICSEARCH_CUSTOM_SYSTEM = "custom";
} 
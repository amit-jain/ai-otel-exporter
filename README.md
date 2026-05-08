# OpenTelemetry Exporter with Queue-Based Fallback

A robust OpenTelemetry exporter implementation that handles high-throughput scenarios using a queue-based fallback
mechanism.

## Features

- Queue-based span processing with configurable size limits
- Multi-tenant handling of projects (for arize phoenix for other collectors project.name can be used to filter per tenant) 
- Automatic fallback to logging when queue is full
- Graceful handling of high-throughput scenarios
- Configurable batch processing parameters
- Integration with Quarkus OpenTelemetry
- Annotation-based telemetry extraction for clean, declarative code
- CDI integration with interceptors for automatic span creation and attribute extraction
- Integrated metrics collection with Micrometer and Prometheus support

## Configuration

### Batch Processing Settings

```properties
# Queue and batch size configuration
OTEL_BSP_MAX_EXPORT_BATCH_SIZE=8192    # Maximum spans per batch (default: 8192)
OTEL_BSP_MAX_QUEUE_SIZE=32768          # Maximum spans in queue (default: 32768)
OTEL_BSP_SCHEDULE_DELAY=10000          # Export interval in ms (default: 10000)
OTEL_EXPORTER_OTLP_TIMEOUT=30          # Export timeout in seconds (default: 30)
```

### OTLP Export Configuration

```properties
OTLP_EXPORT=true                       # Enable OTLP export
OTLP_EXPORTER=http://localhost:4317    # OTLP endpoint
```

### Metrics Configuration

```properties
# Enable metrics collection and export
AI_OTEL_METRICS_ENABLED=true           # Enable metrics collection (default: false)
AI_OTEL_METRICS_PREFIX=test_otel       # Prefix for all metrics (default: ai_otel)
AI_OTEL_SERVICE_NAME=my-service        # Service name to use in metrics tags (default: ai-service)

# Quarkus Micrometer configuration (in application.properties)
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.binder.http-client.enabled=true
quarkus.micrometer.binder.http-server.enabled=true
quarkus.micrometer.binder.jvm=true
quarkus.micrometer.binder.system=true
quarkus.micrometer.export.prometheus.path=/metrics
```

The metrics configuration can be set in three ways, with the following precedence order (highest to lowest):
1. System properties (set programmatically or via JVM args)
2. Environment variables
3. Application properties (in application.properties file)

For test environments, you can enable metrics by setting the system property:
```bash
mvn test -DAI_OTEL_METRICS_ENABLED=true
```

### Available Metrics

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| ${prefix}.operation_duration | Timer | Measures operation execution time | operation, tenant, service |
| ${prefix}.operation_result_count | Counter | Counts number of results returned | operation, tenant, service |
| ${prefix}.operation_success_count | Counter | Counts successful operations | operation, tenant, service |
| ${prefix}.operation_failure_count | Counter | Counts failed operations | operation, tenant, service, error_type |

Where `${prefix}` is the value of `AI_OTEL_METRICS_PREFIX` (default: `ai_otel`).

### Design Patterns

#### NoopMetricsExporter Pattern

The library uses a NOOP (No Operation) pattern for metrics collection:

- When metrics are disabled, a `NoopMetricsExporter` implementation is injected by the CDI system
- The NOOP implementation has empty method implementations that do nothing
- The `DefaultMetricsExporter` implementation is used when metrics are enabled
- This pattern eliminates conditional checks throughout the codebase
- The decision to use NOOP vs real implementation happens once during initialization
- Application code can always call metrics methods without checking if metrics are enabled

This approach provides several benefits:
- Cleaner code with no redundant conditional checks
- Better separation of concerns
- More maintainable and efficient codebase
- Easier testing
- Minimal runtime overhead when metrics are disabled

## How it Works

1. **Queue Management**:
    - Spans are added to a bounded queue
    - When queue is full, spans are automatically routed to a logging fallback
    - Queue state is monitored for recovery

2. **Batch Processing**:
    - Spans are exported in configurable batch sizes
    - Regular export intervals prevent queue buildup
    - Export timeouts ensure system stability

3. **Fallback Mechanism**:
    - When queue is full, spans are logged with trace context
    - System automatically recovers when queue pressure reduces
    - No data loss during high-throughput periods

## Memory and Throughput Analysis

### Memory Usage

1. **Queue Memory (32MB max)**:
    - Queue Size: 32,768 spans
    - Span Size: 1KB per span (average, including attributes)
    - Total: 32,768 × 1KB = 32MB
    - Represents maximum in-memory buffer

2. **Batch Memory (8MB per batch)**:
    - Batch Size: 8,192 spans
    - Span Size: 1KB per span (average, including attributes)
    - Total: 8,192 × 1KB = 8MB
    - Used temporarily during export

3. **Total Memory Consumption**:
    - Queue memory: 32MB
    - Batch memory (during export): 8MB
    - Overhead (20%): ~8MB
    - Maximum total: ~48MB

### Throughput Calculations

1. **Per-Second Processing**:
    - Maximum: 1,000 traces/second
    - 4 spans per trace
    - Total: 4,000 spans/second
    - Raw data: ~4MB/second (uncompressed)

2. **Export Cycle (10 seconds)**:
    - 40,000 spans per cycle
    - ~5 batches per export
    - ~2 seconds available per batch
    - Total data: ~40MB per cycle (uncompressed)

3. **Queue Capacity**:
    - Can buffer 8.2 seconds of spans at max rate
    - Provides cushion for export delays
    - Auto-fallback to logging if buffer fills

### Resource Planning

1. **Memory Requirements**:
    - 32MB for queue (constant)
    - 8MB for batch processing (temporary)
    - Additional overhead for processing: ~8MB
    - Total: ~48MB

2. **Network Considerations**:
    - Peak throughput: ~4MB/second
    - Sustained average: ~4MB/second
    - Actual usage lower due to compression
    - OTLP protocol overhead

3. **Recovery Mechanism**:
    - Queue clears when pressure reduces
    - Automatic fallback prevents data loss
    - System self-heals without manual intervention

## Multi-Tenant Memory Analysis

This section provides memory calculations for multi-tenant deployments, specifically analyzing the memory requirements
for supporting 400 tenants.

### Memory Footprint per TelemetryAgent

Each TelemetryAgent instance consists of:

1. **Core TelemetryAgent Object**: ~176 bytes
    - Tracer reference: ~16 bytes
    - TelemetryConfig reference: ~16 bytes
    - TracingLimits reference: ~16 bytes
    - ConcurrentHashMap for extractors: ~48 bytes (empty map overhead)
    - ConcurrentHashMap for typedExtractors: ~48 bytes (empty map overhead)
    - Logger reference: ~16 bytes
    - Object overhead: ~16 bytes

2. **TelemetryConfig Object**: ~288 bytes
    - 16 primitive/reference fields: ~128 bytes
    - String references (serviceName, otlpEndpoint, etc.): ~128 bytes
    - TracingLimits reference: ~16 bytes
    - Object overhead: ~16 bytes

3. **TracingLimits Object**: ~168 bytes
    - 7 primitive fields: ~56 bytes
    - AtomicLong fields (2): ~32 bytes
    - ConcurrentHashMap: ~48 bytes (empty map overhead)
    - Logger reference: ~16 bytes
    - Object overhead: ~16 bytes

4. **SdkTracerProvider and Related Objects**: ~1,040 bytes
    - SdkTracerProvider: ~200 bytes
    - Tracer implementation: ~100 bytes
    - SpanLimits: ~40 bytes
    - Resource attributes: ~200 bytes
    - Span processors: ~500 bytes

5. **Extractors (if registered)**: ~800 bytes
    - Assuming 5 operation types with 2 extractors each

6. **String Content**: ~200 bytes
    - Service name, tenant ID, endpoint URLs, etc.

**Total per TelemetryAgent**: ~2,672 bytes (approximately 2.7 KB)

### Memory for 400 Tenant-Specific Agents

Base memory: 400 agents × 2.7 KB = 1,080 KB (approximately 1.1 MB)

### Additional Considerations

1. **Shared Objects**:
    - Many objects like extractors are shared across agents, reducing the actual memory footprint
    - The TelemetryAgentProducer maintains a single copy of registered extractors

2. **Runtime Memory**:
    - Active spans: Each active span requires ~1KB (average, including attributes)
    - Span queue: The default queue size is 32,768 spans
    - If fully utilized, the span queue could consume: 32,768 × 1KB = 32MB (shared across all tenants)

3. **Garbage Collection Overhead**:
    - Java memory management adds approximately 10-20% overhead
    - Estimated overhead: ~200 KB

4. **Shared Exporter Resources**:
    - OTLP exporter connections and buffers: ~5 MB (shared across all agents)

### Total Memory Estimate for 400 Tenants

1. Base memory for 400 agents: ~1.1 MB
2. Shared extractors and configurations: ~1 MB
3. GC overhead: ~0.2 MB
4. Shared exporter resources: ~5 MB

**Total static memory**: ~7.3 MB

**Dynamic memory** (primarily shared queue): ~32 MB

**Batch processing memory** (during export): ~8 MB

**Overhead (20%)**: ~9.5 MB

**Total memory**: ~56.8 MB

In practice, the actual memory usage will depend on:
- Max queue size defined
- Actual span sizes and complexity
- Traffic patterns across tenants
- Sampling and rate limiting settings

## Usage

### Basic Usage

```java
// Create configuration
TelemetryConfiguration telemetryConfig = TelemetryConfigurationFactory.getConfiguration(
    "service-name", 
    "tenant-id"
);

// Create agent
TelemetryAgent agent = new TelemetryAgent(telemetryConfig.getTracer());

// Create spans
Span span = agent.startSpan(
    "operation-name",
    SpanKind.INTERNAL,
    "tenant-id",
    "instance-id"
);

try {
    // Your code here
} finally {
    agent.endSpan(span, null);
}
```

### Annotation-Based Usage

The library also supports a declarative, annotation-based approach for cleaner code:

```java
@Trace(
    spanName = "search-operation",
    spanKind = SpanKind.CLIENT,
    operationType = OperationType.SEARCH
)
public SearchResponse search(
        @ExtractAttributes SearchRequest request,
        @AttributeList(attributeName = "search.index") List<String> indices,
        @TenantId String tenantId) {
    
    // Business logic only - no manual telemetry code
    return performSearch(request, indices, tenantId);
}
```

### Using Metrics Recording

The recommended approach for metrics recording is to use the MetricsRecorder directly, which provides a fluent interface for timing operations and recording metrics:

```java
@Inject
MetricsRecorder metricsRecorder;

public SearchResponse performSearch(String query, String tenantId) {
    // Start a timer for the operation
    MetricsRecorder.TimerBuilder.TimerContext timer = metricsRecorder.startTimer(OperationType.SEARCH)
        .withTenant(tenantId)
        .withTag("service", "search-service")
        .withTag("index", "main-index")
        .start();
        
    try {
        // Perform the actual search operation
        SearchResponse response = searchService.search(query);
        
        // Record success with the number of results
        timer.success(response.getResults().size());
        
        return response;
    } catch (Exception e) {
        // Record failure with the error type
        timer.failure(e.getClass().getSimpleName());
        throw e;
    }
}
```

This approach automatically records:
- Operation duration (as a timer)
- Result counts (as a counter)
- Success/failure counts (as counters)
- Tags for operation type, tenant, service, and custom dimensions

### Using measureOperation with TimedResult

For cases where you need both the operation result and the timing information, the `measureOperation` method provides a simpler, more concise approach:

```java
@Inject
MetricsRecorder metricsRecorder;

public SearchResponse performSearch(String query, String tenantId) throws Exception {
    // Create tags for the operation
    Map<String, String> tags = Map.of(
        "service", "search-service",
        "index", "main-index"
    );
    
    // Use measureOperation to get both the result and timing information
    MetricsRecorder.TimedResult<SearchResponse> timedResult = metricsRecorder.measureOperation(
        OperationType.SEARCH.name().toLowerCase(),
        tenantId,
        tags,
        () -> {
            // Perform the actual operation
            return searchService.search(query);
        }
    );
    
    // Access timing information
    long durationMs = timedResult.getDurationMs();
    
    // Log timing information if needed
    LOG.info("Search completed in {}ms with {} results", 
        durationMs, 
        timedResult.getResultCount());
    
    // Return just the result
    return timedResult.getResult();
}
```

Key benefits of using `measureOperation`:
- More concise code with less boilerplate
- Proper handling of checked exceptions (propagates original exception types)
- Returns both the operation result and timing information in a `TimedResult` container
- Automatically detects and records result count for collections, maps, and arrays
- Maintains all the same metrics recording as the manual approach

The `TimedResult<T>` class provides access to:
- `getResult()` - The actual result of the operation
- `getDurationMs()` - The duration of the operation in milliseconds
- `isSuccess()` - Whether the operation was successful
- `getErrorType()` - The error type if the operation failed (null if successful)
- `getResultCount()` - The number of results if available (for collections, maps, and arrays)

This approach is particularly useful for operations where you need to access timing information after the operation completes, for example to include it in logs or to make decisions based on operation duration.

### Proper Exception Handling with measureOperation

The `measureOperation` method is designed to properly handle checked exceptions. Unlike many functional interfaces in Java that only work with unchecked exceptions, the `CheckedSupplier<T>` interface used by `measureOperation` allows any exception type to be thrown and preserves the original exception type:

```java
@Inject
MetricsRecorder metricsRecorder;

public SearchResponse searchWithExceptions(String query, String tenantId) throws IOException, AuthenticationException {
    // Use measureOperation with operations that may throw checked exceptions
    try {
        return metricsRecorder.measureOperation(
            "search",
            tenantId,
            null, // No additional tags needed
            () -> {
                // This code can throw checked exceptions
                if (!authService.isAuthorized(tenantId)) {
                    throw new AuthenticationException("Unauthorized access");
                }
                
                // This may throw IOException
                return searchClient.executeSearch(query);
            }
        ).getResult();
    } catch (IOException e) {
        // Handle the exception appropriately
        LOG.error("IO error during search: {}", e.getMessage());
        throw e; // Re-throw as IOException, not wrapped in RuntimeException
    } catch (AuthenticationException e) {
        // Handle authentication failure
        LOG.error("Authentication failed: {}", e.getMessage());
        throw e; // Re-throw as AuthenticationException
    }
}
```

Benefits of proper exception handling:
- Original exception types are preserved (not wrapped in RuntimeException)
- Method signature can accurately declare the exceptions that might be thrown
- Allows for specific exception handling based on exact exception type
- Reduces the need for exception unwrapping and improves code readability
- Metrics are still recorded properly even when exceptions occur

This is especially valuable when integrating with APIs that use checked exceptions, such as file operations, network calls, or database access.

### Combining Tracing and Metrics

You can use both the @Trace annotation for tracing and MetricsRecorder for metrics in the same method:

```java
@Inject
MetricsRecorder metricsRecorder;

@Trace(
    spanName = "search-operation",
    spanKind = SpanKind.CLIENT,
    operationType = OperationType.SEARCH
)
public SearchResponse search(String query, String tenantId) {
    // Start a timer for metrics
    MetricsRecorder.TimerBuilder.TimerContext timer = metricsRecorder.startTimer(OperationType.SEARCH)
        .withTenant(tenantId)
        .start();
        
    try {
        // Your search implementation
        SearchResponse response = performSearch(query);
        
        // Record success with count of results
        timer.success(response.getResults().size());
        
        return response;
    } catch (Exception e) {
        // Record failure
        timer.failure(e.getClass().getSimpleName());
        throw e;
    }
}
```

This combination provides both detailed tracing information and aggregated metrics for monitoring.

## Using AttributeExtractor and TypedAttributeExtractor

This library provides two approaches for extracting telemetry attributes: `AttributeExtractor` (traditional) and
`TypedAttributeExtractor` (flexible). You can use both approaches together in your application.

### 1. Using AttributeExtractor (Traditional Approach)

This approach is best for standardized responses that can implement the GenericResponse interface.

#### Step 1: Create a response class implementing GenericResponse

```java
public class MyStandardResponse implements GenericResponse {
    private String input;
    private List<ResultData> results;
    // Other fields, getters, setters...
    
    @Override
    public String getInput() {
        return input;
    }
    
    // Implement other required methods from GenericResponse
}
```

#### Step 2: Create an AttributeExtractor implementation

```java
public class MyStandardExtractor implements AttributeExtractor<MyStandardResponse, SearchOperationContext> {
    @Override
    public void extractAttributes(Span span, SearchOperationContext context, MyStandardResponse response, OperationType type) {
        // Add standard attributes
        span.setAttribute(type.getAttributeKey("system"), "my-system");
        span.setAttribute(type.getAttributeKey("query"), response.getInput());
        span.setAttribute(type.getAttributeKey("count"), response.getResults().size());
        
        // Add operation-specific attributes
        span.setAttribute("my-system.custom_field", response.getCustomField());
        
        // Use context information
        span.setAttribute("my-system.endpoint", context.getEndpoint());
    }
}
```

#### Step 3: Register the extractor with TelemetryAgent

```java
@ApplicationScoped
public class MyTelemetryInitializer {
    
    @Inject
    TelemetryAgent telemetryAgent;
    
    public void onStart(@Observes StartupEvent event) {
        // Register your custom extractor
        telemetryAgent.registerExtractor(
                OperationType.SEARCH, 
                new MyStandardExtractor());
    }
}
```

#### Step 4: Use it in your code

```java
@Inject
TelemetryAgent telemetryAgent;

public void performSearch(String query) {
    // Create context
    SearchOperationContext context = SearchOperationContext.builder()
            .query(query)
            .endpoint("my-search-endpoint")
            .build();
    
    // Start span
    Span span = telemetryAgent.startSpan("search-operation", SpanKind.CLIENT);
    
    try {
        // Perform the operation
        MyStandardResponse response = searchService.search(query);
        
        // Add attributes and end span
        telemetryAgent.endSpan(span, response, context, OperationType.SEARCH);
    } catch (Exception e) {
        telemetryAgent.endSpanWithError(span, e);
    }
}
```

### 2. Using TypedAttributeExtractor (Flexible Approach)

This approach is best for working with third-party or existing response types that don't implement GenericResponse.

#### Step 1: Create a TypedAttributeExtractor implementation

```java
public class ThirdPartyResponseExtractor implements TypedAttributeExtractor<ThirdPartyResponse, SearchOperationContext> {
    @Override
    public void extractAttributes(Span span, ThirdPartyResponse response, SearchOperationContext context, OperationType operationType) {
        // Add standard attributes
        span.setAttribute(operationType.getAttributeKey("system"), "third-party-system");
        
        // Extract query from response or context
        String query = (context != null && context.getQuery() != null) 
            ? context.getQuery() : response.getQueryString();
        span.setAttribute(operationType.getAttributeKey("query"), query);
        
        // Add result count
        span.setAttribute(operationType.getAttributeKey("count"), response.getHits().size());
        
        // Add third-party specific attributes
        span.setAttribute("third-party.took_ms", response.getExecutionTime());
        span.setAttribute("third-party.status", response.getStatus());
    }
}
```

#### Step 2: Register the typed extractor

```java
@ApplicationScoped
public class MyTelemetryInitializer {
    
    @Inject
    TelemetryAgent telemetryAgent;
    
    public void onStart(@Observes StartupEvent event) {
        // Register your typed extractor
        telemetryAgent.registerTypedExtractor(
                OperationType.SEARCH,
                ThirdPartyResponse.class,
                new ThirdPartyResponseExtractor());
    }
}
```

#### Step 3: Use it in your code

You have two options for using TypedAttributeExtractor:

##### Option 1: Direct call

```java
@Inject
TelemetryAgent telemetryAgent;

public void performThirdPartySearch(String query) {
    // Create context
    SearchOperationContext context = SearchOperationContext.builder()
            .query(query)
            .endpoint("third-party-endpoint")
            .build();
    
    // Start span
    Span span = telemetryAgent.startSpan("third-party-search", SpanKind.CLIENT);
    
    try {
        // Perform the operation
        ThirdPartyResponse response = thirdPartyService.search(query);
        
        // Add attributes manually
        telemetryAgent.addTypedAttributes(span, response, context, OperationType.SEARCH);
        
        // End span
        telemetryAgent.endSpan(span, null);
    } catch (Exception e) {
        telemetryAgent.endSpanWithError(span, e);
    }
}
```

##### Option 2: Use with @Trace annotation

```java
@Trace(
    spanName = "third-party-search",
    spanKind = SpanKind.CLIENT,
    operationType = OperationType.SEARCH,
    responseType = ThirdPartyResponse.class
)
public ThirdPartyResponse searchThirdParty(String query) {
    // The interceptor will automatically create a span, extract attributes, and end the span
    return thirdPartyService.search(query);
}
```

##### Adding Custom Attributes Inside @Trace Methods

You can add custom attributes to spans inside methods that are already annotated with `@Trace`:

```java
@Trace(
    spanName = "my-operation",
    spanKind = SpanKind.CLIENT,
    operationType = OperationType.SEARCH,
    includeParameters = true
)
public void myMethod(String query, String source) {
    // Get the current span (created by the @Trace annotation)
    Span currentSpan = Span.current();
    
    // Add custom attributes directly
    currentSpan.setAttribute("custom.attribute", "value");
    currentSpan.setAttribute("custom.count", 123);
    
    // Add attributes using OperationType for consistent keys
    OperationType operationType = OperationType.SEARCH;
    currentSpan.setAttribute(operationType.getAttributeKey("system"), "my-search-system");
    
    // Add OpenInference standard attributes
    currentSpan.setAttribute(OpenInferenceAttributes.INPUT_VALUE, query);
    currentSpan.setAttribute(OpenInferenceAttributes.INPUT_MIME_TYPE, "text/plain");
    
    // Your method logic here
    // ...
}
```

For more complex scenarios, you can inject and use the `TelemetryAgent`:

```java
@Inject
private TelemetryAgent agent;

@Trace(
    spanName = "my-operation",
    spanKind = SpanKind.CLIENT,
    operationType = OperationType.SEARCH
)
public MyResponse myMethod(String query) {
    // Your method logic here
    MyResponse response = processRequest(query);
    
    // Create a context object
    SearchOperationContext context = SearchOperationContext.builder()
        .query(query)
        .endpoint("my-endpoint")
        .build();
    
    // Add attributes using the agent
    agent.addAttributes(Span.current(), context, response, OperationType.SEARCH);
    
    return response;
}
```

### 3. Combining Both Approaches

You can use both approaches in the same application, choosing the most appropriate one for each use case:

```java
@ApplicationScoped
public class MyTelemetryInitializer {
    
    @Inject
    TelemetryAgent telemetryAgent;
    
    public void onStart(@Observes StartupEvent event) {
        // Register standard extractors
        telemetryAgent.registerExtractor(
                OperationType.SEARCH, 
                new MyStandardExtractor());
        
        // Register typed extractors
        telemetryAgent.registerTypedExtractor(
                OperationType.SEARCH,
                ThirdPartyResponse.class,
                new ThirdPartyResponseExtractor());
    }
}
```

### 4. Using DefaultAttributeExtractors

The library includes a `DefaultAttributeExtractors` class that provides standard implementations of attribute extractors
for common operation types. This class is automatically used during TelemetryAgent initialization to register default
extractors.

#### How DefaultAttributeExtractors Works

```java
public class DefaultAttributeExtractors {
    /**
     * Registers all default extractors with the given TelemetryAgent.
     */
    public static void registerDefaults(TelemetryAgent agent) {
        // Register standard extractors
        agent.registerExtractor(OperationType.EMBEDDING, embeddingExtractor());
        agent.registerExtractor(OperationType.SEARCH, searchExtractor());
        
        // Register type-specific extractors
        registerTypedExtractors(agent);
    }
    
    // Default extractors for common operations...
}
```

#### Using Default Extractors

The default extractors are automatically registered when you create a TelemetryAgent instance. You don't need to do
anything special to use them:

```java
// Create agent
TelemetryAgent agent = new TelemetryAgent(telemetryConfig.getTracer());

// DefaultAttributeExtractors.registerDefaults() is called internally
// during agent initialization
```

#### Extending Default Extractors

You can extend the DefaultAttributeExtractors class to add your own default extractors:

```java
public class MyDefaultAttributeExtractors extends DefaultAttributeExtractors {
    /**
     * Registers additional default extractors.
     */
    public static void registerMyDefaults(TelemetryAgent agent) {
        // First register the standard defaults
        registerDefaults(agent);
        
        // Then register your additional extractors
        agent.registerExtractor(OperationType.MY_CUSTOM_OPERATION, myCustomExtractor());
    }
    
    private static AttributeExtractor<MyResponse, MyContext> myCustomExtractor() {
        return (span, context, response, type) -> {
            // Extract attributes...
        };
    }
}
```

Then in your application startup:

```java
@ApplicationScoped
public class MyTelemetryInitializer {
    
    @Inject
    TelemetryAgent telemetryAgent;
    
    public void onStart(@Observes StartupEvent event) {
        // Register your extended defaults instead of the standard ones
        MyDefaultAttributeExtractors.registerMyDefaults(telemetryAgent);
    }
}
```

### Best Practices for Using Both

1. **Choose the right approach for each case**:
    - Use AttributeExtractor for your own responses that can implement GenericResponse
    - Use TypedAttributeExtractor for third-party or legacy responses

2. **Maintain consistency in attribute naming**:
    - Use the same attribute keys for similar concepts across both extractor types
    - Use operationType.getAttributeKey() to ensure consistent naming

3. **Register all extractors during application startup**:
    - This ensures they're available when needed

4. **Handle inheritance properly**:
    - For TypedAttributeExtractor, create extractors for the most specific types first
    - The system will find the most specific extractor for a given response type

5. **Document your extractors**:
    - Add clear comments explaining what attributes are extracted and why

For more detailed information about TypedAttributeExtractor,
see [README-TypedAttributeExtractor.md](docs/README-TypedAttributeExtractor.md).

## Working with Context Attributes

The telemetry system provides a context-propagation mechanism to add attributes to spans. These attributes can be used
to enrich spans with additional information that may not be available at span creation time.

### How Context Attributes Work

1. **OpenTelemetry Context Propagation**: The `TelemetryContext` class uses OpenTelemetry's Context propagation
   mechanism to maintain context attributes, ensuring thread safety and proper propagation across asynchronous
   boundaries:

```java
private static final ContextKey<Map<String, Object>> CONTEXT_ATTRIBUTES_KEY = 
        ContextKey.named("telemetry-context-attributes");
```

2. **Adding Context Attributes**: There are two main ways to add context attributes:

a) **Dynamic Context Attributes** - Added directly to the current context:

```java
// Add dynamic context attributes
agent.addContextAttribute("custom.attribute", "custom-value");
```

b) **Operation Type Attributes** - Automatically added by the span creation methods:

```java
// The operation type is automatically added as a context attribute
Span span = agent.createSpan("operation-name", OperationType.SEARCH);
```

3. **Context Propagation**: When a span is created and made current, the context attributes are automatically
   propagated:

```java
Span span = agent.startSpan("operation-name", SpanKind.INTERNAL);
try (Scope scope = span.makeCurrent()) {
    // Context attributes added here are associated with this span
    agent.addContextAttribute("custom.attribute", "custom-value");
    
    // Do work...
} finally {
    agent.endSpan(span, null);  // This adds context attributes to the span before ending it
}
```

### Thread Safety and Async Boundaries

1. **Context Propagation**: OpenTelemetry's Context mechanism properly handles propagation across thread boundaries.

2. **Immutability**: The Context is immutable, so each change creates a new Context instance, preventing race
   conditions.

3. **Context Clearing**: You should explicitly clear the context when done to prevent attribute leakage:

```java
try {
    // Do work...
    agent.addContextAttribute("custom.attribute", "custom-value");
} finally {
    agent.clearContextAttributes();  // Clean up after use
}
```

### Best Practices for Adding Context Attributes

1. **Use try-finally Blocks**: Always wrap context attribute operations in try-finally blocks to ensure proper cleanup:

```java
try {
    agent.addContextAttribute("key", "value");
    // Do work...
} finally {
    agent.clearContextAttributes();
}
```

2. **Keep Attributes Small**: Context attributes should be small and focused on the current operation to avoid bloating
   spans.

3. **Avoid Sensitive Data**: Never store sensitive information in context attributes as they will be exported with
   spans.

The context propagation mechanism ensures that attributes are properly handled across asynchronous boundaries, thread
pools, and concurrent operations while preventing data leaks between different operations.

### Async Operations Example

One of the key benefits of using OpenTelemetry Context propagation is proper handling of asynchronous operations and
thread pools. Here's an example of how context attributes are properly propagated across thread boundaries:

```java
// Create a span and set context attributes
Span parentSpan = agent.startSpan("parent-operation", SpanKind.INTERNAL);
try (Scope scope = parentSpan.makeCurrent()) {
    // Add context attribute
    agent.addContextAttribute("parent.attribute", "parent-value");
    
    // Create CompletableFuture that will run on a different thread
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        // The context is automatically propagated to this thread!
        // We can access the parent context attribute here
        String parentValue = (String) agent.getContextAttribute("parent.attribute");
        System.out.println("Parent value in async thread: " + parentValue);
        
        // Create a child span
        Span childSpan = agent.startSpan("child-operation", SpanKind.INTERNAL);
        try (Scope childScope = childSpan.makeCurrent()) {
            // Add a child-specific attribute
            agent.addContextAttribute("child.attribute", "child-value");
            
            // Do async work...
        } finally {
            agent.endSpan(childSpan, null);
        }
    });
    
    // Wait for async operation to complete
    future.join();
} finally {
    agent.endSpan(parentSpan, null);
}
```

In this example:

1. Context attributes set in the parent thread are automatically available in the child thread
2. Each thread maintains its own context that builds upon the parent context
3. Changes to the context in one thread don't affect other threads
4. The context is properly managed across the thread boundary

## PII Detection and Anonymization

The telemetry system includes built-in support for detecting and anonymizing Personally Identifiable Information (PII)
in span attributes before they are exported. This ensures sensitive data is protected throughout the telemetry pipeline.

### How PII Anonymization Works

1. **PIIAnonymizingSpanExporter**:
    - Wraps any standard OpenTelemetry SpanExporter
    - Intercepts spans before they are exported
    - Scans string attributes for PII using configured detectors
    - Replaces detected PII with anonymized values
    - Forwards anonymized spans to the wrapped exporter

2. **Multiple Detection Methods**:
    - **RegexPIIDetector**: Uses regular expressions to detect common PII patterns
    - **PresidioPIIDetector**: Integrates with Microsoft Presidio for advanced PII detection (optional)
    - Detectors can be used individually or in combination for layered protection

3. **Default PII Patterns**:
    - Email addresses: `[EMAIL]`
    - Phone numbers: `[PHONE]`
    - Social Security Numbers: `[SSN]`
    - Credit card numbers: `[CREDIT_CARD]`
    - IP addresses: `[IP_ADDRESS]`

4. **Fallback Mechanism**:
    - If Presidio services are unavailable, the system automatically falls back to regex-based detection
    - Ensures PII protection continues even when external services are down

### Configuration

```properties
# Enable/disable PII detection
PII_DETECTOR_ENABLED=true

# Configure regex-based detection
PII_DETECTOR_REGEX_ENABLED=true

# Configure Presidio-based detection
PII_DETECTOR_PRESIDIO_ENABLED=false
PII_DETECTOR_PRESIDIO_ANALYZER_ENDPOINT=http://presidio-analyzer:3000/analyze
PII_DETECTOR_PRESIDIO_ANONYMIZER_ENDPOINT=http://presidio-anonymizer:3001/anonymize
PII_DETECTOR_PRESIDIO_TIMEOUT_SECONDS=5
```

These configuration options can be set as either environment variables or system properties using the same names. Environment variables take precedence over system properties.

### Testing with Presidio

To test the Presidio PII detection and anonymization functionality:

1. Start the Presidio services using Docker Compose:
   ```bash
   docker-compose -f docs/docker-compose-presidio.yml up -d
   ```

2. Run the Presidio integration tests:
   ```bash
   mvn test -Dtest=PresidioPIIAnonymizingSpanExporterIntegrationTest
   ```

3. Stop the Presidio services when done:
   ```bash
   docker-compose -f docs/docker-compose-presidio.yml down
   ```

The integration test will verify that PII detection and anonymization are working correctly with the Presidio services.

## Monitoring Recommendations

1. **Key Metrics to Watch**:
    - Queue utilization percentage
    - Number of fallback events
    - Export duration
    - Spans processed per second

2. **Warning Signs**:
    - Frequent queue full events
    - Increasing export durations
    - High sustained throughput

3. **Tuning Options**:
    - Increase queue size (if memory allows)
    - Adjust export frequency
    - Modify batch size
    - Review span data volume

## DelegatingSpanExporter

The `DelegatingSpanExporter` is an abstract base class for span exporters that delegate to another exporter. It provides
a simple way to create a chain of exporters, where each exporter can process spans before passing them to the next
exporter in the chain.

### Creating a Custom Exporter

To create a custom exporter, extend the `DelegatingSpanExporter` class and implement the `processSpans` method:

```java
public class MyCustomExporter extends DelegatingSpanExporter {
    public MyCustomExporter(SpanExporter delegate) {
        super(delegate);
    }
    
    @Override
    protected Collection<SpanData> processSpans(Collection<SpanData> spans) {
        // Process spans here
        return spans;
    }
}
```

### Creating an Exporter Chain

You can create a chain of exporters by composing them in the correct order:

```java
// Create the base OTLP exporter
OtlpGrpcSpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
    .setEndpoint("http://localhost:4317")
    .build();

// Start with the OTLP exporter
SpanExporter exporterChain = otlpExporter;

// Add registered exporters to the chain
if (!registeredExporters.isEmpty()) {
    exporterChain = TelemetrySystem.createExporterChain(
        exporterChain, 
        registeredExporters.toArray(new DelegatingSpanExporter[0])
    );
}

// Add PII anonymization as the FIRST exporter in the chain
// This ensures PII is anonymized before any other processing
if (piiDetectionEnabled) {
    PIIDetectorConfig piiConfig = PIIDetectorConfig.fromSystemProperties();
    PIIAnonymizingSpanExporter piiExporter = new PIIAnonymizingSpanExporter(exporterChain, piiConfig);
    exporterChain = piiExporter;
}

// Use the exporter chain with a tracer provider
SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
    .addSpanProcessor(BatchSpanProcessor.builder(exporterChain).build())
    .build();
```

This creates a chain where PII anonymization happens first, then any registered exporters, and finally the OTLP
exporter.

## Built-in Exporters

### PIIAnonymizingSpanExporter

The `PIIAnonymizingSpanExporter` anonymizes PII in span attributes before delegating to another exporter. It should
typically be the first exporter in the chain to ensure PII is anonymized before any other processing.

```java
// Create the chain first
SpanExporter exporterChain = otlpExporter;

// Add any other exporters
exporterChain = TelemetrySystem.createExporterChain(exporterChain, otherExporters);

// Add PII anonymization as the first exporter in the chain
PIIDetectorConfig config = PIIDetectorConfig.fromSystemProperties();
PIIAnonymizingSpanExporter piiExporter = new PIIAnonymizingSpanExporter(exporterChain, config);
exporterChain = piiExporter;
```

### InMemorySpanExporter

The `InMemorySpanExporter` captures spans in memory for testing and debugging.

```java
// Create the chain first
SpanExporter exporterChain = otlpExporter;

// Add the InMemory exporter to the chain
InMemorySpanExporter inMemoryExporter = new InMemorySpanExporter(exporterChain);
exporterChain = inMemoryExporter;

// Later, retrieve the captured spans
List<SpanData> exportedSpans = inMemoryExporter.getExportedSpans();
```

## Documentation

Detailed documentation is available in the `docs` directory:

- [TypedAttributeExtractor Guide](docs/README-TypedAttributeExtractor.md) - How to use type-safe attribute extractors, including the annotation-based approach
- [Architecture Overview](docs/architecture.md) - Detailed architecture description 

## Usage Examples

### Adding Metrics to a Method

```java
// Basic tracing
@Trace(spanName = "search-operation", operationType = OperationType.SEARCH)
public SearchResponse search(String query) {
    // Method implementation
}

// Direct use of MetricsRecorder
@Inject
MetricsRecorder metricsRecorder;

public SearchResponse searchWithMetrics(String query, String tenantId) {
    // Start metrics timer
    MetricsRecorder.TimerBuilder.TimerContext timer = metricsRecorder.startTimer(OperationType.SEARCH)
        .withTenant(tenantId)
        .start();
        
    try {
        // Method implementation
        SearchResponse response = performSearch(query);
        
        // Record success with count of results
        timer.success(response.getResults().size());
        
        return response;
    } catch (Exception e) {
        // Record failure
        timer.failure(e.getClass().getSimpleName());
        throw e;
    }
}
```

### Available Metrics

| Metric Name | Type | Description | Tags |
|-------------|------|-------------|------|
| ${prefix}.operation_duration | Timer | Measures operation execution time | operation, tenant, service |
| ${prefix}.operation_result_count | Counter | Counts number of results returned | operation, tenant, service |
| ${prefix}.operation_success_count | Counter | Counts successful operations | operation, tenant, service |
| ${prefix}.operation_failure_count | Counter | Counts failed operations | operation, tenant, service, error_type |

Where `${prefix}` is the value of `AI_OTEL_METRICS_PREFIX` (default: `ai_otel`).

### Design Patterns

#### NoopMetricsExporter Pattern

The library uses a NOOP (No Operation) pattern for metrics collection:

- When metrics are disabled, a `NoopMetricsExporter` implementation is injected by the CDI system
- The NOOP implementation has empty method implementations that do nothing
- The `DefaultMetricsExporter` implementation is used when metrics are enabled
- This pattern eliminates conditional checks throughout the codebase
- The decision to use NOOP vs real implementation happens once during initialization
- Application code can always call metrics methods without checking if metrics are enabled

This approach provides several benefits:
- Cleaner code with no redundant conditional checks
- Better separation of concerns
- More maintainable and efficient codebase
- Easier testing
- Minimal runtime overhead when metrics are disabled

For more detailed information about NoopMetricsExporter,
see [README-NoopMetricsExporter.md](docs/README-NoopMetricsExporter.md). 
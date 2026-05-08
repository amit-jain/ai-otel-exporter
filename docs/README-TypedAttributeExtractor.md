# TypedAttributeExtractor

This document explains how to use the `TypedAttributeExtractor` interface for extracting telemetry attributes from
specific response types.

## Overview

The `TypedAttributeExtractor` interface provides a type-safe mechanism for extracting telemetry attributes from specific
response types. Unlike the old `AttributeExtractor` interface, which requires responses to implement the `GenericResponse`
interface, the `TypedAttributeExtractor` interface works with any response type, making it more flexible and easier to
use with existing code.

## Interface Definition

```java
@FunctionalInterface
public interface TypedAttributeExtractor<T, C extends OperationContext> {
    /**
     * Extracts attributes from a response object and adds them to a span.
     *
     * @param span The span to add attributes to
     * @param response The response object to extract attributes from
     * @param context The operation context providing additional data (may be null)
     * @param operationType The type of operation being performed
     */
    void extractAttributes(Span span, T response, C context, OperationType operationType);
    
    /**
     * Convenience method for cases where context is not available.
     * This default implementation calls the main method with a null context.
     *
     * @param span The span to add attributes to
     * @param response The response object to extract attributes from
     * @param operationType The type of operation being performed
     */
    default void extractAttributes(Span span, T response, OperationType operationType) {
        extractAttributes(span, response, null, operationType);
    }
}
```

## Getting Started

### 1. Create a Type-Specific Extractor

Create a class that implements the `TypedAttributeExtractor` interface for your specific response type:

```java
public class MyResponseExtractor implements TypedAttributeExtractor<MyResponse, SearchOperationContext> {
    @Override
    public void extractAttributes(Span span, MyResponse response, SearchOperationContext context, OperationType operationType) {
        // Extract attributes from the response and add them to the span
        span.setAttribute("my.attribute", response.getSomeValue());
        
        // Use context information if available
        if (context != null) {
            span.setAttribute("my.query", context.getQuery());
            span.setAttribute("my.endpoint", context.getEndpoint());
        }
    }
}
```

### 2. Register the Extractor

Register your extractor with the `TelemetryAgentProducer` during system initialization:

```java
@ApplicationScoped
public class TelemetryInitializer {
    
    @Inject
    TelemetryAgentProducer telemetryAgentProducer;
    
    public void onStart(@Observes StartupEvent event) {
        // Register with all agents (current and future)
        telemetryAgentProducer.registerTypedExtractorWithAllAgents(
                OperationType.SEARCH,
                MyResponse.class,
                new MyResponseExtractor());
    }
}
```

## Main Usage Approaches

There are two primary ways to use typed attribute extractors:

### Approach 1: Direct API Usage (Imperative)

This approach gives you full control over span creation and attribute extraction:

```java
// In your service method
public SearchResponse search(String query, List<String> indices, String tenantId) {
    // Create a span
    Span span = telemetryAgent.startSpan("search-operation", SpanKind.CLIENT);
    
    try {
        // Set core attributes
        span.setAttribute("tenant.id", tenantId);
        span.setAttribute("search.query", query);
        
        // Create context
        SearchOperationContext context = SearchOperationContext.builder()
                .query(query)
                .endpoint("elasticsearch")
                .build();
        
        // Execute search
        SearchResponse response = performSearch(query, indices, tenantId);
        
        // Extract attributes using the registered extractor
        telemetryAgent.addTypedAttributes(span, response, context, OperationType.SEARCH);
        
        return response;
    } catch (Exception e) {
        // Record error
        span.recordException(e);
        throw e;
    } finally {
        // End span
        telemetryAgent.endSpan(span, null);
    }
}
```

### Approach 2: Annotation-Based Usage (Declarative)

This approach uses CDI interceptors and annotations for clean, declarative code:

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
    
    // Just focus on business logic - telemetry is handled automatically
    return performSearch(request, indices, tenantId);
}
```

The `CDITraceInterceptor` will:
1. Intercept the method call and create a span
2. Process parameter annotations to extract attributes
3. Execute the method
4. Extract attributes from the return value using registered extractors
5. Handle any exceptions and end the span

To use annotation-based telemetry, add the interceptor to your `beans.xml`:
```xml
<interceptors>
    <class>io.telemetry.ai.otel.cdi.CDITraceInterceptor</class>
</interceptors>
```

### Additional Methods

#### Method 1: Direct TypedAttributeExtractor Calls

For more granular control, you can directly call the `addTypedAttributes` methods:

```java
// Without context
MyResponse response = // ...
Span span = telemetryAgent.startSpan("my-operation", SpanKind.CLIENT);
telemetryAgent.addTypedAttributes(span, response, OperationType.SEARCH);
telemetryAgent.endSpan(span, null);

// With context
MyResponse response = // ...
SearchOperationContext context = SearchOperationContext.builder()
    .query("my query")
    .endpoint("my-endpoint")
    .searchSystem("my-system")
    .build();
Span span = telemetryAgent.startSpan("my-operation", SpanKind.CLIENT);
telemetryAgent.addTypedAttributes(span, response, context, OperationType.SEARCH);
telemetryAgent.endSpan(span, null);
```

#### Method 2: @Trace Annotation with responseType

Use the `@Trace` annotation with the `responseType` parameter to auto-extract from the return value:

```java
@Trace(
    spanName = "my-operation",
    spanKind = SpanKind.CLIENT,
    operationType = OperationType.SEARCH,
    responseType = MyResponse.class
)
public MyResponse myOperation() {
    // The response will automatically have its attributes extracted
    return response;
}
```

## Annotation-Based Extraction Details

The library supports these annotations for telemetry extraction:

1. **@Trace** - Marks methods for tracing:
   ```java
   @Trace(
       spanName = "search_operation",
       spanKind = SpanKind.CLIENT,
       operationType = OperationType.SEARCH
   )
   ```

2. **@ExtractAttributes** - Marks parameters for attribute extraction:
   ```java
   public void search(@ExtractAttributes SearchRequest request) { ... }
   ```

3. **@AttributeList** - Identifies list parameters to be added as a single attribute:
   ```java
   public void search(@AttributeList(attributeName = "search.index") List<String> indices) { ... }
   ```

4. **@TenantId** - Marks tenant ID parameters:
   ```java
   public void search(@TenantId String tenantId) { ... }
   ```

5. **@ServiceId** - Marks service ID parameters:
   ```java
   public void search(@ServiceId String serviceId) { ... }
   ```

### Implementation Details

The `AnnotationProcessor` handles parameter processing with efficient caching:
```java
// Cache for method parameter annotations
private final ConcurrentHashMap<Method, MethodParameterMetadata> methodParameterCache = new ConcurrentHashMap<>();

// Cache for extractors by parameter type
private final ConcurrentHashMap<CacheKey, TypedAttributeExtractor<?, ?>> extractorCache = new ConcurrentHashMap<>();
```

## Example Implementation

Here's an example implementation for Elasticsearch responses:

```java
public class ElasticsearchResponseExtractor 
        implements TypedAttributeExtractor<ElasticsearchResponse, SearchOperationContext> {

    @Override
    public void extractAttributes(Span span, ElasticsearchResponse response, 
                                 SearchOperationContext context, OperationType operationType) {
        // Add standard search attributes
        span.setAttribute(operationType.getAttributeKey("system"), "elasticsearch");
        
        // Use query from context if available, otherwise from response
        String query = (context != null && context.getQuery() != null) 
            ? context.getQuery() : response.getQuery();
        span.setAttribute(operationType.getAttributeKey("query"), query);
        
        span.setAttribute(operationType.getAttributeKey("count"), response.getHits().size());
        
        // Add Elasticsearch-specific attributes
        span.setAttribute("elasticsearch.took_ms", response.getTook());
        
        // Use endpoint from context if available
        if (context != null && context.getEndpoint() != null) {
            span.setAttribute("elasticsearch.index", context.getEndpoint());
        } else {
            span.setAttribute("elasticsearch.indices", String.join(",", response.getIndices()));
        }
        
        // ... more attribute extraction
    }
}
```

## Benefits

- **Type Safety**: The extractor is type-safe, so you get compile-time checking of your attribute extraction code.
- **No Interface Requirement**: The response type doesn't need to implement any specific interface, making it easier to
  use with existing code.
- **Flexibility**: You can create extractors for any response type, including third-party types that you can't modify.
- **Inheritance Support**: The system will find the most specific extractor for a given response type, taking
  inheritance into account.
- **Context Awareness**: The extractor can use additional context information that might not be available in the
  response object itself.

## Best Practices

1. **Register Extractors Early**: Register your extractors during application startup to ensure they're available when
   needed.
2. **Use Specific Types**: Be as specific as possible with your response types to ensure the correct extractor is used.
3. **Handle Inheritance**: If you have a hierarchy of response types, create extractors for the most specific types
   first.
4. **Add Common Attributes**: Always add common attributes like system, query, and count to ensure consistent telemetry
   data.
5. **Gracefully Handle Null Context**: Always check for null context and provide fallbacks when context information is
   not available.
6. **Log Extraction**: Add debug logging to your extractors to help troubleshoot issues.
7. **Use Annotations for Clarity**: When using CDI, prefer annotation-based approach for cleaner, more maintainable code.
8. **Group Data in DTOs**: For parameters with many attributes, group them in DTOs and use a single `@ExtractAttributes`.
9. **Cache Heavily Used Extractors**: For high-throughput systems, ensure extractors and annotation metadata are properly cached.

## Choosing Between Approaches

- **Use Direct API Approach When**:
    - You need fine-grained control over context creation and span enhancement
    - You're in a non-CDI environment
    - You need to customize span attributes based on complex business logic
    - You prefer an imperative programming style

- **Use Annotation-Based Approach When**:
    - You want clean separation of business logic and telemetry
    - You prefer a declarative programming style
    - You're using CDI or another dependency injection framework
    - You want to minimize boilerplate code

Both approaches can coexist in the same system, giving you the flexibility to choose the right approach for each scenario. 
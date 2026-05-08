package io.telemetry.ai.otel.util;

import io.telemetry.ai.otel.annotation.Trace;
import io.telemetry.ai.otel.metrics.MetricsRecorder;
import io.telemetry.ai.otel.model.OperationType;
import io.opentelemetry.api.trace.SpanKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock search service for testing the {@link io.telemetry.ai.otel.extractor.TypedAttributeExtractor}.
 * Demonstrates how to use the @Trace annotation with the TypedAttributeExtractor.
 */
public interface MockSearchService {
    /**
     * Performs a search using a generic search API.
     * This method is annotated with @Trace to demonstrate how the TypedAttributeExtractor
     * is used with the QuarkusTraceInterceptor.
     *
     * @param query       The search query
     * @param source      The source to search
     * @param serviceName The service name
     * @param tenantId    The tenant ID
     * @return A generic search response
     */
    SearchResponseExtractor.GenericSearchResponse search(String query, String source, String serviceName, String tenantId);

    /**
     * Performs a sub-operation as part of the search process.
     * This method is called from within the search method.
     *
     * @param query       The search query
     * @param source      The source to search
     * @return A list of partial search results
     */
    List<SearchResponseExtractor.GenericSearchResponse.Result> searchSubOperation(String query, String source);

    /**
     * Implementation of the MockSearchService interface.
     */
    @ApplicationScoped
    class MockSearchServiceImpl implements MockSearchService {

        private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MockSearchServiceImpl.class);
        
        @Inject
        MetricsRecorder metricsRecorder;

        @Override
        @Trace(
                spanName = "generic-search",
                spanKind = SpanKind.CLIENT,
                operationType = OperationType.SEARCH,
                responseType = SearchResponseExtractor.GenericSearchResponse.class,
                includeParameters = true
        )
        public SearchResponseExtractor.GenericSearchResponse search(String query, String source, String serviceName, String tenantId) {
            logger.info("==== MOCK SEARCH SERVICE CALLED ====");
            logger.info("Search parameters: query={}, source={}, serviceName={}, tenantId={}",
                    query, source, serviceName, tenantId);
            
            // Start a timer using MetricsRecorder directly
            // This ensures metrics are collected even if the TypedAttributeExtractor is not registered
            MetricsRecorder.TimerBuilder.TimerContext timer = metricsRecorder.startTimer(OperationType.SEARCH)
                .withTenant(tenantId)
                .withTag("service", serviceName)
                .start();
                    
            try {
                // Record the actual start time
                long startTimeMs = System.currentTimeMillis();
                
                // Create a mock search response
                SearchResponseExtractor.GenericSearchResponse response = new SearchResponseExtractor.GenericSearchResponse();
                response.setQuery(query);
                response.setSources(List.of(source));
                
                // Call the sub-operation method to get search results
                logger.info("Calling search sub-operation from main search method");
                List<SearchResponseExtractor.GenericSearchResponse.Result> results = searchSubOperation(query, source);
                response.setResults(results);
                
                // Calculate actual latency
                long latencyMs = System.currentTimeMillis() - startTimeMs;
                response.setLatencyMs(latencyMs);
                response.setTimedOut(false);
                
                logger.info("Search operation completed successfully with {} results and actual latency of {}ms", 
                    results.size(), latencyMs);
                
                // Record metrics directly with the timer
                timer.success(results.size());

                logger.info("==== MOCK SEARCH SERVICE RETURNING RESPONSE ====");
                logger.info("Response: query={}, sources={}, results.size={}",
                        response.getQuery(), response.getSources(), response.getResults().size());
                
                // Log detailed information about metrics recording
                logger.info("Metrics directly recorded via MetricsRecorder: operation=search, tenant={}, service={}, results={}", 
                         tenantId, serviceName, results.size());

                return response;
            } catch (Exception e) {
                // Record failure metrics with error type
                logger.error("Search operation failed", e);
                timer.failure(e.getClass().getSimpleName());
                throw e;
            }
        }

        @Override
        public List<SearchResponseExtractor.GenericSearchResponse.Result> searchSubOperation(String query, String source) {
            logger.info("Performing sub-operation search for query: {} in source: {}", query, source);
            
            // Create tags map
            Map<String, String> tags = Map.of("source", source, "queryLength", String.valueOf(query.length()));
            
            try {
                // Directly return the measured operation with a string operation name
                return metricsRecorder.measureOperation(
                    "search_sub_operation", 
                    "test-tenant", 
                    tags,
                    () -> {
                        List<SearchResponseExtractor.GenericSearchResponse.Result> results = new ArrayList<>();
                        
                        // Create 3 results
                        for (int i = 0; i < 3; i++) {
                            SearchResponseExtractor.GenericSearchResponse.Result result = new SearchResponseExtractor.GenericSearchResponse.Result();
                            result.setId("doc-" + i);
                            result.setScore(0.9f - (i * 0.1f));

                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("title", "Document " + i);
                            metadata.put("content", "This is document " + i + " matching query: " + query);
                            metadata.put("created", "2024-01-" + (i + 1));
                            result.setMetadata(metadata);

                            results.add(result);
                        }
                        
                        logger.info("Sub-operation completed with {} results", results.size());
                        return results;
                    }
                ).getResult();
            } catch (Exception e) {
                logger.error("Error in search sub-operation", e);
                throw new RuntimeException("Error in search sub-operation", e);
            }
        }
    }
} 
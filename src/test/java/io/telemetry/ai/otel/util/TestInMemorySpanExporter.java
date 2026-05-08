package io.telemetry.ai.otel.util;

import io.telemetry.ai.otel.system.DelegatingSpanExporter;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * An in-memory span exporter for testing that captures spans for examination.
 * This exporter extends the DelegatingSpanExporter class and delegates to another exporter after capturing spans.
 */
public class TestInMemorySpanExporter extends DelegatingSpanExporter {
    private static final Logger logger = LoggerFactory.getLogger(TestInMemorySpanExporter.class);

    // Use instance fields rather than static fields to avoid shared state
    private final Map<String, SpanData> spans = new ConcurrentHashMap<>();
    private final Map<String, List<String>> childrenByParentId = new ConcurrentHashMap<>();
    private final List<SpanData> exportedSpans = new CopyOnWriteArrayList<>();

    // Singleton instance for static access
    private static TestInMemorySpanExporter INSTANCE;

    /**
     * Creates a new TestInMemorySpanExporter with no delegate.
     */
    public TestInMemorySpanExporter() {
        super(null);
        logger.info("Creating TestInMemorySpanExporter with no delegate");
    }

    /**
     * Creates a new TestInMemorySpanExporter with the specified delegate.
     *
     * @param delegate The SpanExporter to delegate to
     */
    public TestInMemorySpanExporter(SpanExporter delegate) {
        super(delegate);
        logger.info("Creating TestInMemorySpanExporter with delegate: {}",
                delegate != null ? delegate.getClass().getSimpleName() : "null");
    }

    @Override
    protected Collection<SpanData> processSpans(Collection<SpanData> spans) {
        // Enhanced logging for diagnostics
        logger.info("TestInMemorySpanExporter.processSpans called with {} spans", spans.size());

        // Log details about each span
        for (SpanData span : spans) {
            logger.info("TestInMemorySpanExporter processing span: {} ({}), parent: {}, kind: {}",
                    span.getName(),
                    span.getSpanId(),
                    span.getParentSpanContext().getSpanId(),
                    span.getKind());
        }

        // Capture all spans
        for (SpanData span : spans) {
            storeSpan(span);
        }

        // Return the original spans for delegation
        return spans;
    }

    private void storeSpan(SpanData span) {
        String spanId = span.getSpanId();
        String parentSpanId = span.getParentSpanContext().getSpanId();

        // Store this span
        spans.put(spanId, span);
        exportedSpans.add(span);
        logger.info("Stored span: {} with name: {}", spanId, span.getName());

        // Record parent-child relationship if this span has a parent
        if (SpanId.isValid(parentSpanId)) {
            // Add to parent->children mapping
            List<String> children = childrenByParentId.computeIfAbsent(parentSpanId, k -> new ArrayList<>());
            if (!children.contains(spanId)) {
                children.add(spanId);
                logger.info("Recorded parent-child relationship: {} -> {}", parentSpanId, spanId);
            } else {
                logger.info("Parent-child relationship already exists: {} -> {}", parentSpanId, spanId);
            }
        } else {
            logger.info("Span has no valid parent ID: {}", spanId);
        }
    }

    /**
     * Retrieve a span by its ID.
     *
     * @param spanId The ID of the span to retrieve
     * @return The SpanData for the specified span, or null if not found
     */
    public SpanData getSpanById(String spanId) {
        return spans.get(spanId);
    }

    /**
     * Get all exported spans.
     *
     * @return A list of all spans that have been exported
     */
    public List<SpanData> getExportedSpans() {
        return new ArrayList<>(exportedSpans);
    }

    /**
     * Get the children of a parent span.
     *
     * @param parentSpanId The ID of the parent span
     * @return A list of child span IDs
     */
    public List<String> getChildSpanIds(String parentSpanId) {
        return childrenByParentId.getOrDefault(parentSpanId, Collections.emptyList());
    }

    /**
     * Get all parent-child relationships.
     *
     * @return A map of parent span IDs to lists of child span IDs
     */
    public Map<String, List<String>> getParentChildRelationships() {
        return new HashMap<>(childrenByParentId);
    }

    /**
     * Get the children of a parent span.
     *
     * @param parentSpanId The ID of the parent span
     * @return A list of child spans
     */
    public List<SpanData> getChildSpans(String parentSpanId) {
        return getChildSpanIds(parentSpanId).stream()
                .map(this::getSpanById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Clear all stored spans and relationships.
     */
    public void clear() {
        int spanCount = spans.size();
        int relationshipsCount = childrenByParentId.size();

        spans.clear();
        childrenByParentId.clear();
        exportedSpans.clear();

        logger.info("Cleared all stored state: {} spans and {} parent-child relationships",
                spanCount, relationshipsCount);
    }

    /**
     * Get a diagnostic report of all spans and their relationships.
     *
     * @return A string containing a diagnostic report
     */
    public String getDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("TestInMemorySpanExporter Diagnostic Report\n");
        report.append("----------------------------------------\n");
        report.append("Total spans: ").append(exportedSpans.size()).append("\n");
        report.append("Parent-child relationships: ").append(childrenByParentId.size()).append("\n\n");

        report.append("Spans:\n");
        for (SpanData span : exportedSpans) {
            report.append("  ").append(span.getName())
                    .append(" (").append(span.getSpanId()).append(")")
                    .append(" parent=").append(span.getParentSpanContext().getSpanId())
                    .append(" kind=").append(span.getKind())
                    .append("\n");

            // Add attributes
            span.getAttributes().forEach((key, value) -> report.append("    ").append(key.getKey()).append("=").append(value).append("\n"));
        }

        report.append("\nParent-Child Relationships:\n");
        childrenByParentId.forEach((parentId, children) -> {
            SpanData parentSpan = spans.get(parentId);
            String parentName = parentSpan != null ? parentSpan.getName() : "unknown";
            report.append("  ").append(parentName).append(" (").append(parentId).append(")").append("\n");

            for (String childId : children) {
                SpanData childSpan = spans.get(childId);
                String childName = childSpan != null ? childSpan.getName() : "unknown";
                report.append("    -> ").append(childName).append(" (").append(childId).append(")").append("\n");
            }
        });

        return report.toString();
    }
} 
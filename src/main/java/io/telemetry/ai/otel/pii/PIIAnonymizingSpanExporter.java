package io.telemetry.ai.otel.pii;

import io.telemetry.ai.otel.system.DelegatingSpanExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A SpanExporter that anonymizes PII in span attributes before delegating to another exporter.
 * This approach ensures that PII is removed before spans are exported, rather than trying to
 * modify immutable spans after they're created.
 */
public class PIIAnonymizingSpanExporter extends DelegatingSpanExporter {
    private static final Logger logger = LoggerFactory.getLogger(PIIAnonymizingSpanExporter.class);

    private final List<PIIDetector> detectors;
    private final boolean enabled;

    /**
     * Creates a new PIIAnonymizingSpanExporter with the specified configuration.
     *
     * @param delegate The SpanExporter to delegate to after anonymizing PII
     * @param config   The configuration for PII detection and anonymization
     */
    public PIIAnonymizingSpanExporter(SpanExporter delegate, PIIDetectorConfig config) {
        super(delegate);
        this.enabled = config.isEnabled();
        this.detectors = new ArrayList<>();

        // Add configured detectors
        if (config.isRegexDetectionEnabled()) {
            detectors.add(new PIIDetector.RegexPIIDetector(config.getRegexPatterns()));
        }

        if (config.isPresidioDetectionEnabled()) {
            detectors.add(new PresidioPIIDetector(
                    config.getPresidioAnalyzerEndpoint(),
                    config.getPresidioAnonymizerEndpoint(),
                    config.getPresidioTimeoutSeconds()
            ));
        }

        logger.info("Created PIIAnonymizingSpanExporter with {} detectors, enabled={}",
                detectors.size(), enabled);
    }

    @Override
    protected Collection<SpanData> processSpans(Collection<SpanData> spans) {
        // If PII anonymization is disabled or no detectors are configured, return the original spans
        if (!enabled || detectors.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("PII anonymization is disabled or no detectors configured, skipping anonymization");
            }
            return spans;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Processing {} spans for PII anonymization", spans.size());
        }

        // Log details about each span before processing
        if (logger.isTraceEnabled()) {
            for (SpanData span : spans) {
                logger.trace("ANONYMIZING PII: name={}, id={}, parentId={}, parentValid={}, traceId={}",
                        span.getName(),
                        span.getSpanId(),
                        span.getParentSpanContext().getSpanId(),
                        span.getParentSpanContext().isValid(),
                        span.getSpanContext().getTraceId());

                // Log string attributes for debugging
                span.getAttributes().forEach((key, value) -> {
                    if (value instanceof String) {
                        logger.trace("  STRING ATTR: {}={}", key.getKey(), value);
                    }
                });
            }
        }

        // Anonymize PII in each span
        List<SpanData> anonymizedSpans = new ArrayList<>(spans.size());
        Map<String, SpanData> anonymizedSpanMap = new HashMap<>();

        // Process all spans in a single loop to maintain parent-child relationships
        for (SpanData span : spans) {
            SpanData anonymizedSpan = anonymizePII(span);
            anonymizedSpans.add(anonymizedSpan);
            anonymizedSpanMap.put(span.getSpanId(), anonymizedSpan);

            // Log detailed information about the span
            if (logger.isDebugEnabled()) {
                if (span.getParentSpanContext().isValid()) {
                    logger.debug("PROCESSED CHILD SPAN: name={}, id={}, parentId={}, parentValid={}",
                            anonymizedSpan.getName(),
                            anonymizedSpan.getSpanId(),
                            anonymizedSpan.getParentSpanContext().getSpanId(),
                            anonymizedSpan.getParentSpanContext().isValid());
                } else {
                    logger.debug("PROCESSED PARENT SPAN: name={}, id={}, parentValid={}",
                            anonymizedSpan.getName(),
                            anonymizedSpan.getSpanId(),
                            anonymizedSpan.getParentSpanContext().isValid());
                }
            }
        }

        // Log detailed information about span relationships after processing
        if (logger.isTraceEnabled()) {
            logger.trace("========== DETAILED SPAN RELATIONSHIP LOGGING - AFTER PROCESSING ==========");
            Map<String, List<String>> relationships = new HashMap<>();

            for (SpanData span : anonymizedSpans) {
                logger.trace("SPAN AFTER: name={}, id={}, parentId={}, parentValid={}, traceId={}",
                        span.getName(),
                        span.getSpanId(),
                        span.getParentSpanContext().getSpanId(),
                        span.getParentSpanContext().isValid(),
                        span.getSpanContext().getTraceId());

                // Log attributes for debugging
                span.getAttributes().forEach((key, value) -> {
                    if (value instanceof String) {
                        logger.trace("  SPAN ATTR: {}={}", key.getKey(), value);
                    }
                });

                // Track parent-child relationships
                if (span.getParentSpanContext().isValid()) {
                    String parentId = span.getParentSpanContext().getSpanId();
                    relationships.computeIfAbsent(parentId, k -> new ArrayList<>()).add(span.getSpanId());
                }
            }

            // Log all relationships
            for (SpanData span : anonymizedSpans) {
                if (span.getParentSpanContext().isValid()) {
                    logger.trace("PARENT-CHILD: {} -> {}", span.getParentSpanContext().getSpanId(), span.getSpanId());
                }
            }

            logger.trace("RELATIONSHIPS AFTER: {}", relationships);
        }

        return anonymizedSpans;
    }

    /**
     * Anonymizes PII in the span attributes.
     *
     * @param span The span to anonymize
     * @return A new SpanData with anonymized attributes
     */
    private SpanData anonymizePII(SpanData span) {
        if (logger.isTraceEnabled()) {
            logger.trace("Anonymizing PII in span: {} ({}), parent: {}, parentValid: {}",
                    span.getName(),
                    span.getSpanId(),
                    span.getParentSpanContext().getSpanId(),
                    span.getParentSpanContext().isValid());
        }

        // Get all string attributes from the span
        Map<AttributeKey<String>, String> stringAttributes = new HashMap<>();
        span.getAttributes().forEach((key, value) -> {
            if (key != null && value instanceof String) {
                @SuppressWarnings("unchecked")
                AttributeKey<String> stringKey = (AttributeKey<String>) key;
                stringAttributes.put(stringKey, (String) value);
            }
        });

        if (stringAttributes.isEmpty()) {
            if (logger.isTraceEnabled()) {
                logger.trace("No string attributes found in span {}, returning original span", span.getName());
            }
            return span;
        }

        if (logger.isTraceEnabled()) {
            logger.trace("PIIAnonymizingSpanExporter: Processing span '{}' with {} string attributes",
                    span.getName(), stringAttributes.size());
        }

        // Process each attribute with each detector
        Map<AttributeKey<String>, String> anonymizedAttributes = new HashMap<>();
        boolean anyPIIFound = false;

        for (Map.Entry<AttributeKey<String>, String> entry : stringAttributes.entrySet()) {
            AttributeKey<String> key = entry.getKey();
            String value = entry.getValue();

            // Skip null values
            if (value == null) {
                continue;
            }

            if (logger.isTraceEnabled()) {
                logger.trace("Processing attribute: {}={}", key, value);
            }

            // Try each detector
            String anonymizedValue = value;
            for (PIIDetector detector : detectors) {
                String result = detector.anonymize(value);
                if (!result.equals(value)) {
                    // PII was found and anonymized
                    anonymizedValue = result;
                    anyPIIFound = true;
                    if (logger.isDebugEnabled()) {
                        logger.debug("PII found and anonymized in attribute '{}' using detector {}",
                                key.getKey(), detector.getClass().getSimpleName());
                    }
                    break;
                }
            }

            // Only add to map if PII was found
            if (!anonymizedValue.equals(value)) {
                anonymizedAttributes.put(key, anonymizedValue);
            }
        }

        // If no PII was found, return the original span
        if (!anyPIIFound) {
            if (logger.isTraceEnabled()) {
                logger.trace("No PII found in span {}, returning original span", span.getName());
            }
            return span;
        }

        // Build new attributes with anonymized values
        AttributesBuilder attributesBuilder = getAttributesBuilder(span, anonymizedAttributes);

        // Create a new SpanData with the anonymized attributes
        if (logger.isDebugEnabled()) {
            logger.debug("PIIAnonymizingSpanExporter: Anonymized {} PII attributes in span '{}'",
                    anonymizedAttributes.size(), span.getName());
        }

        // Create a new SpanData with the anonymized attributes
        SpanData anonymizedSpan = new AnonymizedSpanData(span, attributesBuilder.build());
        if (logger.isTraceEnabled()) {
            logger.trace("Created anonymized span: {} ({}), parent: {}, parentValid: {}",
                    anonymizedSpan.getName(),
                    anonymizedSpan.getSpanId(),
                    anonymizedSpan.getParentSpanContext().getSpanId(),
                    anonymizedSpan.getParentSpanContext().isValid());
        }

        return anonymizedSpan;
    }

    private AttributesBuilder getAttributesBuilder(SpanData span, Map<AttributeKey<String>, String> anonymizedAttributes) {
        AttributesBuilder attributesBuilder = Attributes.builder();
        span.getAttributes().forEach((key, value) -> {
            if (anonymizedAttributes.containsKey(key)) {
                // Use the anonymized value
                @SuppressWarnings("unchecked")
                AttributeKey<String> stringKey = (AttributeKey<String>) key;
                attributesBuilder.put(stringKey, anonymizedAttributes.get(key));
                if (logger.isTraceEnabled()) {
                    logger.trace("Using anonymized value for attribute: {}", key);
                }
            } else {
                putAttributeValue(attributesBuilder, key, value);
            }
        });
        return attributesBuilder;
    }

    /**
     * Helper method to put an attribute value of any type into the AttributesBuilder.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void putAttributeValue(AttributesBuilder builder, AttributeKey key, Object value) {
        if (value instanceof String) {
            builder.put((AttributeKey<String>) key, (String) value);
        } else if (value instanceof Long) {
            builder.put((AttributeKey<Long>) key, (Long) value);
        } else if (value instanceof Double) {
            builder.put((AttributeKey<Double>) key, (Double) value);
        } else if (value instanceof Boolean) {
            builder.put((AttributeKey<Boolean>) key, (Boolean) value);
        } else if (value instanceof String[]) {
            builder.put((AttributeKey<String[]>) key, (String[]) value);
        } else if (value instanceof Long[]) {
            builder.put((AttributeKey<Long[]>) key, (Long[]) value);
        } else if (value instanceof Double[]) {
            builder.put((AttributeKey<Double[]>) key, (Double[]) value);
        } else if (value instanceof Boolean[]) {
            builder.put((AttributeKey<Boolean[]>) key, (Boolean[]) value);
        }
        // If the type is not supported, the attribute will be skipped
    }

    /**
         * A wrapper around SpanData that replaces the attributes with anonymized ones.
         */
        private record AnonymizedSpanData(SpanData delegate, Attributes attributes) implements SpanData {
            private static final Logger logger = LoggerFactory.getLogger(AnonymizedSpanData.class);

            private AnonymizedSpanData(SpanData delegate, Attributes attributes) {
                this.delegate = delegate;
                this.attributes = attributes;

                logger.trace("CREATING AnonymizedSpanData: name={}, id={}, parentId={}, parentValid={}, traceId={}",
                        delegate.getName(),
                        delegate.getSpanId(),
                        delegate.getParentSpanContext().getSpanId(),
                        delegate.getParentSpanContext().isValid(),
                        delegate.getSpanContext().getTraceId());

                // Enhanced logging for parent-child relationship debugging
                if (delegate.getParentSpanContext().isValid()) {
                    logger.trace("VALID PARENT-CHILD: parent={} -> child={}, traceId={}, parentTraceId={}",
                            delegate.getParentSpanContext().getSpanId(),
                            delegate.getSpanId(),
                            delegate.getSpanContext().getTraceId(),
                            delegate.getParentSpanContext().getTraceId());
                    logger.trace("PARENT CONTEXT: traceId={}, spanId={}, traceFlags={}, remote={}, valid={}",
                            delegate.getParentSpanContext().getTraceId(),
                            delegate.getParentSpanContext().getSpanId(),
                            delegate.getParentSpanContext().getTraceFlags(),
                            delegate.getParentSpanContext().isRemote(),
                            delegate.getParentSpanContext().isValid());
                } else {
                    logger.trace("NO VALID PARENT for span: {} (This is a root span)", delegate.getSpanId());
                }

                // Log the span context details
                logger.trace("SPAN CONTEXT: traceId={}, spanId={}, traceFlags={}, remote={}, valid={}",
                        delegate.getSpanContext().getTraceId(),
                        delegate.getSpanContext().getSpanId(),
                        delegate.getSpanContext().getTraceFlags(),
                        delegate.getSpanContext().isRemote(),
                        delegate.getSpanContext().isValid());
            }

            @Override
            public String getName() {
                return delegate.getName();
            }

            @Override
            public SpanContext getSpanContext() {
                SpanContext context = delegate.getSpanContext();
                if (logger.isTraceEnabled()) {
                    logger.trace("getSpanContext called for span {}: traceId={}, valid={}",
                            delegate.getSpanId(),
                            context.getTraceId(),
                            context.isValid());
                }
                return context;
            }

            @Override
            public SpanContext getParentSpanContext() {
                SpanContext parentContext = delegate.getParentSpanContext();
                if (logger.isTraceEnabled()) {
                    logger.trace("getParentSpanContext called for span {}: parentId={}, valid={}, traceId={}",
                            delegate.getSpanId(),
                            parentContext.getSpanId(),
                            parentContext.isValid(),
                            parentContext.getTraceId());
                }
                return parentContext;
            }

            @Override
            public StatusData getStatus() {
                return delegate.getStatus();
            }

            @Override
            public SpanKind getKind() {
                return delegate.getKind();
            }

            @Override
            public long getStartEpochNanos() {
                return delegate.getStartEpochNanos();
            }

            @Override
            public Attributes getAttributes() {
                return attributes;
            }

            @Override
            public List<EventData> getEvents() {
                return delegate.getEvents();
            }

            @Override
            public List<LinkData> getLinks() {
                return delegate.getLinks();
            }

            @Override
            public long getEndEpochNanos() {
                return delegate.getEndEpochNanos();
            }

            @Override
            public boolean hasEnded() {
                return delegate.hasEnded();
            }

            @Override
            public int getTotalRecordedEvents() {
                return delegate.getTotalRecordedEvents();
            }

            @Override
            public int getTotalRecordedLinks() {
                return delegate.getTotalRecordedLinks();
            }

            @Override
            public int getTotalAttributeCount() {
                return delegate.getTotalAttributeCount();
            }

            @Override
            public Resource getResource() {
                return delegate.getResource();
            }

            @Deprecated
            @Override
            public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
                return delegate.getInstrumentationLibraryInfo();
            }
        }
} 
package io.telemetry.ai.otel.pii;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for {@link io.telemetry.ai.otel.pii.PIIAnonymizingSpanExporter} with an actual OTLP collector.
 * Tests PII detection and anonymization in span attributes before export.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PIIAnonymizingSpanExporterTest {
    private TestSpanExporter testExporter;
    private PIIAnonymizingSpanExporter piiExporter;

    @BeforeEach
    void setup() {
        // Create a test exporter that will be wrapped by the PIIAnonymizingSpanExporter
        testExporter = new TestSpanExporter();

        // Create a custom PIIDetectorConfig with regex patterns
        Map<String, String> regexPatterns = new HashMap<>();
        regexPatterns.put("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", "[EMAIL]");
        regexPatterns.put("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "[PHONE]");
        regexPatterns.put("\\b\\d{3}[-]?\\d{2}[-]?\\d{4}\\b", "[SSN]");

        PIIDetectorConfig config = new PIIDetectorConfig(
                true,                // enabled
                true,                // regexDetectionEnabled
                false,               // presidioDetectionEnabled
                regexPatterns,       // regexPatterns
                null,                // presidioAnalyzerEndpoint
                null,                // presidioAnonymizerEndpoint
                0                    // presidioTimeoutSeconds
        );

        // Create the PII anonymizing span exporter
        piiExporter = new PIIAnonymizingSpanExporter(testExporter, config);
    }

    @Test
    void testEmailAnonymization() {
        // Create a test span with an email address
        SpanData span = createTestSpan("test-span", Attributes.builder()
                .put("user.email", "john.doe@example.com")
                .put("message", "Please contact john.doe@example.com for more information")
                .build());

        // Export the span
        piiExporter.export(List.of(span));

        // Get the exported spans
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        assertEquals(1, exportedSpans.size());
        SpanData anonymizedSpan = exportedSpans.getFirst();

        // Verify that the email address was anonymized
        assertEquals("[EMAIL]", anonymizedSpan.getAttributes().get(AttributeKey.stringKey("user.email")));
        assertEquals("Please contact [EMAIL] for more information",
                anonymizedSpan.getAttributes().get(AttributeKey.stringKey("message")));
    }

    @Test
    void testPhoneNumberAnonymization() {
        // Create a test span with a phone number
        SpanData span = createTestSpan("test-span", Attributes.builder()
                .put("user.phone", "123-456-7890")
                .put("message", "Call me at 123.456.7890 or 1234567890")
                .build());

        // Export the span
        piiExporter.export(List.of(span));

        // Get the exported spans
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        assertEquals(1, exportedSpans.size());
        SpanData anonymizedSpan = exportedSpans.getFirst();

        // Verify that the phone number was anonymized
        assertEquals("[PHONE]", anonymizedSpan.getAttributes().get(AttributeKey.stringKey("user.phone")));
        assertEquals("Call me at [PHONE] or [PHONE]",
                anonymizedSpan.getAttributes().get(AttributeKey.stringKey("message")));
    }

    @Test
    void testSocialSecurityNumberAnonymization() {
        // Create a test span with a social security number
        SpanData span = createTestSpan("test-span", Attributes.builder()
                .put("user.ssn", "123-45-6789")
                .put("message", "SSN: 123456789")
                .build());

        // Export the span
        piiExporter.export(List.of(span));

        // Get the exported spans
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        assertEquals(1, exportedSpans.size());
        SpanData anonymizedSpan = exportedSpans.getFirst();

        // Verify that the social security number was anonymized
        assertEquals("[SSN]", anonymizedSpan.getAttributes().get(AttributeKey.stringKey("user.ssn")));
        assertEquals("SSN: [SSN]",
                anonymizedSpan.getAttributes().get(AttributeKey.stringKey("message")));
    }

    @Test
    void testMultiplePIITypesAnonymization() {
        // Create a test span with multiple PII types
        SpanData span = createTestSpan("test-span", Attributes.builder()
                .put("message", "Contact john.doe@example.com or call 123-456-7890. SSN: 123-45-6789")
                .build());

        // Export the span
        piiExporter.export(List.of(span));

        // Get the exported spans
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        assertEquals(1, exportedSpans.size());
        SpanData anonymizedSpan = exportedSpans.getFirst();

        // Verify that all PII types were anonymized
        assertEquals("Contact [EMAIL] or call [PHONE]. SSN: [SSN]",
                anonymizedSpan.getAttributes().get(AttributeKey.stringKey("message")));
    }

    @Test
    void testNoPIINoChange() {
        // Create a test span with no PII
        SpanData span = createTestSpan("test-span", Attributes.builder()
                .put("message", "This message contains no PII")
                .build());

        // Export the span
        piiExporter.export(List.of(span));

        // Get the exported spans
        List<SpanData> exportedSpans = testExporter.getExportedSpans();
        assertEquals(1, exportedSpans.size());
        SpanData exportedSpan = exportedSpans.getFirst();

        // Verify that the message was not changed
        assertEquals("This message contains no PII",
                exportedSpan.getAttributes().get(AttributeKey.stringKey("message")));
    }

    @Test
    void testDisabledExporter() {
        // Create a disabled PII anonymizing span exporter
        PIIDetectorConfig disabledConfig = new PIIDetectorConfig(
                false,               // enabled
                true,                // regexDetectionEnabled
                false,               // presidioDetectionEnabled
                null,                // regexPatterns
                null,                // presidioAnalyzerEndpoint
                null,                // presidioAnonymizerEndpoint
                0                    // presidioTimeoutSeconds
        );

        TestSpanExporter newExporter = new TestSpanExporter();
        PIIAnonymizingSpanExporter disabledExporter = new PIIAnonymizingSpanExporter(newExporter, disabledConfig);

        // Create a test span with PII
        SpanData span = createTestSpan("test-span", Attributes.builder()
                .put("user.email", "john.doe@example.com")
                .build());

        // Export the span
        disabledExporter.export(List.of(span));

        // Get the exported spans
        List<SpanData> exportedSpans = newExporter.getExportedSpans();
        assertEquals(1, exportedSpans.size());
        SpanData exportedSpan = exportedSpans.getFirst();

        // Verify that the PII was not anonymized (exporter is disabled)
        assertEquals("john.doe@example.com", exportedSpan.getAttributes().get(AttributeKey.stringKey("user.email")));
    }

    @Test
    void testFlushDelegation() {
        // Set up the test exporter to return a specific result code for flush
        CompletableResultCode expectedResult = CompletableResultCode.ofSuccess();
        testExporter.setFlushResult(expectedResult);

        // Call flush on the PII anonymizing exporter
        CompletableResultCode result = piiExporter.flush();

        // Verify that the flush call was delegated to the test exporter
        assertTrue(testExporter.wasFlushCalled());

        // Verify that the result from the test exporter was returned
        assertSame(expectedResult, result);
    }

    @Test
    void testShutdownDelegation() {
        // Set up the test exporter to return a specific result code for shutdown
        CompletableResultCode expectedResult = CompletableResultCode.ofSuccess();
        testExporter.setShutdownResult(expectedResult);

        // Call shutdown on the PII anonymizing exporter
        CompletableResultCode result = piiExporter.shutdown();

        // Verify that the shutdown call was delegated to the test exporter
        assertTrue(testExporter.wasShutdownCalled());

        // Verify that the result from the test exporter was returned
        assertSame(expectedResult, result);
    }

    @Test
    void testIntegrationWithTelemetryConfiguration() {
        // Set up system properties for testing
        System.setProperty("OTLP_EXPORT", "true");
        System.setProperty("otel.pii.detector.enabled", "true");
        System.setProperty("otel.pii.detector.regex.enabled", "true");

        try {
            // Create a test span exporter to capture the spans
            TestSpanExporter testExporter = new TestSpanExporter();

            // Create a PIIDetectorConfig
            PIIDetectorConfig piiConfig = new PIIDetectorConfig();
            piiConfig.setEnabled(true);
            piiConfig.setRegexDetectionEnabled(true);

            // Create a PIIAnonymizingSpanExporter that wraps our test exporter
            PIIAnonymizingSpanExporter piiExporter = new PIIAnonymizingSpanExporter(testExporter, piiConfig);

            // Create a tracer provider with the PII exporter
            SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                    .setResource(Resource.getDefault())
                    .addSpanProcessor(SimpleSpanProcessor.create(piiExporter))
                    .build();

            // Get a tracer from the provider
            io.opentelemetry.api.trace.Tracer tracer = tracerProvider.get("test-app");

            // Create a span with PII
            io.opentelemetry.api.trace.Span span = tracer.spanBuilder("test-span")
                    .setAttribute("user.email", "john.doe@example.com")
                    .setAttribute("message", "Contact me at john.doe@example.com")
                    .startSpan();

            // End the span to trigger export
            span.end();

            // Force flush to ensure the span is exported
            tracerProvider.forceFlush().join(5, TimeUnit.SECONDS);

            // Verify that the span was exported
            assertFalse(testExporter.getExportedSpans().isEmpty(), "No spans were exported");

            // Get the exported span
            SpanData exportedSpan = testExporter.getExportedSpans().getFirst();

            // Verify that the PII was anonymized
            String emailAttribute = exportedSpan.getAttributes().get(AttributeKey.stringKey("user.email"));
            String messageAttribute = exportedSpan.getAttributes().get(AttributeKey.stringKey("message"));

            assertEquals("[EMAIL]", emailAttribute, "Email was not anonymized");
            assertEquals("Contact me at [EMAIL]", messageAttribute, "Email in message was not anonymized");

            // Clean up
            tracerProvider.close();
        } finally {
            // Reset system properties
            System.clearProperty("OTLP_EXPORT");
            System.clearProperty("OTLP_EXPORTER");
            System.clearProperty("otel.pii.detector.enabled");
            System.clearProperty("otel.pii.detector.regex.enabled");
        }
    }

    /**
     * Creates a test span with the specified name and attributes.
     *
     * @param name       The name of the span
     * @param attributes The attributes to add to the span
     * @return A SpanData object representing the span
     */
    private SpanData createTestSpan(String name, Attributes attributes) {
        return new TestSpanData(name, attributes);
    }

    /**
     * A test implementation of SpanExporter that records exported spans.
     */
    private static class TestSpanExporter implements SpanExporter {
        @Getter
        private final List<SpanData> exportedSpans = new ArrayList<>();
        private boolean flushCalled = false;
        private boolean shutdownCalled = false;
        @Setter
        private CompletableResultCode flushResult = CompletableResultCode.ofSuccess();
        @Setter
        private CompletableResultCode shutdownResult = CompletableResultCode.ofSuccess();

        @Override
        public CompletableResultCode export(@Nonnull Collection<SpanData> spans) {
            exportedSpans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            flushCalled = true;
            return flushResult;
        }

        @Override
        public CompletableResultCode shutdown() {
            shutdownCalled = true;
            return shutdownResult;
        }

        public boolean wasFlushCalled() {
            return flushCalled;
        }

        public boolean wasShutdownCalled() {
            return shutdownCalled;
        }

        public void reset() {
            exportedSpans.clear();
            flushCalled = false;
            shutdownCalled = false;
        }
    }

    /**
         * A simple implementation of SpanData for testing.
         */
        private record TestSpanData(String name, Attributes attributes) implements SpanData {

        @Override
            public String getName() {
                return name;
            }

            @Override
            public SpanContext getSpanContext() {
                return SpanContext.create(
                        "00000000000000000000000000000001",
                        "0000000000000001",
                        TraceFlags.getSampled(),
                        TraceState.getDefault()
                );
            }

            @Override
            public SpanContext getParentSpanContext() {
                return SpanContext.getInvalid();
            }

            @Override
            public StatusData getStatus() {
                return StatusData.unset();
            }

            @Override
            public SpanKind getKind() {
                return SpanKind.INTERNAL;
            }

            @Override
            public long getStartEpochNanos() {
                return System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
            }

            @Override
            public Attributes getAttributes() {
                return attributes;
            }

            @Override
            public List<EventData> getEvents() {
                return new ArrayList<>();
            }

            @Override
            public List<LinkData> getLinks() {
                return new ArrayList<>();
            }

            @Override
            public long getEndEpochNanos() {
                return System.nanoTime();
            }

            @Override
            public boolean hasEnded() {
                return true;
            }

            @Override
            public int getTotalRecordedEvents() {
                return 0;
            }

            @Override
            public int getTotalRecordedLinks() {
                return 0;
            }

            @Override
            public int getTotalAttributeCount() {
                return attributes.size();
            }

            @Override
            public Resource getResource() {
                return Resource.getDefault();
            }

            @Override
            public InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
                return InstrumentationLibraryInfo.empty();
            }
        }
} 
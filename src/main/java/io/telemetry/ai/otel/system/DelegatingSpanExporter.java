package io.telemetry.ai.otel.system;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.Nonnull;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Abstract base class for span exporters that delegate to another exporter.
 *
 * <p>This class provides a mechanism to delegate span exporting to another exporter
 * after performing custom processing on the spans. Subclasses should implement the
 * {@link #processSpans(Collection)} method to perform their specific processing.
 *
 * <p>The flush and shutdown operations are propagated to the delegate exporter
 * to ensure proper cleanup of resources throughout the exporter chain.
 */
@Setter
public abstract class DelegatingSpanExporter implements SpanExporter {
    private static final Logger logger = LoggerFactory.getLogger(DelegatingSpanExporter.class);

    /**
     * The delegate exporter to which spans will be forwarded after processing.
     */
    protected SpanExporter delegate;

    /**
     * Creates a new DelegatingSpanExporter with an optional delegate.
     *
     * @param delegate The SpanExporter to delegate to, or null if not yet known
     */
    protected DelegatingSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    /**
     * Process spans before delegating to the next exporter in the chain.
     * Subclasses should override this method to implement their specific processing.
     *
     * @param spans The spans to process
     * @return The processed spans
     */
    protected abstract Collection<SpanData> processSpans(Collection<SpanData> spans);

    @Override
    public CompletableResultCode export(@Nonnull Collection<SpanData> spans) {
        // Process the spans according to the subclass implementation
        Collection<SpanData> processedSpans = processSpans(spans);

        // Delegate to the next exporter in the chain if available
        if (delegate != null) {
            return delegate.export(processedSpans);
        }

        // No delegate, just return success
        if (logger.isDebugEnabled()) {
            logger.debug("{} has no delegate, spans will not be forwarded",
                    this.getClass().getSimpleName());
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
        if (logger.isDebugEnabled()) {
            logger.debug("Flushing DelegatingSpanExporter");
        }

        if (delegate != null) {
            // Delegate the flush operation to the next exporter in the chain
            return delegate.flush();
        }

        // No delegate, just return success
        if (logger.isDebugEnabled()) {
            logger.debug("{} has no delegate, flush is a no-op",
                    this.getClass().getSimpleName());
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        if (delegate != null) {
            return delegate.shutdown();
        }
        return CompletableResultCode.ofSuccess();
    }
} 
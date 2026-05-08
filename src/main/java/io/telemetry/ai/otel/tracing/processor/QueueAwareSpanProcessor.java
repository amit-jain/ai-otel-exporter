package io.telemetry.ai.otel.tracing.processor;

import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Span processor that manages queue overflow and provides fallback behavior.
 * Monitors the span queue size and switches to a fallback exporter when the queue is full.
 */
public class QueueAwareSpanProcessor implements SpanProcessor {
    private static final Logger logger = LoggerFactory.getLogger(QueueAwareSpanProcessor.class);
    private final BatchSpanProcessor delegate;
    private final SpanExporter fallbackExporter;
    private final AtomicLong queuedSpans = new AtomicLong(0);
    private final int maxQueueSize;
    private volatile boolean isOverflowing = false;

    /**
     * Creates a new QueueAwareSpanProcessor that monitors queue size and provides fallback behavior.
     *
     * @param delegate The primary BatchSpanProcessor that will process spans normally
     * @param fallbackExporter The fallback exporter to use when the queue is full
     * @param maxQueueSize The maximum number of spans that can be queued before switching to fallback exporter
     */
    public QueueAwareSpanProcessor(BatchSpanProcessor delegate, SpanExporter fallbackExporter, int maxQueueSize) {
        this.delegate = delegate;
        this.fallbackExporter = fallbackExporter;
        this.maxQueueSize = maxQueueSize;
        if (logger.isDebugEnabled()) {
            logger.debug("Created QueueAwareSpanProcessor with maxQueueSize: {}", maxQueueSize);
        }
    }

    @Override
    public void onStart(@Nonnull Context parentContext, @Nonnull ReadWriteSpan span) {
        delegate.onStart(parentContext, span);
    }

    @Override
    public boolean isStartRequired() {
        return delegate.isStartRequired();
    }

    @Override
    public void onEnd(@Nonnull ReadableSpan span) {
        long currentQueueSize = queuedSpans.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        if (logger.isDebugEnabled()) {
            logger.debug("[{}] Queue state before processing: {}/{}", timestamp, currentQueueSize, maxQueueSize);
        }

        try {
            if (currentQueueSize >= maxQueueSize || isOverflowing) {
                isOverflowing = true;
                logger.warn("[{}] Span queue full ({}/{}), using fallback exporter for span: {} [trace: {}]",
                        timestamp, currentQueueSize, maxQueueSize, span.getName(), span.getSpanContext().getTraceId());
                try {
                    CompletableResultCode result = fallbackExporter.export(Collections.singletonList(span.toSpanData()));
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Fallback export completed with success: {}", timestamp, result.isSuccess());
                    }
                } catch (Exception e) {
                    logger.error("[{}] Error during fallback export", timestamp, e);
                }
            } else {
                try {
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Attempting delegate export for span: {} [trace: {}]",
                                timestamp, span.getName(), span.getSpanContext().getTraceId());
                    }
                    delegate.onEnd(span);
                    if (logger.isDebugEnabled()) {
                        logger.debug("[{}] Delegate export completed successfully", timestamp);
                    }
                } catch (Exception e) {
                    logger.warn("[{}] Failed to process span through delegate, using fallback. Error: {}", timestamp, e.getMessage());
                    fallbackExporter.export(Collections.singletonList(span.toSpanData()));
                }

            }
        } finally {
            long remainingQueue = queuedSpans.decrementAndGet();
            if (logger.isDebugEnabled()) {
                logger.debug("[{}] Queue state after processing: {}/{}", timestamp, remainingQueue, maxQueueSize);
            }
            if (remainingQueue < maxQueueSize / 2) {
                boolean wasOverflowing = isOverflowing;
                isOverflowing = false;
                if (wasOverflowing) {
                    logger.info("[{}] Queue state recovered from overflow. Current size: {}/{}",
                            timestamp, remainingQueue, maxQueueSize);
                }
            }
        }
    }

    @Override
    public boolean isEndRequired() {
        return delegate.isEndRequired();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public CompletableResultCode forceFlush() {
        return delegate.forceFlush();
    }
}

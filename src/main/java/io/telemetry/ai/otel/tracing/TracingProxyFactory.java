package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.annotation.QueryText;
import io.telemetry.ai.otel.annotation.ServiceName;
import io.telemetry.ai.otel.annotation.TenantId;
import io.telemetry.ai.otel.annotation.Trace;
import io.telemetry.ai.otel.config.TelemetryConfig;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.LLMOperationContext;
import io.telemetry.ai.otel.model.context.SearchOperationContext;
import io.telemetry.ai.otel.model.response.EmbeddingResponse;
import io.telemetry.ai.otel.model.response.SearchResponse;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;

/**
 * Factory for creating dynamic proxies that automatically add tracing capabilities to service methods.
 * This class uses Java's dynamic proxy mechanism to intercept method calls and add OpenTelemetry
 * tracing instrumentation based on @Trace annotations.
 */
public class TracingProxyFactory {
    private static final Logger logger = LoggerFactory.getLogger(TracingProxyFactory.class);

    private final TelemetryAgent telemetryAgent;
    private final TelemetryAgentProducer agentProducer;

    /**
     * Creates a new TracingProxyFactory with the specified telemetry agent.
     *
     * @param telemetryAgent The telemetry agent to use for tracing operations
     */
    public TracingProxyFactory(TelemetryAgent telemetryAgent) {
        this.telemetryAgent = telemetryAgent;
        this.agentProducer = null;
    }

    /**
     * Creates a new TracingProxyFactory with the specified telemetry agent producer.
     *
     * @param agentProducer The telemetry agent producer to use for getting appropriate agents
     */
    public TracingProxyFactory(TelemetryAgentProducer agentProducer) {
        this.telemetryAgent = null;
        this.agentProducer = agentProducer;
    }

    /**
     * Creates a tracing proxy for the given target object.
     * The proxy will implement the specified interface and add tracing capabilities
     * to methods annotated with @Trace.
     *
     * @param <T>            The type of the interface to proxy
     * @param target         The target object to proxy
     * @param interfaceClass The interface class that the proxy should implement
     * @return A proxy instance that implements the specified interface
     */
    @SuppressWarnings("unchecked")
    public <T> T createTracingProxy(T target, Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{interfaceClass},
                new TracingInvocationHandler(target)
        );
    }

    /**
     * Invocation handler that implements the tracing logic for proxied methods.
     * Handles method interception, span creation, attribute extraction, and error handling.
     */
    private class TracingInvocationHandler implements InvocationHandler {
        private static final Logger logger = LoggerFactory.getLogger(TracingInvocationHandler.class);
        private final Object target;
        private final TelemetryConfig config;

        /**
         * Creates a new TracingInvocationHandler.
         *
         * @param target The target object being proxied
         */
        public TracingInvocationHandler(Object target) {
            this.target = target;
            this.config = TelemetryConfig.fromSystemProperties();
        }

        /**
         * Handles method invocation on the proxy instance.
         * Implements the tracing logic around method execution, including span creation,
         * attribute extraction, and error handling.
         *
         * @param proxy  The proxy instance
         * @param method The method being invoked
         * @param args   The method arguments
         * @return The result of the method invocation
         * @throws Throwable If an error occurs during method invocation
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Trace traceAnnotation = method.getAnnotation(Trace.class);
            if (traceAnnotation == null) {
                // Try getting the annotation from the implementation class method
                try {
                    Method implMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                    traceAnnotation = implMethod.getAnnotation(Trace.class);
                } catch (NoSuchMethodException e) {
                    logger.error("Could not find implementation method", e);
                }
            }

            if (traceAnnotation == null) {
                return method.invoke(target, args);
            }

            // Extract parameters using annotations
            Parameter[] methodParameters = method.getParameters();
            String query = extractAnnotatedParameter(methodParameters, args, QueryText.class);
            String serviceName = extractAnnotatedParameter(methodParameters, args, ServiceName.class);
            String tenantId = extractAnnotatedParameter(methodParameters, args, TenantId.class);

            // Get the appropriate agent based on service name and tenant ID
            TelemetryAgent agent;
            if (serviceName != null && tenantId != null && agentProducer != null) {
                agent = agentProducer.getAgent(serviceName, tenantId);
                logger.debug("Using agent from producer for service: {}, tenant: {}", serviceName, tenantId);
            } else {
                agent = telemetryAgent;
                logger.debug("Using provided agent (no service context or agent producer)");
            }

            String spanName = traceAnnotation.spanName();
            Span span = agent.startSpan(spanName, traceAnnotation.spanKind(),
                    serviceName, tenantId, query);

            Object result;
            try (Scope scope = Context.current().with(span).makeCurrent()) {
                result = method.invoke(target, args);

                // Add operation-specific attributes based on the method
                if (method.getName().equals("generateEmbedding")) {
                    LLMOperationContext context = LLMOperationContext.builder()
                            .query(query)
                            .endpoint("embedding-service")
                            .build();
                    agent.addAttributes(span, context, (EmbeddingResponse) result, OperationType.EMBEDDING);
                } else if (method.getName().equals("search")) {
                    SearchOperationContext context = SearchOperationContext.builder()
                            .searchSystem(config.getSearchSystem())
                            .query(query)
                            .build();
                    agent.addAttributes(span, context, (SearchResponse) result, OperationType.SEARCH);
                }
            } catch (Exception e) {
                logger.error("Error during method invocation", e);
                agent.endSpan(span, e);
                throw e;
            } finally {
                agent.endSpan(span, null);
            }

            return result;
        }

        /**
         * Extracts a parameter value that is annotated with the specified annotation type.
         *
         * @param methodParameters The method parameter definitions
         * @param parameterValues  The parameter values
         * @param annotationType   The annotation type to look for
         * @return The parameter value, or null if not found
         */
        @SuppressWarnings("unchecked")
        private <T> T extractAnnotatedParameter(Parameter[] methodParameters, Object[] parameterValues,
                                                Class<? extends Annotation> annotationType) {
            if (methodParameters == null || parameterValues == null) {
                return null;
            }

            for (int i = 0; i < methodParameters.length && i < parameterValues.length; i++) {
                if (methodParameters[i].isAnnotationPresent(annotationType)) {
                    return (T) parameterValues[i];
                }
            }

            return null;
        }
    }
} 
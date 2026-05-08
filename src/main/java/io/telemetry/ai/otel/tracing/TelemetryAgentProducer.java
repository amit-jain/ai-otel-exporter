package io.telemetry.ai.otel.tracing;

import io.telemetry.ai.otel.extractor.TypedAttributeExtractor;
import io.telemetry.ai.otel.model.OperationType;
import io.telemetry.ai.otel.model.context.OperationContext;
import io.telemetry.ai.otel.system.TelemetrySystem;
import io.telemetry.ai.otel.system.TelemetrySystemFactory;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_SERVICE_NAME;
import static io.telemetry.ai.otel.config.TelemetryConfigConstants.DEFAULT_TENANT_ID;

/**
 * Producer class for managing TelemetryAgent instances.
 * Provides centralized management of telemetry agents for different services
 * and tenants, ensuring proper resource management and agent reuse.
 *
 * <p>This class supports:
 * <ul>
 *   <li>Default agent creation for standard configurations</li>
 *   <li>Tenant-specific agent creation and management</li>
 *   <li>Agent caching and reuse for efficiency</li>
 * </ul>
 */
@ApplicationScoped
public class TelemetryAgentProducer {
    private static final Logger logger = LoggerFactory.getLogger(TelemetryAgentProducer.class);
    private final ConcurrentHashMap<String, TelemetryAgent> agents = new ConcurrentHashMap<>();
    private TelemetryAgent defaultAgent;

    // Store registered extractors to apply to new agents
    private final Map<OperationType, Map<Class<?>, TypedAttributeExtractor<?, ?>>> registeredExtractors = new HashMap<>();

    /**
     * Creates a new TelemetryAgentProducer.
     * This class is typically used as a CDI producer and injected where needed.
     */
    public TelemetryAgentProducer() {}

    /**
     * Produces a default TelemetryAgent instance.
     * Creates a singleton agent with default service and tenant configuration
     * if one doesn't already exist.
     *
     * @return The default TelemetryAgent instance
     */
    @ApplicationScoped
    public TelemetryAgent produceDefaultAgent() {
        if (defaultAgent == null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating default TelemetryAgent for service: {} and tenant: {}",
                        DEFAULT_SERVICE_NAME, DEFAULT_TENANT_ID);
            }
            TelemetrySystem config = TelemetrySystemFactory.getConfiguration(
                    DEFAULT_SERVICE_NAME, DEFAULT_TENANT_ID);
            defaultAgent = new TelemetryAgent(config.getTracer());

            // Apply all registered extractors to the default agent
            applyRegisteredExtractors(defaultAgent);

            if (logger.isDebugEnabled()) {
                logger.debug("Created default TelemetryAgent");
                logger.debug("Created DEFAULT agent with identity: {}", System.identityHashCode(defaultAgent));
            }
        } else if (logger.isDebugEnabled()) {
            logger.debug("Returning existing DEFAULT agent with identity: {}", System.identityHashCode(defaultAgent));
        }
        return defaultAgent;
    }

    /**
     * Gets or creates a TelemetryAgent for a specific service and tenant.
     * Manages agent lifecycle and ensures proper resource reuse.
     *
     * @param serviceName The identifier of the service requesting the agent
     * @param tenantId  The tenant identifier for multi-tenancy support
     * @return A TelemetryAgent configured for the specified service and tenant
     */
    public TelemetryAgent getAgent(String serviceName, String tenantId) {
        String key = String.format("%s/%s", serviceName, tenantId);

        // Check if agent already exists before computing
        TelemetryAgent existingAgent = agents.get(key);
        if (existingAgent != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Returning existing TelemetryAgent for service: {} and tenant: {}", serviceName, tenantId);
                logger.debug("Returning EXISTING agent with identity: {} for key: {}",
                        System.identityHashCode(existingAgent), key);
            }
            return existingAgent;
        }

        // Create new agent if one doesn't exist
        if (logger.isDebugEnabled()) {
            logger.debug("Creating new TelemetryAgent for service: {} and tenant: {}", serviceName, tenantId);
        }
        TelemetrySystem config = TelemetrySystemFactory.getConfiguration(serviceName, tenantId);
        TelemetryAgent newAgent = new TelemetryAgent(config.getTracer());

        // Apply all registered extractors to the new agent
        applyRegisteredExtractors(newAgent);

        // Store the new agent
        agents.put(key, newAgent);

        if (logger.isDebugEnabled()) {
            logger.debug("Created and stored new TelemetryAgent for service: {} and tenant: {}", serviceName, tenantId);
            logger.debug("Created NEW agent with identity: {} for key: {}",
                    System.identityHashCode(newAgent), key);
        }

        return newAgent;
    }

    /**
     * Registers a typed extractor with all existing agents and stores it for future agents.
     * This ensures that all agents have the same extractors registered.
     *
     * @param type          The operation type
     * @param responseClass The response class
     * @param extractor     The extractor to register
     * @param <T>           The type of response
     * @param <C>           The type of operation context
     */
    public <T, C extends OperationContext> void registerTypedExtractorWithAllAgents(
            OperationType type,
            Class<T> responseClass,
            TypedAttributeExtractor<T, C> extractor) {

        if (logger.isDebugEnabled()) {
            logger.debug("Registering extractor for type {} and class {} with all agents", type, responseClass.getName());
        }

        // Store the extractor for future agents
        registeredExtractors.computeIfAbsent(type, k -> new HashMap<>())
                .put(responseClass, extractor);

        // Register with default agent only if it already exists
        if (defaultAgent != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("Registering extractor with existing default agent (identity: {})", System.identityHashCode(defaultAgent));
            }
            defaultAgent.registerTypedExtractor(type, responseClass, extractor);
            if (logger.isDebugEnabled()) {
                logger.debug("Registered extractor with default agent");
            }
        }

        // Register with all existing agents
        for (TelemetryAgent agent : agents.values()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Registering extractor with existing agent (identity: {})", System.identityHashCode(agent));
            }
            agent.registerTypedExtractor(type, responseClass, extractor);
            if (logger.isDebugEnabled()) {
                logger.debug("Registered extractor with existing agent");
            }
        }

        // Log the registration status
        if (logger.isDebugEnabled()) {
            logger.debug("Completed registration of extractor for type {} and class {}", type, responseClass.getName());
            logger.debug("Registered extractors: {}", registeredExtractors);
        }
    }

    /**
     * Applies all registered extractors to a new agent.
     *
     * @param agent The agent to apply extractors to
     */
    @SuppressWarnings("unchecked")
    private void applyRegisteredExtractors(TelemetryAgent agent) {
        for (Map.Entry<OperationType, Map<Class<?>, TypedAttributeExtractor<?, ?>>> entry : registeredExtractors.entrySet()) {
            OperationType type = entry.getKey();
            Map<Class<?>, TypedAttributeExtractor<?, ?>> extractors = entry.getValue();

            for (Map.Entry<Class<?>, TypedAttributeExtractor<?, ?>> extractorEntry : extractors.entrySet()) {
                Class<?> responseClass = extractorEntry.getKey();
                TypedAttributeExtractor<?, ?> extractor = extractorEntry.getValue();

                if (logger.isDebugEnabled()) {
                    logger.debug("Applying registered extractor for type {} and class {} to new agent",
                            type, responseClass.getName());
                }

                // Use raw types with suppressed warnings to handle the type casting
                agent.registerTypedExtractor(type, (Class) responseClass, extractor);
            }
        }
    }
} 
package io.telemetry.ai.otel.system;

import io.telemetry.ai.otel.tracing.TelemetryAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory class for managing OpenTelemetry configurations and agents.
 * Provides centralized management of telemetry configurations for different services
 * and tenants, ensuring proper resource management and configuration reuse.
 */
public final class TelemetrySystemFactory {
    private static final Logger logger = LoggerFactory.getLogger(TelemetrySystemFactory.class);
    private static final ConcurrentHashMap<String, TelemetrySystem> configurations = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation as this is a utility class.
     */
    private TelemetrySystemFactory() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets or creates a TelemetryConfiguration for the specified service and application.
     * If a configuration already exists for the given combination, it is reused.
     *
     * @param serviceName The name of the service requesting the configuration
     * @param appName     The name of the application or component
     * @return A TelemetryConfiguration instance for the specified service/app combination
     */
    public static TelemetrySystem getConfiguration(String serviceName, String appName) {
        String projectPath = String.format("%s/%s", serviceName, appName);
        return configurations.computeIfAbsent(projectPath, path -> {
            if (logger.isDebugEnabled()) {
                logger.debug("Creating new TelemetryConfiguration for service/app: {}", path);
            }
            return new TelemetrySystem(serviceName, appName);
        });
    }

    /**
     * Gets or creates a TelemetryConfiguration for the specified service and tenant.
     * Uses a default configuration if no tenant ID is provided.
     *
     * @param serviceName The name of the service requesting the configuration
     * @param tenantId    The tenant identifier for multi-tenancy support
     * @return A TelemetryConfiguration instance for the specified service/tenant combination
     */
    public static TelemetrySystem getConfigurationForTenant(String serviceName, String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            logger.warn("No tenant ID provided, using default configuration");
            return getConfiguration(serviceName, "default");
        }
        return getConfiguration(serviceName, tenantId);
    }

    /**
     * Creates a new TelemetryAgent instance for the specified service and tenant.
     * The agent is configured using the appropriate TelemetryConfiguration.
     *
     * @param serviceName The name of the service requesting the agent
     * @param tenantId    The tenant identifier for multi-tenancy support
     * @return A new TelemetryAgent instance configured for the service/tenant
     */
    public static TelemetryAgent createAgent(String serviceName, String tenantId) {
        TelemetrySystem config = getConfigurationForTenant(serviceName, tenantId);
        return new TelemetryAgent(config.getTracer());
    }

    /**
     * Shuts down and removes the configuration for a specific service/app combination.
     * This ensures proper cleanup of resources when they are no longer needed.
     *
     * @param serviceName The name of the service to shut down
     * @param appName     The name of the application or component
     */
    public static void shutdown(String serviceName, String appName) {
        String projectPath = String.format("%s/%s", serviceName, appName);
        TelemetrySystem config = configurations.remove(projectPath);
        if (config != null) {
            config.shutdown();
        }
    }

    /**
     * Shuts down and removes all configurations associated with a specific service.
     * This is useful when decommissioning an entire service and its components.
     *
     * @param serviceName The name of the service to shut down completely
     */
    public static void shutdownService(String serviceName) {
        configurations.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(serviceName + "/")) {
                entry.getValue().shutdown();
                return true;
            }
            return false;
        });
    }

    /**
     * Shuts down and removes all telemetry configurations.
     * This should be called during application shutdown to ensure proper cleanup.
     */
    public static void shutdownAll() {
        configurations.values().forEach(TelemetrySystem::shutdown);
        configurations.clear();
    }
} 
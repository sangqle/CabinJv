package com.cabin.express.server;

import com.cabin.express.logger.CabinLogger;
import com.cabin.express.logger.LoggerConfig;
import com.cabin.express.middleware.LoggingMiddleware;
import com.cabin.express.profiler.ServerProfiler;

import java.time.Duration;

/**
 * A builder class for creating a CabinServer instance
 *
 * @author Sang Le
 * @version 1.0.0
 * @since 2024-12-24
 * <p>
 * Example usage:
 * <pre>
 *     CabinServer server = new ServerBuilder()
 *     .setPort(8080)
 *     .setMaxPoolSize(20)
 *     .build();
 *     server.start();
 */

public class ServerBuilder {
    private int port = 8080;
    private int defaultPoolSize = 20;
    private int maxPoolSize = 200;
    private int maxQueueCapacity = 2000;
    private long timeout = 2000;
    private long idleTimeoutMiles = 10 * 1000;

    // Add profiler settings
    private boolean enableProfiler = false;
    private Duration profilerSamplingInterval = Duration.ofSeconds(10);
    private boolean enableProfilerDashboard = false;

    private LoggerConfig loggerConfig;
    private boolean enableRequestLogging = false;
    
    public ServerBuilder() {
        // Existing initialization
        this.loggerConfig = new LoggerConfig();
    }
    
    public ServerBuilder setLogDirectory(String directory) {
        this.loggerConfig.setLogDirectory(directory);
        return this;
    }
    
    public ServerBuilder setLogLevel(LoggerConfig.LogLevel level) {
        this.loggerConfig.setRootLogLevel(level);
        return this;
    }
    
    public ServerBuilder enableRequestLogging(boolean enabled) {
        this.enableRequestLogging = enabled;
        return this;
    }
    
    public ServerBuilder configureLogger(java.util.function.Consumer<LoggerConfig> configurer) {
        configurer.accept(this.loggerConfig);
        return this;
    }


    /**
     * Set the port number
     *
     * @param port the port number
     * @return the server builder
     */
    public ServerBuilder setPort(int port) {
        if (port < 1 || port > 65535) {
            return this;
        }
        this.port = port;
        return this;
    }

    /**
     * Set the default number of threads in the thread pool
     *
     * @param defaultPoolSize the default number of threads
     * @return the server builder
     */
    public ServerBuilder setDefaultPoolSize(int defaultPoolSize) {
        if (defaultPoolSize < 1) {
            return this;
        }
        this.defaultPoolSize = defaultPoolSize;
        return this;
    }

    /**
     * Set the maximum number of threads in the thread pool
     *
     * @param maxPoolSize the maximum number of threads
     * @return the server builder
     */
    public ServerBuilder setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    /**
     * Set the maximum queue capacity of the thread pool
     *
     * @param maxQueueCapacity the maximum queue capacity
     * @return the server builder
     */
    public ServerBuilder setMaxQueueCapacity(int maxQueueCapacity) {
        this.maxQueueCapacity = maxQueueCapacity;
        return this;
    }

    /**
     * Set the timeout for the server
     *
     * @param timeout the timeout in milliseconds
     * @return the server builder
     */
    public ServerBuilder setTimeout(long timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Set the idle timeout for the server
     *
     * @param idleTimeoutMiles the idle timeout in seconds
     * @return the server builder
     */
    public ServerBuilder setIdleTimeoutMiles(long idleTimeoutMiles) {
        this.idleTimeoutMiles = idleTimeoutMiles;
        return this;
    }

    /**
     * Build the CabinServer instance
     *
     * @return the CabinServer instance
     */
    public CabinServer build() {
        // Initialize logger with config
        CabinLogger.initialize(loggerConfig);
        
        // Existing build logic
        CabinServer cabinServer = new CabinServer(
                port,
                defaultPoolSize,
                maxPoolSize,
                maxQueueCapacity,
                timeout,
                enableProfiler,
                enableProfilerDashboard
        );
        
        // Add logging middleware if enabled
        if (enableRequestLogging) {
            cabinServer.use(new LoggingMiddleware());
        }

        // Config profiler settings
        ServerProfiler.INSTANCE.setEnabled(enableProfiler);

        if (profilerSamplingInterval != null) {
            ServerProfiler.INSTANCE.withSamplingInterval(profilerSamplingInterval);
        }
        
        return cabinServer;
    }

    /**
     * Enable or disable the profiler dashboard web UI
     *
     * @param enable true to enable dashboard, false to disable
     * @return this builder for method chaining
     */
    public ServerBuilder enableProfilerDashboard(boolean enable) {
        this.enableProfilerDashboard = enable;
        return this;
    }

    /**
     * Enable or disable the server profiler
     *
     * @param enable true to enable profiler, false to disable
     * @return this builder for method chaining
     */
    public ServerBuilder enableProfiler(boolean enable) {
        this.enableProfiler = enable;
        return this;
    }

    /**
     * Set the sampling interval for the profiler
     *
     * @param interval the duration between metrics collections
     * @return this builder for method chaining
     */
    public ServerBuilder setProfilerSamplingInterval(Duration interval) {
        this.profilerSamplingInterval = interval;
        return this;
    }
}
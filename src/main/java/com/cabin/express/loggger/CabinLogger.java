package com.cabin.express.loggger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central logging utility for the CabinJV framework.
 * Provides a convenient interface for logging with configurable settings.
 */
public class CabinLogger {
    private static final String DEFAULT_LOGGER_NAME = "CabinJV";
    private static Logger logger = LoggerFactory.getLogger(DEFAULT_LOGGER_NAME);
    private static LoggerConfig config = new LoggerConfig();
    private static boolean initialized = false;

    /**
     * Initialize the logger with the default configuration
     */
    public static synchronized void initialize() {
        if (!initialized) {
            LoggerConfigurer.configure(config);
            initialized = true;
        }
    }

    /**
     * Initialize the logger with a custom configuration
     *
     * @param customConfig The configuration to use
     */
    public static synchronized void initialize(LoggerConfig customConfig) {
        config = customConfig;
        LoggerConfigurer.configure(config);
        initialized = true;
    }

    /**
     * Get the current logger configuration
     *
     * @return The current configuration
     */
    public static LoggerConfig getConfig() {
        return config;
    }

    /**
     * Set the logger name (e.g., for a specific component)
     *
     * @param loggerName The name to use
     */
    public static void setLoggerName(String loggerName) {
        logger = LoggerFactory.getLogger(loggerName);
    }

    /**
     * Get the underlying SLF4J logger
     *
     * @return The Logger instance
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * Create a new logger with the specified name
     *
     * @param name The logger name
     * @return A new CabinLoggerInstance
     */
    public static LoggerInstance getLogger(String name) {
        return new LoggerInstance(name);
    }

    /**
     * Create a new logger for the specified class
     *
     * @param clazz The class
     * @return A new CabinLoggerInstance
     */
    public static LoggerInstance getLogger(Class<?> clazz) {
        return new LoggerInstance(clazz.getName());
    }

    /**
     * Log an info message
     *
     * @param msg The message to log
     */
    public static void info(String msg) {
        ensureInitialized();
        logger.info(msg);
    }

    /**
     * Log a debug message
     *
     * @param msg The message to log
     */
    public static void debug(String msg) {
        ensureInitialized();
        logger.debug(msg);
    }

    /**
     * Log a warning message
     *
     * @param msg The message to log
     */
    public static void warn(String msg) {
        ensureInitialized();
        logger.warn(msg);
    }

    /**
     * Log an error message with exception
     *
     * @param msg The message to log
     * @param e The exception
     */
    public static void error(String msg, Throwable e) {
        ensureInitialized();
        logger.error(msg, e);
    }

    /**
     * Log an error message
     *
     * @param msg The message to log
     */
    public static void error(String msg) {
        ensureInitialized();
        logger.error(msg);
    }

    /**
     * Log a trace message
     *
     * @param msg The message to log
     */
    public static void trace(String msg) {
        ensureInitialized();
        logger.trace(msg);
    }

    /**
     * Check if info logging is enabled
     *
     * @return true if enabled
     */
    public static boolean isInfoEnabled() {
        ensureInitialized();
        return logger.isInfoEnabled();
    }

    /**
     * Check if debug logging is enabled
     *
     * @return true if enabled
     */
    public static boolean isDebugEnabled() {
        ensureInitialized();
        return logger.isDebugEnabled();
    }

    /**
     * Check if trace logging is enabled
     *
     * @return true if enabled
     */
    public static boolean isTraceEnabled() {
        ensureInitialized();
        return logger.isTraceEnabled();
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    /**
     * Instance-based logger for component-specific logging
     */
    public static class LoggerInstance {
        private final Logger instanceLogger;

        private LoggerInstance(String name) {
            this.instanceLogger = LoggerFactory.getLogger(name);
        }

        public void info(String msg) {
            instanceLogger.info(msg);
        }

        public void debug(String msg) {
            instanceLogger.debug(msg);
        }

        public void warn(String msg) {
            instanceLogger.warn(msg);
        }

        public void error(String msg, Throwable e) {
            instanceLogger.error(msg, e);
        }

        public void error(String msg) {
            instanceLogger.error(msg);
        }

        public void trace(String msg) {
            instanceLogger.trace(msg);
        }

        public boolean isInfoEnabled() {
            return instanceLogger.isInfoEnabled();
        }

        public boolean isDebugEnabled() {
            return instanceLogger.isDebugEnabled();
        }

        public boolean isTraceEnabled() {
            return instanceLogger.isTraceEnabled();
        }
    }
}
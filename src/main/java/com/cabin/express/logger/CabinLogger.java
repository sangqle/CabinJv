package com.cabin.express.logger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

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
     * @return A new LoggerInstance
     */
    public static LoggerInstance getLogger(String name) {
        return new LoggerInstance(name);
    }

    /**
     * Create a new logger for the specified class
     *
     * @param clazz The class
     * @return A new LoggerInstance
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
        addCallerInfo();
        logger.info(msg);
        MDC.clear();
    }

    /**
     * Log a debug message
     *
     * @param msg The message to log
     */
    public static void debug(String msg) {
        ensureInitialized();
        addCallerInfo();
        logger.debug(msg);
        MDC.clear();
    }

    /**
     * Log a warning message
     *
     * @param msg The message to log
     */
    public static void warn(String msg) {
        ensureInitialized();
        addCallerInfo();
        logger.warn(msg);
        MDC.clear();
    }

    /**
     * Log an error message with exception
     *
     * @param msg The message to log
     * @param e   The exception
     */
    public static void error(String msg, Throwable e) {
        ensureInitialized();
        addCallerInfo();
        logger.error(msg, e);
        MDC.clear();
    }

    /**
     * Log an error message
     *
     * @param msg The message to log
     */
    public static void error(String msg) {
        ensureInitialized();
        addCallerInfo();
        logger.error(msg);
        MDC.clear();
    }

    /**
     * Log a trace message
     *
     * @param msg The message to log
     */
    public static void trace(String msg) {
        ensureInitialized();
        addCallerInfo();
        logger.trace(msg);
        MDC.clear();
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
     * Add caller information to the MDC
     */
    private static void addCallerInfo() {
        StackTraceElement caller = getCaller();
        // Get the file name and remove the ".java" extension
        String fileName = caller.getFileName();
        if (fileName != null && fileName.endsWith(".java")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        MDC.put("file", fileName);
        MDC.put("line", String.valueOf(caller.getLineNumber()));
        MDC.put("method", caller.getMethodName());
        MDC.put("class", caller.getClassName());
    }

    /**
     * Get the caller information from the stack trace
     */
    private static StackTraceElement getCaller() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Skip getStackTrace, getCaller, addCallerInfo, and the logging method
        for (int i = 4; i < stackTrace.length; i++) {
            StackTraceElement element = stackTrace[i];
            if (!element.getClassName().equals(CabinLogger.class.getName())) {
                return element;
            }
        }
        return stackTrace[4]; // Default if we can't find it
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
            addCallerInfoForInstance();
            instanceLogger.info(msg);
            MDC.clear();
        }

        public void debug(String msg) {
            addCallerInfoForInstance();
            instanceLogger.debug(msg);
            MDC.clear();
        }

        public void warn(String msg) {
            addCallerInfoForInstance();
            instanceLogger.warn(msg);
            MDC.clear();
        }

        public void error(String msg, Throwable e) {
            addCallerInfoForInstance();
            instanceLogger.error(msg, e);
            MDC.clear();
        }

        public void error(String msg) {
            addCallerInfoForInstance();
            instanceLogger.error(msg);
            MDC.clear();
        }

        public void trace(String msg) {
            addCallerInfoForInstance();
            instanceLogger.trace(msg);
            MDC.clear();
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

        /**
         * Add caller information to the MDC for instance loggers
         */
        private void addCallerInfoForInstance() {
            StackTraceElement caller = getCallerForInstance();
            // Get the file name and remove the .java extension
            String fileName = caller.getFileName();
            if (fileName != null && fileName.endsWith(".java")) {
                fileName = fileName.substring(0, fileName.length() - 5); // Remove ".java"
            }

            MDC.put("file", fileName);
            MDC.put("line", String.valueOf(caller.getLineNumber()));
            MDC.put("method", caller.getMethodName());
            MDC.put("class", caller.getClassName());
        }

        /**
         * Get the caller information from the stack trace for instance loggers
         */
        private StackTraceElement getCallerForInstance() {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            // Skip getStackTrace, getCallerForInstance, addCallerInfoForInstance, and the logging method
            for (int i = 4; i < stackTrace.length; i++) {
                StackTraceElement element = stackTrace[i];
                if (!element.getClassName().equals(CabinLogger.LoggerInstance.class.getName())) {
                    return element;
                }
            }
            return stackTrace[4]; // Default if we can't find it
        }
    }
}
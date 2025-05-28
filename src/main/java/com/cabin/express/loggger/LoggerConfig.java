package com.cabin.express.loggger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for the CabinLogger system.
 * Allows customization of log directory, file names, and log levels.
 */
public class LoggerConfig {
    private Path logDirectory;
    private String logFileNamePattern;
    private int maxHistoryDays;
    private Map<String, LogLevel> loggerLevels;
    private LogLevel rootLogLevel;
    private boolean enableConsoleLogging;
    private boolean enableFileLogging;
    private String logPattern;

    /**
     * Enum representing log levels
     */
    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, OFF
    }

    /**
     * Creates a new LoggerConfig with default settings
     */
    public LoggerConfig() {
        // Default settings
        this.logDirectory = Paths.get("logs");
        this.logFileNamePattern = "cabin-framework-%d{yyyy-MM-dd}.log";
        this.maxHistoryDays = 30;
        this.loggerLevels = new HashMap<>();
        this.rootLogLevel = LogLevel.INFO;
        this.enableConsoleLogging = true;
        this.enableFileLogging = true;
        this.logPattern = "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n";
    }

    /**
     * Set the directory where log files will be stored
     *
     * @param logDirectory The log directory path
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig setLogDirectory(String logDirectory) {
        this.logDirectory = Paths.get(logDirectory);
        return this;
    }

    /**
     * Set the log file name pattern for rolling files
     *
     * @param logFileNamePattern The pattern (supports date patterns like %d{yyyy-MM-dd})
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig setLogFileNamePattern(String logFileNamePattern) {
        this.logFileNamePattern = logFileNamePattern;
        return this;
    }

    /**
     * Set the maximum number of days to keep log history
     *
     * @param maxHistoryDays Number of days
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig setMaxHistoryDays(int maxHistoryDays) {
        this.maxHistoryDays = maxHistoryDays;
        return this;
    }

    /**
     * Set the log level for a specific logger
     *
     * @param loggerName The logger name (typically package name)
     * @param level The log level
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig setLoggerLevel(String loggerName, LogLevel level) {
        this.loggerLevels.put(loggerName, level);
        return this;
    }

    /**
     * Set the root log level
     *
     * @param level The log level
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig setRootLogLevel(LogLevel level) {
        this.rootLogLevel = level;
        return this;
    }

    /**
     * Enable or disable console logging
     *
     * @param enable True to enable, false to disable
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig enableConsoleLogging(boolean enable) {
        this.enableConsoleLogging = enable;
        return this;
    }

    /**
     * Enable or disable file logging
     *
     * @param enable True to enable, false to disable
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig enableFileLogging(boolean enable) {
        this.enableFileLogging = enable;
        return this;
    }

    /**
     * Set the log message pattern
     *
     * @param pattern The pattern string
     * @return This LoggerConfig instance for chaining
     */
    public LoggerConfig setLogPattern(String pattern) {
        this.logPattern = pattern;
        return this;
    }

    // Getters

    public Path getLogDirectory() {
        return logDirectory;
    }

    public String getLogFileNamePattern() {
        return logFileNamePattern;
    }

    public int getMaxHistoryDays() {
        return maxHistoryDays;
    }

    public Map<String, LogLevel> getLoggerLevels() {
        return loggerLevels;
    }

    public LogLevel getRootLogLevel() {
        return rootLogLevel;
    }

    public boolean isConsoleLoggingEnabled() {
        return enableConsoleLogging;
    }

    public boolean isFileLoggingEnabled() {
        return enableFileLogging;
    }

    public String getLogPattern() {
        return logPattern;
    }

    /**
     * Convert LogLevel to SLF4J level string
     */
    public String toSlf4jLevel(LogLevel level) {
        return level.toString();
    }
}
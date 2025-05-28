package com.cabin.express.loggger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configures the underlying logging system (Logback) based on LoggerConfig settings
 */
public class LoggerConfigurer {

    /**
     * Configure the logging system with the provided configuration
     *
     * @param config The logger configuration
     */
    public static void configure(LoggerConfig config) {
        // Get the logger context
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        // Create directory if it doesn't exist
        try {
            Files.createDirectories(config.getLogDirectory());
        } catch (Exception e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }

        // Configure appenders
        if (config.isConsoleLoggingEnabled()) {
            configureConsoleAppender(context, config);
        }

        if (config.isFileLoggingEnabled()) {
            configureFileAppender(context, config);
        }

        // Configure logger levels
        for (var entry : config.getLoggerLevels().entrySet()) {
            Logger logger = context.getLogger(entry.getKey());
            logger.setLevel(Level.toLevel(entry.getValue().toString()));
        }

        // Configure root logger
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.toLevel(config.getRootLogLevel().toString()));
    }

    private static void configureConsoleAppender(LoggerContext context, LoggerConfig config) {
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("STDOUT");

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(config.getLogPattern());
        encoder.setCharset(java.nio.charset.StandardCharsets.UTF_8); // Ensure proper encoding
        encoder.start();

        consoleAppender.setEncoder(encoder);
        consoleAppender.start();

        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(consoleAppender);
    }

    private static void configureFileAppender(LoggerContext context, LoggerConfig config) {
        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("FILE");

        // Set the file path
        Path logFile = config.getLogDirectory().resolve("cabin-framework.log");
        fileAppender.setFile(logFile.toString());

        // Configure rolling policy
        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(
                config.getLogDirectory().resolve(config.getLogFileNamePattern()).toString());
        rollingPolicy.setMaxHistory(config.getMaxHistoryDays());
        rollingPolicy.start();

        fileAppender.setRollingPolicy(rollingPolicy);

        // Configure encoder
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern(config.getLogPattern());
        encoder.setCharset(java.nio.charset.StandardCharsets.UTF_8); // Ensure proper encoding
        encoder.start();

        fileAppender.setEncoder(encoder);
        fileAppender.start();

        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(fileAppender);
    }
}
package com.cabin.express.loggger;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CabinLogger {
    private static final Logger logger = Logger.getLogger("CabinJ");
    private static boolean debugEnabled = false;

    static {
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setUseParentHandlers(false); // Disable default console handler
        setDebug(false); // Default to no debug logs
    }

    public static void setDebug(boolean enable) {
        debugEnabled = enable;
        logger.setLevel(enable ? Level.ALL : Level.INFO);
    }

    public static void info(String message) {
        logger.info(message);
    }

    public static void debug(String message) {
        if (debugEnabled) {
            logger.fine(message);
        }
    }

    public static void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}

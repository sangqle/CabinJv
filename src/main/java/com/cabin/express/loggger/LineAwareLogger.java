package com.cabin.express.loggger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.LocationAwareLogger;

/**
 * A wrapper around SLF4J's Logger that captures line number information.
 * This is used internally by CabinLogger to provide location information.
 */
class LineAwareLogger implements Logger {
    private final Logger logger;
    private final String fqcn;
    private final boolean locationAware;
    private final LocationAwareLogger locationAwareLogger;

    /**
     * Create a new LineAwareLogger
     *
     * @param logger The underlying logger
     * @param fqcn Fully qualified class name of the caller
     */
    public LineAwareLogger(Logger logger, String fqcn) {
        this.logger = logger;
        this.fqcn = fqcn;
        this.locationAware = logger instanceof LocationAwareLogger;
        this.locationAwareLogger = locationAware ? (LocationAwareLogger) logger : null;
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    // TRACE METHODS
    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return logger.isTraceEnabled(marker);
    }

    @Override
    public void trace(String msg) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.TRACE_INT, msg, null, null);
        } else {
            logger.trace(msg);
        }
    }

    @Override
    public void trace(String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.TRACE_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.trace(format, arg);
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.TRACE_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.trace(format, arg1, arg2);
        }
    }

    @Override
    public void trace(String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.TRACE_INT, formattedMessage, arguments, null);
        } else {
            logger.trace(format, arguments);
        }
    }

    @Override
    public void trace(String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.TRACE_INT, msg, null, t);
        } else {
            logger.trace(msg, t);
        }
    }

    @Override
    public void trace(Marker marker, String msg) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.TRACE_INT, msg, null, null);
        } else {
            logger.trace(marker, msg);
        }
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.TRACE_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.trace(marker, format, arg);
        }
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.TRACE_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.trace(marker, format, arg1, arg2);
        }
    }

    @Override
    public void trace(Marker marker, String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.TRACE_INT, formattedMessage, arguments, null);
        } else {
            logger.trace(marker, format, arguments);
        }
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.TRACE_INT, msg, null, t);
        } else {
            logger.trace(marker, msg, t);
        }
    }

    // DEBUG METHODS
    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return logger.isDebugEnabled(marker);
    }

    @Override
    public void debug(String msg) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.DEBUG_INT, msg, null, null);
        } else {
            logger.debug(msg);
        }
    }

    @Override
    public void debug(String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.DEBUG_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.debug(format, arg);
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.DEBUG_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.debug(format, arg1, arg2);
        }
    }

    @Override
    public void debug(String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.DEBUG_INT, formattedMessage, arguments, null);
        } else {
            logger.debug(format, arguments);
        }
    }

    @Override
    public void debug(String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.DEBUG_INT, msg, null, t);
        } else {
            logger.debug(msg, t);
        }
    }

    @Override
    public void debug(Marker marker, String msg) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.DEBUG_INT, msg, null, null);
        } else {
            logger.debug(marker, msg);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.DEBUG_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.debug(marker, format, arg);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.DEBUG_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.debug(marker, format, arg1, arg2);
        }
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.DEBUG_INT, formattedMessage, arguments, null);
        } else {
            logger.debug(marker, format, arguments);
        }
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.DEBUG_INT, msg, null, t);
        } else {
            logger.debug(marker, msg, t);
        }
    }

    // INFO METHODS
    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return logger.isInfoEnabled(marker);
    }

    @Override
    public void info(String msg) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.INFO_INT, msg, null, null);
        } else {
            logger.info(msg);
        }
    }

    @Override
    public void info(String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.INFO_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.info(format, arg);
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.INFO_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.info(format, arg1, arg2);
        }
    }

    @Override
    public void info(String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.INFO_INT, formattedMessage, arguments, null);
        } else {
            logger.info(format, arguments);
        }
    }

    @Override
    public void info(String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.INFO_INT, msg, null, t);
        } else {
            logger.info(msg, t);
        }
    }

    @Override
    public void info(Marker marker, String msg) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.INFO_INT, msg, null, null);
        } else {
            logger.info(marker, msg);
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.INFO_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.info(marker, format, arg);
        }
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.INFO_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.info(marker, format, arg1, arg2);
        }
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.INFO_INT, formattedMessage, arguments, null);
        } else {
            logger.info(marker, format, arguments);
        }
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.INFO_INT, msg, null, t);
        } else {
            logger.info(marker, msg, t);
        }
    }

    // WARN METHODS
    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return logger.isWarnEnabled(marker);
    }

    @Override
    public void warn(String msg) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.WARN_INT, msg, null, null);
        } else {
            logger.warn(msg);
        }
    }

    @Override
    public void warn(String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.WARN_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.warn(format, arg);
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.WARN_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.warn(format, arg1, arg2);
        }
    }

    @Override
    public void warn(String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.WARN_INT, formattedMessage, arguments, null);
        } else {
            logger.warn(format, arguments);
        }
    }

    @Override
    public void warn(String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.WARN_INT, msg, null, t);
        } else {
            logger.warn(msg, t);
        }
    }

    @Override
    public void warn(Marker marker, String msg) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.WARN_INT, msg, null, null);
        } else {
            logger.warn(marker, msg);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.WARN_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.warn(marker, format, arg);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.WARN_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.warn(marker, format, arg1, arg2);
        }
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.WARN_INT, formattedMessage, arguments, null);
        } else {
            logger.warn(marker, format, arguments);
        }
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.WARN_INT, msg, null, t);
        } else {
            logger.warn(marker, msg, t);
        }
    }

    // ERROR METHODS
    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return logger.isErrorEnabled(marker);
    }

    @Override
    public void error(String msg) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.ERROR_INT, msg, null, null);
        } else {
            logger.error(msg);
        }
    }

    @Override
    public void error(String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.ERROR_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.error(format, arg);
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.ERROR_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.error(format, arg1, arg2);
        }
    }

    @Override
    public void error(String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.ERROR_INT, formattedMessage, arguments, null);
        } else {
            logger.error(format, arguments);
        }
    }

    @Override
    public void error(String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(null, fqcn, LocationAwareLogger.ERROR_INT, msg, null, t);
        } else {
            logger.error(msg, t);
        }
    }

    @Override
    public void error(Marker marker, String msg) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.ERROR_INT, msg, null, null);
        } else {
            logger.error(marker, msg);
        }
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.ERROR_INT, formattedMessage, new Object[]{arg}, null);
        } else {
            logger.error(marker, format, arg);
        }
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.format(format, arg1, arg2).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.ERROR_INT, formattedMessage, new Object[]{arg1, arg2}, null);
        } else {
            logger.error(marker, format, arg1, arg2);
        }
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
        if (locationAware) {
            String formattedMessage = MessageFormatter.arrayFormat(format, arguments).getMessage();
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.ERROR_INT, formattedMessage, arguments, null);
        } else {
            logger.error(marker, format, arguments);
        }
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
        if (locationAware) {
            locationAwareLogger.log(marker, fqcn, LocationAwareLogger.ERROR_INT, msg, null, t);
        } else {
            logger.error(marker, msg, t);
        }
    }
}
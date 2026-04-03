package org.slf4j;

import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * Minimal SLF4J Logger interface shim for TeaVM.
 *
 * <p>This is a source-compatible replacement for the real SLF4J Logger interface.
 * TeaVM compiles this instead of the jar version, avoiding references to
 * SecurityManager, LinkedBlockingQueue, and ServiceLoader.
 */
public interface Logger {

    String ROOT_LOGGER_NAME = "ROOT";

    String getName();

    // --- Trace ---
    boolean isTraceEnabled();
    void trace(String msg);
    void trace(String format, Object arg);
    void trace(String format, Object arg1, Object arg2);
    void trace(String format, Object... arguments);
    void trace(String msg, Throwable t);
    boolean isTraceEnabled(Marker marker);
    void trace(Marker marker, String msg);
    void trace(Marker marker, String format, Object arg);
    void trace(Marker marker, String format, Object arg1, Object arg2);
    void trace(Marker marker, String format, Object... arguments);
    void trace(Marker marker, String msg, Throwable t);

    // --- Debug ---
    boolean isDebugEnabled();
    void debug(String msg);
    void debug(String format, Object arg);
    void debug(String format, Object arg1, Object arg2);
    void debug(String format, Object... arguments);
    void debug(String msg, Throwable t);
    boolean isDebugEnabled(Marker marker);
    void debug(Marker marker, String msg);
    void debug(Marker marker, String format, Object arg);
    void debug(Marker marker, String format, Object arg1, Object arg2);
    void debug(Marker marker, String format, Object... arguments);
    void debug(Marker marker, String msg, Throwable t);

    // --- Info ---
    boolean isInfoEnabled();
    void info(String msg);
    void info(String format, Object arg);
    void info(String format, Object arg1, Object arg2);
    void info(String format, Object... arguments);
    void info(String msg, Throwable t);
    boolean isInfoEnabled(Marker marker);
    void info(Marker marker, String msg);
    void info(Marker marker, String format, Object arg);
    void info(Marker marker, String format, Object arg1, Object arg2);
    void info(Marker marker, String format, Object... arguments);
    void info(Marker marker, String msg, Throwable t);

    // --- Warn ---
    boolean isWarnEnabled();
    void warn(String msg);
    void warn(String format, Object arg);
    void warn(String format, Object... arguments);
    void warn(String format, Object arg1, Object arg2);
    void warn(String msg, Throwable t);
    boolean isWarnEnabled(Marker marker);
    void warn(Marker marker, String msg);
    void warn(Marker marker, String format, Object arg);
    void warn(Marker marker, String format, Object arg1, Object arg2);
    void warn(Marker marker, String format, Object... arguments);
    void warn(Marker marker, String msg, Throwable t);

    // --- Error ---
    boolean isErrorEnabled();
    void error(String msg);
    void error(String format, Object arg);
    void error(String format, Object arg1, Object arg2);
    void error(String format, Object... arguments);
    void error(String msg, Throwable t);
    boolean isErrorEnabled(Marker marker);
    void error(Marker marker, String msg);
    void error(Marker marker, String format, Object arg);
    void error(Marker marker, String format, Object arg1, Object arg2);
    void error(Marker marker, String format, Object... arguments);
    void error(Marker marker, String msg, Throwable t);

    // --- Fluent API (default no-op) ---
    default LoggingEventBuilder makeLoggingEventBuilder(Level level) { return NOPLoggingEventBuilder.INSTANCE; }
    default LoggingEventBuilder atLevel(Level level) { return makeLoggingEventBuilder(level); }
    default boolean isEnabledForLevel(Level level) { return true; }
    default LoggingEventBuilder atTrace() { return makeLoggingEventBuilder(Level.TRACE); }
    default LoggingEventBuilder atDebug() { return makeLoggingEventBuilder(Level.DEBUG); }
    default LoggingEventBuilder atInfo() { return makeLoggingEventBuilder(Level.INFO); }
    default LoggingEventBuilder atWarn() { return makeLoggingEventBuilder(Level.WARN); }
    default LoggingEventBuilder atError() { return makeLoggingEventBuilder(Level.ERROR); }
}

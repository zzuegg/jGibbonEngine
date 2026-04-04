package org.slf4j.spi;

import org.slf4j.Marker;

/**
 * Minimal SLF4J LoggingEventBuilder shim for TeaVM.
 * Provides a no-op fluent API so default methods on Logger compile.
 */
public interface LoggingEventBuilder {
    LoggingEventBuilder setCause(Throwable cause);
    LoggingEventBuilder addMarker(Marker marker);
    LoggingEventBuilder addArgument(Object p);
    LoggingEventBuilder addKeyValue(String key, Object value);
    void log(String message);
    void log(String message, Object arg);
    void log(String message, Object arg1, Object arg2);
    void log(String message, Object... args);
}

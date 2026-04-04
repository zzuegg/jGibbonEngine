package org.slf4j;

import org.slf4j.spi.LoggingEventBuilder;

/**
 * No-op LoggingEventBuilder for the fluent API default methods.
 */
final class NOPLoggingEventBuilder implements LoggingEventBuilder {

    static final NOPLoggingEventBuilder INSTANCE = new NOPLoggingEventBuilder();

    private NOPLoggingEventBuilder() {}

    @Override public LoggingEventBuilder setCause(Throwable cause) { return this; }
    @Override public LoggingEventBuilder addMarker(Marker marker) { return this; }
    @Override public LoggingEventBuilder addArgument(Object p) { return this; }
    @Override public LoggingEventBuilder addKeyValue(String key, Object value) { return this; }
    @Override public void log(String message) {}
    @Override public void log(String message, Object arg) {}
    @Override public void log(String message, Object arg1, Object arg2) {}
    @Override public void log(String message, Object... args) {}
}

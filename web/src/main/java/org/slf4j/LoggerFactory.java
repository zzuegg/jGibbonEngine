package org.slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal SLF4J LoggerFactory replacement for TeaVM.
 *
 * <p>The real SLF4J LoggerFactory uses ServiceLoader, SecurityManager, and
 * LinkedBlockingQueue — none of which exist in TeaVM's classlib.
 * This replacement directly creates ConsoleLogger instances that output
 * to System.out/System.err (mapped to console.log/console.error by TeaVM).
 *
 * <p>Because the web module excludes the real slf4j-api jar, javac and TeaVM
 * both resolve org.slf4j.LoggerFactory to this class.
 */
public final class LoggerFactory {

    private static final Map<String, Logger> loggers = new HashMap<>();

    private LoggerFactory() {}

    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, ConsoleLogger::new);
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    public static ILoggerFactory getILoggerFactory() {
        return LoggerFactory::getLogger;
    }
}

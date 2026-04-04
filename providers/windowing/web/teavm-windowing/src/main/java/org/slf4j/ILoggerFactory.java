package org.slf4j;

/**
 * Minimal SLF4J ILoggerFactory shim for TeaVM.
 */
public interface ILoggerFactory {
    Logger getLogger(String name);
}

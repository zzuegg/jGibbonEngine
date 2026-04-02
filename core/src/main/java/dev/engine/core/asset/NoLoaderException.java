package dev.engine.core.asset;

public class NoLoaderException extends RuntimeException {
    public NoLoaderException(String path, Class<?> type) {
        super("No loader found for '" + path + "' producing " + type.getSimpleName());
    }
}

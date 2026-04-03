package dev.engine.graphics.window;

import dev.engine.core.property.PropertyKey;

public interface WindowHandle extends AutoCloseable {

    boolean isOpen();
    int width();
    int height();
    String title();
    void show();

    /** Returns the raw platform window handle (e.g., GLFW handle, SDL window pointer). */
    long nativeHandle();

    default <T> void set(PropertyKey<T> key, T value) {
        throw new UnsupportedOperationException("Property not supported: " + key.name());
    }

    default <T> T get(PropertyKey<T> key) {
        throw new UnsupportedOperationException("Property not supported: " + key.name());
    }

    @Override
    void close();
}

package dev.engine.graphics.window;

import dev.engine.core.property.PropertyKey;
import dev.engine.core.versioned.Reference;

public interface WindowHandle extends AutoCloseable {

    boolean isOpen();
    int width();
    int height();
    String title();
    void show();

    /** Returns the raw platform window handle (e.g., GLFW handle, SDL window pointer). */
    long nativeHandle();

    /**
     * Platform-specific surface information for WebGPU/Vulkan surface creation.
     * Each windowing toolkit provides the correct handles for its platform.
     */
    record SurfaceInfo(SurfaceType type, long display, long window) {
        public enum SurfaceType { WAYLAND, X11, WINDOWS, COCOA }
    }

    /**
     * Returns platform surface info for GPU surface creation.
     * Returns null if the toolkit doesn't support surface info.
     */
    default SurfaceInfo surfaceInfo() { return null; }

    /**
     * Versioned window size. Updates are detected via {@code ref.update()}.
     * Returns null if the provider does not support versioned tracking.
     */
    default Reference<int[]> sizeRef() { return null; }

    /**
     * Versioned focus state. Returns null if not supported.
     */
    default Reference<Boolean> focusedRef() { return null; }

    /** Swaps front/back buffers. Called by the render device at end of frame. */
    default void swapBuffers() {}

    default <T> void set(PropertyKey<WindowHandle, T> key, T value) {
        // Ignored by default — toolkits override for supported properties
    }

    default <T> T get(PropertyKey<WindowHandle, T> key) {
        return null;
    }

    @Override
    void close();
}

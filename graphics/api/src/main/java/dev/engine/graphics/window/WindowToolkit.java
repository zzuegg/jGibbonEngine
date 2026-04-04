package dev.engine.graphics.window;

import dev.engine.core.input.InputProvider;

public interface WindowToolkit extends AutoCloseable {

    WindowHandle createWindow(WindowDescriptor descriptor);
    void pollEvents();

    /**
     * Creates an input provider for the given window.
     * Each toolkit knows its platform (GLFW, SDL3, Canvas) and returns the appropriate provider.
     * Returns null if this toolkit does not support input.
     */
    default InputProvider createInputProvider(WindowHandle window) { return null; }

    @Override
    void close();
}

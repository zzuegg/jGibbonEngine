package dev.engine.graphics.window;

public interface WindowToolkit extends AutoCloseable {

    WindowHandle createWindow(WindowDescriptor descriptor);
    void pollEvents();

    @Override
    void close();
}

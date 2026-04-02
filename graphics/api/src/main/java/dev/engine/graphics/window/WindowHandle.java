package dev.engine.graphics.window;

public interface WindowHandle extends AutoCloseable {

    boolean isOpen();
    int width();
    int height();
    String title();

    @Override
    void close();
}

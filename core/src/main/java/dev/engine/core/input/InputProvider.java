package dev.engine.core.input;

public interface InputProvider {
    void initialize(InputSystem system);
    void poll();
    void close();
}

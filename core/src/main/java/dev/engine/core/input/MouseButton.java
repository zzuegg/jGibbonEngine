package dev.engine.core.input;

public interface MouseButton {
    String name();
    static MouseButton of(String name) { return new NamedButton(name); }

    MouseButton LEFT = of("LEFT");
    MouseButton RIGHT = of("RIGHT");
    MouseButton MIDDLE = of("MIDDLE");
}

record NamedButton(String name) implements MouseButton {}

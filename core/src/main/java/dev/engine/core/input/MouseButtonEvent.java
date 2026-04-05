package dev.engine.core.input;

public sealed interface MouseButtonEvent extends BooleanEvent, HasModifiers
        permits InputEvent.MousePressed, InputEvent.MouseReleased {
    MouseButton button();
    double x();
    double y();
    default BooleanSource source() { return button(); }
}

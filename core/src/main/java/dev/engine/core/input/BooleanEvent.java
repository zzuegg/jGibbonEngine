package dev.engine.core.input;

public sealed interface BooleanEvent extends DeviceEvent
        permits KeyEvent, MouseButtonEvent, GamepadButtonEvent {
    BooleanSource source();
    boolean pressed();
}

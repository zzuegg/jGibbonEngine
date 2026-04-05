package dev.engine.core.input;

public sealed interface AxisEvent extends DeviceEvent, HasModifiers
        permits InputEvent.CursorMoved, InputEvent.Scrolled, InputEvent.GamepadAxisMoved {
    AxisSource source();
    double x();
    double y();
}

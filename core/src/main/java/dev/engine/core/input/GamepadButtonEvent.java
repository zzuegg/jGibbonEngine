package dev.engine.core.input;

public sealed interface GamepadButtonEvent extends BooleanEvent
        permits InputEvent.GamepadPressed, InputEvent.GamepadReleased {
    GamepadButton button();
    default BooleanSource source() { return button(); }
}

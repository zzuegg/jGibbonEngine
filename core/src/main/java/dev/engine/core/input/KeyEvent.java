package dev.engine.core.input;

public sealed interface KeyEvent extends BooleanEvent, HasModifiers
        permits InputEvent.KeyPressed, InputEvent.KeyReleased, InputEvent.KeyRepeated {
    KeyCode keyCode();
    ScanCode scanCode();
    default BooleanSource source() { return keyCode(); }
}

package dev.engine.core.input;

import dev.engine.core.module.Time;

public sealed interface InputEvent permits DeviceEvent {

    Time time();

    // --- Key events ---

    record KeyPressed(Time time, DeviceId device, KeyCode keyCode,
                      ScanCode scanCode, Modifiers modifiers) implements KeyEvent {
        public boolean pressed() { return true; }
    }

    record KeyReleased(Time time, DeviceId device, KeyCode keyCode,
                       ScanCode scanCode, Modifiers modifiers) implements KeyEvent {
        public boolean pressed() { return false; }
    }

    record KeyRepeated(Time time, DeviceId device, KeyCode keyCode,
                       ScanCode scanCode, Modifiers modifiers) implements KeyEvent {
        public boolean pressed() { return true; }
    }

    // --- Mouse button events ---

    record MousePressed(Time time, DeviceId device, MouseButton button,
                        Modifiers modifiers, double x, double y) implements MouseButtonEvent {
        public boolean pressed() { return true; }
    }

    record MouseReleased(Time time, DeviceId device, MouseButton button,
                         Modifiers modifiers, double x, double y) implements MouseButtonEvent {
        public boolean pressed() { return false; }
    }

    // --- Gamepad button events ---

    record GamepadPressed(Time time, DeviceId device,
                          GamepadButton button) implements GamepadButtonEvent {
        public boolean pressed() { return true; }
    }

    record GamepadReleased(Time time, DeviceId device,
                           GamepadButton button) implements GamepadButtonEvent {
        public boolean pressed() { return false; }
    }

    // --- Axis events ---

    record CursorMoved(Time time, DeviceId device, Modifiers modifiers,
                       double x, double y) implements AxisEvent {
        public AxisSource source() { return MouseAxis.CURSOR; }
    }

    record Scrolled(Time time, DeviceId device, Modifiers modifiers,
                    double x, double y) implements AxisEvent {
        public AxisSource source() { return MouseAxis.SCROLL; }
    }

    record GamepadAxisMoved(Time time, DeviceId device, GamepadAxis axis,
                            double x, double y) implements AxisEvent {
        public AxisSource source() { return axis; }
        public Modifiers modifiers() { return new Modifiers(0); }
    }

    // --- Other device events ---

    record CharTyped(Time time, DeviceId device,
                     Codepoint codepoint) implements DeviceEvent {}

    record DeviceConnectionChanged(Time time, DeviceId device,
                                   boolean connected) implements DeviceEvent {}
}

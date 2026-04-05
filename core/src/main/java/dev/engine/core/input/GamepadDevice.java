package dev.engine.core.input;

import dev.engine.core.versioned.Reference;

public interface GamepadDevice extends InputDevice {
    void setDeadzone(GamepadAxis axis, float value);
    float deadzone(GamepadAxis axis);
    Reference<Float> deadzoneRef(GamepadAxis axis);
}

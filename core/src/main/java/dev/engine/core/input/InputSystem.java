package dev.engine.core.input;

import java.util.List;

public interface InputSystem {
    MouseDevice mouse(int index);
    KeyboardDevice keyboard(int index);
    GamepadDevice gamepad(int index);

    InputDevice device(DeviceId id);
    List<InputDevice> devices();
    List<InputDevice> devices(DeviceType type);

    void registerProvider(InputProvider provider);

    InputEventQueue queue();
}

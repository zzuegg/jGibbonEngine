package dev.engine.core.input;

import dev.engine.core.versioned.Reference;

public interface InputDevice {
    DeviceId id();
    String name();
    Reference<Boolean> connected();
}

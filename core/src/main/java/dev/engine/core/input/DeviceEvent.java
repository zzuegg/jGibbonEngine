package dev.engine.core.input;

public sealed interface DeviceEvent extends InputEvent
        permits BooleanEvent, AxisEvent, InputEvent.CharTyped, InputEvent.DeviceConnectionChanged {
    DeviceId device();
}

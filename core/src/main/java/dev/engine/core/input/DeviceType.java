package dev.engine.core.input;

public interface DeviceType {
    String name();

    static DeviceType of(String name) { return new NamedDeviceType(name); }

    DeviceType KEYBOARD = of("keyboard");
    DeviceType MOUSE = of("mouse");
    DeviceType GAMEPAD = of("gamepad");
    DeviceType TOUCH = of("touch");
}

record NamedDeviceType(String name) implements DeviceType {}

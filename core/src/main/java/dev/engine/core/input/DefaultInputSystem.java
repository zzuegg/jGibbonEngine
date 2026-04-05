package dev.engine.core.input;

import dev.engine.core.versioned.Reference;
import dev.engine.core.versioned.Versioned;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultInputSystem implements InputSystem {

    private final InputEventQueue eventQueue = new InputEventQueue();
    private final Map<DeviceId, InputDevice> devices = new ConcurrentHashMap<>();
    private final List<InputProvider> providers = new ArrayList<>();

    @Override
    public MouseDevice mouse(int index) {
        var id = new DeviceId(DeviceType.MOUSE, index);
        return (MouseDevice) devices.computeIfAbsent(id, DefaultMouseDevice::new);
    }

    @Override
    public KeyboardDevice keyboard(int index) {
        var id = new DeviceId(DeviceType.KEYBOARD, index);
        return (KeyboardDevice) devices.computeIfAbsent(id, DefaultKeyboardDevice::new);
    }

    @Override
    public GamepadDevice gamepad(int index) {
        var id = new DeviceId(DeviceType.GAMEPAD, index);
        return (GamepadDevice) devices.computeIfAbsent(id, DefaultGamepadDevice::new);
    }

    @Override
    public InputDevice device(DeviceId id) {
        return devices.computeIfAbsent(id, k -> {
            if (k.type() == DeviceType.MOUSE) return new DefaultMouseDevice(k);
            if (k.type() == DeviceType.KEYBOARD) return new DefaultKeyboardDevice(k);
            if (k.type() == DeviceType.GAMEPAD) return new DefaultGamepadDevice(k);
            return new DefaultInputDevice(k);
        });
    }

    @Override
    public List<InputDevice> devices() {
        return List.copyOf(devices.values());
    }

    @Override
    public List<InputDevice> devices(DeviceType type) {
        return devices.values().stream()
                .filter(d -> d.id().type().equals(type))
                .toList();
    }

    @Override
    public void registerProvider(InputProvider provider) {
        providers.add(provider);
        provider.initialize(this);
    }

    @Override
    public InputEventQueue queue() {
        return eventQueue;
    }

    // --- Default device implementations ---

    private static class DefaultInputDevice implements InputDevice {
        private final DeviceId id;
        private final Versioned<Boolean> connected;

        DefaultInputDevice(DeviceId id) {
            this.id = id;
            this.connected = new Versioned<>(false);
        }

        @Override public DeviceId id() { return id; }
        @Override public String name() { return id.type().name() + "_" + id.index(); }
        @Override public Reference<Boolean> connected() { return connected.createReference(); }
    }

    private static class DefaultMouseDevice extends DefaultInputDevice implements MouseDevice {
        private final Versioned<CursorMode> cursorMode = new Versioned<>(CursorMode.NORMAL);

        DefaultMouseDevice(DeviceId id) { super(id); }

        @Override public void setCursorMode(CursorMode mode) { cursorMode.set(mode); }
        @Override public CursorMode cursorMode() { return cursorMode.getValue(); }
        @Override public Reference<CursorMode> cursorModeRef() { return cursorMode.createReference(); }
    }

    private static class DefaultKeyboardDevice extends DefaultInputDevice implements KeyboardDevice {
        DefaultKeyboardDevice(DeviceId id) { super(id); }
    }

    private static class DefaultGamepadDevice extends DefaultInputDevice implements GamepadDevice {
        private final EnumMap<GamepadAxis, Versioned<Float>> deadzones = new EnumMap<>(GamepadAxis.class);

        DefaultGamepadDevice(DeviceId id) { super(id); }

        @Override
        public void setDeadzone(GamepadAxis axis, float value) {
            deadzoneVersioned(axis).set(value);
        }

        @Override
        public float deadzone(GamepadAxis axis) {
            return deadzoneVersioned(axis).getValue();
        }

        @Override
        public Reference<Float> deadzoneRef(GamepadAxis axis) {
            return deadzoneVersioned(axis).createReference();
        }

        private Versioned<Float> deadzoneVersioned(GamepadAxis axis) {
            return deadzones.computeIfAbsent(axis, k -> new Versioned<>(0f));
        }
    }
}

package dev.engine.core.input;

import dev.engine.core.versioned.Reference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSystemTest {

    @Nested
    class DeviceAccess {
        @Test void mouseDeviceByIndex() {
            var system = new DefaultInputSystem();
            var mouse = system.mouse(0);
            assertNotNull(mouse);
            assertEquals(new DeviceId(DeviceType.MOUSE, 0), mouse.id());
        }

        @Test void keyboardDeviceByIndex() {
            var system = new DefaultInputSystem();
            var kb = system.keyboard(0);
            assertNotNull(kb);
            assertEquals(new DeviceId(DeviceType.KEYBOARD, 0), kb.id());
        }

        @Test void gamepadDeviceByIndex() {
            var system = new DefaultInputSystem();
            var gp = system.gamepad(0);
            assertNotNull(gp);
            assertEquals(new DeviceId(DeviceType.GAMEPAD, 0), gp.id());
        }

        @Test void genericDeviceAccess() {
            var system = new DefaultInputSystem();
            var id = new DeviceId(DeviceType.MOUSE, 0);
            var device = system.device(id);
            assertNotNull(device);
            assertEquals(id, device.id());
        }

        @Test void queueIsAccessible() {
            var system = new DefaultInputSystem();
            assertNotNull(system.queue());
        }
    }

    @Nested
    class MouseDeviceProperties {
        @Test void cursorModeDefaultsToNormal() {
            var system = new DefaultInputSystem();
            var mouse = system.mouse(0);
            assertEquals(CursorMode.NORMAL, mouse.cursorMode());
        }

        @Test void setCursorModeUpdatesValue() {
            var system = new DefaultInputSystem();
            var mouse = system.mouse(0);
            mouse.setCursorMode(CursorMode.LOCKED);
            assertEquals(CursorMode.LOCKED, mouse.cursorMode());
        }

        @Test void cursorModeRefTracksChanges() {
            var system = new DefaultInputSystem();
            var mouse = system.mouse(0);
            var ref = mouse.cursorModeRef();
            ref.update(); // consume initial

            mouse.setCursorMode(CursorMode.HIDDEN);
            assertTrue(ref.update());
            assertEquals(CursorMode.HIDDEN, ref.getValue());
        }
    }

    @Nested
    class GamepadDeviceProperties {
        @Test void deadzoneDefaultsToZero() {
            var system = new DefaultInputSystem();
            var gp = system.gamepad(0);
            assertEquals(0f, gp.deadzone(GamepadAxis.LEFT_STICK));
        }

        @Test void setDeadzoneUpdatesValue() {
            var system = new DefaultInputSystem();
            var gp = system.gamepad(0);
            gp.setDeadzone(GamepadAxis.LEFT_STICK, 0.15f);
            assertEquals(0.15f, gp.deadzone(GamepadAxis.LEFT_STICK));
        }
    }

    @Nested
    class CommonDeviceProperties {
        @Test void deviceNameDefault() {
            var system = new DefaultInputSystem();
            var mouse = system.mouse(0);
            assertNotNull(mouse.name());
        }

        @Test void connectedRefTracksState() {
            var system = new DefaultInputSystem();
            var gp = system.gamepad(0);
            var ref = gp.connected();
            // Default is disconnected for gamepads
            ref.update();
            assertFalse(ref.getValue());
        }
    }
}

package dev.engine.core.input;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputSourceTest {

    @Nested
    class KeyCodeEnum {
        @Test void commonKeysExist() {
            assertNotNull(KeyCode.A);
            assertNotNull(KeyCode.SPACE);
            assertNotNull(KeyCode.ESCAPE);
            assertNotNull(KeyCode.LEFT_SHIFT);
            assertNotNull(KeyCode.F1);
            assertNotNull(KeyCode.UNKNOWN);
        }

        @Test void implementsBooleanSource() {
            assertInstanceOf(BooleanSource.class, KeyCode.A);
            assertInstanceOf(InputSource.class, KeyCode.A);
        }

        @Test void nameReturnsEnumName() {
            assertEquals("A", KeyCode.A.name());
            assertEquals("SPACE", KeyCode.SPACE.name());
        }
    }

    @Nested
    class MouseButtonEnum {
        @Test void standardButtonsExist() {
            assertNotNull(MouseButton.LEFT);
            assertNotNull(MouseButton.RIGHT);
            assertNotNull(MouseButton.MIDDLE);
            assertNotNull(MouseButton.UNKNOWN);
        }

        @Test void implementsBooleanSource() {
            assertInstanceOf(BooleanSource.class, MouseButton.LEFT);
        }
    }

    @Nested
    class MouseAxisEnum {
        @Test void axesExist() {
            assertNotNull(MouseAxis.CURSOR);
            assertNotNull(MouseAxis.SCROLL);
        }

        @Test void implementsAxisSource() {
            assertInstanceOf(AxisSource.class, MouseAxis.CURSOR);
            assertInstanceOf(InputSource.class, MouseAxis.CURSOR);
        }
    }

    @Nested
    class GamepadTypes {
        @Test void gamepadButtonsExist() {
            assertNotNull(GamepadButton.A);
            assertNotNull(GamepadButton.LB);
            assertNotNull(GamepadButton.DPAD_UP);
            assertInstanceOf(BooleanSource.class, GamepadButton.A);
        }

        @Test void gamepadAxesExist() {
            assertNotNull(GamepadAxis.LEFT_STICK);
            assertNotNull(GamepadAxis.LEFT_TRIGGER);
            assertInstanceOf(AxisSource.class, GamepadAxis.LEFT_STICK);
        }
    }

    @Nested
    class ValueTypes {
        @Test void scanCodeWrapsInt() {
            var sc = new ScanCode(42);
            assertEquals(42, sc.value());
        }

        @Test void codepointWrapsInt() {
            var cp = new Codepoint(0x1F600);
            assertEquals(0x1F600, cp.value());
        }

        @Test void modifiersBitFlags() {
            var mods = new Modifiers(0x01 | 0x04); // shift + alt
            assertTrue(mods.shift());
            assertFalse(mods.ctrl());
            assertTrue(mods.alt());
            assertFalse(mods.superKey());
        }

        @Test void emptyModifiers() {
            var mods = new Modifiers(0);
            assertFalse(mods.shift());
            assertFalse(mods.ctrl());
            assertFalse(mods.alt());
            assertFalse(mods.superKey());
        }

        @Test void cursorModeValues() {
            assertNotNull(CursorMode.NORMAL);
            assertNotNull(CursorMode.HIDDEN);
            assertNotNull(CursorMode.LOCKED);
            assertNotNull(CursorMode.CONFINED);
        }
    }

    @Nested
    class DeviceIdentification {
        @Test void standardDeviceTypes() {
            assertNotNull(DeviceType.KEYBOARD);
            assertNotNull(DeviceType.MOUSE);
            assertNotNull(DeviceType.GAMEPAD);
            assertNotNull(DeviceType.TOUCH);
        }

        @Test void customDeviceType() {
            var flightStick = DeviceType.of("flight_stick");
            assertEquals("flight_stick", flightStick.name());
        }

        @Test void deviceIdCombinesTypeAndIndex() {
            var id = new DeviceId(DeviceType.KEYBOARD, 0);
            assertEquals(DeviceType.KEYBOARD, id.type());
            assertEquals(0, id.index());
        }

        @Test void deviceIdEquality() {
            var a = new DeviceId(DeviceType.MOUSE, 0);
            var b = new DeviceId(DeviceType.MOUSE, 0);
            assertEquals(a, b);
        }

        @Test void differentIndexMeansDifferentDevice() {
            var a = new DeviceId(DeviceType.GAMEPAD, 0);
            var b = new DeviceId(DeviceType.GAMEPAD, 1);
            assertNotEquals(a, b);
        }
    }
}

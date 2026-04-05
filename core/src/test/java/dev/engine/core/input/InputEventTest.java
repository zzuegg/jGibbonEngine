package dev.engine.core.input;

import dev.engine.core.module.Time;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputEventTest {

    static final Time TIME = new Time(1, 0.016);
    static final DeviceId KB = new DeviceId(DeviceType.KEYBOARD, 0);
    static final DeviceId MOUSE = new DeviceId(DeviceType.MOUSE, 0);
    static final DeviceId GP = new DeviceId(DeviceType.GAMEPAD, 0);
    static final Modifiers NO_MODS = new Modifiers(0);
    static final Modifiers SHIFT = new Modifiers(0x01);

    @Nested
    class KeyEvents {
        @Test void keyPressedIsBooleanEvent() {
            var e = new InputEvent.KeyPressed(TIME, KB, KeyCode.E, new ScanCode(18), NO_MODS);
            assertInstanceOf(BooleanEvent.class, e);
            assertInstanceOf(KeyEvent.class, e);
            assertInstanceOf(HasModifiers.class, e);
            assertTrue(e.pressed());
            assertEquals(KeyCode.E, e.keyCode());
            assertEquals(KeyCode.E, e.source());
        }

        @Test void keyReleasedIsNotPressed() {
            var e = new InputEvent.KeyReleased(TIME, KB, KeyCode.A, new ScanCode(30), NO_MODS);
            assertFalse(e.pressed());
        }

        @Test void keyRepeatedIsPressed() {
            var e = new InputEvent.KeyRepeated(TIME, KB, KeyCode.W, new ScanCode(17), SHIFT);
            assertTrue(e.pressed());
            assertTrue(e.modifiers().shift());
        }
    }

    @Nested
    class MouseButtonEvents {
        @Test void mousePressedHasPosition() {
            var e = new InputEvent.MousePressed(TIME, MOUSE, MouseButton.LEFT, NO_MODS, 100.0, 200.0);
            assertInstanceOf(MouseButtonEvent.class, e);
            assertInstanceOf(BooleanEvent.class, e);
            assertTrue(e.pressed());
            assertEquals(100.0, e.x());
            assertEquals(200.0, e.y());
            assertEquals(MouseButton.LEFT, e.button());
            assertEquals(MouseButton.LEFT, e.source());
        }

        @Test void mouseReleasedIsNotPressed() {
            var e = new InputEvent.MouseReleased(TIME, MOUSE, MouseButton.RIGHT, NO_MODS, 50.0, 60.0);
            assertFalse(e.pressed());
        }
    }

    @Nested
    class GamepadButtonEvents {
        @Test void gamepadPressedIsBooleanEvent() {
            var e = new InputEvent.GamepadPressed(TIME, GP, GamepadButton.A);
            assertInstanceOf(GamepadButtonEvent.class, e);
            assertInstanceOf(BooleanEvent.class, e);
            assertTrue(e.pressed());
            assertEquals(GamepadButton.A, e.button());
            assertEquals(GamepadButton.A, e.source());
        }

        @Test void gamepadReleasedIsNotPressed() {
            var e = new InputEvent.GamepadReleased(TIME, GP, GamepadButton.B);
            assertFalse(e.pressed());
        }
    }

    @Nested
    class AxisEvents {
        @Test void cursorMovedIsAxisEvent() {
            var e = new InputEvent.CursorMoved(TIME, MOUSE, NO_MODS, 400.0, 300.0);
            assertInstanceOf(AxisEvent.class, e);
            assertInstanceOf(HasModifiers.class, e);
            assertEquals(MouseAxis.CURSOR, e.source());
            assertEquals(400.0, e.x());
            assertEquals(300.0, e.y());
        }

        @Test void scrolledIsAxisEvent() {
            var e = new InputEvent.Scrolled(TIME, MOUSE, SHIFT, 0.0, -3.0);
            assertInstanceOf(AxisEvent.class, e);
            assertEquals(MouseAxis.SCROLL, e.source());
            assertTrue(e.modifiers().shift());
        }

        @Test void gamepadAxisMovedHasSource() {
            var e = new InputEvent.GamepadAxisMoved(TIME, GP, GamepadAxis.LEFT_STICK, 0.5, -0.3);
            assertInstanceOf(AxisEvent.class, e);
            assertEquals(GamepadAxis.LEFT_STICK, e.source());
        }
    }

    @Nested
    class OtherEvents {
        @Test void charTypedCarriesCodepoint() {
            var e = new InputEvent.CharTyped(TIME, KB, new Codepoint(65));
            assertInstanceOf(DeviceEvent.class, e);
            assertEquals(65, e.codepoint().value());
        }

        @Test void deviceConnectionChanged() {
            var e = new InputEvent.DeviceConnectionChanged(TIME, GP, true);
            assertInstanceOf(DeviceEvent.class, e);
            assertTrue(e.connected());
            assertEquals(GP, e.device());
        }
    }

    @Nested
    class PatternMatching {
        @Test void switchOnBooleanEvent() {
            InputEvent event = new InputEvent.KeyPressed(TIME, KB, KeyCode.E, new ScanCode(18), NO_MODS);
            String result = switch (event) {
                case BooleanEvent e when e.pressed() -> "pressed: " + e.source().name();
                case BooleanEvent e -> "released: " + e.source().name();
                default -> "other";
            };
            assertEquals("pressed: E", result);
        }

        @Test void switchOnSpecificKeyWithDevice() {
            InputEvent event = new InputEvent.KeyPressed(TIME, KB, KeyCode.E, new ScanCode(18), NO_MODS);
            boolean handled = switch (event) {
                case InputEvent.KeyPressed e
                    when e.device().equals(KB) && e.keyCode() == KeyCode.E -> true;
                default -> false;
            };
            assertTrue(handled);
        }

        @Test void switchOnAxisEvent() {
            InputEvent event = new InputEvent.Scrolled(TIME, MOUSE, new Modifiers(0x02), 0, -1.0);
            boolean handled = switch (event) {
                case InputEvent.Scrolled e when e.modifiers().ctrl() -> true;
                default -> false;
            };
            assertTrue(handled);
        }
    }

    @Nested
    class WindowEvents {
        @Test void windowResized() {
            var e = new WindowEvent.Resized(TIME, 1920, 1080);
            assertEquals(1920, e.width());
            assertEquals(1080, e.height());
            assertEquals(TIME, e.time());
        }

        @Test void windowFocusEvents() {
            assertNotNull(new WindowEvent.FocusGained(TIME));
            assertNotNull(new WindowEvent.FocusLost(TIME));
        }

        @Test void windowClosed() {
            assertNotNull(new WindowEvent.Closed(TIME));
        }

        @Test void windowMoved() {
            var e = new WindowEvent.Moved(TIME, 100, 200);
            assertEquals(100, e.x());
            assertEquals(200, e.y());
        }
    }
}

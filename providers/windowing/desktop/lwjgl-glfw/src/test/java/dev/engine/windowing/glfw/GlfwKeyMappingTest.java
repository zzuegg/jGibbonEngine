package dev.engine.windowing.glfw;

import dev.engine.core.input.KeyCode;
import dev.engine.core.input.Modifiers;
import dev.engine.core.input.MouseButton;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.*;

class GlfwKeyMappingTest {

    @Nested
    class KeyMapping {
        @ParameterizedTest
        @CsvSource({
            "65, A",      // GLFW_KEY_A
            "66, B",
            "87, W",      // GLFW_KEY_W
            "83, S",      // GLFW_KEY_S
            "68, D",      // GLFW_KEY_D
            "69, E",      // GLFW_KEY_E
            "32, SPACE",  // GLFW_KEY_SPACE
            "256, ESCAPE",// GLFW_KEY_ESCAPE
            "257, ENTER", // GLFW_KEY_ENTER
            "258, TAB",   // GLFW_KEY_TAB
            "259, BACKSPACE",
            "262, RIGHT", // GLFW_KEY_RIGHT
            "263, LEFT",
            "264, DOWN",
            "265, UP",
            "340, LEFT_SHIFT",
            "341, LEFT_CTRL",
            "342, LEFT_ALT",
            "343, LEFT_SUPER",
            "290, F1",    // GLFW_KEY_F1
            "301, F12",   // GLFW_KEY_F12
        })
        void mapsGlfwKeyToKeyCode(int glfwKey, String expectedName) {
            var keyCode = GlfwKeyMapping.mapKey(glfwKey);
            assertEquals(expectedName, keyCode.name());
        }

        @Test void unknownKeyMapsToUnknown() {
            assertEquals(KeyCode.UNKNOWN, GlfwKeyMapping.mapKey(-1));
            assertEquals(KeyCode.UNKNOWN, GlfwKeyMapping.mapKey(999));
        }

        @Test void allLettersMapped() {
            for (int i = 0; i < 26; i++) {
                var keyCode = GlfwKeyMapping.mapKey(GLFW.GLFW_KEY_A + i);
                assertNotEquals(KeyCode.UNKNOWN, keyCode, "Key " + (char)('A' + i) + " should be mapped");
            }
        }

        @Test void allDigitsMapped() {
            for (int i = 0; i <= 9; i++) {
                var keyCode = GlfwKeyMapping.mapKey(GLFW.GLFW_KEY_0 + i);
                assertNotEquals(KeyCode.UNKNOWN, keyCode, "Digit " + i + " should be mapped");
            }
        }

        @Test void allFunctionKeysMapped() {
            for (int i = 0; i < 12; i++) {
                var keyCode = GlfwKeyMapping.mapKey(GLFW.GLFW_KEY_F1 + i);
                assertNotEquals(KeyCode.UNKNOWN, keyCode, "F" + (i + 1) + " should be mapped");
            }
        }
    }

    @Nested
    class MouseButtonMapping {
        @Test void leftButton() {
            assertEquals(MouseButton.LEFT, GlfwKeyMapping.mapMouseButton(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        }

        @Test void rightButton() {
            assertEquals(MouseButton.RIGHT, GlfwKeyMapping.mapMouseButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        }

        @Test void middleButton() {
            assertEquals(MouseButton.MIDDLE, GlfwKeyMapping.mapMouseButton(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
        }

        @Test void extraButtons() {
            assertEquals(MouseButton.BUTTON_4, GlfwKeyMapping.mapMouseButton(GLFW.GLFW_MOUSE_BUTTON_4));
            assertEquals(MouseButton.BUTTON_5, GlfwKeyMapping.mapMouseButton(GLFW.GLFW_MOUSE_BUTTON_5));
        }

        @Test void unknownButton() {
            assertEquals(MouseButton.UNKNOWN, GlfwKeyMapping.mapMouseButton(99));
        }
    }

    @Nested
    class ModifierMapping {
        @Test void noModifiers() {
            var mods = GlfwKeyMapping.mapModifiers(0);
            assertFalse(mods.shift());
            assertFalse(mods.ctrl());
            assertFalse(mods.alt());
            assertFalse(mods.superKey());
        }

        @Test void shiftOnly() {
            var mods = GlfwKeyMapping.mapModifiers(GLFW.GLFW_MOD_SHIFT);
            assertTrue(mods.shift());
            assertFalse(mods.ctrl());
        }

        @Test void ctrlOnly() {
            var mods = GlfwKeyMapping.mapModifiers(GLFW.GLFW_MOD_CONTROL);
            assertTrue(mods.ctrl());
            assertFalse(mods.shift());
        }

        @Test void altOnly() {
            var mods = GlfwKeyMapping.mapModifiers(GLFW.GLFW_MOD_ALT);
            assertTrue(mods.alt());
        }

        @Test void superOnly() {
            var mods = GlfwKeyMapping.mapModifiers(GLFW.GLFW_MOD_SUPER);
            assertTrue(mods.superKey());
        }

        @Test void combinedModifiers() {
            var mods = GlfwKeyMapping.mapModifiers(GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT);
            assertTrue(mods.shift());
            assertTrue(mods.ctrl());
            assertTrue(mods.alt());
            assertFalse(mods.superKey());
        }
    }
}

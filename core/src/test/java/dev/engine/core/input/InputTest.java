package dev.engine.core.input;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InputTest {

    private InputState input;

    @BeforeEach
    void setUp() { input = new InputState(); }

    @Nested
    class KeyState {
        @Test void keyNotPressedByDefault() {
            assertFalse(input.isKeyDown(Key.of("W")));
        }

        @Test void keyDownAfterPress() {
            input.keyPressed(Key.of("W"));
            assertTrue(input.isKeyDown(Key.of("W")));
        }

        @Test void keyUpAfterRelease() {
            input.keyPressed(Key.of("W"));
            input.keyReleased(Key.of("W"));
            assertFalse(input.isKeyDown(Key.of("W")));
        }

        @Test void keyJustPressedOnlyOnFirstFrame() {
            input.keyPressed(Key.of("SPACE"));
            assertTrue(input.isKeyJustPressed(Key.of("SPACE")));
            input.update(); // advance frame
            assertFalse(input.isKeyJustPressed(Key.of("SPACE")));
            assertTrue(input.isKeyDown(Key.of("SPACE"))); // still held
        }
    }

    @Nested
    class MouseState {
        @Test void mousePositionTracked() {
            input.mouseMoved(100.0, 200.0);
            assertEquals(100.0, input.mouseX());
            assertEquals(200.0, input.mouseY());
        }

        @Test void mouseDeltaCalculated() {
            input.mouseMoved(100.0, 100.0);
            input.update();
            input.mouseMoved(110.0, 105.0);
            assertEquals(10.0, input.mouseDeltaX(), 1e-5);
            assertEquals(5.0, input.mouseDeltaY(), 1e-5);
        }

        @Test void mouseButtonTracked() {
            input.mouseButtonPressed(MouseButton.of("LEFT"));
            assertTrue(input.isMouseButtonDown(MouseButton.of("LEFT")));
            input.mouseButtonReleased(MouseButton.of("LEFT"));
            assertFalse(input.isMouseButtonDown(MouseButton.of("LEFT")));
        }
    }
}

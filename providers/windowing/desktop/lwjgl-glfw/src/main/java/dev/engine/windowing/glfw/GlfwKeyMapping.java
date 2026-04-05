package dev.engine.windowing.glfw;

import dev.engine.core.input.KeyCode;
import dev.engine.core.input.MouseButton;
import org.lwjgl.glfw.GLFW;

/**
 * Maps GLFW key/button codes to engine input types.
 */
final class GlfwKeyMapping {

    private GlfwKeyMapping() {}

    static KeyCode mapKey(int glfwKey) {
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_A -> KeyCode.A;
            case GLFW.GLFW_KEY_B -> KeyCode.B;
            case GLFW.GLFW_KEY_C -> KeyCode.C;
            case GLFW.GLFW_KEY_D -> KeyCode.D;
            case GLFW.GLFW_KEY_E -> KeyCode.E;
            case GLFW.GLFW_KEY_F -> KeyCode.F;
            case GLFW.GLFW_KEY_G -> KeyCode.G;
            case GLFW.GLFW_KEY_H -> KeyCode.H;
            case GLFW.GLFW_KEY_I -> KeyCode.I;
            case GLFW.GLFW_KEY_J -> KeyCode.J;
            case GLFW.GLFW_KEY_K -> KeyCode.K;
            case GLFW.GLFW_KEY_L -> KeyCode.L;
            case GLFW.GLFW_KEY_M -> KeyCode.M;
            case GLFW.GLFW_KEY_N -> KeyCode.N;
            case GLFW.GLFW_KEY_O -> KeyCode.O;
            case GLFW.GLFW_KEY_P -> KeyCode.P;
            case GLFW.GLFW_KEY_Q -> KeyCode.Q;
            case GLFW.GLFW_KEY_R -> KeyCode.R;
            case GLFW.GLFW_KEY_S -> KeyCode.S;
            case GLFW.GLFW_KEY_T -> KeyCode.T;
            case GLFW.GLFW_KEY_U -> KeyCode.U;
            case GLFW.GLFW_KEY_V -> KeyCode.V;
            case GLFW.GLFW_KEY_W -> KeyCode.W;
            case GLFW.GLFW_KEY_X -> KeyCode.X;
            case GLFW.GLFW_KEY_Y -> KeyCode.Y;
            case GLFW.GLFW_KEY_Z -> KeyCode.Z;
            case GLFW.GLFW_KEY_0 -> KeyCode.NUM_0;
            case GLFW.GLFW_KEY_1 -> KeyCode.NUM_1;
            case GLFW.GLFW_KEY_2 -> KeyCode.NUM_2;
            case GLFW.GLFW_KEY_3 -> KeyCode.NUM_3;
            case GLFW.GLFW_KEY_4 -> KeyCode.NUM_4;
            case GLFW.GLFW_KEY_5 -> KeyCode.NUM_5;
            case GLFW.GLFW_KEY_6 -> KeyCode.NUM_6;
            case GLFW.GLFW_KEY_7 -> KeyCode.NUM_7;
            case GLFW.GLFW_KEY_8 -> KeyCode.NUM_8;
            case GLFW.GLFW_KEY_9 -> KeyCode.NUM_9;
            case GLFW.GLFW_KEY_KP_0 -> KeyCode.NUMPAD_0;
            case GLFW.GLFW_KEY_KP_1 -> KeyCode.NUMPAD_1;
            case GLFW.GLFW_KEY_KP_2 -> KeyCode.NUMPAD_2;
            case GLFW.GLFW_KEY_KP_3 -> KeyCode.NUMPAD_3;
            case GLFW.GLFW_KEY_KP_4 -> KeyCode.NUMPAD_4;
            case GLFW.GLFW_KEY_KP_5 -> KeyCode.NUMPAD_5;
            case GLFW.GLFW_KEY_KP_6 -> KeyCode.NUMPAD_6;
            case GLFW.GLFW_KEY_KP_7 -> KeyCode.NUMPAD_7;
            case GLFW.GLFW_KEY_KP_8 -> KeyCode.NUMPAD_8;
            case GLFW.GLFW_KEY_KP_9 -> KeyCode.NUMPAD_9;
            case GLFW.GLFW_KEY_KP_ADD -> KeyCode.NUMPAD_ADD;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> KeyCode.NUMPAD_SUBTRACT;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> KeyCode.NUMPAD_MULTIPLY;
            case GLFW.GLFW_KEY_KP_DIVIDE -> KeyCode.NUMPAD_DIVIDE;
            case GLFW.GLFW_KEY_KP_ENTER -> KeyCode.NUMPAD_ENTER;
            case GLFW.GLFW_KEY_KP_DECIMAL -> KeyCode.NUMPAD_DECIMAL;
            case GLFW.GLFW_KEY_F1 -> KeyCode.F1;
            case GLFW.GLFW_KEY_F2 -> KeyCode.F2;
            case GLFW.GLFW_KEY_F3 -> KeyCode.F3;
            case GLFW.GLFW_KEY_F4 -> KeyCode.F4;
            case GLFW.GLFW_KEY_F5 -> KeyCode.F5;
            case GLFW.GLFW_KEY_F6 -> KeyCode.F6;
            case GLFW.GLFW_KEY_F7 -> KeyCode.F7;
            case GLFW.GLFW_KEY_F8 -> KeyCode.F8;
            case GLFW.GLFW_KEY_F9 -> KeyCode.F9;
            case GLFW.GLFW_KEY_F10 -> KeyCode.F10;
            case GLFW.GLFW_KEY_F11 -> KeyCode.F11;
            case GLFW.GLFW_KEY_F12 -> KeyCode.F12;
            case GLFW.GLFW_KEY_SPACE -> KeyCode.SPACE;
            case GLFW.GLFW_KEY_ENTER -> KeyCode.ENTER;
            case GLFW.GLFW_KEY_ESCAPE -> KeyCode.ESCAPE;
            case GLFW.GLFW_KEY_TAB -> KeyCode.TAB;
            case GLFW.GLFW_KEY_BACKSPACE -> KeyCode.BACKSPACE;
            case GLFW.GLFW_KEY_DELETE -> KeyCode.DELETE;
            case GLFW.GLFW_KEY_INSERT -> KeyCode.INSERT;
            case GLFW.GLFW_KEY_HOME -> KeyCode.HOME;
            case GLFW.GLFW_KEY_END -> KeyCode.END;
            case GLFW.GLFW_KEY_PAGE_UP -> KeyCode.PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KeyCode.PAGE_DOWN;
            case GLFW.GLFW_KEY_LEFT -> KeyCode.LEFT;
            case GLFW.GLFW_KEY_RIGHT -> KeyCode.RIGHT;
            case GLFW.GLFW_KEY_UP -> KeyCode.UP;
            case GLFW.GLFW_KEY_DOWN -> KeyCode.DOWN;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> KeyCode.LEFT_SHIFT;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyCode.RIGHT_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL -> KeyCode.LEFT_CTRL;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyCode.RIGHT_CTRL;
            case GLFW.GLFW_KEY_LEFT_ALT -> KeyCode.LEFT_ALT;
            case GLFW.GLFW_KEY_RIGHT_ALT -> KeyCode.RIGHT_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER -> KeyCode.LEFT_SUPER;
            case GLFW.GLFW_KEY_RIGHT_SUPER -> KeyCode.RIGHT_SUPER;
            case GLFW.GLFW_KEY_CAPS_LOCK -> KeyCode.CAPS_LOCK;
            case GLFW.GLFW_KEY_NUM_LOCK -> KeyCode.NUM_LOCK;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> KeyCode.SCROLL_LOCK;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> KeyCode.PRINT_SCREEN;
            case GLFW.GLFW_KEY_PAUSE -> KeyCode.PAUSE;
            case GLFW.GLFW_KEY_COMMA -> KeyCode.COMMA;
            case GLFW.GLFW_KEY_PERIOD -> KeyCode.PERIOD;
            case GLFW.GLFW_KEY_SLASH -> KeyCode.SLASH;
            case GLFW.GLFW_KEY_SEMICOLON -> KeyCode.SEMICOLON;
            case GLFW.GLFW_KEY_APOSTROPHE -> KeyCode.APOSTROPHE;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> KeyCode.LEFT_BRACKET;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> KeyCode.RIGHT_BRACKET;
            case GLFW.GLFW_KEY_BACKSLASH -> KeyCode.BACKSLASH;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> KeyCode.GRAVE_ACCENT;
            case GLFW.GLFW_KEY_MINUS -> KeyCode.MINUS;
            case GLFW.GLFW_KEY_EQUAL -> KeyCode.EQUALS;
            case GLFW.GLFW_KEY_MENU -> KeyCode.MENU;
            default -> KeyCode.UNKNOWN;
        };
    }

    static MouseButton mapMouseButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW.GLFW_MOUSE_BUTTON_LEFT -> MouseButton.LEFT;
            case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> MouseButton.RIGHT;
            case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> MouseButton.MIDDLE;
            case GLFW.GLFW_MOUSE_BUTTON_4 -> MouseButton.BUTTON_4;
            case GLFW.GLFW_MOUSE_BUTTON_5 -> MouseButton.BUTTON_5;
            default -> MouseButton.UNKNOWN;
        };
    }

    static dev.engine.core.input.Modifiers mapModifiers(int glfwMods) {
        // GLFW modifier bits: 0x01=shift, 0x02=ctrl, 0x04=alt, 0x08=super
        // Engine uses same layout
        return new dev.engine.core.input.Modifiers(glfwMods & 0x0F);
    }
}

package dev.engine.bindings.sdl3;

import dev.engine.core.input.KeyCode;
import dev.engine.core.input.Modifiers;
import dev.engine.core.input.MouseButton;
import org.lwjgl.sdl.SDLKeycode;
import org.lwjgl.sdl.SDLKeyboard;
import org.lwjgl.sdl.SDLMouse;

/**
 * Maps SDL3 key/button codes to engine input types.
 * Uses LWJGL's SDLKeycode constants.
 */
final class Sdl3KeyMapping {

    private Sdl3KeyMapping() {}

    static KeyCode mapKey(int sdlKey) {
        return switch (sdlKey) {
            case SDLKeycode.SDLK_A -> KeyCode.A;
            case SDLKeycode.SDLK_B -> KeyCode.B;
            case SDLKeycode.SDLK_C -> KeyCode.C;
            case SDLKeycode.SDLK_D -> KeyCode.D;
            case SDLKeycode.SDLK_E -> KeyCode.E;
            case SDLKeycode.SDLK_F -> KeyCode.F;
            case SDLKeycode.SDLK_G -> KeyCode.G;
            case SDLKeycode.SDLK_H -> KeyCode.H;
            case SDLKeycode.SDLK_I -> KeyCode.I;
            case SDLKeycode.SDLK_J -> KeyCode.J;
            case SDLKeycode.SDLK_K -> KeyCode.K;
            case SDLKeycode.SDLK_L -> KeyCode.L;
            case SDLKeycode.SDLK_M -> KeyCode.M;
            case SDLKeycode.SDLK_N -> KeyCode.N;
            case SDLKeycode.SDLK_O -> KeyCode.O;
            case SDLKeycode.SDLK_P -> KeyCode.P;
            case SDLKeycode.SDLK_Q -> KeyCode.Q;
            case SDLKeycode.SDLK_R -> KeyCode.R;
            case SDLKeycode.SDLK_S -> KeyCode.S;
            case SDLKeycode.SDLK_T -> KeyCode.T;
            case SDLKeycode.SDLK_U -> KeyCode.U;
            case SDLKeycode.SDLK_V -> KeyCode.V;
            case SDLKeycode.SDLK_W -> KeyCode.W;
            case SDLKeycode.SDLK_X -> KeyCode.X;
            case SDLKeycode.SDLK_Y -> KeyCode.Y;
            case SDLKeycode.SDLK_Z -> KeyCode.Z;
            case SDLKeycode.SDLK_0 -> KeyCode.NUM_0;
            case SDLKeycode.SDLK_1 -> KeyCode.NUM_1;
            case SDLKeycode.SDLK_2 -> KeyCode.NUM_2;
            case SDLKeycode.SDLK_3 -> KeyCode.NUM_3;
            case SDLKeycode.SDLK_4 -> KeyCode.NUM_4;
            case SDLKeycode.SDLK_5 -> KeyCode.NUM_5;
            case SDLKeycode.SDLK_6 -> KeyCode.NUM_6;
            case SDLKeycode.SDLK_7 -> KeyCode.NUM_7;
            case SDLKeycode.SDLK_8 -> KeyCode.NUM_8;
            case SDLKeycode.SDLK_9 -> KeyCode.NUM_9;
            case SDLKeycode.SDLK_F1 -> KeyCode.F1;
            case SDLKeycode.SDLK_F2 -> KeyCode.F2;
            case SDLKeycode.SDLK_F3 -> KeyCode.F3;
            case SDLKeycode.SDLK_F4 -> KeyCode.F4;
            case SDLKeycode.SDLK_F5 -> KeyCode.F5;
            case SDLKeycode.SDLK_F6 -> KeyCode.F6;
            case SDLKeycode.SDLK_F7 -> KeyCode.F7;
            case SDLKeycode.SDLK_F8 -> KeyCode.F8;
            case SDLKeycode.SDLK_F9 -> KeyCode.F9;
            case SDLKeycode.SDLK_F10 -> KeyCode.F10;
            case SDLKeycode.SDLK_F11 -> KeyCode.F11;
            case SDLKeycode.SDLK_F12 -> KeyCode.F12;
            case SDLKeycode.SDLK_SPACE -> KeyCode.SPACE;
            case SDLKeycode.SDLK_RETURN -> KeyCode.ENTER;
            case SDLKeycode.SDLK_ESCAPE -> KeyCode.ESCAPE;
            case SDLKeycode.SDLK_TAB -> KeyCode.TAB;
            case SDLKeycode.SDLK_BACKSPACE -> KeyCode.BACKSPACE;
            case SDLKeycode.SDLK_DELETE -> KeyCode.DELETE;
            case SDLKeycode.SDLK_INSERT -> KeyCode.INSERT;
            case SDLKeycode.SDLK_HOME -> KeyCode.HOME;
            case SDLKeycode.SDLK_END -> KeyCode.END;
            case SDLKeycode.SDLK_PAGEUP -> KeyCode.PAGE_UP;
            case SDLKeycode.SDLK_PAGEDOWN -> KeyCode.PAGE_DOWN;
            case SDLKeycode.SDLK_LEFT -> KeyCode.LEFT;
            case SDLKeycode.SDLK_RIGHT -> KeyCode.RIGHT;
            case SDLKeycode.SDLK_UP -> KeyCode.UP;
            case SDLKeycode.SDLK_DOWN -> KeyCode.DOWN;
            case SDLKeycode.SDLK_LSHIFT -> KeyCode.LEFT_SHIFT;
            case SDLKeycode.SDLK_RSHIFT -> KeyCode.RIGHT_SHIFT;
            case SDLKeycode.SDLK_LCTRL -> KeyCode.LEFT_CTRL;
            case SDLKeycode.SDLK_RCTRL -> KeyCode.RIGHT_CTRL;
            case SDLKeycode.SDLK_LALT -> KeyCode.LEFT_ALT;
            case SDLKeycode.SDLK_RALT -> KeyCode.RIGHT_ALT;
            case SDLKeycode.SDLK_LGUI -> KeyCode.LEFT_SUPER;
            case SDLKeycode.SDLK_RGUI -> KeyCode.RIGHT_SUPER;
            case SDLKeycode.SDLK_CAPSLOCK -> KeyCode.CAPS_LOCK;
            default -> KeyCode.UNKNOWN;
        };
    }

    static MouseButton mapMouseButton(int sdlButton) {
        return switch (sdlButton) {
            case SDLMouse.SDL_BUTTON_LEFT -> MouseButton.LEFT;
            case SDLMouse.SDL_BUTTON_MIDDLE -> MouseButton.MIDDLE;
            case SDLMouse.SDL_BUTTON_RIGHT -> MouseButton.RIGHT;
            case SDLMouse.SDL_BUTTON_X1 -> MouseButton.BUTTON_4;
            case SDLMouse.SDL_BUTTON_X2 -> MouseButton.BUTTON_5;
            default -> MouseButton.UNKNOWN;
        };
    }

    static Modifiers mapModifiers(int sdlMod) {
        int bits = 0;
        if ((sdlMod & SDLKeycode.SDL_KMOD_SHIFT) != 0) bits |= 0x01;
        if ((sdlMod & SDLKeycode.SDL_KMOD_CTRL) != 0) bits |= 0x02;
        if ((sdlMod & SDLKeycode.SDL_KMOD_ALT) != 0) bits |= 0x04;
        if ((sdlMod & SDLKeycode.SDL_KMOD_GUI) != 0) bits |= 0x08;
        return new Modifiers(bits);
    }
}

package dev.engine.providers.teavm.windowing;

import dev.engine.core.input.KeyCode;
import dev.engine.core.input.MouseButton;

/**
 * Maps DOM event.code strings to engine KeyCode values.
 */
final class TeaVmKeyMapping {

    private TeaVmKeyMapping() {}

    static KeyCode mapKey(String domCode) {
        if (domCode == null) return KeyCode.UNKNOWN;
        return switch (domCode) {
            case "KeyA" -> KeyCode.A;
            case "KeyB" -> KeyCode.B;
            case "KeyC" -> KeyCode.C;
            case "KeyD" -> KeyCode.D;
            case "KeyE" -> KeyCode.E;
            case "KeyF" -> KeyCode.F;
            case "KeyG" -> KeyCode.G;
            case "KeyH" -> KeyCode.H;
            case "KeyI" -> KeyCode.I;
            case "KeyJ" -> KeyCode.J;
            case "KeyK" -> KeyCode.K;
            case "KeyL" -> KeyCode.L;
            case "KeyM" -> KeyCode.M;
            case "KeyN" -> KeyCode.N;
            case "KeyO" -> KeyCode.O;
            case "KeyP" -> KeyCode.P;
            case "KeyQ" -> KeyCode.Q;
            case "KeyR" -> KeyCode.R;
            case "KeyS" -> KeyCode.S;
            case "KeyT" -> KeyCode.T;
            case "KeyU" -> KeyCode.U;
            case "KeyV" -> KeyCode.V;
            case "KeyW" -> KeyCode.W;
            case "KeyX" -> KeyCode.X;
            case "KeyY" -> KeyCode.Y;
            case "KeyZ" -> KeyCode.Z;
            case "Digit0" -> KeyCode.NUM_0;
            case "Digit1" -> KeyCode.NUM_1;
            case "Digit2" -> KeyCode.NUM_2;
            case "Digit3" -> KeyCode.NUM_3;
            case "Digit4" -> KeyCode.NUM_4;
            case "Digit5" -> KeyCode.NUM_5;
            case "Digit6" -> KeyCode.NUM_6;
            case "Digit7" -> KeyCode.NUM_7;
            case "Digit8" -> KeyCode.NUM_8;
            case "Digit9" -> KeyCode.NUM_9;
            case "F1" -> KeyCode.F1;
            case "F2" -> KeyCode.F2;
            case "F3" -> KeyCode.F3;
            case "F4" -> KeyCode.F4;
            case "F5" -> KeyCode.F5;
            case "F6" -> KeyCode.F6;
            case "F7" -> KeyCode.F7;
            case "F8" -> KeyCode.F8;
            case "F9" -> KeyCode.F9;
            case "F10" -> KeyCode.F10;
            case "F11" -> KeyCode.F11;
            case "F12" -> KeyCode.F12;
            case "Space" -> KeyCode.SPACE;
            case "Enter" -> KeyCode.ENTER;
            case "Escape" -> KeyCode.ESCAPE;
            case "Tab" -> KeyCode.TAB;
            case "Backspace" -> KeyCode.BACKSPACE;
            case "Delete" -> KeyCode.DELETE;
            case "Insert" -> KeyCode.INSERT;
            case "Home" -> KeyCode.HOME;
            case "End" -> KeyCode.END;
            case "PageUp" -> KeyCode.PAGE_UP;
            case "PageDown" -> KeyCode.PAGE_DOWN;
            case "ArrowLeft" -> KeyCode.LEFT;
            case "ArrowRight" -> KeyCode.RIGHT;
            case "ArrowUp" -> KeyCode.UP;
            case "ArrowDown" -> KeyCode.DOWN;
            case "ShiftLeft" -> KeyCode.LEFT_SHIFT;
            case "ShiftRight" -> KeyCode.RIGHT_SHIFT;
            case "ControlLeft" -> KeyCode.LEFT_CTRL;
            case "ControlRight" -> KeyCode.RIGHT_CTRL;
            case "AltLeft" -> KeyCode.LEFT_ALT;
            case "AltRight" -> KeyCode.RIGHT_ALT;
            case "MetaLeft" -> KeyCode.LEFT_SUPER;
            case "MetaRight" -> KeyCode.RIGHT_SUPER;
            case "CapsLock" -> KeyCode.CAPS_LOCK;
            case "NumLock" -> KeyCode.NUM_LOCK;
            case "ScrollLock" -> KeyCode.SCROLL_LOCK;
            default -> KeyCode.UNKNOWN;
        };
    }

    static MouseButton mapMouseButton(int domButton) {
        return switch (domButton) {
            case 0 -> MouseButton.LEFT;
            case 1 -> MouseButton.MIDDLE;
            case 2 -> MouseButton.RIGHT;
            case 3 -> MouseButton.BUTTON_4;
            case 4 -> MouseButton.BUTTON_5;
            default -> MouseButton.UNKNOWN;
        };
    }
}

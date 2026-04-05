package dev.engine.ui;

import dev.engine.core.input.*;

import java.util.List;

/**
 * Bridges engine {@link InputEvent}s to the UI {@link NkInput} system.
 * Call {@link #feedEvents(NkInput, List)} each frame before UI processing.
 */
public final class NkInputBridge {

    private NkInputBridge() {}

    /**
     * Feeds a list of engine input events into the UI input system.
     * Automatically calls begin/end on the input.
     */
    public static void feedEvents(NkInput input, List<InputEvent> events) {
        input.begin();
        for (var event : events) {
            switch (event) {
                case InputEvent.CursorMoved e -> input.motion((float) e.x(), (float) e.y());

                case InputEvent.MousePressed e -> {
                    int btn = mapMouseButton(e.button());
                    if (btn >= 0) input.button(btn, (float) e.x(), (float) e.y(), true);
                }
                case InputEvent.MouseReleased e -> {
                    int btn = mapMouseButton(e.button());
                    if (btn >= 0) input.button(btn, (float) e.x(), (float) e.y(), false);
                }

                case InputEvent.Scrolled e -> input.scroll((float) e.x(), (float) e.y());

                case InputEvent.KeyPressed e -> feedKey(input, e.keyCode(), e.modifiers(), true);
                case InputEvent.KeyReleased e -> feedKey(input, e.keyCode(), e.modifiers(), false);
                case InputEvent.KeyRepeated e -> feedKey(input, e.keyCode(), e.modifiers(), true);

                case InputEvent.CharTyped e -> input.character(e.codepoint().value());

                default -> {} // Gamepad events etc. ignored for UI
            }
        }
        input.end();
    }

    private static int mapMouseButton(MouseButton button) {
        return switch (button) {
            case LEFT -> 0;
            case RIGHT -> 1;
            case MIDDLE -> 2;
            default -> -1;
        };
    }

    private static void feedKey(NkInput input, KeyCode keyCode, Modifiers mods, boolean down) {
        int nkKey = mapKey(keyCode, mods);
        if (nkKey != NkKeys.NONE) {
            input.key(nkKey, down);
        }
    }

    private static int mapKey(KeyCode keyCode, Modifiers mods) {
        return switch (keyCode) {
            case LEFT_SHIFT, RIGHT_SHIFT -> NkKeys.SHIFT;
            case LEFT_CTRL, RIGHT_CTRL -> NkKeys.CTRL;
            case DELETE -> NkKeys.DEL;
            case ENTER -> NkKeys.ENTER;
            case TAB -> NkKeys.TAB;
            case BACKSPACE -> NkKeys.BACKSPACE;
            case UP -> NkKeys.UP;
            case DOWN -> NkKeys.DOWN;
            case LEFT -> NkKeys.LEFT;
            case RIGHT -> NkKeys.RIGHT;
            case HOME -> NkKeys.HOME;
            case END -> NkKeys.END;
            case A -> mods.ctrl() ? NkKeys.SELECT_ALL : NkKeys.NONE;
            case C -> mods.ctrl() ? NkKeys.COPY : NkKeys.NONE;
            case X -> mods.ctrl() ? NkKeys.CUT : NkKeys.NONE;
            case V -> mods.ctrl() ? NkKeys.PASTE : NkKeys.NONE;
            default -> NkKeys.NONE;
        };
    }
}

package dev.engine.ui;

/**
 * Input state for the UI system. Updated each frame before UI processing.
 */
public class NkInput {

    // Mouse state
    private float mouseX, mouseY;
    private float prevMouseX, prevMouseY;
    private float scrollX, scrollY;
    private final boolean[] mouseDown = new boolean[3];
    private final boolean[] mouseClicked = new boolean[3];
    private final float[] mouseClickX = new float[3];
    private final float[] mouseClickY = new float[3];

    // Keyboard state
    private final boolean[] keys = new boolean[NkKeys.COUNT];
    private final StringBuilder textInput = new StringBuilder();

    private boolean active = false;

    public void begin() {
        active = true;
        for (int i = 0; i < 3; i++) mouseClicked[i] = false;
        scrollX = 0;
        scrollY = 0;
        textInput.setLength(0);
    }

    public void end() {
        active = false;
    }

    // --- Mouse ---

    public void motion(float x, float y) {
        prevMouseX = mouseX;
        prevMouseY = mouseY;
        mouseX = x;
        mouseY = y;
    }

    public void button(int btn, float x, float y, boolean down) {
        if (btn < 0 || btn >= 3) return;
        if (down && !mouseDown[btn]) {
            mouseClicked[btn] = true;
            mouseClickX[btn] = x;
            mouseClickY[btn] = y;
        }
        mouseDown[btn] = down;
    }

    public void scroll(float x, float y) {
        scrollX += x;
        scrollY += y;
    }

    // --- Keyboard ---

    public void key(int nkKey, boolean down) {
        if (nkKey >= 0 && nkKey < NkKeys.COUNT) {
            keys[nkKey] = down;
        }
    }

    public void character(int codepoint) {
        if (codepoint > 0 && codepoint < 0xFFFF) {
            textInput.appendCodePoint(codepoint);
        }
    }

    // --- Queries ---

    public float mouseX() { return mouseX; }
    public float mouseY() { return mouseY; }
    public float mouseDeltaX() { return mouseX - prevMouseX; }
    public float mouseDeltaY() { return mouseY - prevMouseY; }
    public float scrollX() { return scrollX; }
    public float scrollY() { return scrollY; }

    public boolean isMouseDown(int btn) {
        return btn >= 0 && btn < 3 && mouseDown[btn];
    }

    public boolean isMouseClicked(int btn) {
        return btn >= 0 && btn < 3 && mouseClicked[btn];
    }

    public float mouseClickX(int btn) {
        return btn >= 0 && btn < 3 ? mouseClickX[btn] : 0;
    }

    public float mouseClickY(int btn) {
        return btn >= 0 && btn < 3 ? mouseClickY[btn] : 0;
    }

    public boolean isMouseHovering(NkRect rect) {
        return rect.contains(mouseX, mouseY);
    }

    public boolean isMouseClickInRect(int btn, NkRect rect) {
        return isMouseClicked(btn) && rect.contains(mouseClickX(btn), mouseClickY(btn));
    }

    /** Returns true if mouse was pressed this frame within the given rect. */
    public boolean isMousePressed(int btn, NkRect rect) {
        return isMouseClicked(btn) && rect.contains(mouseClickX(btn), mouseClickY(btn));
    }

    /** Returns true if mouse is held down and was originally clicked in rect. */
    public boolean isMouseDragging(int btn, NkRect rect) {
        return isMouseDown(btn) && rect.contains(mouseClickX(btn), mouseClickY(btn));
    }

    public boolean isKeyDown(int nkKey) {
        return nkKey >= 0 && nkKey < NkKeys.COUNT && keys[nkKey];
    }

    public String textInput() {
        return textInput.toString();
    }

    public boolean hasTextInput() {
        return !textInput.isEmpty();
    }
}

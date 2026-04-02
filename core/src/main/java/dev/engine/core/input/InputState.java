package dev.engine.core.input;

import java.util.HashSet;
import java.util.Set;

public class InputState {

    private final Set<Key> keysDown = new HashSet<>();
    private final Set<Key> keysJustPressed = new HashSet<>();
    private final Set<MouseButton> buttonsDown = new HashSet<>();

    private double mouseX, mouseY;
    private double prevMouseX, prevMouseY;
    private double deltaX, deltaY;

    public void keyPressed(Key key) {
        if (keysDown.add(key)) {
            keysJustPressed.add(key);
        }
    }

    public void keyReleased(Key key) {
        keysDown.remove(key);
    }

    public boolean isKeyDown(Key key) { return keysDown.contains(key); }
    public boolean isKeyJustPressed(Key key) { return keysJustPressed.contains(key); }

    public void mouseButtonPressed(MouseButton button) { buttonsDown.add(button); }
    public void mouseButtonReleased(MouseButton button) { buttonsDown.remove(button); }
    public boolean isMouseButtonDown(MouseButton button) { return buttonsDown.contains(button); }

    public void mouseMoved(double x, double y) {
        mouseX = x;
        mouseY = y;
        deltaX = x - prevMouseX;
        deltaY = y - prevMouseY;
    }

    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }
    public double mouseDeltaX() { return deltaX; }
    public double mouseDeltaY() { return deltaY; }

    public void update() {
        keysJustPressed.clear();
        prevMouseX = mouseX;
        prevMouseY = mouseY;
        deltaX = 0;
        deltaY = 0;
    }
}

package dev.engine.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent state for a UI window. Keyed by title string.
 */
public class NkWindow {

    final String title;
    NkRect bounds;
    boolean collapsed;
    boolean closed;
    boolean dragging;
    float dragOffsetX, dragOffsetY;

    // Scroll state
    float scrollY;
    float contentHeight; // total content height from last frame

    // Tree node states (keyed by title)
    final Map<String, Boolean> treeStates = new HashMap<>();

    // Combo popup state
    boolean comboOpen;
    String comboId;

    NkWindow(String title, NkRect bounds) {
        this.title = title;
        this.bounds = bounds;
    }
}

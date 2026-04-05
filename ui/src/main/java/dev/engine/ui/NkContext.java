package dev.engine.ui;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate-mode UI context. Inspired by Nuklear.
 *
 * <p>Usage per frame:
 * <pre>
 * ctx.input().begin();
 * // feed input events...
 * ctx.input().end();
 *
 * if (ctx.begin("Window", 10, 10, 300, 400)) {
 *     ctx.layoutRowDynamic(30, 1);
 *     ctx.label("Hello");
 *     if (ctx.button("Click")) { ... }
 * }
 * ctx.end();
 *
 * // Render: iterate ctx.drawCommands()
 * ctx.clear();
 * </pre>
 */
public class NkContext {

    /** Window flags */
    public static final int WINDOW_BORDER = 1;
    public static final int WINDOW_MOVABLE = 1 << 1;
    public static final int WINDOW_SCALABLE = 1 << 2;
    public static final int WINDOW_CLOSABLE = 1 << 3;
    public static final int WINDOW_MINIMIZABLE = 1 << 4;
    public static final int WINDOW_TITLE = 1 << 5;
    public static final int WINDOW_SCROLL_AUTO_HIDE = 1 << 6;
    public static final int WINDOW_NO_SCROLLBAR = 1 << 7;

    /** Text alignment */
    public static final int TEXT_LEFT = 0;
    public static final int TEXT_CENTER = 1;
    public static final int TEXT_RIGHT = 2;

    private final NkInput input = new NkInput();
    private final NkStyle style = new NkStyle();
    private NkFont font;

    // Window management
    private final Map<String, NkWindow> windows = new LinkedHashMap<>();
    private final List<String> windowOrder = new ArrayList<>();
    private NkWindow currentWindow;
    private final Deque<NkPanel> panelStack = new ArrayDeque<>();

    // Draw commands: per-window deferred rendering for z-order.
    // Each window's commands are collected separately, then merged in focus order.
    private final Map<String, List<NkDrawCommand>> windowDrawCommands = new LinkedHashMap<>();
    private List<NkDrawCommand> activeWindowCommands;

    // Overlay commands rendered on top of everything (popups, tooltips)
    private final List<NkDrawCommand> overlayCommands = new ArrayList<>();

    // Tooltip state
    private boolean tooltipActive;

    public NkContext(NkFont font) {
        this.font = font;
    }

    /** Routes draw commands to the active window's list for z-order sorting. */
    private void emit(NkDrawCommand cmd) {
        if (activeWindowCommands != null) {
            activeWindowCommands.add(cmd);
        }
    }

    /** Emits to the overlay layer — rendered on top of all windows (for popups). */
    private void emitOverlay(NkDrawCommand cmd) {
        overlayCommands.add(cmd);
    }

    /** Returns true if the current window accepts mouse input (not blocked by a front window). */
    private boolean windowAcceptsInput() {
        return currentWindow != null && currentWindow.inputActive;
    }

    public NkInput input() { return input; }
    public NkStyle style() { return style; }
    public NkFont font() { return font; }
    public void setFont(NkFont font) { this.font = font; }

    // ========================= Window API =========================

    /**
     * Begins a UI window. Returns true if the window is visible (not collapsed/closed).
     * Must be paired with {@link #end()}.
     */
    public boolean begin(String title, float x, float y, float w, float h) {
        return begin(title, x, y, w, h, WINDOW_BORDER | WINDOW_MOVABLE | WINDOW_TITLE);
    }

    public boolean begin(String title, float x, float y, float w, float h, int flags) {
        NkWindow win = windows.get(title);
        if (win == null) {
            win = new NkWindow(title, new NkRect(x, y, w, h));
            windows.put(title, win);
            windowOrder.add(title);
        }
        currentWindow = win;

        // Check if this window is the topmost at the click position
        boolean isTopmostAtClick = true;
        int myOrder = windowOrder.indexOf(title);
        if (input.isMouseClicked(0)) {
            for (int wi = windowOrder.size() - 1; wi > myOrder; wi--) {
                var frontWin = windows.get(windowOrder.get(wi));
                if (frontWin != null && !frontWin.closed && !frontWin.collapsed
                        && frontWin.bounds.contains(input.mouseClickX(0), input.mouseClickY(0))) {
                    isTopmostAtClick = false;
                    break;
                }
            }
        }
        win.inputActive = isTopmostAtClick;

        // Bring to front when clicked (only if topmost at click position)
        if (isTopmostAtClick && input.isMousePressed(0, win.bounds) && myOrder != windowOrder.size() - 1) {
            windowOrder.remove(title);
            windowOrder.add(title);
        }

        // Start collecting draw commands for this window
        activeWindowCommands = windowDrawCommands.computeIfAbsent(title, k -> new ArrayList<>());

        if (win.closed) {
            // Still need to call end()
            return false;
        }

        var bounds = win.bounds;

        // Handle window dragging (header area) — only if this window accepts input
        if ((flags & WINDOW_MOVABLE) != 0 && (flags & WINDOW_TITLE) != 0 && windowAcceptsInput()) {
            var headerRect = new NkRect(bounds.x(), bounds.y(), bounds.w(), style.headerHeight);
            if (input.isMousePressed(0, headerRect)) {
                win.dragging = true;
                win.dragOffsetX = input.mouseX() - bounds.x();
                win.dragOffsetY = input.mouseY() - bounds.y();
            }
            if (!input.isMouseDown(0)) {
                win.dragging = false;
            }
            if (win.dragging) {
                float nx = input.mouseX() - win.dragOffsetX;
                float ny = input.mouseY() - win.dragOffsetY;
                win.bounds = new NkRect(nx, ny, bounds.w(), bounds.h());
                bounds = win.bounds;
            }
        }

        // Handle minimize button
        if ((flags & WINDOW_MINIMIZABLE) != 0 && (flags & WINDOW_TITLE) != 0) {
            float btnSize = style.headerHeight - 4;
            float btnX = bounds.x() + bounds.w() - btnSize - 4;
            float btnY = bounds.y() + 2;
            var minimizeRect = new NkRect(btnX, btnY, btnSize, btnSize);
            if (input.isMousePressed(0, minimizeRect)) {
                win.collapsed = !win.collapsed;
            }
        }

        // Handle close button
        if ((flags & WINDOW_CLOSABLE) != 0 && (flags & WINDOW_TITLE) != 0) {
            float btnSize = style.headerHeight - 4;
            float offset = ((flags & WINDOW_MINIMIZABLE) != 0) ? 2 * (btnSize + 4) : btnSize + 4;
            float btnX = bounds.x() + bounds.w() - offset;
            float btnY = bounds.y() + 2;
            var closeRect = new NkRect(btnX, btnY, btnSize, btnSize);
            if (input.isMousePressed(0, closeRect)) {
                win.closed = true;
                return false;
            }
        }

        // Handle scrolling
        if ((flags & WINDOW_NO_SCROLLBAR) == 0) {
            if (input.isMouseHovering(bounds)) {
                win.scrollY -= input.scrollY() * 20;
                if (win.scrollY < 0) win.scrollY = 0;
                float maxScroll = Math.max(0, win.contentHeight - (bounds.h() - style.headerHeight - style.windowPadY * 2));
                if (win.scrollY > maxScroll) win.scrollY = maxScroll;
            }
        }

        // Draw window background (header-only when collapsed)
        var drawBounds = win.collapsed && (flags & WINDOW_TITLE) != 0
                ? new NkRect(bounds.x(), bounds.y(), bounds.w(), style.headerHeight)
                : bounds;
        emit(new NkDrawCommand.FilledRect(drawBounds, style.windowRounding, style.windowBackground));

        // Draw border
        if ((flags & WINDOW_BORDER) != 0) {
            emit(new NkDrawCommand.StrokedRect(drawBounds, style.windowRounding,
                    style.windowBorderWidth, style.windowBorder));
        }

        // Draw header
        float contentStartY = bounds.y();
        if ((flags & WINDOW_TITLE) != 0) {
            var headerRect = new NkRect(bounds.x(), bounds.y(), bounds.w(), style.headerHeight);
            emit(new NkDrawCommand.FilledRect(headerRect, style.windowRounding, style.headerBackground));
            // Title text
            float textX = bounds.x() + style.headerPadX;
            float textY = bounds.y() + (style.headerHeight - font.height()) / 2;
            emit(new NkDrawCommand.Text(
                    new NkRect(textX, textY, bounds.w() - style.headerPadX * 2, font.height()),
                    title, font, style.headerText));

            // Minimize button
            if ((flags & WINDOW_MINIMIZABLE) != 0) {
                float btnSize = style.headerHeight - 4;
                float btnX = bounds.x() + bounds.w() - btnSize - 4;
                float btnY2 = bounds.y() + 2;
                var btnRect = new NkRect(btnX, btnY2, btnSize, btnSize);
                emit(new NkDrawCommand.FilledRect(btnRect, 2, style.buttonNormal));
                // Draw - or + symbol
                String sym = win.collapsed ? "+" : "-";
                float symX = btnX + (btnSize - font.textWidth(sym)) / 2;
                float symY = btnY2 + (btnSize - font.height()) / 2;
                emit(new NkDrawCommand.Text(
                        new NkRect(symX, symY, font.textWidth(sym), font.height()),
                        sym, font, style.buttonText));
            }

            // Close button
            if ((flags & WINDOW_CLOSABLE) != 0) {
                float btnSize = style.headerHeight - 4;
                float offset = ((flags & WINDOW_MINIMIZABLE) != 0) ? 2 * (btnSize + 4) : btnSize + 4;
                float btnX = bounds.x() + bounds.w() - offset;
                float btnY2 = bounds.y() + 2;
                var btnRect = new NkRect(btnX, btnY2, btnSize, btnSize);
                emit(new NkDrawCommand.FilledRect(btnRect, 2, style.buttonNormal));
                float xX = btnX + (btnSize - font.textWidth("x")) / 2;
                float xY = btnY2 + (btnSize - font.height()) / 2;
                emit(new NkDrawCommand.Text(
                        new NkRect(xX, xY, font.textWidth("x"), font.height()),
                        "x", font, style.buttonText));
            }

            contentStartY = bounds.y() + style.headerHeight;
        }

        if (win.collapsed) {
            return false;
        }

        // Set up content panel
        var panel = new NkPanel();
        panel.contentX = bounds.x() + style.windowPadX;
        panel.contentY = contentStartY + style.windowPadY;
        panel.contentW = bounds.w() - style.windowPadX * 2;
        panel.contentH = bounds.h() - (contentStartY - bounds.y()) - style.windowPadY * 2;
        panel.cursorX = panel.contentX;
        panel.cursorY = panel.contentY;
        panel.scrollY = win.scrollY;
        panel.maxY = panel.contentY;

        // Clip to window content area
        panel.clip = new NkRect(bounds.x(), contentStartY, bounds.w(),
                bounds.h() - (contentStartY - bounds.y()));
        emit(new NkDrawCommand.Scissor(
                (int) panel.clip.x(), (int) panel.clip.y(),
                (int) panel.clip.w(), (int) panel.clip.h()));

        panelStack.push(panel);

        // Default layout
        layoutRowDynamic(font.height() + style.itemSpacingY, 1);

        return true;
    }

    /** Ends the current window. */
    public void end() {
        if (!panelStack.isEmpty()) {
            var panel = panelStack.pop();
            if (currentWindow != null) {
                // Record content height for scrollbar calculations
                currentWindow.contentHeight = panel.maxY - panel.contentY + panel.scrollY;
            }
            // Reset scissor
            emit(new NkDrawCommand.Scissor(0, 0, 8192, 8192));
        }
        currentWindow = null;
    }

    // ========================= Layout API =========================

    /** Sets up a dynamic row layout where items share width equally. */
    public void layoutRowDynamic(float height, int columns) {
        var panel = currentPanel();
        if (panel == null) return;
        // Advance to next row
        if (panel.rowIndex > 0) {
            panel.cursorY += panel.rowHeight + style.itemSpacingY;
        }
        panel.beginRow(height, columns, true, 0);
    }

    /** Sets up a static row layout where each item has a fixed width. */
    public void layoutRowStatic(float height, float itemWidth, int columns) {
        var panel = currentPanel();
        if (panel == null) return;
        if (panel.rowIndex > 0) {
            panel.cursorY += panel.rowHeight + style.itemSpacingY;
        }
        panel.beginRow(height, columns, false, itemWidth);
    }

    // ========================= Widget API =========================

    /** Draws a text label. */
    public void label(String text) {
        label(text, TEXT_LEFT);
    }

    public void label(String text, int align) {
        var rect = allocateWidget();
        if (rect == null) return;

        float textW = font.textWidth(text);
        float textX;
        switch (align) {
            case TEXT_CENTER -> textX = rect.x() + (rect.w() - textW) / 2;
            case TEXT_RIGHT -> textX = rect.x() + rect.w() - textW;
            default -> textX = rect.x();
        }
        float textY = rect.y() + (rect.h() - font.height()) / 2;

        emit(new NkDrawCommand.Text(
                new NkRect(textX, textY, textW, font.height()),
                text, font, style.labelText));
    }

    /** Draws a colored label. */
    public void labelColored(String text, NkColor color) {
        var rect = allocateWidget();
        if (rect == null) return;

        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(rect.x(), textY, font.textWidth(text), font.height()),
                text, font, color));
    }

    /** Draws a button. Returns true if clicked. */
    public boolean button(String text) {
        var rect = allocateWidget();
        if (rect == null) return false;

        boolean hovering = input.isMouseHovering(rect);
        boolean pressed = input.isMouseDown(0) && hovering;
        boolean clicked = input.isMousePressed(0, rect);

        NkColor bg = pressed ? style.buttonActive : hovering ? style.buttonHover : style.buttonNormal;

        emit(new NkDrawCommand.FilledRect(rect, style.buttonRounding, bg));
        emit(new NkDrawCommand.StrokedRect(rect, style.buttonRounding,
                style.buttonBorderWidth, style.buttonBorder));

        float textW = font.textWidth(text);
        float textX = rect.x() + (rect.w() - textW) / 2;
        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(textX, textY, textW, font.height()),
                text, font, style.buttonText));

        return clicked;
    }

    /** Draws a checkbox. Returns the new state. */
    public boolean checkbox(String text, boolean active) {
        var rect = allocateWidget();
        if (rect == null) return active;

        float boxSize = Math.min(style.checkboxSize, rect.h());
        float boxY = rect.y() + (rect.h() - boxSize) / 2;
        var boxRect = new NkRect(rect.x(), boxY, boxSize, boxSize);

        boolean clicked = input.isMousePressed(0, rect);
        if (clicked) active = !active;

        // Draw checkbox box
        emit(new NkDrawCommand.FilledRect(boxRect, 2,
                active ? style.checkboxActive : style.checkboxBackground));
        emit(new NkDrawCommand.StrokedRect(boxRect, 2, 1, style.checkboxBorder));

        // Draw checkmark
        if (active) {
            var inner = boxRect.shrink(style.checkboxPadding);
            emit(new NkDrawCommand.FilledRect(inner, 1, style.checkboxCursor));
        }

        // Draw text
        float textX = rect.x() + boxSize + style.itemSpacingX;
        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(textX, textY, font.textWidth(text), font.height()),
                text, font, style.checkboxText));

        return active;
    }

    /** Draws a float slider. Returns the new value. */
    public float sliderFloat(float min, float value, float max, float step) {
        var rect = allocateWidget();
        if (rect == null) return value;

        float cursorSize = style.sliderCursorSize;
        float barH = style.sliderBarHeight;
        float barY = rect.y() + (rect.h() - barH) / 2;
        float usableW = rect.w() - cursorSize;

        // Draw bar background (no rounding — thin bars distort with corner arcs)
        var barRect = new NkRect(rect.x() + cursorSize / 2, barY, usableW, barH);
        emit(new NkDrawCommand.FilledRect(barRect, 0, style.sliderBar));

        // Handle interaction
        float ratio = (max > min) ? (value - min) / (max - min) : 0;

        if (input.isMouseDragging(0, rect) || input.isMousePressed(0, rect)) {
            float mouseRatio = (input.mouseX() - rect.x() - cursorSize / 2) / usableW;
            mouseRatio = Math.max(0, Math.min(1, mouseRatio));
            value = min + mouseRatio * (max - min);
            if (step > 0) {
                value = Math.round(value / step) * step;
            }
            value = Math.max(min, Math.min(max, value));
            ratio = (max > min) ? (value - min) / (max - min) : 0;
        }

        // Draw filled portion
        float filledW = ratio * usableW;
        if (filledW > 0) {
            emit(new NkDrawCommand.FilledRect(
                    new NkRect(barRect.x(), barY, filledW, barH),
                    0, style.sliderBarFilled));
        }

        // Draw cursor
        float cursorX = rect.x() + ratio * usableW;
        float cursorY = rect.y() + (rect.h() - cursorSize) / 2;
        var cursorRect = new NkRect(cursorX, cursorY, cursorSize, cursorSize);

        boolean hovering = input.isMouseHovering(cursorRect);
        boolean dragging = input.isMouseDown(0) && input.isMouseHovering(rect);
        NkColor cursorColor = dragging ? style.sliderCursorActive
                : hovering ? style.sliderCursorHover : style.sliderCursor;
        emit(new NkDrawCommand.FilledCircle(cursorRect, cursorColor));

        return value;
    }

    /** Draws an integer slider. Returns the new value. */
    public int sliderInt(int min, int value, int max, int step) {
        return (int) sliderFloat(min, value, max, Math.max(1, step));
    }

    /** Draws a progress bar. Returns the new value. */
    public float progress(float current, float max, boolean modifiable) {
        var rect = allocateWidget();
        if (rect == null) return current;

        // Background
        emit(new NkDrawCommand.FilledRect(rect, style.progressRounding, style.progressBackground));
        emit(new NkDrawCommand.StrokedRect(rect, style.progressRounding, 1, style.progressBorder));

        // Handle modification
        if (modifiable && input.isMouseDragging(0, rect)) {
            float ratio = (input.mouseX() - rect.x()) / rect.w();
            current = Math.max(0, Math.min(max, ratio * max));
        }

        // Fill
        float ratio = max > 0 ? current / max : 0;
        float fillW = ratio * (rect.w() - 2);
        if (fillW > 0) {
            emit(new NkDrawCommand.FilledRect(
                    new NkRect(rect.x() + 1, rect.y() + 1, fillW, rect.h() - 2),
                    Math.max(0, style.progressRounding - 1), style.progressFill));
        }

        return current;
    }

    /** Draws a selectable label. Returns the new selection state. */
    public boolean selectableLabel(String text, boolean selected) {
        var rect = allocateWidget();
        if (rect == null) return selected;

        boolean clicked = input.isMousePressed(0, rect);
        if (clicked) selected = !selected;

        if (selected || input.isMouseHovering(rect)) {
            emit(new NkDrawCommand.FilledRect(rect, 0,
                    selected ? style.checkboxActive.withAlpha(60) : style.treeNodeHover));
        }

        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(rect.x() + 4, textY, font.textWidth(text), font.height()),
                text, font, style.labelText));

        return selected;
    }

    /** Draws a text input field. Returns the modified text. */
    public String editString(String text, int maxLength) {
        var rect = allocateWidget();
        if (rect == null) return text;

        boolean hovering = input.isMouseHovering(rect);
        boolean clicked = input.isMousePressed(0, rect);

        // Background
        emit(new NkDrawCommand.FilledRect(rect, 2, style.editBackground));
        emit(new NkDrawCommand.StrokedRect(rect, 2, 1, style.editBorder));

        // Simple editing: append typed text, handle backspace
        if (clicked || hovering) {
            if (input.hasTextInput()) {
                String typed = input.textInput();
                if (text.length() + typed.length() <= maxLength) {
                    text = text + typed;
                }
            }
            if (input.isKeyDown(NkKeys.BACKSPACE) && !text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
        }

        // Draw text
        float textY = rect.y() + (rect.h() - font.height()) / 2;
        float textX = rect.x() + 4;
        emit(new NkDrawCommand.Text(
                new NkRect(textX, textY, font.textWidth(text), font.height()),
                text, font, style.editText));

        // Draw cursor
        if (hovering) {
            float cursorX = textX + font.textWidth(text);
            emit(new NkDrawCommand.FilledRect(
                    new NkRect(cursorX, textY, 1, font.height()), 0, style.editCursor));
        }

        return text;
    }

    /** Draws a separator line. */
    public void separator() {
        var panel = currentPanel();
        if (panel == null) return;

        // Force a new row allocation for the separator
        if (panel.rowIndex > 0) {
            panel.cursorY += panel.rowHeight + style.itemSpacingY;
            panel.cursorX = panel.contentX;
            panel.rowIndex = 0;
        }

        float y = panel.cursorY - panel.scrollY;
        emit(new NkDrawCommand.Line(
                new NkVec2(panel.contentX, y),
                new NkVec2(panel.contentX + panel.contentW, y),
                1, style.separatorColor));

        panel.cursorY += style.itemSpacingY;
    }

    // ========================= Chart API =========================

    /**
     * Draws a live value chart — a scrolling line graph from a ring buffer of values.
     *
     * @param values  array of values (oldest first)
     * @param count   number of valid values in the array (may be less than values.length)
     * @param offset  ring buffer start index (oldest value position)
     * @param min     minimum Y axis value
     * @param max     maximum Y axis value
     * @param fgColor line color
     * @param bgColor background color
     */
    public void chart(float[] values, int count, int offset, float min, float max,
                      NkColor fgColor, NkColor bgColor) {
        var rect = allocateWidget();
        if (rect == null || count < 2) return;

        // Background
        emit(new NkDrawCommand.FilledRect(rect, 0, bgColor));
        emit(new NkDrawCommand.StrokedRect(rect, 0, 1, style.windowBorder));

        float range = max - min;
        if (range <= 0) range = 1;

        // Draw line segments between consecutive values
        for (int i = 1; i < count; i++) {
            int idx0 = (offset + i - 1) % values.length;
            int idx1 = (offset + i) % values.length;

            float v0 = Math.max(min, Math.min(max, values[idx0]));
            float v1 = Math.max(min, Math.min(max, values[idx1]));

            float x0 = rect.x() + (float)(i - 1) / (count - 1) * rect.w();
            float x1 = rect.x() + (float) i / (count - 1) * rect.w();
            float y0 = rect.y() + rect.h() - ((v0 - min) / range) * rect.h();
            float y1 = rect.y() + rect.h() - ((v1 - min) / range) * rect.h();

            emit(new NkDrawCommand.Line(new NkVec2(x0, y0), new NkVec2(x1, y1), 1, fgColor));
        }

        // Draw min/max labels
        String maxLabel = String.format("%.1f", max);
        String minLabel = String.format("%.1f", min);
        emit(new NkDrawCommand.Text(
                new NkRect(rect.x() + 2, rect.y() + 1, font.textWidth(maxLabel), font.height()),
                maxLabel, font, fgColor));
        emit(new NkDrawCommand.Text(
                new NkRect(rect.x() + 2, rect.y() + rect.h() - font.height() - 1,
                        font.textWidth(minLabel), font.height()),
                minLabel, font, fgColor));
    }

    /**
     * Simplified chart — draws the most recent `values.length` samples with auto-range.
     */
    public void chart(float[] values, int count, int offset, NkColor fgColor) {
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            float v = values[(offset + i) % values.length];
            if (v < min) min = v;
            if (v > max) max = v;
        }
        float pad = (max - min) * 0.1f;
        if (pad < 0.001f) pad = 1;
        chart(values, count, offset, min - pad, max + pad, fgColor,
                style.windowBackground.withAlpha(200));
    }

    /** Begins a collapsible tree node. Returns true if expanded. Must call treePop() if returns true. */
    public boolean treePush(String title, boolean defaultOpen) {
        if (currentWindow == null) return false;

        var rect = allocateWidget();
        if (rect == null) return false;

        Boolean state = currentWindow.treeStates.get(title);
        if (state == null) {
            state = defaultOpen;
            currentWindow.treeStates.put(title, state);
        }

        boolean hovering = input.isMouseHovering(rect);
        if (input.isMousePressed(0, rect)) {
            state = !state;
            currentWindow.treeStates.put(title, state);
        }

        // Draw hover highlight
        if (hovering) {
            emit(new NkDrawCommand.FilledRect(rect, 0, style.treeNodeHover));
        }

        // Draw arrow
        String arrow = state ? "v " : "> ";
        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(rect.x(), textY, font.textWidth(arrow + title), font.height()),
                arrow + title, font, style.treeNodeText));

        return state;
    }

    /** Ends a collapsible tree node (only call if treePush returned true). */
    public void treePop() {
        // Tree indentation is handled by layout — no additional action needed
    }

    /** Draws a simple combo box (dropdown). Returns the new selected index. */
    public int combo(String[] items, int selected, float itemHeight) {
        if (items.length == 0) return selected;

        var rect = allocateWidget();
        if (rect == null) return selected;

        String currentText = (selected >= 0 && selected < items.length) ? items[selected] : "";
        String comboId = currentWindow != null ? currentWindow.title + ":" + rect.x() + ":" + rect.y() : "";

        boolean isOpen = currentWindow != null && currentWindow.comboOpen
                && comboId.equals(currentWindow.comboId);

        // Draw combo button
        boolean hovering = input.isMouseHovering(rect);
        emit(new NkDrawCommand.FilledRect(rect, 2,
                hovering ? style.comboButtonHover : style.comboBackground));
        emit(new NkDrawCommand.StrokedRect(rect, 2, 1, style.comboBorder));

        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(rect.x() + 4, textY, font.textWidth(currentText), font.height()),
                currentText, font, style.comboText));

        // Arrow indicator
        String arrow = isOpen ? "^" : "v";
        float arrowX = rect.x() + rect.w() - font.textWidth(arrow) - 4;
        emit(new NkDrawCommand.Text(
                new NkRect(arrowX, textY, font.textWidth(arrow), font.height()),
                arrow, font, style.comboText));

        // Toggle popup
        if (input.isMousePressed(0, rect) && currentWindow != null) {
            if (isOpen) {
                currentWindow.comboOpen = false;
            } else {
                currentWindow.comboOpen = true;
                currentWindow.comboId = comboId;
            }
            isOpen = currentWindow.comboOpen && comboId.equals(currentWindow.comboId);
        }

        // Draw popup on overlay layer (on top of all windows, not clipped by parent)
        if (isOpen) {
            float popupH = items.length * itemHeight + 2;
            float popupY = rect.y() + rect.h();
            var popupRect = new NkRect(rect.x(), popupY, rect.w(), popupH);

            emitOverlay(new NkDrawCommand.Scissor(0, 0, 8192, 8192));
            emitOverlay(new NkDrawCommand.FilledRect(popupRect, 2, style.comboBackground));
            emitOverlay(new NkDrawCommand.StrokedRect(popupRect, 2, 1, style.comboBorder));

            for (int i = 0; i < items.length; i++) {
                float iy = popupY + 1 + i * itemHeight;
                var itemRect = new NkRect(rect.x() + 1, iy, rect.w() - 2, itemHeight);

                boolean itemHover = input.isMouseHovering(itemRect);
                if (itemHover) {
                    emitOverlay(new NkDrawCommand.FilledRect(itemRect, 0, style.comboButtonHover));
                }
                if (i == selected) {
                    emitOverlay(new NkDrawCommand.FilledRect(itemRect, 0,
                            style.checkboxActive.withAlpha(40)));
                }

                float ity = iy + (itemHeight - font.height()) / 2;
                emitOverlay(new NkDrawCommand.Text(
                        new NkRect(itemRect.x() + 4, ity, font.textWidth(items[i]), font.height()),
                        items[i], font, style.comboText));

                if (input.isMousePressed(0, itemRect)) {
                    selected = i;
                    if (currentWindow != null) currentWindow.comboOpen = false;
                }
            }

            // Close popup if clicked outside
            if (input.isMouseClicked(0) && !input.isMouseHovering(popupRect)
                    && !input.isMouseHovering(rect) && currentWindow != null) {
                currentWindow.comboOpen = false;
            }

            // Consume mouse clicks when popup is open so items below don't activate
            input.consumeClick();
        }

        return selected;
    }

    /** Draws a property editor (label + value + drag to change). Returns the new value. */
    public float propertyFloat(String name, float min, float value, float max, float step, float incPerPixel) {
        var rect = allocateWidget();
        if (rect == null) return value;

        // Split rect: [label] [-] [value] [+]
        float btnW = rect.h(); // square buttons
        float labelW = font.textWidth(name) + 8;
        float valueW = rect.w() - labelW - btnW * 2;

        var labelRect = new NkRect(rect.x(), rect.y(), labelW, rect.h());
        var decRect = new NkRect(rect.x() + labelW, rect.y(), btnW, rect.h());
        var valueRect = new NkRect(rect.x() + labelW + btnW, rect.y(), valueW, rect.h());
        var incRect = new NkRect(rect.x() + rect.w() - btnW, rect.y(), btnW, rect.h());

        // Draw decrement button
        emit(new NkDrawCommand.FilledRect(decRect, 2, style.buttonNormal));
        emit(new NkDrawCommand.StrokedRect(decRect, 2, 1, style.buttonBorder));
        float symY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(decRect.x() + (btnW - font.textWidth("-")) / 2, symY,
                        font.textWidth("-"), font.height()),
                "-", font, style.buttonText));

        // Draw label
        float textY = rect.y() + (rect.h() - font.height()) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(labelRect.x() + 4, textY, font.textWidth(name), font.height()),
                name, font, style.labelText));

        // Draw value background
        emit(new NkDrawCommand.FilledRect(valueRect, 2, style.editBackground));
        emit(new NkDrawCommand.StrokedRect(valueRect, 2, 1, style.editBorder));

        // Draw increment button
        emit(new NkDrawCommand.FilledRect(incRect, 2, style.buttonNormal));
        emit(new NkDrawCommand.StrokedRect(incRect, 2, 1, style.buttonBorder));
        emit(new NkDrawCommand.Text(
                new NkRect(incRect.x() + (btnW - font.textWidth("+")) / 2, symY,
                        font.textWidth("+"), font.height()),
                "+", font, style.buttonText));

        // Handle +/- button clicks
        if (windowAcceptsInput()) {
            if (input.isMousePressed(0, decRect)) {
                value -= step > 0 ? step : 0.1f;
                value = Math.max(min, value);
            }
            if (input.isMousePressed(0, incRect)) {
                value += step > 0 ? step : 0.1f;
                value = Math.min(max, value);
            }

            // Handle dragging — don't snap to step during drag for smooth feel
            if (input.isMouseDragging(0, rect)) {
                float dx = input.mouseDeltaX();
                if (dx != 0) {
                    value += dx * incPerPixel;
                    value = Math.max(min, Math.min(max, value));
                }
            }
        }

        // Draw value text
        String valueText = formatFloat(value);
        float vTextX = valueRect.x() + (valueRect.w() - font.textWidth(valueText)) / 2;
        emit(new NkDrawCommand.Text(
                new NkRect(vTextX, textY, font.textWidth(valueText), font.height()),
                valueText, font, style.editText));

        return value;
    }

    /** Draws a tooltip at the mouse position. */
    public void tooltip(String text) {
        if (text == null || text.isEmpty()) return;

        float pad = style.tooltipPadding;
        float w = font.textWidth(text) + pad * 2;
        float h = font.height() + pad * 2;
        float x = input.mouseX() + 12;
        float y = input.mouseY() + 12;

        var rect = new NkRect(x, y, w, h);
        emit(new NkDrawCommand.FilledRect(rect, 2, style.tooltipBackground));
        emit(new NkDrawCommand.StrokedRect(rect, 2, 1, style.tooltipBorder));
        emit(new NkDrawCommand.Text(
                new NkRect(x + pad, y + pad, font.textWidth(text), font.height()),
                text, font, style.tooltipText));
    }

    /** Returns true if the previous widget is being hovered. */
    public boolean isWidgetHovered() {
        // Simple approximation: check if mouse is in the last allocated rect
        var panel = currentPanel();
        if (panel == null) return false;
        // Re-calculate last widget position (approximate)
        return input.isMouseHovering(new NkRect(
                panel.cursorX - (panel.rowDynamic
                        ? (panel.contentW - (panel.rowColumns - 1) * style.itemSpacingX) / panel.rowColumns + style.itemSpacingX
                        : panel.rowItemWidth + style.itemSpacingX),
                panel.cursorY - panel.scrollY,
                panel.rowDynamic
                        ? (panel.contentW - (panel.rowColumns - 1) * style.itemSpacingX) / panel.rowColumns
                        : panel.rowItemWidth,
                panel.rowHeight));
    }

    // ========================= Group API =========================

    /** Begins a scrollable group within the current window. */
    public boolean groupBegin(String title, int flags) {
        var rect = allocateWidget();
        if (rect == null) return false;

        // Draw group background (slightly darker)
        emit(new NkDrawCommand.FilledRect(rect, 0,
                style.windowBackground.withAlpha(200)));
        if ((flags & WINDOW_BORDER) != 0) {
            emit(new NkDrawCommand.StrokedRect(rect, 0, 1, style.windowBorder));
        }

        // Retrieve persisted scroll state for this group
        float scroll = 0;
        if (currentWindow != null) {
            scroll = currentWindow.groupScrollY.getOrDefault(title, 0f);
            // Handle scroll wheel when hovering the group
            if (input.isMouseHovering(rect)) {
                scroll -= input.scrollY() * 20;
                scroll = Math.max(0, scroll);
                currentWindow.groupScrollY.put(title, scroll);
            }
        }

        // Push a new panel for the group
        var panel = new NkPanel();
        float pad = style.groupPadding;
        panel.contentX = rect.x() + pad;
        panel.contentY = rect.y() + pad;
        panel.contentW = rect.w() - pad * 2;
        panel.contentH = rect.h() - pad * 2;
        panel.cursorX = panel.contentX;
        panel.cursorY = panel.contentY;
        panel.scrollY = scroll;
        panel.maxY = panel.contentY;
        panel.clip = rect;

        emit(new NkDrawCommand.Scissor(
                (int) rect.x(), (int) rect.y(), (int) rect.w(), (int) rect.h()));

        panelStack.push(panel);
        layoutRowDynamic(font.height() + style.itemSpacingY, 1);

        return true;
    }

    /** Ends a group. */
    public void groupEnd() {
        if (!panelStack.isEmpty()) {
            panelStack.pop();
        }
        // Restore parent scissor
        var parent = currentPanel();
        if (parent != null && parent.clip != null) {
            emit(new NkDrawCommand.Scissor(
                    (int) parent.clip.x(), (int) parent.clip.y(),
                    (int) parent.clip.w(), (int) parent.clip.h()));
        } else {
            emit(new NkDrawCommand.Scissor(0, 0, 8192, 8192));
        }
    }

    /** Draws a simple color picker (HSV bar). Returns the new color. */
    /**
     * Draws an HSV color picker: saturation-value grid + hue bar + preview swatch.
     * The widget height should be at least 60px for usability.
     */
    public NkColor colorPicker(NkColor color) {
        var rect = allocateWidget();
        if (rect == null) return color;

        // Convert current color to HSV for editing
        float[] hsv = rgbToHsv(color);
        float hue = hsv[0], sat = hsv[1], val = hsv[2];

        // Layout: [SV square] [hue bar] [preview]
        float hueBarW = 16;
        float previewW = 24;
        float gap = 3;
        float svSize = rect.h(); // square
        float svW = Math.min(svSize, rect.w() - hueBarW - previewW - gap * 2);

        // ── Saturation-Value square ──
        // Draw as a grid of colored cells
        var svRect = new NkRect(rect.x(), rect.y(), svW, rect.h());
        int cells = 8;
        float cellW = svW / cells;
        float cellH = rect.h() / cells;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                float s = (float)(cx + 0.5f) / cells;
                float v = 1.0f - (float)(cy + 0.5f) / cells;
                emit(new NkDrawCommand.FilledRect(
                        new NkRect(svRect.x() + cx * cellW, svRect.y() + cy * cellH, cellW + 1, cellH + 1),
                        0, hsvToRgb(hue, s, v)));
            }
        }
        emit(new NkDrawCommand.StrokedRect(svRect, 0, 1, style.editBorder));

        // SV cursor indicator
        float cursorX = svRect.x() + sat * svW;
        float cursorY = svRect.y() + (1 - val) * rect.h();
        emit(new NkDrawCommand.StrokedRect(
                new NkRect(cursorX - 3, cursorY - 3, 6, 6), 0, 1, NkColor.rgb(255, 255, 255)));

        // Handle SV interaction
        if (windowAcceptsInput() && (input.isMouseDragging(0, svRect) || input.isMousePressed(0, svRect))) {
            sat = Math.max(0, Math.min(1, (input.mouseX() - svRect.x()) / svW));
            val = Math.max(0, Math.min(1, 1.0f - (input.mouseY() - svRect.y()) / rect.h()));
            color = hsvToRgb(hue, sat, val);
        }

        // ── Hue bar (vertical) ──
        float hueX = svRect.x() + svW + gap;
        var hueRect = new NkRect(hueX, rect.y(), hueBarW, rect.h());
        int hueSteps = 12;
        float hueStepH = rect.h() / hueSteps;
        for (int i = 0; i < hueSteps; i++) {
            float h = (float) i / hueSteps;
            emit(new NkDrawCommand.FilledRect(
                    new NkRect(hueX, rect.y() + i * hueStepH, hueBarW, hueStepH + 1),
                    0, hsvToRgb(h, 1, 1)));
        }
        emit(new NkDrawCommand.StrokedRect(hueRect, 0, 1, style.editBorder));

        // Hue indicator
        float hueIndicatorY = rect.y() + hue * rect.h();
        emit(new NkDrawCommand.FilledRect(
                new NkRect(hueX - 1, hueIndicatorY - 1, hueBarW + 2, 3), 0, NkColor.rgb(255, 255, 255)));

        // Handle hue interaction
        if (windowAcceptsInput() && (input.isMouseDragging(0, hueRect) || input.isMousePressed(0, hueRect))) {
            hue = Math.max(0, Math.min(0.999f, (input.mouseY() - rect.y()) / rect.h()));
            color = hsvToRgb(hue, sat, val);
        }

        // ── Preview swatch ──
        float previewX = hueX + hueBarW + gap;
        var previewRect = new NkRect(previewX, rect.y(), previewW, rect.h());
        emit(new NkDrawCommand.FilledRect(previewRect, 2, color));
        emit(new NkDrawCommand.StrokedRect(previewRect, 2, 1, style.editBorder));

        return color;
    }

    /**
     * Draws a quick color palette — grid of preset colors. Click to select.
     */
    public NkColor colorPalette(NkColor color, NkColor[] palette) {
        var rect = allocateWidget();
        if (rect == null) return color;

        int cols = Math.min(palette.length, (int)(rect.w() / 16));
        if (cols <= 0) cols = 1;
        int rows = (palette.length + cols - 1) / cols;
        float cellW = rect.w() / cols;
        float cellH = rect.h() / rows;

        for (int i = 0; i < palette.length; i++) {
            int col = i % cols;
            int row = i / cols;
            var cellRect = new NkRect(rect.x() + col * cellW, rect.y() + row * cellH, cellW, cellH);
            emit(new NkDrawCommand.FilledRect(cellRect, 0, palette[i]));

            // Highlight selected
            if (palette[i].r() == color.r() && palette[i].g() == color.g() && palette[i].b() == color.b()) {
                emit(new NkDrawCommand.StrokedRect(cellRect, 0, 2, NkColor.rgb(255, 255, 255)));
            }

            if (windowAcceptsInput() && input.isMousePressed(0, cellRect)) {
                color = palette[i];
            }
        }
        emit(new NkDrawCommand.StrokedRect(rect, 0, 1, style.editBorder));

        return color;
    }

    // ========================= Draw output =========================

    /** Returns draw commands sorted by window focus order (back to front), overlay on top. */
    public List<NkDrawCommand> drawCommands() {
        var sorted = new ArrayList<NkDrawCommand>();
        for (var title : windowOrder) {
            var cmds = windowDrawCommands.get(title);
            if (cmds != null) sorted.addAll(cmds);
        }
        // Overlay (popups, tooltips) renders on top of everything
        sorted.addAll(overlayCommands);
        return sorted;
    }

    /** Clears all draw commands and per-frame state. Call at the end of each frame. */
    public void clear() {
        windowDrawCommands.values().forEach(List::clear);
        overlayCommands.clear();
        activeWindowCommands = null;
        tooltipActive = false;
    }

    /** Returns true if the UI wants the mouse (hovering over any window). */
    public boolean wantsMouse() {
        for (var win : windows.values()) {
            if (!win.closed && !win.collapsed && win.bounds.contains(input.mouseX(), input.mouseY())) {
                return true;
            }
        }
        return false;
    }

    // ========================= Internal =========================

    private NkPanel currentPanel() {
        return panelStack.peek();
    }

    private NkRect allocateWidget() {
        var panel = currentPanel();
        if (panel == null) return null;
        return panel.allocateWidget(style);
    }

    private static String formatFloat(float v) {
        if (v == (int) v) return Integer.toString((int) v);
        return String.format("%.2f", v);
    }

    private static float[] rgbToHsv(NkColor c) {
        float r = c.r() / 255f, g = c.g() / 255f, b = c.b() / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0, s = max > 0 ? d / max : 0, v = max;
        if (d > 0) {
            if (max == r) h = ((g - b) / d + 6) % 6 / 6f;
            else if (max == g) h = ((b - r) / d + 2) / 6f;
            else h = ((r - g) / d + 4) / 6f;
        }
        return new float[]{h, s, v};
    }

    private static NkColor hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 1f / 6) { r = c; g = x; b = 0; }
        else if (h < 2f / 6) { r = x; g = c; b = 0; }
        else if (h < 3f / 6) { r = 0; g = c; b = x; }
        else if (h < 4f / 6) { r = 0; g = x; b = c; }
        else if (h < 5f / 6) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return NkColor.rgb((int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
    }
}

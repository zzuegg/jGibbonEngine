package dev.engine.ui;

/**
 * Layout state for a window or group. Tracks current row layout
 * and cursor position for widget placement.
 */
public class NkPanel {

    // Content region (inside padding/header)
    float contentX, contentY, contentW, contentH;

    // Current cursor position
    float cursorX, cursorY;

    // Current row layout
    float rowHeight;
    int rowColumns;
    int rowIndex;
    boolean rowDynamic = true;
    float rowItemWidth;

    // Scrolling
    float scrollY;
    float maxY; // tracks the maximum Y extent for scroll bounds

    // Clip rect (for scissor)
    NkRect clip;

    void beginRow(float height, int columns, boolean dynamic, float itemWidth) {
        this.rowHeight = height;
        this.rowColumns = columns;
        this.rowIndex = 0;
        this.rowDynamic = dynamic;
        this.rowItemWidth = itemWidth;
        this.cursorX = contentX;
    }

    /** Allocates the next widget rect in the current row. Returns null if row exhausted. */
    NkRect allocateWidget(NkStyle style) {
        if (rowIndex >= rowColumns) {
            // Auto-advance to next row
            cursorY += rowHeight + style.itemSpacingY;
            cursorX = contentX;
            rowIndex = 0;
        }

        float itemW;
        if (rowDynamic) {
            float totalSpacing = (rowColumns - 1) * style.itemSpacingX;
            itemW = (contentW - totalSpacing) / rowColumns;
        } else {
            itemW = rowItemWidth;
        }

        float x = cursorX;
        float y = cursorY - scrollY;

        cursorX += itemW + style.itemSpacingX;
        rowIndex++;

        if (y + rowHeight > maxY) {
            maxY = y + rowHeight;
        }

        return new NkRect(x, y, itemW, rowHeight);
    }
}

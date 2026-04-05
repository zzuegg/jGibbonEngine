package dev.engine.ui;

/**
 * Visual style configuration for the UI. Mutable for easy customization.
 */
public class NkStyle {

    // Window
    public NkColor windowBackground = NkColor.rgba(45, 45, 45, 255);
    public NkColor windowBorder = NkColor.rgba(65, 65, 65, 255);
    public float windowBorderWidth = 1;
    public float windowPadX = 8;
    public float windowPadY = 8;
    public float windowRounding = 0;

    // Window header
    public NkColor headerBackground = NkColor.rgba(40, 40, 40, 255);
    public NkColor headerText = NkColor.rgba(220, 220, 220, 255);
    public float headerHeight = 24;
    public float headerPadX = 6;

    // Button
    public NkColor buttonNormal = NkColor.rgba(60, 60, 60, 255);
    public NkColor buttonHover = NkColor.rgba(75, 75, 75, 255);
    public NkColor buttonActive = NkColor.rgba(50, 50, 50, 255);
    public NkColor buttonBorder = NkColor.rgba(80, 80, 80, 255);
    public NkColor buttonText = NkColor.rgba(220, 220, 220, 255);
    public float buttonBorderWidth = 1;
    public float buttonRounding = 2;
    public float buttonPadX = 4;
    public float buttonPadY = 2;

    // Label
    public NkColor labelText = NkColor.rgba(200, 200, 200, 255);

    // Checkbox
    public NkColor checkboxBackground = NkColor.rgba(50, 50, 50, 255);
    public NkColor checkboxBorder = NkColor.rgba(100, 100, 100, 255);
    public NkColor checkboxActive = NkColor.rgba(90, 160, 230, 255);
    public NkColor checkboxCursor = NkColor.rgba(220, 220, 220, 255);
    public NkColor checkboxText = NkColor.rgba(200, 200, 200, 255);
    public float checkboxSize = 14;
    public float checkboxPadding = 3;

    // Slider
    public NkColor sliderBackground = NkColor.rgba(50, 50, 50, 255);
    public NkColor sliderBar = NkColor.rgba(38, 38, 38, 255);
    public NkColor sliderBarFilled = NkColor.rgba(90, 160, 230, 255);
    public NkColor sliderCursor = NkColor.rgba(200, 200, 200, 255);
    public NkColor sliderCursorHover = NkColor.rgba(230, 230, 230, 255);
    public NkColor sliderCursorActive = NkColor.rgba(90, 160, 230, 255);
    public float sliderBarHeight = 6;
    public float sliderCursorSize = 14;

    // Progress bar
    public NkColor progressBackground = NkColor.rgba(50, 50, 50, 255);
    public NkColor progressFill = NkColor.rgba(90, 160, 230, 255);
    public NkColor progressBorder = NkColor.rgba(80, 80, 80, 255);
    public float progressRounding = 2;

    // Combo / Dropdown
    public NkColor comboBackground = NkColor.rgba(50, 50, 50, 255);
    public NkColor comboBorder = NkColor.rgba(80, 80, 80, 255);
    public NkColor comboText = NkColor.rgba(200, 200, 200, 255);
    public NkColor comboButtonNormal = NkColor.rgba(60, 60, 60, 255);
    public NkColor comboButtonHover = NkColor.rgba(75, 75, 75, 255);

    // Text input
    public NkColor editBackground = NkColor.rgba(38, 38, 38, 255);
    public NkColor editBorder = NkColor.rgba(80, 80, 80, 255);
    public NkColor editText = NkColor.rgba(220, 220, 220, 255);
    public NkColor editCursor = NkColor.rgba(220, 220, 220, 255);
    public NkColor editSelection = NkColor.rgba(90, 160, 230, 100);

    // Tree
    public NkColor treeNodeText = NkColor.rgba(200, 200, 200, 255);
    public NkColor treeNodeHover = NkColor.rgba(55, 55, 55, 255);

    // Tooltip
    public NkColor tooltipBackground = NkColor.rgba(60, 60, 60, 240);
    public NkColor tooltipBorder = NkColor.rgba(100, 100, 100, 255);
    public NkColor tooltipText = NkColor.rgba(220, 220, 220, 255);
    public float tooltipPadding = 4;

    // Scrollbar
    public NkColor scrollbarBackground = NkColor.rgba(35, 35, 35, 255);
    public NkColor scrollbarThumb = NkColor.rgba(80, 80, 80, 255);
    public NkColor scrollbarThumbHover = NkColor.rgba(100, 100, 100, 255);
    public float scrollbarWidth = 12;

    // Separator
    public NkColor separatorColor = NkColor.rgba(80, 80, 80, 255);

    // Section (accordion headers)
    public NkColor sectionBackground = NkColor.rgba(50, 50, 55, 255);
    public NkColor sectionBackgroundHover = NkColor.rgba(60, 65, 75, 255);
    public NkColor sectionText = NkColor.rgba(220, 220, 220, 255);
    public NkColor sectionBorder = NkColor.rgba(70, 70, 70, 255);

    // General spacing
    public float itemSpacingX = 4;
    public float itemSpacingY = 4;
    public float groupPadding = 4;

    /** Returns a deep copy of this style. */
    public NkStyle copy() {
        var s = new NkStyle();
        // This is a value-style copy since all fields are records or primitives
        s.windowBackground = windowBackground;
        s.windowBorder = windowBorder;
        s.windowBorderWidth = windowBorderWidth;
        s.windowPadX = windowPadX;
        s.windowPadY = windowPadY;
        s.windowRounding = windowRounding;
        s.headerBackground = headerBackground;
        s.headerText = headerText;
        s.headerHeight = headerHeight;
        s.headerPadX = headerPadX;
        s.buttonNormal = buttonNormal;
        s.buttonHover = buttonHover;
        s.buttonActive = buttonActive;
        s.buttonBorder = buttonBorder;
        s.buttonText = buttonText;
        s.buttonBorderWidth = buttonBorderWidth;
        s.buttonRounding = buttonRounding;
        s.buttonPadX = buttonPadX;
        s.buttonPadY = buttonPadY;
        s.labelText = labelText;
        s.checkboxBackground = checkboxBackground;
        s.checkboxBorder = checkboxBorder;
        s.checkboxActive = checkboxActive;
        s.checkboxCursor = checkboxCursor;
        s.checkboxText = checkboxText;
        s.checkboxSize = checkboxSize;
        s.checkboxPadding = checkboxPadding;
        s.sliderBackground = sliderBackground;
        s.sliderBar = sliderBar;
        s.sliderBarFilled = sliderBarFilled;
        s.sliderCursor = sliderCursor;
        s.sliderCursorHover = sliderCursorHover;
        s.sliderCursorActive = sliderCursorActive;
        s.sliderBarHeight = sliderBarHeight;
        s.sliderCursorSize = sliderCursorSize;
        s.progressBackground = progressBackground;
        s.progressFill = progressFill;
        s.progressBorder = progressBorder;
        s.progressRounding = progressRounding;
        s.comboBackground = comboBackground;
        s.comboBorder = comboBorder;
        s.comboText = comboText;
        s.comboButtonNormal = comboButtonNormal;
        s.comboButtonHover = comboButtonHover;
        s.editBackground = editBackground;
        s.editBorder = editBorder;
        s.editText = editText;
        s.editCursor = editCursor;
        s.editSelection = editSelection;
        s.treeNodeText = treeNodeText;
        s.treeNodeHover = treeNodeHover;
        s.tooltipBackground = tooltipBackground;
        s.tooltipBorder = tooltipBorder;
        s.tooltipText = tooltipText;
        s.tooltipPadding = tooltipPadding;
        s.scrollbarBackground = scrollbarBackground;
        s.scrollbarThumb = scrollbarThumb;
        s.scrollbarThumbHover = scrollbarThumbHover;
        s.scrollbarWidth = scrollbarWidth;
        s.separatorColor = separatorColor;
        s.sectionBackground = sectionBackground;
        s.sectionBackgroundHover = sectionBackgroundHover;
        s.sectionText = sectionText;
        s.sectionBorder = sectionBorder;
        s.itemSpacingX = itemSpacingX;
        s.itemSpacingY = itemSpacingY;
        s.groupPadding = groupPadding;
        return s;
    }
}

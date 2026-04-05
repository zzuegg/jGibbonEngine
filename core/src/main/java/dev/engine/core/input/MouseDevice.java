package dev.engine.core.input;

import dev.engine.core.versioned.Reference;

public interface MouseDevice extends InputDevice {
    void setCursorMode(CursorMode mode);
    CursorMode cursorMode();
    Reference<CursorMode> cursorModeRef();
}

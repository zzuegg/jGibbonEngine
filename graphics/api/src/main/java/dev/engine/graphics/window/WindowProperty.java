package dev.engine.graphics.window;

import dev.engine.core.property.PropertyKey;

public interface WindowProperty {
    PropertyKey<WindowHandle, String>  TITLE          = PropertyKey.of("window.title", String.class);
    PropertyKey<WindowHandle, Boolean> VSYNC          = PropertyKey.of("window.vsync", Boolean.class);
    PropertyKey<WindowHandle, Boolean> RESIZABLE      = PropertyKey.of("window.resizable", Boolean.class);
    PropertyKey<WindowHandle, Boolean> FULLSCREEN     = PropertyKey.of("window.fullscreen", Boolean.class);
    PropertyKey<WindowHandle, Boolean> DECORATED      = PropertyKey.of("window.decorated", Boolean.class);
    PropertyKey<WindowHandle, Boolean> VISIBLE        = PropertyKey.of("window.visible", Boolean.class);
    PropertyKey<WindowHandle, Integer> SWAP_INTERVAL  = PropertyKey.of("window.swapInterval", Integer.class);
    PropertyKey<WindowHandle, Boolean> ALWAYS_ON_TOP  = PropertyKey.of("window.alwaysOnTop", Boolean.class);
}

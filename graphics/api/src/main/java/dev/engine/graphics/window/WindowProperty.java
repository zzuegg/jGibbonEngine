package dev.engine.graphics.window;

import dev.engine.core.property.PropertyKey;

public interface WindowProperty {
    PropertyKey<String>  TITLE          = PropertyKey.of("window.title", String.class);
    PropertyKey<Boolean> VSYNC          = PropertyKey.of("window.vsync", Boolean.class);
    PropertyKey<Boolean> RESIZABLE      = PropertyKey.of("window.resizable", Boolean.class);
    PropertyKey<Boolean> FULLSCREEN     = PropertyKey.of("window.fullscreen", Boolean.class);
    PropertyKey<Boolean> DECORATED      = PropertyKey.of("window.decorated", Boolean.class);
    PropertyKey<Boolean> VISIBLE        = PropertyKey.of("window.visible", Boolean.class);
    PropertyKey<Integer> SWAP_INTERVAL  = PropertyKey.of("window.swapInterval", Integer.class);
}

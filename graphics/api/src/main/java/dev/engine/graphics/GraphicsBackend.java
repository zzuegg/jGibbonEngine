package dev.engine.graphics;

import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * The result of creating a graphics backend — bundles the toolkit, window, and device.
 */
public record GraphicsBackend(WindowToolkit toolkit, WindowHandle window, RenderDevice device) {}

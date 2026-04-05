package dev.engine.graphics;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Base class for graphics backend configuration. Each backend extends this
 * with backend-specific settings while inheriting common configuration.
 *
 * <p>Common settings apply across all backends. Backend-specific subclasses
 * add their own settings (present mode for Vulkan, etc.) and implement
 * {@link #createDevice} to construct the appropriate render device.
 *
 * <pre>{@code
 * // OpenGL with defaults
 * var gfx = new OpenGlConfig(toolkit, glBindings);
 *
 * // Vulkan with specific settings
 * var gfx = VulkanConfig.builder(toolkit, vkBindings, surfaceCreator)
 *     .presentMode(VulkanConfig.PresentMode.MAILBOX)
 *     .validation(true)
 *     .build();
 * }</pre>
 */
public abstract class GraphicsConfig {

    private final WindowToolkit toolkit;
    private boolean headless;
    private boolean validation;

    protected GraphicsConfig(WindowToolkit toolkit) {
        this.toolkit = toolkit;
    }

    /** The window toolkit used for window creation. */
    public WindowToolkit toolkit() { return toolkit; }

    /** Whether this is a headless (offscreen) configuration. */
    public boolean headless() { return headless; }

    /** Whether to enable backend validation layers (Vulkan validation, WebGPU error reporting). */
    public boolean validation() { return validation; }

    public GraphicsConfig headless(boolean headless) { this.headless = headless; return this; }
    public GraphicsConfig validation(boolean validation) { this.validation = validation; return this; }

    /**
     * Creates the complete backend infrastructure: window + render device.
     * Called by the engine during initialization.
     */
    public final GraphicsBackend create(WindowDescriptor window) {
        var win = toolkit.createWindow(window);
        var device = createDevice(win);
        return new GraphicsBackend(toolkit, win, device);
    }

    /**
     * Creates the backend-specific render device for the given window.
     * Implemented by each backend config.
     */
    protected abstract RenderDevice createDevice(dev.engine.graphics.window.WindowHandle window);
}

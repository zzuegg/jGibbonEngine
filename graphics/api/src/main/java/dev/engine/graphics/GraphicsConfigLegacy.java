package dev.engine.graphics;

/**
 * Legacy GraphicsConfig for backward compatibility with GraphicsBackendFactory.
 * @deprecated Use backend-specific GraphicsConfig subclasses instead.
 */
@Deprecated
public class GraphicsConfigLegacy extends GraphicsConfig {

    public static final GraphicsConfigLegacy DEFAULT = new GraphicsConfigLegacy(false);
    public static final GraphicsConfigLegacy HEADLESS = new GraphicsConfigLegacy(true);

    public GraphicsConfigLegacy(boolean headless) {
        super(null); // No toolkit — legacy path creates its own
        headless(headless);
    }

    @Override
    protected RenderDevice createDevice(dev.engine.graphics.window.WindowHandle window) {
        throw new UnsupportedOperationException("Legacy config cannot create devices directly. Use GraphicsBackendFactory.");
    }
}

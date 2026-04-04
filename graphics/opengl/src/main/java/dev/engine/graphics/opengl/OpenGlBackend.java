package dev.engine.graphics.opengl;

import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.window.WindowToolkit;

/**
 * OpenGL backend factory.
 * Takes an externally provided window toolkit and creates an OpenGL render device.
 * The toolkit must create windows with an OpenGL context.
 */
public final class OpenGlBackend {

    private OpenGlBackend() {}

    public static GraphicsBackendFactory factory(WindowToolkit toolkit, GlBindings gl) {
        return (windowDesc, config) -> {
            var window = toolkit.createWindow(windowDesc);
            var device = new GlRenderDevice(window, gl);
            return new GraphicsBackend(toolkit, window, device);
        };
    }
}

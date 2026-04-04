package dev.engine.graphics.opengl;

import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

/**
 * OpenGL backend factory.
 * Creates GLFW window (with OpenGL hints) + OpenGL 4.5 render device.
 */
public final class OpenGlBackend {

    private OpenGlBackend() {}

    public static GraphicsBackendFactory factory(GlBindings gl) {
        return windowDesc -> {
            var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
            var window = toolkit.createWindow(windowDesc);
            var device = new GlRenderDevice(window, gl);
            return new GraphicsBackend(toolkit, window, device);
        };
    }
}

package dev.engine.graphics.opengl;

import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.window.WindowDescriptor;

/**
 * OpenGL backend factory for BaseApplication.
 * Creates GLFW window + OpenGL 4.5 render device.
 */
public final class OpenGlBackend {

    private OpenGlBackend() {}

    public static BaseApplication.BackendFactory factory() {
        return config -> {
            var toolkit = new GlfwWindowToolkit();
            var window = toolkit.createWindow(
                    new WindowDescriptor(config.windowTitle(), config.windowWidth(), config.windowHeight()));
            var device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
            return new BaseApplication.BackendInstance(toolkit, window, device);
        };
    }
}

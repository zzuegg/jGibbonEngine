package dev.engine.graphics.opengl;

import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.graphics.window.WindowDescriptor;

/**
 * OpenGL backend factory for BaseApplication.
 * Creates GLFW window (with OpenGL hints) + OpenGL 4.5 render device.
 */
public final class OpenGlBackend {

    private OpenGlBackend() {}

    public static BaseApplication.BackendFactory factory(GlBindings gl) {
        return factory(gl, null);
    }

    public static BaseApplication.BackendFactory factory(GlBindings gl, ShaderCompiler compiler) {
        return config -> {
            var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
            var window = toolkit.createWindow(
                    new WindowDescriptor(config.windowTitle(), config.windowWidth(), config.windowHeight()));
            var device = new GlRenderDevice(window, gl);
            if (compiler != null) {
                return new BaseApplication.BackendInstance(toolkit, window, device, compiler);
            }
            return new BaseApplication.BackendInstance(toolkit, window, device);
        };
    }
}

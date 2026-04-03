package dev.engine.providers.lwjgl.graphics.opengl;

import dev.engine.core.gpu.GpuMemory;
import dev.engine.core.gpu.NativeGpuMemory;
import dev.engine.graphics.platform.Platform;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

import java.lang.foreign.Arena;

/**
 * Desktop OpenGL 4.5 platform using LWJGL + GLFW.
 */
public class OpenGlPlatform implements Platform {

    @Override
    public String name() {
        return "Desktop OpenGL";
    }

    @Override
    public WindowToolkit createWindowToolkit() {
        return new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
    }

    @Override
    public RenderDevice createRenderDevice(WindowHandle window, int width, int height) {
        return new GlRenderDevice(window, new LwjglGlBindings());
    }

    @Override
    public GpuMemory allocateMemory(long size) {
        return new NativeGpuMemory(Arena.global().allocate(size));
    }
}

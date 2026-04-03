package dev.engine.providers.jwebgpu;

import dev.engine.core.gpu.GpuMemory;
import dev.engine.core.gpu.NativeGpuMemory;
import dev.engine.graphics.platform.Platform;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

import java.lang.foreign.Arena;

/**
 * Desktop WebGPU platform using jwebgpu (wgpu-native) + GLFW.
 */
public class WebGpuDesktopPlatform implements Platform {

    @Override
    public String name() {
        return "Desktop WebGPU";
    }

    @Override
    public WindowToolkit createWindowToolkit() {
        return new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
    }

    @Override
    public RenderDevice createRenderDevice(WindowHandle window, int width, int height) {
        return new WgpuRenderDevice(window, new JWebGpuBindings());
    }

    @Override
    public GpuMemory allocateMemory(long size) {
        return new NativeGpuMemory(Arena.global().allocate(size));
    }
}

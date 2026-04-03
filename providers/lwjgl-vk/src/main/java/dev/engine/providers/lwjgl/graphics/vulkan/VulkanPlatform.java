package dev.engine.providers.lwjgl.graphics.vulkan;

import dev.engine.core.gpu.GpuMemory;
import dev.engine.core.gpu.NativeGpuMemory;
import dev.engine.graphics.platform.Platform;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.vulkan.VkRenderDevice;
import dev.engine.graphics.vulkan.VulkanBackend;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

import java.lang.foreign.Arena;

/**
 * Desktop Vulkan platform using LWJGL + GLFW.
 *
 * <p>Requires a {@link VulkanBackend.SurfaceCreator} to bridge between
 * the windowing system and Vulkan surface creation.
 */
public class VulkanPlatform implements Platform {

    private final VulkanBackend.SurfaceCreator surfaceCreator;

    public VulkanPlatform(VulkanBackend.SurfaceCreator surfaceCreator) {
        this.surfaceCreator = surfaceCreator;
    }

    @Override
    public String name() {
        return "Desktop Vulkan";
    }

    @Override
    public WindowToolkit createWindowToolkit() {
        return new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
    }

    @Override
    public RenderDevice createRenderDevice(WindowHandle window, int width, int height) {
        var extensions = surfaceCreator.requiredInstanceExtensions();
        long windowHandle = window.nativeHandle();
        return new VkRenderDevice(
                new LwjglVkBindings(), extensions,
                instance -> surfaceCreator.createSurface(instance, windowHandle),
                width, height);
    }

    @Override
    public GpuMemory allocateMemory(long size) {
        return new NativeGpuMemory(Arena.global().allocate(size));
    }
}

package dev.engine.tests.screenshot;

import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.vulkan.VkRenderDevice;
import dev.engine.graphics.vulkan.VulkanBackend;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.providers.jwebgpu.JWebGpuBindings;
import dev.engine.providers.lwjgl.graphics.vulkan.LwjglVkBindings;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

/**
 * Graphics backends available for screenshot testing.
 */
public enum Backend {

    OPENGL {
        @Override
        public GraphicsBackendFactory factory() {
            return (windowDesc, config) -> {
                var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
                var window = toolkit.createWindow(windowDesc);
                var device = new GlRenderDevice(window,
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());
                return new GraphicsBackend(toolkit, window, device);
            };
        }

        @Override public boolean isAvailable() { return true; }
    },

    VULKAN {
        @Override
        public GraphicsBackendFactory factory() {
            return (windowDesc, config) -> {
                var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
                var window = toolkit.createWindow(windowDesc);
                var device = new VkRenderDevice(
                        new LwjglVkBindings(),
                        GlfwWindowToolkit.getRequiredVulkanExtensions(),
                        instance -> GlfwWindowToolkit.createVulkanSurfaceFromHandle(
                                instance, window.nativeHandle()),
                        windowDesc.width(), windowDesc.height());
                return new GraphicsBackend(toolkit, window, device);
            };
        }

        @Override public boolean isAvailable() { return true; }
    },

    WEBGPU {
        @Override
        public GraphicsBackendFactory factory() {
            return (windowDesc, config) -> {
                var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
                var window = toolkit.createWindow(windowDesc);
                var device = new WgpuRenderDevice(window, new dev.engine.providers.wgpu.FfmWgpuBindings());
                return new GraphicsBackend(toolkit, window, device);
            };
        }

        @Override
        public boolean isAvailable() {
            return new dev.engine.providers.wgpu.FfmWgpuBindings().isAvailable();
        }
    };

    public abstract GraphicsBackendFactory factory();
    public abstract boolean isAvailable();
}

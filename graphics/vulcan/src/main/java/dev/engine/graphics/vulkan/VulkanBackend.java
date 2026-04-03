package dev.engine.graphics.vulkan;

import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Vulkan backend factory for BaseApplication.
 * Requires a WindowToolkit that supports Vulkan surface creation.
 *
 * <pre>{@code
 * // With GLFW:
 * new MyGame().launch(config, VulkanBackend.factory(
 *     new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS),
 *     surfaceCreator,
 *     vkBindings));
 * }</pre>
 */
public final class VulkanBackend {

    private VulkanBackend() {}

    /**
     * Creates a Vulkan backend factory using the given window toolkit.
     * The caller provides a surface creation function since it's toolkit-specific.
     *
     * @param toolkit        the window toolkit (e.g., GLFW with NO_API hints)
     * @param surfaceCreator given (VkInstance handle, nativeWindowHandle) returns VkSurfaceKHR handle
     * @param vk             the Vulkan bindings implementation
     */
    public static BaseApplication.BackendFactory factory(
            WindowToolkit toolkit,
            SurfaceCreator surfaceCreator,
            VkBindings vk) {
        return factory(toolkit, surfaceCreator, vk, null);
    }

    public static BaseApplication.BackendFactory factory(
            WindowToolkit toolkit,
            SurfaceCreator surfaceCreator,
            VkBindings vk,
            ShaderCompiler compiler) {
        return config -> {
            var window = toolkit.createWindow(
                    new WindowDescriptor(config.windowTitle(), config.windowWidth(), config.windowHeight()));
            var extensions = surfaceCreator.requiredInstanceExtensions();
            long windowHandle = window.nativeHandle();
            var device = new VkRenderDevice(vk, extensions,
                    instance -> surfaceCreator.createSurface(instance, windowHandle),
                    window.width(), window.height());
            if (compiler != null) {
                return new BaseApplication.BackendInstance(toolkit, window, device, compiler);
            }
            return new BaseApplication.BackendInstance(toolkit, window, device);
        };
    }

    /**
     * Creates a Vulkan surface from a VkInstance handle and a native window handle.
     * Implemented by the windowing toolkit (GLFW, SDL3, etc.).
     */
    public interface SurfaceCreator {
        String[] requiredInstanceExtensions();
        long createSurface(long instance, long windowHandle);
    }
}

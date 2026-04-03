package dev.engine.graphics.vulkan;

import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Vulkan backend factory for BaseApplication.
 * Requires a WindowToolkit that supports Vulkan surface creation.
 *
 * <pre>{@code
 * // With GLFW:
 * new MyGame().launch(config, VulkanBackend.factory(
 *     new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS)));
 * }</pre>
 */
public final class VulkanBackend {

    private VulkanBackend() {}

    /**
     * Creates a Vulkan backend factory using the given window toolkit.
     * The caller provides a surface creation function since it's toolkit-specific.
     *
     * @param toolkit        the window toolkit (e.g., GLFW with NO_API hints)
     * @param surfaceCreator given (VkInstance, nativeWindowHandle) → VkSurfaceKHR
     */
    public static BaseApplication.BackendFactory factory(
            WindowToolkit toolkit,
            SurfaceCreator surfaceCreator) {
        return config -> {
            var window = toolkit.createWindow(
                    new WindowDescriptor(config.windowTitle(), config.windowWidth(), config.windowHeight()));
            var extensions = surfaceCreator.requiredInstanceExtensions();
            long windowHandle = window.nativeHandle();
            var device = new VkRenderDevice(extensions,
                    instance -> surfaceCreator.createSurface(instance, windowHandle),
                    window.width(), window.height());
            return new BaseApplication.BackendInstance(toolkit, window, device);
        };
    }

    /**
     * Creates a Vulkan surface from a VkInstance and a native window handle.
     * Implemented by the windowing toolkit (GLFW, SDL3, etc.).
     */
    public interface SurfaceCreator {
        org.lwjgl.PointerBuffer requiredInstanceExtensions();
        long createSurface(org.lwjgl.vulkan.VkInstance instance, long windowHandle);
    }
}

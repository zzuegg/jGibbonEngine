package dev.engine.graphics.vulkan;

import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Vulkan backend factory.
 * Requires a WindowToolkit that supports Vulkan surface creation.
 *
 * <pre>{@code
 * var platform = DesktopPlatform.builder().build();
 * new MyGame().launch(config.toBuilder()
 *     .graphicsBackend(VulkanBackend.factory(toolkit, surfaceCreator, vkBindings))
 *     .build());
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
    public static GraphicsBackendFactory factory(
            WindowToolkit toolkit,
            SurfaceCreator surfaceCreator,
            VkBindings vk) {
        return (windowDesc, config) -> {
            var window = toolkit.createWindow(windowDesc);
            var extensions = surfaceCreator.requiredInstanceExtensions();
            long windowHandle = window.nativeHandle();
            var device = new VkRenderDevice(vk, extensions,
                    instance -> surfaceCreator.createSurface(instance, windowHandle),
                    window.width(), window.height());
            return new GraphicsBackend(toolkit, window, device);
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

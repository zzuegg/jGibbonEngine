package dev.engine.graphics.vulkan;

import dev.engine.graphics.GraphicsConfig;
import dev.engine.graphics.PresentMode;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Vulkan graphics configuration with backend-specific settings.
 *
 * <pre>{@code
 * var gfx = VulkanConfig.builder(toolkit, vkBindings, surfaceCreator)
 *     .presentMode(PresentMode.MAILBOX)
 *     .validation(true)
 *     .build();
 * }</pre>
 */
public final class VulkanConfig extends GraphicsConfig {

    /** Preferred swapchain surface format. */
    public enum SurfaceFormat {
        BGRA8_UNORM(VkBindings.VK_FORMAT_B8G8R8A8_UNORM),
        RGBA8_UNORM(VkBindings.VK_FORMAT_R8G8B8A8_UNORM),
        BGRA8_SRGB(VkBindings.VK_FORMAT_B8G8R8A8_SRGB),
        RGBA8_SRGB(VkBindings.VK_FORMAT_R8G8B8A8_SRGB);

        final int vkValue;
        SurfaceFormat(int vkValue) { this.vkValue = vkValue; }
    }

    private final VkBindings vk;
    private final VulkanBackend.SurfaceCreator surfaceCreator;
    private final SurfaceFormat surfaceFormat;

    private VulkanConfig(Builder builder) {
        super(builder.toolkit);
        this.vk = builder.vk;
        this.surfaceCreator = builder.surfaceCreator;
        this.surfaceFormat = builder.surfaceFormat;
        headless(builder.headless);
        validation(builder.validation);
        presentMode(builder.presentMode);
    }

    public SurfaceFormat surfaceFormat() { return surfaceFormat; }
    public VkBindings bindings() { return vk; }

    @Override
    protected RenderDevice createDevice(WindowHandle window) {
        long windowHandle = window.nativeHandle();
        var extensions = surfaceCreator.requiredInstanceExtensions();
        int vkPresentMode = switch (presentMode()) {
            case IMMEDIATE -> VkBindings.VK_PRESENT_MODE_IMMEDIATE_KHR;
            case MAILBOX -> VkBindings.VK_PRESENT_MODE_MAILBOX_KHR;
            case FIFO -> VkBindings.VK_PRESENT_MODE_FIFO_KHR;
        };
        return new VkRenderDevice(vk, extensions,
                instance -> surfaceCreator.createSurface(instance, windowHandle),
                window.width(), window.height(),
                surfaceFormat.vkValue, vkPresentMode);
    }

    public static Builder builder(WindowToolkit toolkit, VkBindings vk, VulkanBackend.SurfaceCreator surfaceCreator) {
        return new Builder(toolkit, vk, surfaceCreator);
    }

    public static class Builder {
        private final WindowToolkit toolkit;
        private final VkBindings vk;
        private final VulkanBackend.SurfaceCreator surfaceCreator;
        private PresentMode presentMode = PresentMode.FIFO;
        private SurfaceFormat surfaceFormat = SurfaceFormat.BGRA8_UNORM;
        private boolean headless;
        private boolean validation;

        Builder(WindowToolkit toolkit, VkBindings vk, VulkanBackend.SurfaceCreator surfaceCreator) {
            this.toolkit = toolkit;
            this.vk = vk;
            this.surfaceCreator = surfaceCreator;
        }

        public Builder presentMode(PresentMode mode) { this.presentMode = mode; return this; }
        public Builder surfaceFormat(SurfaceFormat format) { this.surfaceFormat = format; return this; }
        public Builder headless(boolean headless) { this.headless = headless; return this; }
        public Builder validation(boolean validation) { this.validation = validation; return this; }

        public VulkanConfig build() { return new VulkanConfig(this); }
    }
}

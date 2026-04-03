package dev.engine.graphics.vulkan;

/**
 * Manages per-swapchain-image framebuffers and a shared depth buffer.
 */
class VkFramebufferSet implements AutoCloseable {

    private final VkBindings vk;
    private final long device;
    private final long physicalDevice;

    private long depthImage = VkBindings.VK_NULL_HANDLE;
    private long depthMemory = VkBindings.VK_NULL_HANDLE;
    private long depthImageView = VkBindings.VK_NULL_HANDLE;
    private long[] framebuffers;

    VkFramebufferSet(VkBindings vk, long device, long physicalDevice) {
        this.vk = vk;
        this.device = device;
        this.physicalDevice = physicalDevice;
    }

    void create(VkSwapchain swapchain, long renderPass, int depthFormat) {
        int width = swapchain.width();
        int height = swapchain.height();

        // Create depth image + view
        var depthAlloc = vk.createImageNoView(device, physicalDevice,
                width, height, 1, 1, 1,
                depthFormat, VkBindings.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                VkBindings.VK_IMAGE_TYPE_2D, 0);
        depthImage = depthAlloc.image();
        depthMemory = depthAlloc.memory();

        depthImageView = vk.createImageView(device, depthImage, depthFormat,
                VkBindings.VK_IMAGE_VIEW_TYPE_2D, VkBindings.VK_IMAGE_ASPECT_DEPTH_BIT,
                0, 1, 0, 1);

        // Create framebuffers (one per swapchain image)
        framebuffers = new long[swapchain.imageCount()];
        for (int i = 0; i < swapchain.imageCount(); i++) {
            framebuffers[i] = vk.createFramebuffer(device, renderPass,
                    new long[]{swapchain.imageView(i), depthImageView},
                    width, height);
        }
    }

    long framebuffer(int index) { return framebuffers[index]; }
    long depthImageView() { return depthImageView; }

    @Override
    public void close() {
        if (framebuffers != null) {
            for (long fb : framebuffers) {
                if (fb != VkBindings.VK_NULL_HANDLE) vk.destroyFramebuffer(device, fb);
            }
        }
        if (depthImageView != VkBindings.VK_NULL_HANDLE) vk.destroyImageView(device, depthImageView);
        if (depthImage != VkBindings.VK_NULL_HANDLE) vk.destroyImage(device, depthImage);
        if (depthMemory != VkBindings.VK_NULL_HANDLE) vk.freeMemory(device, depthMemory);
    }
}

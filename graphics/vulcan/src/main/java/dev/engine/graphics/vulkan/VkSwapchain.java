package dev.engine.graphics.vulkan;

/**
 * Manages the Vulkan swapchain lifecycle: creation, image acquisition, presentation, and recreation.
 */
class VkSwapchain implements AutoCloseable {

    private final VkBindings vk;
    private final long device;
    private final long physicalDevice;
    private final long surface;

    private long swapchain = VkBindings.VK_NULL_HANDLE;
    private int imageFormat;
    private int width;
    private int height;
    private long[] images;
    private long[] imageViews;

    VkSwapchain(VkBindings vk, long device, long physicalDevice, long surface) {
        this.vk = vk;
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.surface = surface;
    }

    void create(int requestedWidth, int requestedHeight) {
        long oldSwapchain = swapchain;

        // Destroy old image views before creating new swapchain
        if (oldSwapchain != VkBindings.VK_NULL_HANDLE) {
            destroyImageViews();
        }

        var result = vk.createSwapchain(device, physicalDevice, surface,
                requestedWidth, requestedHeight, oldSwapchain);

        // Destroy old swapchain handle
        if (oldSwapchain != VkBindings.VK_NULL_HANDLE) {
            vk.destroySwapchain(device, oldSwapchain);
        }

        this.swapchain = result.swapchain();
        this.imageFormat = result.format();
        this.width = result.width();
        this.height = result.height();
        this.images = result.images();
        this.imageViews = result.imageViews();
    }

    int acquireNextImage(long semaphore) {
        return vk.acquireNextImage(device, swapchain, semaphore);
    }

    int present(long queue, long waitSemaphore, int imageIndex) {
        return vk.queuePresent(queue, swapchain, imageIndex, waitSemaphore);
    }

    long swapchain() { return swapchain; }
    long image(int index) { return images[index]; }
    int imageFormat() { return imageFormat; }
    int width() { return width; }
    int height() { return height; }
    int imageCount() { return images != null ? images.length : 0; }
    long imageView(int index) { return imageViews[index]; }

    private void destroyImageViews() {
        if (imageViews != null) {
            for (long view : imageViews) {
                if (view != VkBindings.VK_NULL_HANDLE) vk.destroyImageView(device, view);
            }
        }
    }

    @Override
    public void close() {
        destroyImageViews();
        if (swapchain != VkBindings.VK_NULL_HANDLE) {
            vk.destroySwapchain(device, swapchain);
            swapchain = VkBindings.VK_NULL_HANDLE;
        }
    }
}

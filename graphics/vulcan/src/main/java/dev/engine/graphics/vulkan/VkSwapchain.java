package dev.engine.graphics.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages the Vulkan swapchain lifecycle: creation, image acquisition, presentation, and recreation.
 */
class VkSwapchain implements AutoCloseable {

    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    private final long surface;
    private final int graphicsQueueFamily;

    private long swapchain = VK_NULL_HANDLE;
    private int imageFormat;
    private int width;
    private int height;
    private long[] images;
    private long[] imageViews;

    VkSwapchain(VkDevice device, VkPhysicalDevice physicalDevice, long surface, int graphicsQueueFamily) {
        this.device = device;
        this.physicalDevice = physicalDevice;
        this.surface = surface;
        this.graphicsQueueFamily = graphicsQueueFamily;
    }

    void create(int requestedWidth, int requestedHeight) {
        try (var stack = stackPush()) {
            var capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            // Choose format
            IntBuffer formatCount = stack.ints(0);
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
            var formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);

            // Prefer linear (UNORM) format to match OpenGL's default framebuffer behavior.
            // SRGB format applies gamma correction which makes colors appear different.
            int chosenFormat = VK_FORMAT_B8G8R8A8_UNORM;
            int chosenColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
            boolean found = false;
            for (int i = 0; i < formats.capacity(); i++) {
                if (formats.get(i).format() == VK_FORMAT_B8G8R8A8_UNORM) {
                    chosenColorSpace = formats.get(i).colorSpace();
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Fallback to first available
                chosenFormat = formats.get(0).format();
                chosenColorSpace = formats.get(0).colorSpace();
            }
            this.imageFormat = chosenFormat;

            // Choose present mode (FIFO = vsync, always available)
            int presentMode = VK_PRESENT_MODE_FIFO_KHR;

            // Choose extent
            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                this.width = capabilities.currentExtent().width();
                this.height = capabilities.currentExtent().height();
            } else {
                this.width = Math.clamp(requestedWidth,
                        capabilities.minImageExtent().width(), capabilities.maxImageExtent().width());
                this.height = Math.clamp(requestedHeight,
                        capabilities.minImageExtent().height(), capabilities.maxImageExtent().height());
            }

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }

            var createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(chosenFormat)
                    .imageColorSpace(chosenColorSpace)
                    .imageExtent(e -> e.width(this.width).height(this.height))
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(presentMode)
                    .clipped(true)
                    .oldSwapchain(swapchain); // for recreation

            LongBuffer pSwapchain = stack.mallocLong(1);
            int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swapchain: " + result);
            }

            // Destroy old swapchain if recreating
            if (swapchain != VK_NULL_HANDLE) {
                destroyImageViews();
                vkDestroySwapchainKHR(device, swapchain, null);
            }
            swapchain = pSwapchain.get(0);

            // Get swapchain images
            IntBuffer imgCount = stack.ints(0);
            vkGetSwapchainImagesKHR(device, swapchain, imgCount, null);
            images = new long[imgCount.get(0)];
            LongBuffer pImages = stack.mallocLong(imgCount.get(0));
            vkGetSwapchainImagesKHR(device, swapchain, imgCount, pImages);
            for (int i = 0; i < images.length; i++) {
                images[i] = pImages.get(i);
            }

            // Create image views
            imageViews = new long[images.length];
            for (int i = 0; i < images.length; i++) {
                var viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(images[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(chosenFormat)
                        .components(c -> c
                                .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                                .a(VK_COMPONENT_SWIZZLE_IDENTITY))
                        .subresourceRange(sr -> sr
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0)
                                .levelCount(1)
                                .baseArrayLayer(0)
                                .layerCount(1));

                LongBuffer pView = stack.mallocLong(1);
                result = vkCreateImageView(device, viewInfo, null, pView);
                if (result != VK_SUCCESS) {
                    throw new RuntimeException("Failed to create image view: " + result);
                }
                imageViews[i] = pView.get(0);
            }
        }
    }

    int acquireNextImage(long semaphore) {
        try (var stack = stackPush()) {
            IntBuffer pIndex = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE, semaphore, VK_NULL_HANDLE, pIndex);
            if (result == VK_ERROR_OUT_OF_DATE_KHR) return -1; // needs recreation
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swapchain image: " + result);
            }
            return pIndex.get(0);
        }
    }

    int present(VkQueue queue, long waitSemaphore, int imageIndex) {
        try (var stack = stackPush()) {
            var presentInfo = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(waitSemaphore))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain))
                    .pImageIndices(stack.ints(imageIndex));

            return vkQueuePresentKHR(queue, presentInfo);
        }
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
                if (view != VK_NULL_HANDLE) vkDestroyImageView(device, view, null);
            }
        }
    }

    @Override
    public void close() {
        destroyImageViews();
        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK_NULL_HANDLE;
        }
    }
}

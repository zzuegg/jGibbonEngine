package dev.engine.graphics.vulkan;

import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Manages per-swapchain-image framebuffers and a shared depth buffer.
 */
class VkFramebufferSet implements AutoCloseable {

    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;

    private long depthImage = VK_NULL_HANDLE;
    private long depthMemory = VK_NULL_HANDLE;
    private long depthImageView = VK_NULL_HANDLE;
    private long[] framebuffers;

    VkFramebufferSet(VkDevice device, VkPhysicalDevice physicalDevice) {
        this.device = device;
        this.physicalDevice = physicalDevice;
    }

    void create(VkSwapchain swapchain, long renderPass, int depthFormat) {
        int width = swapchain.width();
        int height = swapchain.height();

        // Create depth image
        try (var stack = stackPush()) {
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(depthFormat)
                    .extent(e -> e.width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);

            var pImage = stack.mallocLong(1);
            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create depth image: " + result);
            depthImage = pImage.get(0);

            // Allocate depth memory
            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, depthImage, memReqs);

            int memType = findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);

            var pMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate depth memory: " + result);
            depthMemory = pMemory.get(0);

            vkBindImageMemory(device, depthImage, depthMemory, 0);

            // Create depth image view
            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(depthImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(depthFormat)
                    .subresourceRange(sr -> sr
                            .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1));

            var pView = stack.mallocLong(1);
            result = vkCreateImageView(device, viewInfo, null, pView);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create depth image view: " + result);
            depthImageView = pView.get(0);

            // Create framebuffers (one per swapchain image)
            framebuffers = new long[swapchain.imageCount()];
            for (int i = 0; i < swapchain.imageCount(); i++) {
                var attachments = stack.longs(swapchain.imageView(i), depthImageView);

                var fbInfo = VkFramebufferCreateInfo.calloc(stack)
                        .sType$Default()
                        .renderPass(renderPass)
                        .pAttachments(attachments)
                        .width(width)
                        .height(height)
                        .layers(1);

                var pFb = stack.mallocLong(1);
                result = vkCreateFramebuffer(device, fbInfo, null, pFb);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create framebuffer: " + result);
                framebuffers[i] = pFb.get(0);
            }
        }
    }

    long framebuffer(int index) { return framebuffers[index]; }
    long depthImageView() { return depthImageView; }

    private int findMemoryType(int typeFilter, int properties) {
        var memProperties = VkPhysicalDeviceMemoryProperties.calloc();
        try {
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);
            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 &&
                        (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
        } finally {
            memProperties.free();
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }

    @Override
    public void close() {
        if (framebuffers != null) {
            for (long fb : framebuffers) {
                if (fb != VK_NULL_HANDLE) vkDestroyFramebuffer(device, fb, null);
            }
        }
        if (depthImageView != VK_NULL_HANDLE) vkDestroyImageView(device, depthImageView, null);
        if (depthImage != VK_NULL_HANDLE) vkDestroyImage(device, depthImage, null);
        if (depthMemory != VK_NULL_HANDLE) vkFreeMemory(device, depthMemory, null);
    }
}

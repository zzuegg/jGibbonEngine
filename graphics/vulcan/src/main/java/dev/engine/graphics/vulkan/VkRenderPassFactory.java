package dev.engine.graphics.vulkan;

/**
 * Creates Vulkan render passes.
 */
final class VkRenderPassFactory {

    private VkRenderPassFactory() {}

    /**
     * Creates a render pass with one color attachment and one depth attachment.
     */
    static long createColorDepth(VkBindings vk, long device, int colorFormat, int depthFormat) {
        var colorAttachment = new VkBindings.AttachmentDesc(
                colorFormat, true, true, VkBindings.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        var depthAttachment = new VkBindings.AttachmentDesc(
                depthFormat, true, false, VkBindings.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

        var dependency = new VkBindings.SubpassDependencyDesc(
                VkBindings.VK_SUBPASS_EXTERNAL, 0,
                VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VkBindings.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VkBindings.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT,
                0,
                VkBindings.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VkBindings.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT,
                0);

        return vk.createRenderPass(device,
                new VkBindings.AttachmentDesc[]{colorAttachment},
                depthAttachment,
                new VkBindings.SubpassDependencyDesc[]{dependency});
    }

    /**
     * Finds a supported depth format.
     */
    static int findDepthFormat(VkBindings vk, long instance, long physicalDevice) {
        return vk.findDepthFormat(instance, physicalDevice);
    }
}

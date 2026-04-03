package dev.engine.graphics.vulkan;

import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Per-frame-in-flight resources: command buffer, semaphores, fence.
 * The renderer maintains MAX_FRAMES_IN_FLIGHT of these and cycles through them.
 */
class VkFrameContext implements AutoCloseable {

    private final VkDevice device;
    final VkCommandBuffer commandBuffer;
    final long imageAvailableSemaphore;
    final long renderFinishedSemaphore;
    final long inFlightFence;

    VkFrameContext(VkDevice device, long commandPool) {
        this.device = device;

        try (var stack = stackPush()) {
            // Allocate command buffer
            var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

            var pCommandBuffer = stack.mallocPointer(1);
            int result = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate command buffer: " + result);
            this.commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device);

            // Create semaphores
            var semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            var pSemaphore = stack.mallocLong(1);

            result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create semaphore: " + result);
            this.imageAvailableSemaphore = pSemaphore.get(0);

            result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create semaphore: " + result);
            this.renderFinishedSemaphore = pSemaphore.get(0);

            // Create fence (start signaled so first frame doesn't deadlock)
            var fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            var pFence = stack.mallocLong(1);
            result = vkCreateFence(device, fenceInfo, null, pFence);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create fence: " + result);
            this.inFlightFence = pFence.get(0);
        }
    }

    /** Waits for this frame's work to finish, then resets the command buffer. */
    void waitAndReset() {
        vkWaitForFences(device, inFlightFence, true, Long.MAX_VALUE);
        vkResetFences(device, inFlightFence);
        vkResetCommandBuffer(commandBuffer, 0);
    }

    /** Begins recording commands. */
    void beginCommandBuffer() {
        try (var stack = stackPush()) {
            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            int result = vkBeginCommandBuffer(commandBuffer, beginInfo);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to begin command buffer: " + result);
        }
    }

    /** Ends recording and submits to the given queue. */
    void submitTo(VkQueue queue) {
        int result = vkEndCommandBuffer(commandBuffer);
        if (result != VK_SUCCESS) throw new RuntimeException("Failed to end command buffer: " + result);

        try (var stack = stackPush()) {
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                    .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                    .pCommandBuffers(stack.pointers(commandBuffer))
                    .pSignalSemaphores(stack.longs(renderFinishedSemaphore));

            result = vkQueueSubmit(queue, submitInfo, inFlightFence);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to submit command buffer: " + result);
        }
    }

    @Override
    public void close() {
        vkDestroyFence(device, inFlightFence, null);
        vkDestroySemaphore(device, renderFinishedSemaphore, null);
        vkDestroySemaphore(device, imageAvailableSemaphore, null);
        // Command buffers freed when pool is destroyed
    }
}

package dev.engine.graphics.vulkan;

/**
 * Per-frame-in-flight resources: command buffer, semaphores, fence.
 * The renderer maintains MAX_FRAMES_IN_FLIGHT of these and cycles through them.
 */
class VkFrameContext implements AutoCloseable {

    private final VkBindings vk;
    private final long device;
    final long commandBuffer;
    final long imageAvailableSemaphore;
    final long renderFinishedSemaphore;
    final long inFlightFence;

    VkFrameContext(VkBindings vk, long device, long commandPool) {
        this.vk = vk;
        this.device = device;

        this.commandBuffer = vk.allocateCommandBuffer(device, commandPool);
        this.imageAvailableSemaphore = vk.createSemaphore(device);
        this.renderFinishedSemaphore = vk.createSemaphore(device);
        this.inFlightFence = vk.createFence(device, true);
    }

    /** Waits for this frame's work to finish, then resets the command buffer. */
    void waitAndReset() {
        vk.waitForFences(device, inFlightFence, Long.MAX_VALUE);
        vk.resetFences(device, inFlightFence);
        vk.resetCommandBuffer(commandBuffer);
    }

    /** Begins recording commands. */
    void beginCommandBuffer() {
        vk.beginCommandBuffer(commandBuffer, VkBindings.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
    }

    /** Ends recording and submits to the given queue. */
    void submitTo(long queue) {
        vk.endCommandBuffer(commandBuffer);
        vk.queueSubmit(queue, commandBuffer, imageAvailableSemaphore,
                VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                renderFinishedSemaphore, inFlightFence);
    }

    @Override
    public void close() {
        vk.destroyFence(device, inFlightFence);
        vk.destroySemaphore(device, renderFinishedSemaphore);
        vk.destroySemaphore(device, imageAvailableSemaphore);
        // Command buffers freed when pool is destroyed
    }
}

package dev.engine.graphics.vulkan;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.*;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.core.mesh.VertexFormat;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

public class VkRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(VkRenderDevice.class);

    private final VkInstance instance;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue graphicsQueue;
    private final long commandPool;
    private final int graphicsQueueFamily;
    private final VkPhysicalDeviceProperties deviceProperties;
    private final VkPhysicalDeviceMemoryProperties memoryProperties;

    private final long surface;
    private final VkSwapchain swapchain;
    private final long renderPass;
    private final int depthFormat;
    private final VkFramebufferSet framebuffers;

    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private final VkFrameContext[] frames;
    private final VkDescriptorManager descriptorManager;
    private int currentFrame = 0;
    private int currentImageIndex = -1;
    private long currentDescriptorSet = VK_NULL_HANDLE;
    private boolean descriptorDirty = false;
    private final long[] pendingUboBuffers = new long[16];
    private final long[] pendingUboSizes = new long[16];

    private final HandlePool<BufferResource> bufferPool = new HandlePool<>();
    private final Map<Integer, BufferAllocation> buffers = new HashMap<>();

    private final HandlePool<TextureResource> texturePool = new HandlePool<>();
    private final HandlePool<RenderTargetResource> renderTargetPool = new HandlePool<>();
    private final HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();
    private final HandlePool<SamplerResource> samplerPool = new HandlePool<>();
    private final HandlePool<PipelineResource> pipelinePool = new HandlePool<>();

    private final AtomicLong frameCounter = new AtomicLong(0);

    private record BufferAllocation(long buffer, long memory, long size) {}

    /**
     * Creates a Vulkan render device.
     *
     * @param requiredExtensions instance extensions needed for surface support
     *                           (e.g., from {@code glfwGetRequiredInstanceExtensions})
     * @param surfaceFactory     given the VkInstance, creates and returns a VkSurfaceKHR handle
     */
    public VkRenderDevice(PointerBuffer requiredExtensions,
                           java.util.function.Function<VkInstance, Long> surfaceFactory,
                           int initialWidth, int initialHeight) {
        try (var stack = stackPush()) {
            // --- Create VkInstance ---
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("Engine"))
                    .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                    .pEngineName(stack.UTF8("Engine"))
                    .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                    .apiVersion(VK_API_VERSION_1_0);

            // Required surface extensions (provided by windowing toolkit)

            // Check for validation layer support
            boolean validationAvailable = false;
            IntBuffer layerCount = stack.ints(0);
            vkEnumerateInstanceLayerProperties(layerCount, null);
            var availableLayers = VkLayerProperties.calloc(layerCount.get(0), stack);
            vkEnumerateInstanceLayerProperties(layerCount, availableLayers);
            for (int i = 0; i < availableLayers.capacity(); i++) {
                if ("VK_LAYER_KHRONOS_validation".equals(availableLayers.get(i).layerNameString())) {
                    validationAvailable = true;
                    break;
                }
            }

            PointerBuffer enabledLayers = null;
            if (validationAvailable) {
                enabledLayers = stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation"));
                log.info("Vulkan validation layers enabled");
            }

            // Add debug utils extension if validation is available
            PointerBuffer allExtensions;
            if (validationAvailable && requiredExtensions != null) {
                allExtensions = stack.mallocPointer(requiredExtensions.remaining() + 1);
                for (int i = 0; i < requiredExtensions.remaining(); i++) {
                    allExtensions.put(requiredExtensions.get(requiredExtensions.position() + i));
                }
                allExtensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
                allExtensions.flip();
            } else {
                allExtensions = requiredExtensions;
            }

            var createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(allExtensions)
                    .ppEnabledLayerNames(enabledLayers);

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkInstance: " + result);
            }
            this.instance = new VkInstance(pInstance.get(0), createInfo);

            // Setup debug messenger for validation messages
            if (validationAvailable) {
                try {
                    var debugInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                            .sType$Default()
                            .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                            .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                            .pfnUserCallback((severity, type, pCallbackData, pUserData) -> {
                                var data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                                var msg = data.pMessageString();
                                if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                                    log.error("[Vulkan Validation] {}", msg);
                                } else {
                                    log.warn("[Vulkan Validation] {}", msg);
                                }
                                return VK_FALSE;
                            });
                    var pMessenger = stack.mallocLong(1);
                    // VK_EXT_debug_utils must be enabled - add to instance extensions
                    // For now just log, don't fail if extension not available
                    vkCreateDebugUtilsMessengerEXT(instance, debugInfo, null, pMessenger);
                } catch (Exception e) {
                    log.debug("Debug messenger setup failed (expected if VK_EXT_debug_utils not enabled): {}", e.getMessage());
                }
            }

            this.surface = surfaceFactory.apply(instance);

            // --- Pick physical device ---
            IntBuffer deviceCount = stack.ints(0);
            vkEnumeratePhysicalDevices(instance, deviceCount, null);
            if (deviceCount.get(0) == 0) {
                throw new RuntimeException("No Vulkan-capable GPUs found");
            }
            PointerBuffer pDevices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(instance, deviceCount, pDevices);

            VkPhysicalDevice chosen = null;
            int chosenQueueFamily = -1;

            for (int i = 0; i < deviceCount.get(0); i++) {
                var candidate = new VkPhysicalDevice(pDevices.get(i), instance);

                IntBuffer queueFamilyCount = stack.ints(0);
                vkGetPhysicalDeviceQueueFamilyProperties(candidate, queueFamilyCount, null);
                var queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), stack);
                vkGetPhysicalDeviceQueueFamilyProperties(candidate, queueFamilyCount, queueFamilies);

                for (int q = 0; q < queueFamilyCount.get(0); q++) {
                    if ((queueFamilies.get(q).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                        // Also check presentation support
                        IntBuffer presentSupport = stack.ints(0);
                        vkGetPhysicalDeviceSurfaceSupportKHR(candidate, q, surface, presentSupport);
                        if (presentSupport.get(0) == VK_TRUE) {
                            chosen = candidate;
                            chosenQueueFamily = q;
                            break;
                        }
                    }
                }
                if (chosen != null) break;
            }

            if (chosen == null) {
                throw new RuntimeException("No GPU with graphics queue found");
            }
            this.physicalDevice = chosen;
            this.graphicsQueueFamily = chosenQueueFamily;

            // Query and log device properties
            this.deviceProperties = VkPhysicalDeviceProperties.calloc();
            vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties);

            int apiVersion = deviceProperties.apiVersion();
            log.info("Vulkan device: {} (Vulkan {}.{}.{})",
                    deviceProperties.deviceNameString(),
                    VK_VERSION_MAJOR(apiVersion),
                    VK_VERSION_MINOR(apiVersion),
                    VK_VERSION_PATCH(apiVersion));

            // Query memory properties
            this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);

            // --- Create logical device ---
            float[] priorities = {1.0f};
            var queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .queueFamilyIndex(graphicsQueueFamily)
                    .pQueuePriorities(stack.floats(priorities));

            // Enable VK_KHR_swapchain
            var deviceExtensions = stack.pointers(stack.UTF8(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pQueueCreateInfos(queueCreateInfo)
                    .ppEnabledExtensionNames(deviceExtensions);

            PointerBuffer pDevice = stack.mallocPointer(1);
            result = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device: " + result);
            }
            this.device = new VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo);

            // --- Get graphics queue ---
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, graphicsQueueFamily, 0, pQueue);
            this.graphicsQueue = new VkQueue(pQueue.get(0), device);

            // --- Create command pool ---
            var poolCreateInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(graphicsQueueFamily);

            LongBuffer pCommandPool = stack.mallocLong(1);
            result = vkCreateCommandPool(device, poolCreateInfo, null, pCommandPool);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool: " + result);
            }
            this.commandPool = pCommandPool.get(0);

            // --- Create swapchain + render pass + framebuffers ---
            this.swapchain = new VkSwapchain(device, physicalDevice, surface, graphicsQueueFamily);
            swapchain.create(initialWidth, initialHeight);

            this.depthFormat = VkRenderPassFactory.findDepthFormat(physicalDevice);
            this.renderPass = VkRenderPassFactory.createColorDepth(device, swapchain.imageFormat(), depthFormat);

            this.framebuffers = new VkFramebufferSet(device, physicalDevice);
            framebuffers.create(swapchain, renderPass, depthFormat);

            // --- Create per-frame resources ---
            this.frames = new VkFrameContext[MAX_FRAMES_IN_FLIGHT];
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                frames[i] = new VkFrameContext(device, commandPool);
            }

            this.descriptorManager = new VkDescriptorManager(device, physicalDevice, MAX_FRAMES_IN_FLIGHT, this::findMemoryType);

            log.info("Vulkan render device initialized (swapchain: {}x{}, {} images)",
                    swapchain.width(), swapchain.height(), swapchain.imageCount());
        }
    }

    // --- Buffer operations ---

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        try (var stack = stackPush()) {
            int usageFlags = mapBufferUsage(descriptor.usage());
            int memoryFlags = mapAccessPattern(descriptor.accessPattern());

            var bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(descriptor.size())
                    .usage(usageFlags)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            int result = vkCreateBuffer(device, bufferInfo, null, pBuffer);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create buffer: " + result);
            }
            long buffer = pBuffer.get(0);

            var memRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memRequirements);

            int memTypeIndex = findMemoryType(memRequirements.memoryTypeBits(), memoryFlags);

            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(memTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) {
                vkDestroyBuffer(device, buffer, null);
                throw new RuntimeException("Failed to allocate buffer memory: " + result);
            }
            long memory = pMemory.get(0);

            result = vkBindBufferMemory(device, buffer, memory, 0);
            if (result != VK_SUCCESS) {
                vkFreeMemory(device, memory, null);
                vkDestroyBuffer(device, buffer, null);
                throw new RuntimeException("Failed to bind buffer memory: " + result);
            }

            Handle<BufferResource> handle = bufferPool.allocate();
            buffers.put(handle.index(), new BufferAllocation(buffer, memory, descriptor.size()));
            return handle;
        }
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> handle) {
        if (!bufferPool.isValid(handle)) return;
        var alloc = buffers.remove(handle.index());
        if (alloc != null) {
            vkFreeMemory(device, alloc.memory(), null);
            vkDestroyBuffer(device, alloc.buffer(), null);
        }
        bufferPool.release(handle);
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> handle) {
        return bufferPool.isValid(handle);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> handle) {
        var alloc = buffers.get(handle.index());
        if (alloc == null) throw new IllegalArgumentException("Invalid buffer handle");
        return writeBuffer(handle, 0, alloc.size());
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> handle, long offset, long length) {
        var alloc = buffers.get(handle.index());
        if (alloc == null) throw new IllegalArgumentException("Invalid buffer handle");

        try (var stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(device, alloc.memory(), offset, length, 0, pData);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to map buffer memory: " + result);
            }
            long dataPtr = pData.get(0);
            MemorySegment segment = MemorySegment.ofAddress(dataPtr).reinterpret(length);
            long memory = alloc.memory();
            VkDevice dev = this.device;

            return new BufferWriter() {
                @Override
                public MemorySegment segment() {
                    return segment;
                }

                @Override
                public void close() {
                    vkUnmapMemory(dev, memory);
                }
            };
        }
    }

    // --- Texture operations (stubs) ---

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        return texturePool.allocate();
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        // Stub: no-op
    }

    @Override
    public void destroyTexture(Handle<TextureResource> handle) {
        if (texturePool.isValid(handle)) {
            texturePool.release(handle);
        }
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> handle) {
        return texturePool.isValid(handle);
    }

    @Override
    public long getBindlessTextureHandle(Handle<TextureResource> texture) {
        return 0L;
    }

    // --- Render target operations (stubs) ---

    @Override
    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        return renderTargetPool.allocate();
    }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) {
        return Handle.invalid();
    }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> handle) {
        if (renderTargetPool.isValid(handle)) {
            renderTargetPool.release(handle);
        }
    }

    // --- Vertex input operations (stubs) ---

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        return vertexInputPool.allocate();
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> handle) {
        if (vertexInputPool.isValid(handle)) {
            vertexInputPool.release(handle);
        }
    }

    // --- Sampler operations (stubs) ---

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        return samplerPool.allocate();
    }

    @Override
    public void destroySampler(Handle<SamplerResource> handle) {
        if (samplerPool.isValid(handle)) {
            samplerPool.release(handle);
        }
    }

    private final Map<Integer, Long> vkPipelines = new HashMap<>();

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        if (descriptor.hasSpirv()) {
            log.debug("createPipeline: using pipelineLayout=0x{}", Long.toHexString(descriptorManager.pipelineLayout()));
            long pipeline = VkPipelineFactory.create(device, renderPass,
                    descriptorManager.pipelineLayout(), descriptor.binaries(), descriptor.vertexFormat());
            var handle = pipelinePool.allocate();
            vkPipelines.put(handle.index(), pipeline);
            return handle;
        }
        log.warn("Vulkan backend received GLSL source pipeline descriptor — ignoring");
        return pipelinePool.allocate();
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> handle) {
        if (!pipelinePool.isValid(handle)) return;
        var pipeline = vkPipelines.remove(handle.index());
        if (pipeline != null) {
            vkDestroyPipeline(device, pipeline, null);
        }
        pipelinePool.release(handle);
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> handle) {
        return pipelinePool.isValid(handle);
    }

    // --- Frame operations ---

    @Override
    public StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage) {
        return null;
    }

    @Override
    public GpuFence createFence() {
        return new GpuFence() {
            @Override public boolean isSignaled() { return true; }
            @Override public void waitFor() {}
            @Override public boolean waitFor(long timeoutNanos) { return true; }
            @Override public void close() {}
        };
    }

    @Override
    public void beginFrame() {
        frameCounter.getAndIncrement();
        var frame = frames[currentFrame];

        // Wait for this frame's previous work to finish
        frame.waitAndReset();
        descriptorManager.resetPool(currentFrame);
        java.util.Arrays.fill(pendingUboBuffers, VK_NULL_HANDLE);
        java.util.Arrays.fill(pendingUboSizes, 0);
        descriptorDirty = false;

        // Acquire next swapchain image
        currentImageIndex = swapchain.acquireNextImage(frame.imageAvailableSemaphore);
        if (currentImageIndex < 0) {
            // Swapchain out of date — recreate
            recreateSwapchain();
            currentImageIndex = swapchain.acquireNextImage(frame.imageAvailableSemaphore);
        }

        // Begin recording
        frame.beginCommandBuffer();

        // Begin render pass
        try (var stack = stackPush()) {
            var clearValues = VkClearValue.calloc(2, stack);
            clearValues.get(0).color().float32(0, 0.05f).float32(1, 0.05f).float32(2, 0.08f).float32(3, 1.0f);
            clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

            var renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .framebuffer(framebuffers.framebuffer(currentImageIndex))
                    .renderArea(ra -> ra.offset(o -> o.x(0).y(0)).extent(e -> e.width(swapchain.width()).height(swapchain.height())))
                    .pClearValues(clearValues);

            vkCmdBeginRenderPass(frame.commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        }
    }

    @Override
    public void endFrame() {
        var frame = frames[currentFrame];

        // End render pass + submit
        vkCmdEndRenderPass(frame.commandBuffer);
        frame.submitTo(graphicsQueue);

        // Present
        int presentResult = swapchain.present(graphicsQueue, frame.renderFinishedSemaphore, currentImageIndex);
        if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
            recreateSwapchain();
        }

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
    }

    /**
     * Reads back pixels from the current swapchain image after rendering.
     * Must be called between endFrame() submissions — waits for GPU idle.
     * Returns RGBA8 pixel data as int[] {R, G, B, A} for 0-255 values.
     */
    public int[] readPixel(int x, int y) {
        vkDeviceWaitIdle(device);

        try (var stack = stackPush()) {
            // Create staging buffer
            long pixelSize = 4; // RGBA8
            var bufInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(pixelSize)
                    .usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            var pBuf = stack.mallocLong(1);
            vkCreateBuffer(device, bufInfo, null, pBuf);
            long stagingBuffer = pBuf.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, memReqs);
            int memType = findMemoryType(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);
            var pMem = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMem);
            long stagingMemory = pMem.get(0);
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0);

            // Get the last rendered swapchain image
            long image = swapchain.image(currentImageIndex >= 0 ? currentImageIndex : 0);

            // Allocate a one-shot command buffer
            var cmdAllocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            var pCmd = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, cmdAllocInfo, pCmd);
            var cmd = new VkCommandBuffer(pCmd.get(0), device);

            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            vkBeginCommandBuffer(cmd, beginInfo);

            // Transition image: PRESENT_SRC → TRANSFER_SRC
            var barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .subresourceRange(sr -> sr.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1))
                    .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);

            // Copy pixel
            var region = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                    .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0).baseArrayLayer(0).layerCount(1))
                    .imageOffset(o -> o.x(x).y(y).z(0))
                    .imageExtent(e -> e.width(1).height(1).depth(1));
            vkCmdCopyImageToBuffer(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, stagingBuffer, region);

            // Transition back: TRANSFER_SRC → PRESENT_SRC
            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                    0, null, null, barrier);

            vkEndCommandBuffer(cmd);

            // Submit and wait
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(cmd));
            vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(graphicsQueue);

            // Read back pixel
            var pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, pixelSize, 0, pData);
            var seg = java.lang.foreign.MemorySegment.ofAddress(pData.get(0)).reinterpret(pixelSize);

            // Swapchain format is B8G8R8A8_SRGB — BGRA order
            int b = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 0));
            int g = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 1));
            int r = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 2));
            int a = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 3));

            vkUnmapMemory(device, stagingMemory);

            // Cleanup
            vkFreeMemory(device, stagingMemory, null);
            vkDestroyBuffer(device, stagingBuffer, null);

            return new int[]{r, g, b, a};
        }
    }

    /**
     * Reads back the entire framebuffer as RGBA8 byte array.
     * Must be called after endFrame(). Waits for GPU idle.
     */
    @Override
    public byte[] readFramebuffer(int width, int height) {
        vkDeviceWaitIdle(device);
        int w = swapchain.width();
        int h = swapchain.height();

        try (var stack = stackPush()) {
            long pixelSize = (long) w * h * 4;
            var bufInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(pixelSize)
                    .usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            var pBuf = stack.mallocLong(1);
            vkCreateBuffer(device, bufInfo, null, pBuf);
            long stagingBuffer = pBuf.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, stagingBuffer, memReqs);
            int memType = findMemoryType(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(memReqs.size()).memoryTypeIndex(memType);
            var pMem = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMem);
            long stagingMemory = pMem.get(0);
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0);

            long image = swapchain.image(currentImageIndex >= 0 ? currentImageIndex : 0);

            var cmdAllocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default().commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            var pCmd = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, cmdAllocInfo, pCmd);
            var cmd = new VkCommandBuffer(pCmd.get(0), device);

            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default().flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            vkBeginCommandBuffer(cmd, beginInfo);

            var barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .subresourceRange(sr -> sr.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1))
                    .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);

            var region = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                    .imageSubresource(s -> s.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0).baseArrayLayer(0).layerCount(1))
                    .imageOffset(o -> o.x(0).y(0).z(0))
                    .imageExtent(e -> e.width(w).height(h).depth(1));
            vkCmdCopyImageToBuffer(cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, stagingBuffer, region);

            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT);
            vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                    0, null, null, barrier);

            vkEndCommandBuffer(cmd);

            var submitInfo = VkSubmitInfo.calloc(stack).sType$Default().pCommandBuffers(stack.pointers(cmd));
            vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(graphicsQueue);

            var pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, pixelSize, 0, pData);
            var seg = java.lang.foreign.MemorySegment.ofAddress(pData.get(0)).reinterpret(pixelSize);

            // Convert BGRA (swapchain format) to RGBA, no Y-flip needed
            // (Vulkan's Y-axis is already flipped by the negative viewport height)
            byte[] rgba = new byte[(int) pixelSize];
            for (int i = 0; i < w * h; i++) {
                int off = i * 4;
                rgba[off]     = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off + 2); // R from B position
                rgba[off + 1] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off + 1); // G
                rgba[off + 2] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off);     // B from R position
                rgba[off + 3] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off + 3); // A
            }

            vkUnmapMemory(device, stagingMemory);
            vkFreeMemory(device, stagingMemory, null);
            vkDestroyBuffer(device, stagingBuffer, null);

            return rgba;
        }
    }

    /** Swapchain width. */
    public int swapchainWidth() { return swapchain.width(); }
    /** Swapchain height. */
    public int swapchainHeight() { return swapchain.height(); }

    private void flushDescriptorSet(VkCommandBuffer cmd) {
        if (!descriptorDirty) {
            return;
        }
        descriptorDirty = false;

        long set = descriptorManager.allocateSet(currentFrame);

        // Batch all UBO updates into a single vkUpdateDescriptorSets call
        try (var stack = stackPush()) {
            int count = 0;
            for (int i = 0; i < pendingUboBuffers.length; i++) {
                if (pendingUboBuffers[i] != VK_NULL_HANDLE) count++;
            }

            var writes = VkWriteDescriptorSet.calloc(count, stack);
            int idx = 0;
            for (int i = 0; i < pendingUboBuffers.length; i++) {
                if (pendingUboBuffers[i] != VK_NULL_HANDLE) {
                    var bufInfo = VkDescriptorBufferInfo.calloc(1, stack)
                            .buffer(pendingUboBuffers[i])
                            .offset(0)
                            .range(pendingUboSizes[i]);
                    writes.get(idx)
                            .sType$Default()
                            .dstSet(set)
                            .dstBinding(i)
                            .dstArrayElement(0)
                            .descriptorCount(1)
                            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                            .pBufferInfo(bufInfo);
                    idx++;
                }
            }
            vkUpdateDescriptorSets(device, writes, null);

            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    descriptorManager.pipelineLayout(), 0, stack.longs(set), null);
        }
    }

    private void recreateSwapchain() {
        vkDeviceWaitIdle(device);
        framebuffers.close();
        swapchain.create(swapchain.width(), swapchain.height());
        framebuffers.create(swapchain, renderPass, depthFormat);
    }

    @Override
    public void submit(dev.engine.graphics.command.CommandList commands) {
        if (currentImageIndex < 0) return; // no valid frame
        var cmd = frames[currentFrame].commandBuffer;

        for (var command : commands.commands()) {
            switch (command) {
                case dev.engine.graphics.command.RenderCommand.BindPipeline bp -> {
                    var pipeline = vkPipelines.get(bp.pipeline().index());
                    if (pipeline != null) {
                        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindVertexBuffer bvb -> {
                    var alloc = buffers.get(bvb.buffer().index());
                    if (alloc != null) {
                        try (var stack = stackPush()) {
                            vkCmdBindVertexBuffers(cmd, 0, stack.longs(alloc.buffer()), stack.longs(0));
                        }
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindIndexBuffer bib -> {
                    var alloc = buffers.get(bib.buffer().index());
                    if (alloc != null) {
                        vkCmdBindIndexBuffer(cmd, alloc.buffer(), 0, VK_INDEX_TYPE_UINT32);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Draw draw -> {
                    flushDescriptorSet(cmd);
                    log.debug("vkCmdDraw(vertexCount={}, firstVertex={})", draw.vertexCount(), draw.firstVertex());
                    vkCmdDraw(cmd, draw.vertexCount(), 1, draw.firstVertex(), 0);
                }
                case dev.engine.graphics.command.RenderCommand.DrawIndexed di -> {
                    flushDescriptorSet(cmd);
                    log.debug("vkCmdDrawIndexed(indexCount={}, firstIndex={})", di.indexCount(), di.firstIndex());
                    vkCmdDrawIndexed(cmd, di.indexCount(), 1, di.firstIndex(), 0, 0);
                }
                case dev.engine.graphics.command.RenderCommand.Viewport vp -> {
                    try (var stack = stackPush()) {
                        // Negative height flips Y axis to match OpenGL convention
                        var viewport = VkViewport.calloc(1, stack)
                                .x(vp.x()).y(vp.height())
                                .width(vp.width()).height(-vp.height())
                                .minDepth(0f).maxDepth(1f);
                        vkCmdSetViewport(cmd, 0, viewport);

                        var scissor = VkRect2D.calloc(1, stack)
                                .offset(o -> o.x(vp.x()).y(vp.y()))
                                .extent(e -> e.width(vp.width()).height(vp.height()));
                        vkCmdSetScissor(cmd, 0, scissor);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Scissor sc -> {
                    try (var stack = stackPush()) {
                        var scissor = VkRect2D.calloc(1, stack)
                                .offset(o -> o.x(sc.x()).y(sc.y()))
                                .extent(e -> e.width(sc.width()).height(sc.height()));
                        vkCmdSetScissor(cmd, 0, scissor);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Clear c -> {
                    // Clear is handled by render pass load op — ignore here
                }
                case dev.engine.graphics.command.RenderCommand.BindUniformBuffer bub -> {
                    var alloc = buffers.get(bub.buffer().index());
                    if (alloc != null && bub.binding() < pendingUboBuffers.length) {
                        pendingUboBuffers[bub.binding()] = alloc.buffer();
                        pendingUboSizes[bub.binding()] = alloc.size();
                        descriptorDirty = true;
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindTexture bt -> {
                    // TODO: descriptor set binding
                }
                case dev.engine.graphics.command.RenderCommand.BindSampler bs -> {
                    // TODO: descriptor set binding
                }
                case dev.engine.graphics.command.RenderCommand.BindStorageBuffer bsb -> {
                    // TODO: descriptor set binding
                }
                case dev.engine.graphics.command.RenderCommand.SetDepthTest sdt -> {
                    // TODO: dynamic state or pipeline variant
                }
                case dev.engine.graphics.command.RenderCommand.SetBlending sb -> {
                    // TODO: dynamic state or pipeline variant
                }
                case dev.engine.graphics.command.RenderCommand.SetCullFace scf -> {
                    // TODO: dynamic state or pipeline variant
                }
                case dev.engine.graphics.command.RenderCommand.SetWireframe sw -> {
                    // TODO: dynamic state or pipeline variant
                }
                case dev.engine.graphics.command.RenderCommand.BindRenderTarget brt -> {
                    // TODO: off-screen render targets
                }
                case dev.engine.graphics.command.RenderCommand.BindDefaultRenderTarget bdrt -> {
                    // Already in the default render pass
                }
                case dev.engine.graphics.command.RenderCommand.SetRenderState state -> {
                    // TODO: implement in Task 17
                    log.warn("SetRenderState not yet implemented in Vulkan backend");
                }
                case dev.engine.graphics.command.RenderCommand.PushConstants pc -> {
                    // TODO: implement in Task 18
                    log.warn("PushConstants not yet implemented in Vulkan backend");
                }
                case dev.engine.graphics.command.RenderCommand.BindComputePipeline bcp -> {
                    // TODO: implement in Task 19
                    log.warn("BindComputePipeline not yet implemented in Vulkan backend");
                }
                case dev.engine.graphics.command.RenderCommand.Dispatch dispatch -> {
                    // TODO: implement in Task 19
                    log.warn("Dispatch not yet implemented in Vulkan backend");
                }
                case dev.engine.graphics.command.RenderCommand.MemoryBarrier barrier -> {
                    // TODO: implement in Task 19
                    log.warn("MemoryBarrier not yet implemented in Vulkan backend");
                }
            }
        }
    }

    // --- Capabilities ---

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(DeviceCapability<T> capability) {
        var limits = deviceProperties.limits();
        return switch (capability.name()) {
            case "MAX_TEXTURE_SIZE" -> (T) Integer.valueOf(limits.maxImageDimension2D());
            case "MAX_FRAMEBUFFER_WIDTH" -> (T) Integer.valueOf(limits.maxFramebufferWidth());
            case "MAX_FRAMEBUFFER_HEIGHT" -> (T) Integer.valueOf(limits.maxFramebufferHeight());
            case "BACKEND_NAME" -> (T) "Vulkan";
            case "DEVICE_NAME" -> (T) deviceProperties.deviceNameString();
            default -> null;
        };
    }

    // --- Cleanup ---

    @Override
    public void close() {
        vkDeviceWaitIdle(device);

        // Destroy remaining buffers
        for (var alloc : buffers.values()) {
            vkFreeMemory(device, alloc.memory(), null);
            vkDestroyBuffer(device, alloc.buffer(), null);
        }
        buffers.clear();

        for (var frame : frames) frame.close();
        descriptorManager.close();
        framebuffers.close();
        vkDestroyRenderPass(device, renderPass, null);
        swapchain.close();
        vkDestroyCommandPool(device, commandPool, null);
        vkDestroyDevice(device, null);
        vkDestroySurfaceKHR(instance, surface, null);
        vkDestroyInstance(instance, null);

        deviceProperties.free();
        memoryProperties.free();

        log.info("Vulkan render device destroyed");
    }

    // --- Helpers ---

    private int mapBufferUsage(BufferUsage usage) {
        return switch (usage.name()) {
            case "VERTEX" -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            case "INDEX" -> VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            case "UNIFORM" -> VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            case "STORAGE" -> VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            default -> VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        };
    }

    private int mapAccessPattern(AccessPattern pattern) {
        return switch (pattern.name()) {
            case "STATIC" -> VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
            case "DYNAMIC", "STREAM" -> VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            default -> VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        };
    }

    private int findMemoryType(int typeFilter, int properties) {
        for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
            if ((typeFilter & (1 << i)) != 0 &&
                    (memoryProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                return i;
            }
        }
        // Fallback: try without DEVICE_LOCAL if we asked for it
        int fallbackProps = properties & ~VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
        if (fallbackProps != properties) {
            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 &&
                        (memoryProperties.memoryTypes(i).propertyFlags() & fallbackProps) == fallbackProps) {
                    return i;
                }
            }
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }

}

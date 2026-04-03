package dev.engine.graphics.vulkan;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.*;
import dev.engine.graphics.resource.ResourceRegistry;
import dev.engine.graphics.buffer.*;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.FrontFace;
import dev.engine.graphics.renderstate.RenderState;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final long[] pendingSsboBuffers = new long[8];
    private final long[] pendingSsboSizes = new long[8];

    private final AtomicLong frameCounter = new AtomicLong(0);

    private record BufferAllocation(long buffer, long memory, long size) {}
    private record VkTextureAllocation(long image, long memory, long imageView, TextureDescriptor desc) {}
    private record VkSamplerAllocation(long sampler, SamplerDescriptor desc) {}
    private record VkRenderTargetAllocation(
        long renderPass,
        long framebuffer,
        int width, int height,
        List<VkTextureAllocation> colorAttachments,
        VkTextureAllocation depthAttachment,
        List<Handle<TextureResource>> colorTextureHandles
    ) {}

    private final ResourceRegistry<BufferResource, BufferAllocation> bufferRegistry = new ResourceRegistry<>("buffer");
    private final ResourceRegistry<TextureResource, VkTextureAllocation> textureRegistry = new ResourceRegistry<>("texture");
    private final ResourceRegistry<SamplerResource, VkSamplerAllocation> samplerRegistry = new ResourceRegistry<>("sampler");
    private final ResourceRegistry<PipelineResource, Long> pipelineRegistry = new ResourceRegistry<>("pipeline");
    private final ResourceRegistry<RenderTargetResource, VkRenderTargetAllocation> renderTargetRegistry = new ResourceRegistry<>("render-target");
    private final ResourceRegistry<VertexInputResource, Void> vertexInputRegistry = new ResourceRegistry<>("vertex-input");

    private final Map<Integer, Boolean> textureMipsDirty = new HashMap<>();
    // Track bound textures/samplers per unit for lazy mip generation
    @SuppressWarnings("unchecked")
    private final Handle<TextureResource>[] currentTextures = new Handle[8];
    @SuppressWarnings("unchecked")
    private final Handle<SamplerResource>[] currentSamplerHandles = new Handle[8];

    // Pending texture+sampler bindings for descriptor flush (unit -> imageView, unit -> sampler)
    private final long[] pendingTextureViews = new long[8];
    private final long[] pendingTextureSamplers = new long[8];

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
                    .apiVersion(VK13.VK_API_VERSION_1_3);

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

            return bufferRegistry.register(new BufferAllocation(buffer, memory, descriptor.size()));
        }
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> handle) {
        if (!bufferRegistry.isValid(handle)) return;
        var alloc = bufferRegistry.remove(handle);
        if (alloc != null) {
            vkFreeMemory(device, alloc.memory(), null);
            vkDestroyBuffer(device, alloc.buffer(), null);
        }
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> handle) {
        return bufferRegistry.isValid(handle);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> handle) {
        var alloc = bufferRegistry.get(handle);
        if (alloc == null) throw new IllegalArgumentException("Invalid buffer handle");
        return writeBuffer(handle, 0, alloc.size());
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> handle, long offset, long length) {
        var alloc = bufferRegistry.get(handle);
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

    // --- Texture operations ---

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        int vkFormat = mapTextureFormat(descriptor.format());
        int mipLevels = computeMipLevels(descriptor);
        boolean isDepth = isDepthFormat(descriptor.format());
        int aspectMask = isDepth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;
        int usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        if (mipLevels > 1) {
            usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT; // needed for mip generation
        }

        try (var stack = stackPush()) {
            // Create VkImage
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(vkFormat)
                    .extent(e -> e.width(descriptor.width()).height(descriptor.height()).depth(1))
                    .mipLevels(mipLevels)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            var pImage = stack.mallocLong(1);
            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create image: " + result);
            long image = pImage.get(0);

            // Allocate DEVICE_LOCAL memory
            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memReqs);
            int memType = findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);
            var pMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate image memory: " + result);
            long memory = pMemory.get(0);
            vkBindImageMemory(device, image, memory, 0);

            // Create VkImageView
            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(vkFormat)
                    .subresourceRange(sr -> sr.aspectMask(aspectMask)
                            .baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0).layerCount(1));
            var pView = stack.mallocLong(1);
            result = vkCreateImageView(device, viewInfo, null, pView);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create image view: " + result);
            long imageView = pView.get(0);

            // Transition layout: UNDEFINED -> SHADER_READ_ONLY_OPTIMAL
            transitionImageLayout(image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    aspectMask, mipLevels);

            return textureRegistry.register(new VkTextureAllocation(image, memory, imageView, descriptor));
        }
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        var alloc = textureRegistry.get(texture);
        if (alloc == null) return;

        long imageSize = pixels.remaining();
        int mipLevels = computeMipLevels(alloc.desc());
        boolean isDepth = isDepthFormat(alloc.desc().format());
        int aspectMask = isDepth ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT;

        try (var stack = stackPush()) {
            // Create staging buffer
            var bufInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(imageSize)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
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

            // Map and copy pixel data
            var pData = stack.mallocPointer(1);
            vkMapMemory(device, stagingMemory, 0, imageSize, 0, pData);
            memCopy(memAddress(pixels), pData.get(0), imageSize);
            vkUnmapMemory(device, stagingMemory);

            // Execute copy via one-shot command buffer
            executeOneShot(cmd -> {
                try (var inner = stackPush()) {
                    // Transition: SHADER_READ_ONLY -> TRANSFER_DST
                    var barrier = VkImageMemoryBarrier.calloc(1, inner)
                            .sType$Default()
                            .oldLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .image(alloc.image())
                            .subresourceRange(sr -> sr.aspectMask(aspectMask)
                                    .baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0).layerCount(1))
                            .srcAccessMask(0)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT);
                    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

                    // Copy buffer to image (mip level 0)
                    var region = VkBufferImageCopy.calloc(1, inner)
                            .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                            .imageSubresource(s -> s.aspectMask(aspectMask)
                                    .mipLevel(0).baseArrayLayer(0).layerCount(1))
                            .imageOffset(o -> o.x(0).y(0).z(0))
                            .imageExtent(e -> e.width(alloc.desc().width()).height(alloc.desc().height()).depth(1));
                    vkCmdCopyBufferToImage(cmd, stagingBuffer, alloc.image(),
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

                    // Transition back: TRANSFER_DST -> SHADER_READ_ONLY
                    barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                            .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT);
                    vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT,
                            VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, barrier);
                }
            });

            // Mark mips dirty so they are generated lazily when a mipmap sampler is bound
            if (computeMipLevels(alloc.desc()) > 1) {
                textureMipsDirty.put(texture.index(), true);
            }

            // Cleanup staging resources
            vkFreeMemory(device, stagingMemory, null);
            vkDestroyBuffer(device, stagingBuffer, null);
        }
    }

    @Override
    public void destroyTexture(Handle<TextureResource> handle) {
        if (!textureRegistry.isValid(handle)) return;
        var alloc = textureRegistry.remove(handle);
        if (alloc != null) {
            vkDestroyImageView(device, alloc.imageView(), null);
            vkDestroyImage(device, alloc.image(), null);
            vkFreeMemory(device, alloc.memory(), null);
        }
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> handle) {
        return textureRegistry.isValid(handle);
    }

    @Override
    public long getBindlessTextureHandle(Handle<TextureResource> texture) {
        return 0L; // Vulkan doesn't use GL-style bindless handles
    }

    // --- Render target operations ---

    @Override
    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        try (var stack = stackPush()) {
            var colorAttachments = new ArrayList<VkTextureAllocation>();
            var colorTextureHandles = new ArrayList<Handle<TextureResource>>();

            // Create color attachments
            for (var colorFormat : descriptor.colorAttachments()) {
                int vkFormat = mapTextureFormat(colorFormat);
                int usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;

                // Create VkImage
                var imageInfo = VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(vkFormat)
                        .extent(e -> e.width(descriptor.width()).height(descriptor.height()).depth(1))
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(usage)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                var pImage = stack.mallocLong(1);
                int result = vkCreateImage(device, imageInfo, null, pImage);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create RT color image: " + result);
                long image = pImage.get(0);

                // Allocate DEVICE_LOCAL memory
                var memReqs = VkMemoryRequirements.calloc(stack);
                vkGetImageMemoryRequirements(device, image, memReqs);
                int memType = findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType$Default()
                        .allocationSize(memReqs.size())
                        .memoryTypeIndex(memType);
                var pMemory = stack.mallocLong(1);
                result = vkAllocateMemory(device, allocInfo, null, pMemory);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate RT color memory: " + result);
                long memory = pMemory.get(0);
                vkBindImageMemory(device, image, memory, 0);

                // Create VkImageView
                var viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(image)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(vkFormat)
                        .subresourceRange(sr -> sr.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));
                var pView = stack.mallocLong(1);
                result = vkCreateImageView(device, viewInfo, null, pView);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create RT color image view: " + result);
                long imageView = pView.get(0);

                // Transition to SHADER_READ_ONLY_OPTIMAL
                transitionImageLayout(image, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_IMAGE_ASPECT_COLOR_BIT, 1);

                var texDesc = new TextureDescriptor(descriptor.width(), descriptor.height(), colorFormat);
                var texAlloc = new VkTextureAllocation(image, memory, imageView, texDesc);
                colorAttachments.add(texAlloc);

                // Register as a texture so it can be bound with BindTexture
                var texHandle = textureRegistry.register(texAlloc);
                colorTextureHandles.add(texHandle);
            }

            // Create depth attachment if requested
            VkTextureAllocation depthAttachment = null;
            if (descriptor.depthFormat() != null) {
                int vkDepthFormat = mapTextureFormat(descriptor.depthFormat());
                int depthUsage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;

                var imageInfo = VkImageCreateInfo.calloc(stack)
                        .sType$Default()
                        .imageType(VK_IMAGE_TYPE_2D)
                        .format(vkDepthFormat)
                        .extent(e -> e.width(descriptor.width()).height(descriptor.height()).depth(1))
                        .mipLevels(1)
                        .arrayLayers(1)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(depthUsage)
                        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
                var pImage = stack.mallocLong(1);
                int result = vkCreateImage(device, imageInfo, null, pImage);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create RT depth image: " + result);
                long depthImage = pImage.get(0);

                var memReqs = VkMemoryRequirements.calloc(stack);
                vkGetImageMemoryRequirements(device, depthImage, memReqs);
                int memType = findMemoryType(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
                var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                        .sType$Default()
                        .allocationSize(memReqs.size())
                        .memoryTypeIndex(memType);
                var pMemory = stack.mallocLong(1);
                result = vkAllocateMemory(device, allocInfo, null, pMemory);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate RT depth memory: " + result);
                long depthMemory = pMemory.get(0);
                vkBindImageMemory(device, depthImage, depthMemory, 0);

                var viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(depthImage)
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(vkDepthFormat)
                        .subresourceRange(sr -> sr.aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
                                .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));
                var pView = stack.mallocLong(1);
                result = vkCreateImageView(device, viewInfo, null, pView);
                if (result != VK_SUCCESS) throw new RuntimeException("Failed to create RT depth image view: " + result);
                long depthView = pView.get(0);

                var depthDesc = new TextureDescriptor(descriptor.width(), descriptor.height(), descriptor.depthFormat());
                depthAttachment = new VkTextureAllocation(depthImage, depthMemory, depthView, depthDesc);
            }

            // Create render pass
            long rtRenderPass = createOffscreenRenderPass(descriptor, colorAttachments, depthAttachment);

            // Create framebuffer
            int attachmentCount = colorAttachments.size() + (depthAttachment != null ? 1 : 0);
            var attachmentViews = stack.mallocLong(attachmentCount);
            for (int i = 0; i < colorAttachments.size(); i++) {
                attachmentViews.put(i, colorAttachments.get(i).imageView());
            }
            if (depthAttachment != null) {
                attachmentViews.put(colorAttachments.size(), depthAttachment.imageView());
            }

            var fbInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(rtRenderPass)
                    .pAttachments(attachmentViews)
                    .width(descriptor.width())
                    .height(descriptor.height())
                    .layers(1);
            var pFramebuffer = stack.mallocLong(1);
            int result = vkCreateFramebuffer(device, fbInfo, null, pFramebuffer);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create RT framebuffer: " + result);
            long framebuffer = pFramebuffer.get(0);

            var handle = renderTargetRegistry.register(new VkRenderTargetAllocation(
                    rtRenderPass, framebuffer, descriptor.width(), descriptor.height(),
                    List.copyOf(colorAttachments), depthAttachment, List.copyOf(colorTextureHandles)));

            log.info("Created Vulkan render target {}x{} with {} color attachment(s){}",
                    descriptor.width(), descriptor.height(), colorAttachments.size(),
                    depthAttachment != null ? " + depth" : "");
            return handle;
        }
    }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) {
        var rtAlloc = renderTargetRegistry.get(renderTarget);
        if (rtAlloc == null || index < 0 || index >= rtAlloc.colorTextureHandles().size()) {
            return Handle.invalid();
        }
        return rtAlloc.colorTextureHandles().get(index);
    }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> handle) {
        if (!renderTargetRegistry.isValid(handle)) return;
        var rtAlloc = renderTargetRegistry.remove(handle);
        if (rtAlloc != null) {
            vkDestroyFramebuffer(device, rtAlloc.framebuffer(), null);
            vkDestroyRenderPass(device, rtAlloc.renderPass(), null);

            // Destroy color attachments and their registered texture handles
            for (int i = 0; i < rtAlloc.colorAttachments().size(); i++) {
                var colorAlloc = rtAlloc.colorAttachments().get(i);
                var texHandle = rtAlloc.colorTextureHandles().get(i);
                textureRegistry.remove(texHandle);
                vkDestroyImageView(device, colorAlloc.imageView(), null);
                vkDestroyImage(device, colorAlloc.image(), null);
                vkFreeMemory(device, colorAlloc.memory(), null);
            }

            // Destroy depth attachment
            if (rtAlloc.depthAttachment() != null) {
                var depthAlloc = rtAlloc.depthAttachment();
                vkDestroyImageView(device, depthAlloc.imageView(), null);
                vkDestroyImage(device, depthAlloc.image(), null);
                vkFreeMemory(device, depthAlloc.memory(), null);
            }
        }
    }

    private long createOffscreenRenderPass(RenderTargetDescriptor descriptor,
                                           List<VkTextureAllocation> colorAttachments,
                                           VkTextureAllocation depthAttachment) {
        try (var stack = stackPush()) {
            int totalAttachments = colorAttachments.size() + (depthAttachment != null ? 1 : 0);
            var attachments = VkAttachmentDescription.calloc(totalAttachments, stack);

            // Color attachments: clear, store, final layout = SHADER_READ_ONLY (for sampling)
            for (int i = 0; i < colorAttachments.size(); i++) {
                int vkFormat = mapTextureFormat(descriptor.colorAttachments().get(i));
                attachments.get(i)
                        .format(vkFormat)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .finalLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }

            // Depth attachment
            VkAttachmentReference depthRef = null;
            if (depthAttachment != null) {
                int vkDepthFmt = mapTextureFormat(descriptor.depthFormat());
                attachments.get(colorAttachments.size())
                        .format(vkDepthFmt)
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

                depthRef = VkAttachmentReference.calloc(stack)
                        .attachment(colorAttachments.size())
                        .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            // Color attachment references for the subpass
            var colorRefs = VkAttachmentReference.calloc(colorAttachments.size(), stack);
            for (int i = 0; i < colorAttachments.size(); i++) {
                colorRefs.get(i)
                        .attachment(i)
                        .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }

            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(colorAttachments.size())
                    .pColorAttachments(colorRefs)
                    .pDepthStencilAttachment(depthRef);

            // Two dependencies: external -> subpass, subpass -> external (for shader read)
            var dependencies = VkSubpassDependency.calloc(2, stack);
            dependencies.get(0)
                    .srcSubpass(VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                            | (depthAttachment != null ? VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT : 0))
                    .srcAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | (depthAttachment != null ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT : 0))
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);
            dependencies.get(1)
                    .srcSubpass(0)
                    .dstSubpass(VK_SUBPASS_EXTERNAL)
                    .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT);

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(dependencies);

            var pRenderPass = stack.mallocLong(1);
            int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create offscreen render pass: " + result);
            return pRenderPass.get(0);
        }
    }

    // --- Vertex input operations (stubs) ---

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        return vertexInputRegistry.register(null);
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> handle) {
        if (vertexInputRegistry.isValid(handle)) {
            vertexInputRegistry.remove(handle);
        }
    }

    // --- Sampler operations ---

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        try (var stack = stackPush()) {
            int magFilter = mapFilter(descriptor.magFilter());
            int minFilter = mapFilter(descriptor.minFilter());
            int mipmapMode = mapMipmapMode(descriptor.minFilter());
            float maxLod = isMipmapFilter(descriptor.minFilter()) ? 1000.0f : 0.0f;

            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(magFilter)
                    .minFilter(minFilter)
                    .mipmapMode(mipmapMode)
                    .addressModeU(mapWrapMode(descriptor.wrapS()))
                    .addressModeV(mapWrapMode(descriptor.wrapT()))
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .minLod(0.0f)
                    .maxLod(maxLod)
                    .mipLodBias(0.0f)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false);

            var pSampler = stack.mallocLong(1);
            int result = vkCreateSampler(device, samplerInfo, null, pSampler);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create sampler: " + result);

            return samplerRegistry.register(new VkSamplerAllocation(pSampler.get(0), descriptor));
        }
    }

    @Override
    public void destroySampler(Handle<SamplerResource> handle) {
        if (!samplerRegistry.isValid(handle)) return;
        var alloc = samplerRegistry.remove(handle);
        if (alloc != null) {
            vkDestroySampler(device, alloc.sampler(), null);
        }
    }

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        if (descriptor.hasSpirv()) {
            log.debug("createPipeline: using pipelineLayout=0x{}", Long.toHexString(descriptorManager.pipelineLayout()));
            long pipeline = VkPipelineFactory.create(device, renderPass,
                    descriptorManager.pipelineLayout(), descriptor.binaries(), descriptor.vertexFormat());
            return pipelineRegistry.register(pipeline);
        }
        log.warn("Vulkan backend received GLSL source pipeline descriptor — ignoring");
        return pipelineRegistry.register(VK_NULL_HANDLE);
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> handle) {
        if (!pipelineRegistry.isValid(handle)) return;
        var pipeline = pipelineRegistry.remove(handle);
        if (pipeline != null && pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, pipeline, null);
        }
    }

    @Override
    public Handle<PipelineResource> createComputePipeline(
            dev.engine.graphics.pipeline.ComputePipelineDescriptor descriptor) {
        if (!descriptor.hasSpirv()) {
            throw new UnsupportedOperationException("Vulkan compute requires SPIRV binary");
        }
        try (var stack = stackPush()) {
            byte[] spirvBytes = descriptor.binary().spirv();
            var spirvBuf = memAlloc(spirvBytes.length);
            spirvBuf.put(spirvBytes).flip();

            var moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(spirvBuf);

            var pModule = stack.mallocLong(1);
            int result = vkCreateShaderModule(device, moduleInfo, null, pModule);
            memFree(spirvBuf);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create compute shader module: " + result);
            }
            long shaderModule = pModule.get(0);

            var stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8("main"));

            var pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default();
            pipelineInfo.get(0)
                    .stage(stageInfo)
                    .layout(descriptorManager.pipelineLayout());

            var pPipeline = stack.mallocLong(1);
            result = vkCreateComputePipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            vkDestroyShaderModule(device, shaderModule, null);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create compute pipeline: " + result);
            }

            return pipelineRegistry.register(pPipeline.get(0));
        }
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> handle) {
        return pipelineRegistry.isValid(handle);
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
        java.util.Arrays.fill(pendingSsboBuffers, VK_NULL_HANDLE);
        java.util.Arrays.fill(pendingSsboSizes, 0);
        java.util.Arrays.fill(pendingTextureViews, VK_NULL_HANDLE);
        java.util.Arrays.fill(pendingTextureSamplers, VK_NULL_HANDLE);
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
        currentDescriptorSet = set;

        // Count UBO + texture writes
        try (var stack = stackPush()) {
            int count = 0;
            for (int i = 0; i < pendingUboBuffers.length; i++) {
                if (pendingUboBuffers[i] != VK_NULL_HANDLE) count++;
            }
            for (int i = 0; i < pendingTextureViews.length; i++) {
                if (pendingTextureViews[i] != VK_NULL_HANDLE) count++;
            }
            for (int i = 0; i < pendingSsboBuffers.length; i++) {
                if (pendingSsboBuffers[i] != VK_NULL_HANDLE) count++;
            }

            var writes = VkWriteDescriptorSet.calloc(count, stack);
            int idx = 0;

            // UBO writes
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

            // Texture+sampler writes (combined image sampler)
            int texOffset = descriptorManager.textureBindingOffset();
            for (int i = 0; i < pendingTextureViews.length; i++) {
                if (pendingTextureViews[i] != VK_NULL_HANDLE) {
                    long sampler = pendingTextureSamplers[i];
                    // If no sampler was bound for this unit, skip (need both)
                    if (sampler == VK_NULL_HANDLE) continue;

                    var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                            .imageView(pendingTextureViews[i])
                            .sampler(sampler);
                    writes.get(idx)
                            .sType$Default()
                            .dstSet(set)
                            .dstBinding(texOffset + i)
                            .dstArrayElement(0)
                            .descriptorCount(1)
                            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                            .pImageInfo(imageInfo);
                    idx++;
                }
            }

            // SSBO writes
            int ssboOffset = descriptorManager.ssboBindingOffset();
            for (int i = 0; i < pendingSsboBuffers.length; i++) {
                if (pendingSsboBuffers[i] != VK_NULL_HANDLE) {
                    var bufInfo = VkDescriptorBufferInfo.calloc(1, stack)
                            .buffer(pendingSsboBuffers[i])
                            .offset(0)
                            .range(pendingSsboSizes[i]);
                    writes.get(idx)
                            .sType$Default()
                            .dstSet(set)
                            .dstBinding(ssboOffset + i)
                            .dstArrayElement(0)
                            .descriptorCount(1)
                            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                            .pBufferInfo(bufInfo);
                    idx++;
                }
            }

            // If we skipped some writes due to missing samplers, limit the buffer
            if (idx > 0) {
                writes.limit(idx);
                vkUpdateDescriptorSets(device, writes, null);
            }

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
                    var pipeline = pipelineRegistry.get(bp.pipeline());
                    if (pipeline != null) {
                        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindVertexBuffer bvb -> {
                    var alloc = bufferRegistry.get(bvb.buffer());
                    if (alloc != null) {
                        try (var stack = stackPush()) {
                            vkCmdBindVertexBuffers(cmd, 0, stack.longs(alloc.buffer()), stack.longs(0));
                        }
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindIndexBuffer bib -> {
                    var alloc = bufferRegistry.get(bib.buffer());
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
                case dev.engine.graphics.command.RenderCommand.DrawInstanced di -> {
                    flushDescriptorSet(cmd);
                    vkCmdDraw(cmd, di.vertexCount(), di.instanceCount(), di.firstVertex(), di.firstInstance());
                }
                case dev.engine.graphics.command.RenderCommand.DrawIndexedInstanced di -> {
                    flushDescriptorSet(cmd);
                    vkCmdDrawIndexed(cmd, di.indexCount(), di.instanceCount(), di.firstIndex(), 0, di.firstInstance());
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
                    var alloc = bufferRegistry.get(bub.buffer());
                    if (alloc != null && bub.binding() < pendingUboBuffers.length) {
                        pendingUboBuffers[bub.binding()] = alloc.buffer();
                        pendingUboSizes[bub.binding()] = alloc.size();
                        descriptorDirty = true;
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindTexture bt -> {
                    var texAlloc = textureRegistry.get(bt.texture());
                    if (texAlloc != null && bt.unit() < pendingTextureViews.length) {
                        pendingTextureViews[bt.unit()] = texAlloc.imageView();
                        descriptorDirty = true;
                    }
                    if (bt.unit() < currentTextures.length) {
                        currentTextures[bt.unit()] = bt.texture();
                        maybeGenerateMipmaps(bt.unit());
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindSampler bs -> {
                    var samplerAlloc = samplerRegistry.get(bs.sampler());
                    if (samplerAlloc != null && bs.unit() < pendingTextureSamplers.length) {
                        pendingTextureSamplers[bs.unit()] = samplerAlloc.sampler();
                        descriptorDirty = true;
                    }
                    if (bs.unit() < currentSamplerHandles.length) {
                        currentSamplerHandles[bs.unit()] = bs.sampler();
                        maybeGenerateMipmaps(bs.unit());
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindStorageBuffer bsb -> {
                    var alloc = bufferRegistry.get(bsb.buffer());
                    if (alloc != null && bsb.binding() < pendingSsboBuffers.length) {
                        pendingSsboBuffers[bsb.binding()] = alloc.buffer();
                        pendingSsboSizes[bsb.binding()] = alloc.size();
                        descriptorDirty = true;
                    }
                }
                case dev.engine.graphics.command.RenderCommand.SetDepthTest sdt -> {
                    VK13.vkCmdSetDepthTestEnable(cmd, sdt.enabled());
                    VK13.vkCmdSetDepthWriteEnable(cmd, sdt.enabled());
                }
                case dev.engine.graphics.command.RenderCommand.SetBlending sb -> {
                    // Blending enable/disable requires VK_EXT_extended_dynamic_state3
                    // which is not widely available. Blending is baked into the pipeline.
                    log.debug("SetBlending dynamic state not available in Vulkan; blending is pipeline-baked");
                }
                case dev.engine.graphics.command.RenderCommand.SetCullFace scf -> {
                    VK13.vkCmdSetCullMode(cmd, scf.enabled() ? VK_CULL_MODE_BACK_BIT : VK_CULL_MODE_NONE);
                }
                case dev.engine.graphics.command.RenderCommand.SetWireframe sw -> {
                    // Wireframe (polygon mode) requires VK_EXT_extended_dynamic_state3
                    // which is rarely available. No-op for now.
                    if (sw.enabled()) {
                        log.debug("SetWireframe not available as dynamic state in Vulkan");
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindRenderTarget brt -> {
                    var rtAlloc = renderTargetRegistry.get(brt.renderTarget());
                    if (rtAlloc != null) {
                        // End current render pass
                        vkCmdEndRenderPass(cmd);

                        try (var rtStack = stackPush()) {
                            int clearCount = rtAlloc.colorAttachments().size()
                                    + (rtAlloc.depthAttachment() != null ? 1 : 0);
                            var clearValues = VkClearValue.calloc(clearCount, rtStack);
                            for (int i = 0; i < rtAlloc.colorAttachments().size(); i++) {
                                clearValues.get(i).color()
                                        .float32(0, 0f).float32(1, 0f).float32(2, 0f).float32(3, 1f);
                            }
                            if (rtAlloc.depthAttachment() != null) {
                                clearValues.get(rtAlloc.colorAttachments().size())
                                        .depthStencil().depth(1.0f).stencil(0);
                            }

                            var rpBegin = VkRenderPassBeginInfo.calloc(rtStack)
                                    .sType$Default()
                                    .renderPass(rtAlloc.renderPass())
                                    .framebuffer(rtAlloc.framebuffer())
                                    .renderArea(a -> a
                                            .offset(o -> o.set(0, 0))
                                            .extent(e -> e.set(rtAlloc.width(), rtAlloc.height())))
                                    .pClearValues(clearValues);
                            vkCmdBeginRenderPass(cmd, rpBegin, VK_SUBPASS_CONTENTS_INLINE);
                        }
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindDefaultRenderTarget bdrt -> {
                    // End the current (off-screen) render pass and re-begin the swapchain one
                    vkCmdEndRenderPass(cmd);

                    try (var dfStack = stackPush()) {
                        var clearValues = VkClearValue.calloc(2, dfStack);
                        clearValues.get(0).color()
                                .float32(0, 0.05f).float32(1, 0.05f).float32(2, 0.08f).float32(3, 1.0f);
                        clearValues.get(1).depthStencil().depth(1.0f).stencil(0);

                        var rpBegin = VkRenderPassBeginInfo.calloc(dfStack)
                                .sType$Default()
                                .renderPass(renderPass)
                                .framebuffer(framebuffers.framebuffer(currentImageIndex))
                                .renderArea(a -> a
                                        .offset(o -> o.set(0, 0))
                                        .extent(e -> e.set(swapchain.width(), swapchain.height())))
                                .pClearValues(clearValues);
                        vkCmdBeginRenderPass(cmd, rpBegin, VK_SUBPASS_CONTENTS_INLINE);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.SetRenderState state -> {
                    var props = state.properties();
                    if (props.contains(RenderState.DEPTH_TEST)) {
                        VK13.vkCmdSetDepthTestEnable(cmd, props.get(RenderState.DEPTH_TEST));
                    }
                    if (props.contains(RenderState.DEPTH_WRITE)) {
                        VK13.vkCmdSetDepthWriteEnable(cmd, props.get(RenderState.DEPTH_WRITE));
                    }
                    if (props.contains(RenderState.CULL_MODE)) {
                        CullMode mode = props.get(RenderState.CULL_MODE);
                        int vkMode = switch (mode.name()) {
                            case "BACK"  -> VK_CULL_MODE_BACK_BIT;
                            case "FRONT" -> VK_CULL_MODE_FRONT_BIT;
                            default      -> VK_CULL_MODE_NONE;
                        };
                        VK13.vkCmdSetCullMode(cmd, vkMode);
                    }
                    if (props.contains(RenderState.FRONT_FACE)) {
                        FrontFace ff = props.get(RenderState.FRONT_FACE);
                        VK13.vkCmdSetFrontFace(cmd,
                                "CCW".equals(ff.name()) ? VK_FRONT_FACE_COUNTER_CLOCKWISE : VK_FRONT_FACE_CLOCKWISE);
                    }
                    // BLEND_MODE, WIREFRAME, LINE_WIDTH: not yet available as dynamic state
                }
                case dev.engine.graphics.command.RenderCommand.PushConstants(var data) -> {
                    data.rewind();
                    vkCmdPushConstants(cmd, descriptorManager.pipelineLayout(),
                            VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT,
                            0, data);
                }
                case dev.engine.graphics.command.RenderCommand.BindComputePipeline(var pipeline) -> {
                    var vkPipeline = pipelineRegistry.get(pipeline);
                    if (vkPipeline != null) {
                        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, vkPipeline);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Dispatch(int gx, int gy, int gz) -> {
                    // Note: Vulkan compute dispatches cannot happen inside a render pass.
                    // The caller is responsible for issuing compute work outside of render passes.
                    if (descriptorDirty) {
                        flushDescriptorSet(cmd);
                    }
                    // Also bind descriptor set to compute bind point
                    if (currentDescriptorSet != VK_NULL_HANDLE) {
                        try (var stack = stackPush()) {
                            vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE,
                                    descriptorManager.pipelineLayout(), 0,
                                    stack.longs(currentDescriptorSet), null);
                        }
                    }
                    vkCmdDispatch(cmd, gx, gy, gz);
                }
                case dev.engine.graphics.command.RenderCommand.MemoryBarrier(var scope) -> {
                    try (var stack = stackPush()) {
                        int srcStage, dstStage, srcAccess, dstAccess;
                        if (scope == dev.engine.graphics.renderstate.BarrierScope.STORAGE_BUFFER) {
                            srcStage = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                            dstStage = VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                            srcAccess = VK_ACCESS_SHADER_WRITE_BIT;
                            dstAccess = VK_ACCESS_SHADER_READ_BIT;
                        } else if (scope == dev.engine.graphics.renderstate.BarrierScope.TEXTURE) {
                            srcStage = VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                            dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                            srcAccess = VK_ACCESS_SHADER_WRITE_BIT;
                            dstAccess = VK_ACCESS_SHADER_READ_BIT;
                        } else {
                            srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                            dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                            srcAccess = VK_ACCESS_MEMORY_WRITE_BIT;
                            dstAccess = VK_ACCESS_MEMORY_READ_BIT;
                        }

                        var memBarrier = VkMemoryBarrier.calloc(1, stack)
                                .sType$Default()
                                .srcAccessMask(srcAccess)
                                .dstAccessMask(dstAccess);

                        vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0,
                                memBarrier, null, null);
                    }
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

        // Report leaked resources
        int leaks = bufferRegistry.reportLeaks()
                + textureRegistry.reportLeaks()
                + vertexInputRegistry.reportLeaks()
                + renderTargetRegistry.reportLeaks()
                + samplerRegistry.reportLeaks()
                + pipelineRegistry.reportLeaks();
        if (leaks > 0) {
            log.warn("Total {} resource handle(s) leaked at Vulkan device shutdown", leaks);
        }

        // Destroy remaining render targets (before textures, since RT owns some textures)
        renderTargetRegistry.destroyAll(rtAlloc -> {
            vkDestroyFramebuffer(device, rtAlloc.framebuffer(), null);
            vkDestroyRenderPass(device, rtAlloc.renderPass(), null);
            for (var texHandle : rtAlloc.colorTextureHandles()) {
                textureRegistry.remove(texHandle);
            }
            for (var colorAlloc : rtAlloc.colorAttachments()) {
                vkDestroyImageView(device, colorAlloc.imageView(), null);
                vkDestroyImage(device, colorAlloc.image(), null);
                vkFreeMemory(device, colorAlloc.memory(), null);
            }
            if (rtAlloc.depthAttachment() != null) {
                vkDestroyImageView(device, rtAlloc.depthAttachment().imageView(), null);
                vkDestroyImage(device, rtAlloc.depthAttachment().image(), null);
                vkFreeMemory(device, rtAlloc.depthAttachment().memory(), null);
            }
        });

        // Destroy remaining textures
        textureRegistry.destroyAll(alloc -> {
            vkDestroyImageView(device, alloc.imageView(), null);
            vkDestroyImage(device, alloc.image(), null);
            vkFreeMemory(device, alloc.memory(), null);
        });

        // Destroy remaining samplers
        samplerRegistry.destroyAll(alloc -> vkDestroySampler(device, alloc.sampler(), null));

        // Destroy remaining buffers
        bufferRegistry.destroyAll(alloc -> {
            vkFreeMemory(device, alloc.memory(), null);
            vkDestroyBuffer(device, alloc.buffer(), null);
        });

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

    private void executeOneShot(java.util.function.Consumer<VkCommandBuffer> recorder) {
        try (var stack = stackPush()) {
            var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            var pCmd = stack.mallocPointer(1);
            vkAllocateCommandBuffers(device, allocInfo, pCmd);
            var cmd = new VkCommandBuffer(pCmd.get(0), device);

            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            vkBeginCommandBuffer(cmd, beginInfo);
            recorder.accept(cmd);
            vkEndCommandBuffer(cmd);

            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(pCmd);
            vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE);
            vkQueueWaitIdle(graphicsQueue);
            vkFreeCommandBuffers(device, commandPool, pCmd);
        }
    }

    private void transitionImageLayout(long image, int oldLayout, int newLayout, int aspectMask, int mipLevels) {
        executeOneShot(cmd -> {
            try (var stack = stackPush()) {
                int srcAccess, dstAccess, srcStage, dstStage;
                if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    srcAccess = 0;
                    dstAccess = VK_ACCESS_SHADER_READ_BIT;
                    srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                    dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                } else if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                    srcAccess = 0;
                    dstAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
                    srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                    dstStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                    srcAccess = VK_ACCESS_TRANSFER_WRITE_BIT;
                    dstAccess = VK_ACCESS_SHADER_READ_BIT;
                    srcStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
                    dstStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                } else {
                    srcAccess = 0;
                    dstAccess = 0;
                    srcStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    dstStage = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                }

                var barrier = VkImageMemoryBarrier.calloc(1, stack)
                        .sType$Default()
                        .oldLayout(oldLayout)
                        .newLayout(newLayout)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image)
                        .subresourceRange(sr -> sr.aspectMask(aspectMask)
                                .baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0).layerCount(1))
                        .srcAccessMask(srcAccess)
                        .dstAccessMask(dstAccess);
                vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, null, null, barrier);
            }
        });
    }

    private int mapTextureFormat(dev.engine.graphics.texture.TextureFormat format) {
        return switch (format.name()) {
            case "RGBA8" -> VK_FORMAT_R8G8B8A8_UNORM;
            case "RGB8" -> VK_FORMAT_R8G8B8_UNORM;
            case "R8" -> VK_FORMAT_R8_UNORM;
            case "DEPTH24" -> VK_FORMAT_D24_UNORM_S8_UINT;
            case "DEPTH32F" -> VK_FORMAT_D32_SFLOAT;
            case "RGBA16F" -> VK_FORMAT_R16G16B16A16_SFLOAT;
            case "RGBA32F" -> VK_FORMAT_R32G32B32A32_SFLOAT;
            case "RG16F" -> VK_FORMAT_R16G16_SFLOAT;
            case "RG32F" -> VK_FORMAT_R32G32_SFLOAT;
            case "R16F" -> VK_FORMAT_R16_SFLOAT;
            case "R32F" -> VK_FORMAT_R32_SFLOAT;
            case "R32UI" -> VK_FORMAT_R32_UINT;
            case "R32I" -> VK_FORMAT_R32_SINT;
            default -> VK_FORMAT_R8G8B8A8_UNORM;
        };
    }

    private boolean isDepthFormat(dev.engine.graphics.texture.TextureFormat format) {
        return "DEPTH24".equals(format.name()) || "DEPTH32F".equals(format.name());
    }

    private int computeMipLevels(TextureDescriptor desc) {
        int requested = desc.mipMode().levelCount();
        if (requested == -1) { // AUTO
            return (int) (Math.floor(Math.log(Math.max(desc.width(), desc.height())) / Math.log(2))) + 1;
        }
        return requested;
    }

    private int mapFilter(dev.engine.graphics.sampler.FilterMode mode) {
        return switch (mode.name()) {
            case "LINEAR", "LINEAR_MIPMAP_LINEAR" -> VK_FILTER_LINEAR;
            default -> VK_FILTER_NEAREST;
        };
    }

    private int mapMipmapMode(dev.engine.graphics.sampler.FilterMode mode) {
        return switch (mode.name()) {
            case "LINEAR_MIPMAP_LINEAR" -> VK_SAMPLER_MIPMAP_MODE_LINEAR;
            default -> VK_SAMPLER_MIPMAP_MODE_NEAREST;
        };
    }

    private boolean isMipmapFilter(dev.engine.graphics.sampler.FilterMode mode) {
        return switch (mode.name()) {
            case "NEAREST_MIPMAP_NEAREST", "LINEAR_MIPMAP_LINEAR" -> true;
            default -> false;
        };
    }

    private int mapWrapMode(dev.engine.graphics.sampler.WrapMode mode) {
        return switch (mode.name()) {
            case "CLAMP_TO_EDGE" -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case "MIRRORED_REPEAT" -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            default -> VK_SAMPLER_ADDRESS_MODE_REPEAT;
        };
    }

    private void maybeGenerateMipmaps(int unit) {
        var texHandle = currentTextures[unit];
        var samplerHandle = currentSamplerHandles[unit];
        if (texHandle == null || samplerHandle == null) return;

        var texAlloc = textureRegistry.get(texHandle);
        if (texAlloc == null) return;
        if (texAlloc.desc().mipMode() == dev.engine.graphics.texture.MipMode.NONE) return;

        Boolean dirty = textureMipsDirty.get(texHandle.index());
        if (dirty == null || !dirty) return;

        var samplerAlloc = samplerRegistry.get(samplerHandle);
        if (samplerAlloc == null) return;
        if (!usesMipmaps(samplerAlloc.desc())) return;

        generateMipmaps(texHandle);
    }

    private boolean usesMipmaps(SamplerDescriptor desc) {
        return isMipmapFilter(desc.minFilter());
    }

    private void generateMipmaps(Handle<TextureResource> textureHandle) {
        var alloc = textureRegistry.get(textureHandle);
        if (alloc == null) return;

        var desc = alloc.desc();
        int mipLevels = computeMipLevels(desc);
        if (mipLevels <= 1) return;

        executeOneShot(cmd -> {
            int mipWidth = desc.width();
            int mipHeight = desc.height();

            for (int i = 1; i < mipLevels; i++) {
                // Transition level i-1 from SHADER_READ_ONLY to TRANSFER_SRC
                insertImageBarrier(cmd, alloc.image(),
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                        VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        i - 1, 1);

                // Transition level i from UNDEFINED to TRANSFER_DST
                insertImageBarrier(cmd, alloc.image(),
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        0, VK_ACCESS_TRANSFER_WRITE_BIT,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        i, 1);

                int nextWidth = Math.max(1, mipWidth / 2);
                int nextHeight = Math.max(1, mipHeight / 2);

                try (var stack = stackPush()) {
                    var blit = VkImageBlit.calloc(1, stack);
                    blit.srcSubresource()
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(i - 1)
                            .baseArrayLayer(0)
                            .layerCount(1);
                    blit.srcOffsets(0).set(0, 0, 0);
                    blit.srcOffsets(1).set(mipWidth, mipHeight, 1);

                    blit.dstSubresource()
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(i)
                            .baseArrayLayer(0)
                            .layerCount(1);
                    blit.dstOffsets(0).set(0, 0, 0);
                    blit.dstOffsets(1).set(nextWidth, nextHeight, 1);

                    vkCmdBlitImage(cmd, alloc.image(),
                            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            alloc.image(),
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            blit, VK_FILTER_LINEAR);
                }

                // Transition level i-1 back to SHADER_READ_ONLY
                insertImageBarrier(cmd, alloc.image(),
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_SHADER_READ_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        i - 1, 1);

                // Transition level i to SHADER_READ_ONLY
                insertImageBarrier(cmd, alloc.image(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        i, 1);

                mipWidth = nextWidth;
                mipHeight = nextHeight;
            }
        });

        textureMipsDirty.put(textureHandle.index(), false);
    }

    private void insertImageBarrier(VkCommandBuffer cmd, long image,
                                    int oldLayout, int newLayout,
                                    int srcAccess, int dstAccess,
                                    int srcStage, int dstStage,
                                    int baseMipLevel, int levelCount) {
        try (var stack = stackPush()) {
            var barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .subresourceRange(sr -> sr.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(baseMipLevel).levelCount(levelCount)
                            .baseArrayLayer(0).layerCount(1))
                    .srcAccessMask(srcAccess)
                    .dstAccessMask(dstAccess);
            vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, null, null, barrier);
        }
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

package dev.engine.graphics.vulkan;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.*;
import dev.engine.graphics.opengl.GlfwWindowToolkit;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.vertex.VertexFormat;
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

import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
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

    private final HandlePool<BufferResource> bufferPool = new HandlePool<>();
    private final Map<Integer, BufferAllocation> buffers = new HashMap<>();

    private final HandlePool<TextureResource> texturePool = new HandlePool<>();
    private final HandlePool<RenderTargetResource> renderTargetPool = new HandlePool<>();
    private final HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();
    private final HandlePool<SamplerResource> samplerPool = new HandlePool<>();
    private final HandlePool<PipelineResource> pipelinePool = new HandlePool<>();

    private final AtomicLong frameCounter = new AtomicLong(0);

    private record BufferAllocation(long buffer, long memory, long size) {}

    public VkRenderDevice(GlfwWindowToolkit.GlfwWindowHandle window) {
        try (var stack = stackPush()) {
            // --- Create VkInstance ---
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("Engine"))
                    .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                    .pEngineName(stack.UTF8("Engine"))
                    .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                    .apiVersion(VK_API_VERSION_1_0);

            // Required GLFW extensions
            PointerBuffer glfwExtensions = glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) {
                throw new RuntimeException("Failed to get required GLFW Vulkan extensions");
            }

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

            var createInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationInfo(appInfo)
                    .ppEnabledExtensionNames(glfwExtensions)
                    .ppEnabledLayerNames(enabledLayers);

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create VkInstance: " + result);
            }
            this.instance = new VkInstance(pInstance.get(0), createInfo);

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
                        chosen = candidate;
                        chosenQueueFamily = q;
                        break;
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

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pQueueCreateInfos(queueCreateInfo);

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

            log.info("Vulkan render device initialized");
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

    // --- Pipeline operations (stubs) ---

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        return pipelinePool.allocate();
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> handle) {
        if (pipelinePool.isValid(handle)) {
            pipelinePool.release(handle);
        }
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> handle) {
        return pipelinePool.isValid(handle);
    }

    // --- Frame operations ---

    @Override
    public void beginFrame() {
        frameCounter.getAndIncrement();
    }

    @Override
    public void endFrame() {
        // Stub: no presentation for now
    }

    @Override
    public void submit(dev.engine.graphics.command.CommandList commands) {
        // TODO: translate commands to Vulkan command buffers
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

        vkDestroyCommandPool(device, commandPool, null);
        vkDestroyDevice(device, null);
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

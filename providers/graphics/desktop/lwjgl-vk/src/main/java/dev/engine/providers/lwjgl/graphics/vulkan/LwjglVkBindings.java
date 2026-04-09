package dev.engine.providers.lwjgl.graphics.vulkan;

import dev.engine.graphics.vulkan.VkBindings;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.function.BiConsumer;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * LWJGL-based implementation of {@link VkBindings}.
 *
 * <p>Each method manages its own {@code MemoryStack} for temporary struct allocation,
 * so callers never touch LWJGL types directly.
 *
 * <p>Internally we need to keep VkInstance and VkDevice wrapper objects alive
 * for LWJGL's function dispatch. We cache them keyed by handle.
 */
public class LwjglVkBindings implements VkBindings {

    // LWJGL requires VkInstance/VkDevice objects for dispatch.
    // We maintain a simple cache keyed by the native handle.
    private final java.util.Map<Long, VkInstance> instanceCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, VkDevice> deviceCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, VkQueue> queueCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, VkCommandBuffer> cmdCache = new java.util.concurrent.ConcurrentHashMap<>();

    // ===== Instance / Device =====

    @Override
    public long createInstance(boolean validationEnabled, String[] requiredExtensions,
                               BiConsumer<Integer, String> debugCallback) {
        try (var stack = stackPush()) {
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("Engine"))
                    .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                    .pEngineName(stack.UTF8("Engine"))
                    .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                    .apiVersion(VK_API_VERSION_1_3);

            // Check for validation layers
            boolean validationAvailable = false;
            if (validationEnabled) {
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
            }

            PointerBuffer enabledLayers = null;
            if (validationAvailable) {
                enabledLayers = stack.pointers(stack.UTF8("VK_LAYER_KHRONOS_validation"));
            }

            // Build extensions list
            int extCount = (requiredExtensions != null ? requiredExtensions.length : 0)
                    + (validationAvailable ? 1 : 0);
            PointerBuffer allExtensions = null;
            if (extCount > 0) {
                allExtensions = stack.mallocPointer(extCount);
                if (requiredExtensions != null) {
                    for (String ext : requiredExtensions) {
                        allExtensions.put(stack.UTF8(ext));
                    }
                }
                if (validationAvailable) {
                    allExtensions.put(stack.UTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
                }
                allExtensions.flip();
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
            var instance = new VkInstance(pInstance.get(0), createInfo);
            instanceCache.put(instance.address(), instance);

            // Setup debug messenger
            if (validationAvailable && debugCallback != null) {
                try {
                    var debugInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                            .sType$Default()
                            .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                                    | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                            .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                                    | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                                    | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                            .pfnUserCallback((severity, type, pCallbackData, pUserData) -> {
                                var data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                                debugCallback.accept(severity, data.pMessageString());
                                return VK_FALSE;
                            });
                    var pMessenger = stack.mallocLong(1);
                    vkCreateDebugUtilsMessengerEXT(instance, debugInfo, null, pMessenger);
                } catch (Exception ignored) {
                    // Debug utils extension might not be available
                }
            }

            return instance.address();
        }
    }

    @Override
    public long[] enumeratePhysicalDevices(long instanceHandle) {
        var instance = instanceCache.get(instanceHandle);
        try (var stack = stackPush()) {
            IntBuffer count = stack.ints(0);
            vkEnumeratePhysicalDevices(instance, count, null);
            if (count.get(0) == 0) return new long[0];
            PointerBuffer pDevices = stack.mallocPointer(count.get(0));
            vkEnumeratePhysicalDevices(instance, count, pDevices);
            long[] devices = new long[count.get(0)];
            for (int i = 0; i < devices.length; i++) {
                devices[i] = pDevices.get(i);
            }
            return devices;
        }
    }

    @Override
    public String getDeviceName(long instanceHandle, long physicalDeviceHandle) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        var props = VkPhysicalDeviceProperties.calloc();
        try {
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            return props.deviceNameString();
        } finally {
            props.free();
        }
    }

    @Override
    public int[] getApiVersion(long instanceHandle, long physicalDeviceHandle) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        var props = VkPhysicalDeviceProperties.calloc();
        try {
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            int v = props.apiVersion();
            return new int[]{VK_VERSION_MAJOR(v), VK_VERSION_MINOR(v), VK_VERSION_PATCH(v)};
        } finally {
            props.free();
        }
    }

    @Override
    public int findGraphicsQueueFamily(long instanceHandle, long physicalDeviceHandle, long surface) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        try (var stack = stackPush()) {
            IntBuffer queueFamilyCount = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, null);
            var queueFamilies = VkQueueFamilyProperties.calloc(queueFamilyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyCount, queueFamilies);

            for (int q = 0; q < queueFamilyCount.get(0); q++) {
                if ((queueFamilies.get(q).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    IntBuffer presentSupport = stack.ints(0);
                    vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, q, surface, presentSupport);
                    if (presentSupport.get(0) == VK_TRUE) {
                        return q;
                    }
                }
            }
            return -1;
        }
    }

    @Override
    public long createDevice(long instanceHandle, long physicalDeviceHandle, int queueFamily,
                             String[] extensions) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        try (var stack = stackPush()) {
            var queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamily)
                    .pQueuePriorities(stack.floats(1.0f));

            PointerBuffer deviceExtensions = null;
            if (extensions != null && extensions.length > 0) {
                deviceExtensions = stack.mallocPointer(extensions.length);
                for (String ext : extensions) {
                    deviceExtensions.put(stack.UTF8(ext));
                }
                deviceExtensions.flip();
            }

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pQueueCreateInfos(queueCreateInfo)
                    .ppEnabledExtensionNames(deviceExtensions);

            PointerBuffer pDevice = stack.mallocPointer(1);
            int result = vkCreateDevice(physicalDevice, deviceCreateInfo, null, pDevice);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create logical device: " + result);
            }
            var device = new VkDevice(pDevice.get(0), physicalDevice, deviceCreateInfo);
            deviceCache.put(device.address(), device);
            return device.address();
        }
    }

    @Override
    public long getDeviceQueue(long deviceHandle, long physicalDeviceHandle, int queueFamily) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, queueFamily, 0, pQueue);
            var queue = new VkQueue(pQueue.get(0), device);
            queueCache.put(queue.address(), queue);
            return queue.address();
        }
    }

    @Override
    public void deviceWaitIdle(long deviceHandle) {
        vkDeviceWaitIdle(deviceCache.get(deviceHandle));
    }

    // ===== Surface =====

    @Override
    public void destroySurface(long instanceHandle, long surface) {
        vkDestroySurfaceKHR(instanceCache.get(instanceHandle), surface, null);
    }

    // ===== Swapchain =====

    @Override
    public SwapchainResult createSwapchain(long deviceHandle, long physicalDeviceHandle, long surface,
                                            int requestedWidth, int requestedHeight, long oldSwapchain) {
        return createSwapchain(deviceHandle, physicalDeviceHandle, surface,
                requestedWidth, requestedHeight, oldSwapchain,
                VK_FORMAT_B8G8R8A8_UNORM, VK_PRESENT_MODE_FIFO_KHR);
    }

    @Override
    public SwapchainResult createSwapchain(long deviceHandle, long physicalDeviceHandle, long surface,
                                            int requestedWidth, int requestedHeight, long oldSwapchain,
                                            int preferredFormat, int preferredPresentMode) {
        var device = deviceCache.get(deviceHandle);
        var instance = device.getPhysicalDevice().getInstance();
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);

        try (var stack = stackPush()) {
            var capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities);

            IntBuffer formatCount = stack.ints(0);
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null);
            var formats = VkSurfaceFormatKHR.calloc(formatCount.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats);

            int chosenFormat = preferredFormat;
            int chosenColorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
            boolean found = false;
            for (int i = 0; i < formats.capacity(); i++) {
                if (formats.get(i).format() == preferredFormat) {
                    chosenColorSpace = formats.get(i).colorSpace();
                    found = true;
                    break;
                }
            }
            if (!found) {
                chosenFormat = formats.get(0).format();
                chosenColorSpace = formats.get(0).colorSpace();
            }

            // Negotiate present mode — fall back to FIFO (guaranteed available)
            IntBuffer presentModeCount = stack.ints(0);
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null);
            IntBuffer presentModes = stack.mallocInt(presentModeCount.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes);
            int chosenPresentMode = VK_PRESENT_MODE_FIFO_KHR;
            for (int i = 0; i < presentModes.capacity(); i++) {
                if (presentModes.get(i) == preferredPresentMode) {
                    chosenPresentMode = preferredPresentMode;
                    break;
                }
            }

            int width, height;
            if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
                width = capabilities.currentExtent().width();
                height = capabilities.currentExtent().height();
            } else {
                width = Math.clamp(requestedWidth,
                        capabilities.minImageExtent().width(), capabilities.maxImageExtent().width());
                height = Math.clamp(requestedHeight,
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
                    .imageExtent(e -> e.width(width).height(height))
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(chosenPresentMode)
                    .clipped(true)
                    .oldSwapchain(oldSwapchain);

            LongBuffer pSwapchain = stack.mallocLong(1);
            int result = vkCreateSwapchainKHR(device, createInfo, null, pSwapchain);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to create swapchain: " + result);
            }
            long swapchain = pSwapchain.get(0);

            // Get swapchain images
            IntBuffer imgCount = stack.ints(0);
            vkGetSwapchainImagesKHR(device, swapchain, imgCount, null);
            long[] images = new long[imgCount.get(0)];
            LongBuffer pImages = stack.mallocLong(imgCount.get(0));
            vkGetSwapchainImagesKHR(device, swapchain, imgCount, pImages);
            for (int i = 0; i < images.length; i++) {
                images[i] = pImages.get(i);
            }

            // Create image views
            long[] imageViews = new long[images.length];
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
                    throw new RuntimeException("Failed to create swapchain image view: " + result);
                }
                imageViews[i] = pView.get(0);
            }

            return new SwapchainResult(swapchain, chosenFormat, width, height, images, imageViews);
        }
    }

    @Override
    public int acquireNextImage(long deviceHandle, long swapchain, long semaphore) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            IntBuffer pIndex = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(device, swapchain, Long.MAX_VALUE, semaphore, VK_NULL_HANDLE, pIndex);
            if (result == VK_ERROR_OUT_OF_DATE_KHR) return -1;
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swapchain image: " + result);
            }
            return pIndex.get(0);
        }
    }

    @Override
    public int queuePresent(long queueHandle, long swapchain, int imageIndex, long waitSemaphore) {
        var queue = queueCache.get(queueHandle);
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

    @Override
    public void destroySwapchain(long deviceHandle, long swapchain) {
        vkDestroySwapchainKHR(deviceCache.get(deviceHandle), swapchain, null);
    }

    // ===== Buffer =====

    @Override
    public BufferAlloc createBuffer(long deviceHandle, long physicalDeviceHandle, long size,
                                    int usage, int memProps) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            int result = vkCreateBuffer(device, bufferInfo, null, pBuffer);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create buffer: " + result);
            long buffer = pBuffer.get(0);

            var memRequirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, buffer, memRequirements);
            int memTypeIndex = findMemoryType(physicalDeviceHandle, memRequirements.memoryTypeBits(), memProps);

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

            return new BufferAlloc(buffer, memory, size);
        }
    }

    @Override
    public void destroyBuffer(long deviceHandle, long buffer) {
        vkDestroyBuffer(deviceCache.get(deviceHandle), buffer, null);
    }

    @Override
    public void freeMemory(long deviceHandle, long memory) {
        vkFreeMemory(deviceCache.get(deviceHandle), memory, null);
    }

    @Override
    public long mapMemory(long deviceHandle, long memory, long offset, long size) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            PointerBuffer pData = stack.mallocPointer(1);
            int result = vkMapMemory(device, memory, offset, size, 0, pData);
            if (result != VK_SUCCESS) {
                throw new RuntimeException("Failed to map memory: " + result);
            }
            return pData.get(0);
        }
    }

    @Override
    public void unmapMemory(long deviceHandle, long memory) {
        vkUnmapMemory(deviceCache.get(deviceHandle), memory);
    }

    @Override
    public void bindBufferMemory(long deviceHandle, long buffer, long memory, long offset) {
        vkBindBufferMemory(deviceCache.get(deviceHandle), buffer, memory, offset);
    }

    // ===== Image / Texture =====

    @Override
    public ImageAlloc createImage(long deviceHandle, long physicalDeviceHandle,
                                  int width, int height, int depth, int arrayLayers,
                                  int mipLevels, int format, int usage,
                                  int imageType, int viewType, int aspectMask,
                                  int createFlags) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(createFlags)
                    .imageType(imageType)
                    .format(format)
                    .extent(e -> e.width(width).height(height).depth(depth))
                    .mipLevels(mipLevels)
                    .arrayLayers(arrayLayers)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            var pImage = stack.mallocLong(1);
            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create image: " + result);
            long image = pImage.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memReqs);
            int memType = findMemoryType(physicalDeviceHandle, memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);
            var pMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate image memory: " + result);
            long memory = pMemory.get(0);
            vkBindImageMemory(device, image, memory, 0);

            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(viewType)
                    .format(format)
                    .subresourceRange(sr -> sr.aspectMask(aspectMask)
                            .baseMipLevel(0).levelCount(mipLevels)
                            .baseArrayLayer(0).layerCount(arrayLayers));
            var pView = stack.mallocLong(1);
            result = vkCreateImageView(device, viewInfo, null, pView);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create image view: " + result);
            long imageView = pView.get(0);

            return new ImageAlloc(image, memory, imageView);
        }
    }

    @Override
    public ImageNoView createImageNoView(long deviceHandle, long physicalDeviceHandle,
                                          int width, int height, int depth, int arrayLayers,
                                          int mipLevels, int format, int usage,
                                          int imageType, int createFlags) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(createFlags)
                    .imageType(imageType)
                    .format(format)
                    .extent(e -> e.width(width).height(height).depth(depth))
                    .mipLevels(mipLevels)
                    .arrayLayers(arrayLayers)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            var pImage = stack.mallocLong(1);
            int result = vkCreateImage(device, imageInfo, null, pImage);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create image: " + result);
            long image = pImage.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image, memReqs);
            int memType = findMemoryType(physicalDeviceHandle, memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);
            var pMemory = stack.mallocLong(1);
            result = vkAllocateMemory(device, allocInfo, null, pMemory);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate image memory: " + result);
            long memory = pMemory.get(0);
            vkBindImageMemory(device, image, memory, 0);

            return new ImageNoView(image, memory);
        }
    }

    @Override
    public long createImageView(long deviceHandle, long image, int format, int viewType,
                                int aspectMask, int baseMipLevel, int levelCount,
                                int baseArrayLayer, int layerCount) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image)
                    .viewType(viewType)
                    .format(format)
                    .subresourceRange(sr -> sr.aspectMask(aspectMask)
                            .baseMipLevel(baseMipLevel).levelCount(levelCount)
                            .baseArrayLayer(baseArrayLayer).layerCount(layerCount));
            var pView = stack.mallocLong(1);
            int result = vkCreateImageView(device, viewInfo, null, pView);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create image view: " + result);
            return pView.get(0);
        }
    }

    @Override
    public void destroyImage(long deviceHandle, long image) {
        vkDestroyImage(deviceCache.get(deviceHandle), image, null);
    }

    @Override
    public void destroyImageView(long deviceHandle, long imageView) {
        vkDestroyImageView(deviceCache.get(deviceHandle), imageView, null);
    }

    @Override
    public void bindImageMemory(long deviceHandle, long image, long memory, long offset) {
        vkBindImageMemory(deviceCache.get(deviceHandle), image, memory, offset);
    }

    // ===== Sampler =====

    @Override
    public long createSampler(long deviceHandle, int magFilter, int minFilter, int mipmapMode,
                              int addressModeU, int addressModeV, int addressModeW,
                              float minLod, float maxLod, float lodBias,
                              boolean anisotropyEnable, float maxAnisotropy,
                              boolean compareEnable, int compareOp,
                              int borderColor) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(magFilter)
                    .minFilter(minFilter)
                    .mipmapMode(mipmapMode)
                    .addressModeU(addressModeU)
                    .addressModeV(addressModeV)
                    .addressModeW(addressModeW)
                    .minLod(minLod)
                    .maxLod(maxLod)
                    .mipLodBias(lodBias)
                    .anisotropyEnable(anisotropyEnable)
                    .maxAnisotropy(maxAnisotropy)
                    .compareEnable(compareEnable)
                    .compareOp(compareOp)
                    .borderColor(borderColor)
                    .unnormalizedCoordinates(false);
            var pSampler = stack.mallocLong(1);
            int result = vkCreateSampler(device, samplerInfo, null, pSampler);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create sampler: " + result);
            return pSampler.get(0);
        }
    }

    @Override
    public void destroySampler(long deviceHandle, long sampler) {
        vkDestroySampler(deviceCache.get(deviceHandle), sampler, null);
    }

    // ===== Shader =====

    @Override
    public long createShaderModule(long deviceHandle, byte[] spirv) {
        var device = deviceCache.get(deviceHandle);
        ByteBuffer spirvBuf = memAlloc(spirv.length);
        spirvBuf.put(spirv).flip();
        try (var stack = stackPush()) {
            var moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(spirvBuf);
            var pModule = stack.mallocLong(1);
            int result = vkCreateShaderModule(device, moduleInfo, null, pModule);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create shader module: " + result);
            return pModule.get(0);
        } finally {
            memFree(spirvBuf);
        }
    }

    @Override
    public void destroyShaderModule(long deviceHandle, long module) {
        vkDestroyShaderModule(deviceCache.get(deviceHandle), module, null);
    }

    // ===== Render Pass =====

    @Override
    public long createRenderPass(long deviceHandle,
                                 AttachmentDesc[] colorAttachments,
                                 AttachmentDesc depthAttachment,
                                 SubpassDependencyDesc[] dependencies) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            int totalAttachments = colorAttachments.length + (depthAttachment != null ? 1 : 0);
            var attachments = VkAttachmentDescription.calloc(totalAttachments, stack);

            for (int i = 0; i < colorAttachments.length; i++) {
                var desc = colorAttachments[i];
                attachments.get(i)
                        .format(desc.format())
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(desc.clear() ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .storeOp(desc.store() ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .finalLayout(desc.finalLayout());
            }

            VkAttachmentReference depthRef = null;
            if (depthAttachment != null) {
                attachments.get(colorAttachments.length)
                        .format(depthAttachment.format())
                        .samples(VK_SAMPLE_COUNT_1_BIT)
                        .loadOp(depthAttachment.clear() ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .storeOp(depthAttachment.store() ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        // Clear stencil buffer each frame so stencil masking starts clean
                        .stencilLoadOp(depthAttachment.clear() ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                        .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                        .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .finalLayout(depthAttachment.finalLayout());

                depthRef = VkAttachmentReference.calloc(stack)
                        .attachment(colorAttachments.length)
                        .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            var colorRefs = VkAttachmentReference.calloc(colorAttachments.length, stack);
            for (int i = 0; i < colorAttachments.length; i++) {
                colorRefs.get(i)
                        .attachment(i)
                        .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            }

            var subpass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(colorAttachments.length)
                    .pColorAttachments(colorRefs)
                    .pDepthStencilAttachment(depthRef);

            VkSubpassDependency.Buffer deps = null;
            if (dependencies != null && dependencies.length > 0) {
                deps = VkSubpassDependency.calloc(dependencies.length, stack);
                for (int i = 0; i < dependencies.length; i++) {
                    var d = dependencies[i];
                    deps.get(i)
                            .srcSubpass(d.srcSubpass())
                            .dstSubpass(d.dstSubpass())
                            .srcStageMask(d.srcStageMask())
                            .dstStageMask(d.dstStageMask())
                            .srcAccessMask(d.srcAccessMask())
                            .dstAccessMask(d.dstAccessMask())
                            .dependencyFlags(d.dependencyFlags());
                }
            }

            var renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subpass)
                    .pDependencies(deps);

            var pRenderPass = stack.mallocLong(1);
            int result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create render pass: " + result);
            return pRenderPass.get(0);
        }
    }

    @Override
    public void destroyRenderPass(long deviceHandle, long renderPass) {
        vkDestroyRenderPass(deviceCache.get(deviceHandle), renderPass, null);
    }

    @Override
    public int findDepthFormat(long instanceHandle, long physicalDeviceHandle) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        // Prefer depth+stencil formats so stencil operations work
        int[] candidates = {VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D32_SFLOAT};
        try (var stack = stackPush()) {
            for (int format : candidates) {
                var props = VkFormatProperties.calloc(stack);
                vkGetPhysicalDeviceFormatProperties(physicalDevice, format, props);
                if ((props.optimalTilingFeatures() & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return format;
                }
            }
        }
        throw new RuntimeException("Failed to find supported depth format");
    }

    // ===== Framebuffer =====

    @Override
    public long createFramebuffer(long deviceHandle, long renderPass, long[] attachments,
                                  int width, int height) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var pAttachments = stack.mallocLong(attachments.length);
            for (long a : attachments) pAttachments.put(a);
            pAttachments.flip();

            var fbInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .pAttachments(pAttachments)
                    .width(width)
                    .height(height)
                    .layers(1);

            var pFb = stack.mallocLong(1);
            int result = vkCreateFramebuffer(device, fbInfo, null, pFb);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create framebuffer: " + result);
            return pFb.get(0);
        }
    }

    @Override
    public void destroyFramebuffer(long deviceHandle, long framebuffer) {
        vkDestroyFramebuffer(deviceCache.get(deviceHandle), framebuffer, null);
    }

    // ===== Pipeline =====

    @Override
    public long createGraphicsPipeline(long deviceHandle, long renderPass, long pipelineLayout,
                                       long pipelineCache,
                                       long[] shaderModules, int[] shaderStages,
                                       int[] vertexAttribLocations, int[] vertexAttribFormats,
                                       int[] vertexAttribOffsets, int vertexStride,
                                       boolean blendEnabled, int srcColorFactor, int dstColorFactor,
                                       int srcAlphaFactor, int dstAlphaFactor,
                                       boolean wireframe, int[] dynamicStates) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var stageInfos = VkPipelineShaderStageCreateInfo.calloc(shaderModules.length, stack);
            for (int i = 0; i < shaderModules.length; i++) {
                stageInfos.get(i)
                        .sType$Default()
                        .stage(shaderStages[i])
                        .module(shaderModules[i])
                        .pName(stack.UTF8("main"));
            }

            var vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            if (vertexAttribLocations != null && vertexAttribLocations.length > 0) {
                var bindingDesc = VkVertexInputBindingDescription.calloc(1, stack)
                        .binding(0)
                        .stride(vertexStride)
                        .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

                var attrDescs = VkVertexInputAttributeDescription.calloc(vertexAttribLocations.length, stack);
                for (int i = 0; i < vertexAttribLocations.length; i++) {
                    attrDescs.get(i)
                            .binding(0)
                            .location(vertexAttribLocations[i])
                            .format(vertexAttribFormats[i])
                            .offset(vertexAttribOffsets[i]);
                }

                vertexInputInfo
                        .pVertexBindingDescriptions(bindingDesc)
                        .pVertexAttributeDescriptions(attrDescs);
            }

            var inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                    .primitiveRestartEnable(false);

            var dynamicStatesBuf = stack.mallocInt(dynamicStates.length);
            for (int ds : dynamicStates) dynamicStatesBuf.put(ds);
            dynamicStatesBuf.flip();

            var dynamicStateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(dynamicStatesBuf);

            var viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(wireframe ? VK_POLYGON_MODE_LINE : VK_POLYGON_MODE_FILL)
                    .lineWidth(1.0f)
                    .cullMode(VK_CULL_MODE_NONE)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(false);

            var multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            var depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);

            var colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                            | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                    .blendEnable(blendEnabled)
                    .srcColorBlendFactor(srcColorFactor)
                    .dstColorBlendFactor(dstColorFactor)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(srcAlphaFactor)
                    .dstAlphaBlendFactor(dstAlphaFactor)
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            var colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .logicOpEnable(false)
                    .pAttachments(colorBlendAttachment);

            var pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pStages(stageInfos)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicStateInfo)
                    .layout(pipelineLayout)
                    .renderPass(renderPass)
                    .subpass(0);

            var pPipeline = stack.mallocLong(1);
            int result = vkCreateGraphicsPipelines(device, pipelineCache, pipelineInfo, null, pPipeline);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create graphics pipeline: " + result);
            return pPipeline.get(0);
        }
    }

    @Override
    public long createComputePipeline(long deviceHandle, long pipelineLayout, long pipelineCache, long shaderModule) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8("main"));

            var pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default();
            pipelineInfo.get(0)
                    .stage(stageInfo)
                    .layout(pipelineLayout);

            var pPipeline = stack.mallocLong(1);
            int result = vkCreateComputePipelines(device, pipelineCache, pipelineInfo, null, pPipeline);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create compute pipeline: " + result);
            return pPipeline.get(0);
        }
    }

    @Override
    public void destroyPipeline(long deviceHandle, long pipeline) {
        vkDestroyPipeline(deviceCache.get(deviceHandle), pipeline, null);
    }

    // ===== Pipeline Cache =====

    @Override
    public long createPipelineCache(long deviceHandle, byte[] initialData) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack)
                    .sType$Default();
            if (initialData != null && initialData.length > 0) {
                var buf = stack.malloc(initialData.length);
                buf.put(initialData).flip();
                createInfo.pInitialData(buf);
            }
            var pCache = stack.mallocLong(1);
            int result = vkCreatePipelineCache(device, createInfo, null, pCache);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create pipeline cache: " + result);
            return pCache.get(0);
        }
    }

    @Override
    public byte[] getPipelineCacheData(long deviceHandle, long pipelineCache) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var pSize = stack.mallocPointer(1);
            vkGetPipelineCacheData(device, pipelineCache, pSize, null);
            long size = pSize.get(0);
            if (size == 0) return new byte[0];
            ByteBuffer buf = MemoryUtil.memAlloc((int) size);
            try {
                vkGetPipelineCacheData(device, pipelineCache, pSize, buf);
                byte[] data = new byte[(int) pSize.get(0)];
                buf.get(data);
                return data;
            } finally {
                MemoryUtil.memFree(buf);
            }
        }
    }

    @Override
    public void destroyPipelineCache(long deviceHandle, long pipelineCache) {
        vkDestroyPipelineCache(deviceCache.get(deviceHandle), pipelineCache, null);
    }

    // ===== Descriptor =====

    @Override
    public long createDescriptorSetLayout(long deviceHandle, int[] bindings, int[] types,
                                          int[] stageFlags, int[] counts) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var layoutBindings = VkDescriptorSetLayoutBinding.calloc(bindings.length, stack);
            for (int i = 0; i < bindings.length; i++) {
                layoutBindings.get(i)
                        .binding(bindings[i])
                        .descriptorType(types[i])
                        .descriptorCount(counts[i])
                        .stageFlags(stageFlags[i]);
            }

            var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(layoutBindings);

            var pLayout = stack.mallocLong(1);
            int result = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create descriptor set layout: " + result);
            return pLayout.get(0);
        }
    }

    @Override
    public long createPipelineLayout(long deviceHandle, long descriptorSetLayout,
                                     int pushConstantSize, int pushConstantStages) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            VkPushConstantRange.Buffer pushConstantRange = null;
            if (pushConstantSize > 0) {
                pushConstantRange = VkPushConstantRange.calloc(1, stack)
                        .stageFlags(pushConstantStages)
                        .offset(0)
                        .size(pushConstantSize);
            }

            var pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(pushConstantRange);

            var pPipelineLayout = stack.mallocLong(1);
            int result = vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create pipeline layout: " + result);
            return pPipelineLayout.get(0);
        }
    }

    @Override
    public long createDescriptorPool(long deviceHandle, int[] types, int[] descriptorCounts, int maxSets) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var poolSizes = VkDescriptorPoolSize.calloc(types.length, stack);
            for (int i = 0; i < types.length; i++) {
                poolSizes.get(i)
                        .type(types[i])
                        .descriptorCount(descriptorCounts[i]);
            }

            var poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPoolSizes(poolSizes)
                    .maxSets(maxSets);

            var pPool = stack.mallocLong(1);
            int result = vkCreateDescriptorPool(device, poolInfo, null, pPool);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create descriptor pool: " + result);
            return pPool.get(0);
        }
    }

    @Override
    public long allocateDescriptorSet(long deviceHandle, long pool, long layout) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pool)
                    .pSetLayouts(stack.longs(layout));

            var pSet = stack.mallocLong(1);
            int result = vkAllocateDescriptorSets(device, allocInfo, pSet);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate descriptor set: " + result);
            return pSet.get(0);
        }
    }

    @Override
    public void resetDescriptorPool(long deviceHandle, long pool) {
        vkResetDescriptorPool(deviceCache.get(deviceHandle), pool, 0);
    }

    @Override
    public void updateDescriptorSets(long deviceHandle, long set,
                                     int[] bufferBindings, int[] bufferTypes,
                                     long[] buffers, long[] bufferOffsets, long[] bufferRanges,
                                     int[] imageBindings, long[] imageViews,
                                     long[] imageSamplers, int[] imageLayouts) {
        var device = deviceCache.get(deviceHandle);
        int bufCount = bufferBindings != null ? bufferBindings.length : 0;
        int imgCount = imageBindings != null ? imageBindings.length : 0;
        if (bufCount + imgCount == 0) return;

        try (var stack = stackPush()) {
            var writes = VkWriteDescriptorSet.calloc(bufCount + imgCount, stack);

            for (int i = 0; i < bufCount; i++) {
                var bufInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffers[i])
                        .offset(bufferOffsets[i])
                        .range(bufferRanges[i]);
                writes.get(i)
                        .sType$Default()
                        .dstSet(set)
                        .dstBinding(bufferBindings[i])
                        .dstArrayElement(0)
                        .descriptorCount(1)
                        .descriptorType(bufferTypes[i])
                        .pBufferInfo(bufInfo);
            }

            for (int i = 0; i < imgCount; i++) {
                var imgInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .imageLayout(imageLayouts[i])
                        .imageView(imageViews[i])
                        .sampler(imageSamplers[i]);
                writes.get(bufCount + i)
                        .sType$Default()
                        .dstSet(set)
                        .dstBinding(imageBindings[i])
                        .dstArrayElement(0)
                        .descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .pImageInfo(imgInfo);
            }

            vkUpdateDescriptorSets(device, writes, null);
        }
    }

    @Override
    public void destroyDescriptorPool(long deviceHandle, long pool) {
        vkDestroyDescriptorPool(deviceCache.get(deviceHandle), pool, null);
    }

    @Override
    public void destroyDescriptorSetLayout(long deviceHandle, long layout) {
        vkDestroyDescriptorSetLayout(deviceCache.get(deviceHandle), layout, null);
    }

    @Override
    public void destroyPipelineLayout(long deviceHandle, long pipelineLayout) {
        vkDestroyPipelineLayout(deviceCache.get(deviceHandle), pipelineLayout, null);
    }

    // ===== Command Pool / Buffer =====

    @Override
    public long createCommandPool(long deviceHandle, int queueFamily, int flags) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var poolCreateInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(flags)
                    .queueFamilyIndex(queueFamily);

            LongBuffer pPool = stack.mallocLong(1);
            int result = vkCreateCommandPool(device, poolCreateInfo, null, pPool);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create command pool: " + result);
            return pPool.get(0);
        }
    }

    @Override
    public long allocateCommandBuffer(long deviceHandle, long commandPool) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);

            var pCmd = stack.mallocPointer(1);
            int result = vkAllocateCommandBuffers(device, allocInfo, pCmd);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate command buffer: " + result);
            var cmd = new VkCommandBuffer(pCmd.get(0), device);
            cmdCache.put(cmd.address(), cmd);
            return cmd.address();
        }
    }

    @Override
    public void beginCommandBuffer(long cmdHandle, int flags) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(flags);
            int result = vkBeginCommandBuffer(cmd, beginInfo);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to begin command buffer: " + result);
        }
    }

    @Override
    public void endCommandBuffer(long cmdHandle) {
        int result = vkEndCommandBuffer(cmdCache.get(cmdHandle));
        if (result != VK_SUCCESS) throw new RuntimeException("Failed to end command buffer: " + result);
    }

    @Override
    public void resetCommandBuffer(long cmdHandle) {
        vkResetCommandBuffer(cmdCache.get(cmdHandle), 0);
    }

    @Override
    public void freeCommandBuffer(long deviceHandle, long pool, long cmdHandle) {
        var device = deviceCache.get(deviceHandle);
        var cmd = cmdCache.remove(cmdHandle);
        if (cmd != null) {
            try (var stack = stackPush()) {
                vkFreeCommandBuffers(device, pool, stack.pointers(cmd));
            }
        }
    }

    @Override
    public void destroyCommandPool(long deviceHandle, long commandPool) {
        vkDestroyCommandPool(deviceCache.get(deviceHandle), commandPool, null);
    }

    // ===== Command Recording =====

    @Override
    public void cmdBeginRenderPass(long cmdHandle, long renderPass, long framebuffer,
                                   int x, int y, int width, int height,
                                   float[] colorClearValues, float clearDepth, int clearStencil) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            // colorClearValues: groups of 4 floats (r,g,b,a) per color attachment
            int colorAttachmentCount = colorClearValues != null ? colorClearValues.length / 4 : 0;
            boolean hasDepth = clearDepth >= 0f; // convention: negative means no depth clear
            int clearCount = colorAttachmentCount + (hasDepth ? 1 : 0);

            var clearValues = VkClearValue.calloc(clearCount, stack);
            for (int i = 0; i < colorAttachmentCount; i++) {
                clearValues.get(i).color()
                        .float32(0, colorClearValues[i * 4])
                        .float32(1, colorClearValues[i * 4 + 1])
                        .float32(2, colorClearValues[i * 4 + 2])
                        .float32(3, colorClearValues[i * 4 + 3]);
            }
            if (hasDepth) {
                clearValues.get(colorAttachmentCount).depthStencil()
                        .depth(clearDepth).stencil(clearStencil);
            }

            var rpBegin = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass)
                    .framebuffer(framebuffer)
                    .renderArea(ra -> ra.offset(o -> o.x(x).y(y))
                            .extent(e -> e.width(width).height(height)))
                    .pClearValues(clearValues);

            vkCmdBeginRenderPass(cmd, rpBegin, VK_SUBPASS_CONTENTS_INLINE);
        }
    }

    @Override
    public void cmdEndRenderPass(long cmdHandle) {
        vkCmdEndRenderPass(cmdCache.get(cmdHandle));
    }

    @Override
    public void cmdBindPipeline(long cmdHandle, int bindPoint, long pipeline) {
        vkCmdBindPipeline(cmdCache.get(cmdHandle), bindPoint, pipeline);
    }

    @Override
    public void cmdBindVertexBuffers(long cmdHandle, long buffer) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            vkCmdBindVertexBuffers(cmd, 0, stack.longs(buffer), stack.longs(0));
        }
    }

    @Override
    public void cmdBindIndexBuffer(long cmdHandle, long buffer, int indexType) {
        vkCmdBindIndexBuffer(cmdCache.get(cmdHandle), buffer, 0, indexType);
    }

    @Override
    public void cmdBindDescriptorSets(long cmdHandle, int bindPoint, long pipelineLayout,
                                      int firstSet, long set) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            vkCmdBindDescriptorSets(cmd, bindPoint, pipelineLayout, firstSet, stack.longs(set), null);
        }
    }

    @Override
    public void cmdDraw(long cmdHandle, int vertexCount, int instanceCount,
                        int firstVertex, int firstInstance) {
        vkCmdDraw(cmdCache.get(cmdHandle), vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void cmdDrawIndexed(long cmdHandle, int indexCount, int instanceCount,
                               int firstIndex, int vertexOffset, int firstInstance) {
        vkCmdDrawIndexed(cmdCache.get(cmdHandle), indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    @Override
    public void cmdDrawIndirect(long cmdHandle, long buffer, long offset, int drawCount, int stride) {
        vkCmdDrawIndirect(cmdCache.get(cmdHandle), buffer, offset, drawCount, stride);
    }

    @Override
    public void cmdDrawIndexedIndirect(long cmdHandle, long buffer, long offset,
                                       int drawCount, int stride) {
        vkCmdDrawIndexedIndirect(cmdCache.get(cmdHandle), buffer, offset, drawCount, stride);
    }

    @Override
    public void cmdDispatch(long cmdHandle, int groupCountX, int groupCountY, int groupCountZ) {
        vkCmdDispatch(cmdCache.get(cmdHandle), groupCountX, groupCountY, groupCountZ);
    }

    @Override
    public void cmdSetViewport(long cmdHandle, float x, float y, float width, float height,
                               float minDepth, float maxDepth) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var viewport = VkViewport.calloc(1, stack)
                    .x(x).y(y).width(width).height(height)
                    .minDepth(minDepth).maxDepth(maxDepth);
            vkCmdSetViewport(cmd, 0, viewport);
        }
    }

    @Override
    public void cmdSetScissor(long cmdHandle, int x, int y, int width, int height) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var scissor = VkRect2D.calloc(1, stack)
                    .offset(o -> o.x(x).y(y))
                    .extent(e -> e.width(width).height(height));
            vkCmdSetScissor(cmd, 0, scissor);
        }
    }

    @Override
    public void cmdPushConstants(long cmdHandle, long pipelineLayout, int stageFlags,
                                 int offset, ByteBuffer data) {
        vkCmdPushConstants(cmdCache.get(cmdHandle), pipelineLayout, stageFlags, offset, data);
    }

    @Override
    public void cmdPipelineBarrier(long cmdHandle, int srcStageMask, int dstStageMask,
                                   int srcAccessMask, int dstAccessMask) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var memBarrier = VkMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .srcAccessMask(srcAccessMask)
                    .dstAccessMask(dstAccessMask);
            vkCmdPipelineBarrier(cmd, srcStageMask, dstStageMask, 0, memBarrier, null, null);
        }
    }

    @Override
    public void cmdImageBarrier(long cmdHandle, long image, int oldLayout, int newLayout,
                                int srcStageMask, int dstStageMask,
                                int srcAccessMask, int dstAccessMask,
                                int aspectMask, int baseMipLevel, int levelCount) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var barrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .oldLayout(oldLayout)
                    .newLayout(newLayout)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .subresourceRange(sr -> sr.aspectMask(aspectMask)
                            .baseMipLevel(baseMipLevel).levelCount(levelCount)
                            .baseArrayLayer(0).layerCount(1))
                    .srcAccessMask(srcAccessMask)
                    .dstAccessMask(dstAccessMask);
            vkCmdPipelineBarrier(cmd, srcStageMask, dstStageMask, 0, null, null, barrier);
        }
    }

    @Override
    public void cmdCopyBufferToImage(long cmdHandle, long buffer, long image,
                                     int imageLayout, int width, int height,
                                     int aspectMask, int mipLevel) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var region = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                    .imageSubresource(s -> s.aspectMask(aspectMask)
                            .mipLevel(mipLevel).baseArrayLayer(0).layerCount(1))
                    .imageOffset(o -> o.x(0).y(0).z(0))
                    .imageExtent(e -> e.width(width).height(height).depth(1));
            vkCmdCopyBufferToImage(cmd, buffer, image, imageLayout, region);
        }
    }

    @Override
    public void cmdCopyImageToBuffer(long cmdHandle, long image, int imageLayout, long buffer,
                                     int x, int y, int width, int height,
                                     int aspectMask, int mipLevel) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var region = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0).bufferRowLength(0).bufferImageHeight(0)
                    .imageSubresource(s -> s.aspectMask(aspectMask)
                            .mipLevel(mipLevel).baseArrayLayer(0).layerCount(1))
                    .imageOffset(o -> o.x(x).y(y).z(0))
                    .imageExtent(e -> e.width(width).height(height).depth(1));
            vkCmdCopyImageToBuffer(cmd, image, imageLayout, buffer, region);
        }
    }

    @Override
    public void cmdCopyBuffer(long cmdHandle, long src, long dst,
                              long srcOffset, long dstOffset, long size) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var region = VkBufferCopy.calloc(1, stack)
                    .srcOffset(srcOffset)
                    .dstOffset(dstOffset)
                    .size(size);
            vkCmdCopyBuffer(cmd, src, dst, region);
        }
    }

    @Override
    public void cmdBlitImage(long cmdHandle, long srcImage, long dstImage,
                             int srcWidth, int srcHeight, int dstWidth, int dstHeight,
                             int srcMip, int dstMip, int filter) {
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var blit = VkImageBlit.calloc(1, stack);
            blit.srcSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(srcMip)
                    .baseArrayLayer(0)
                    .layerCount(1);
            blit.srcOffsets(0).set(0, 0, 0);
            blit.srcOffsets(1).set(srcWidth, srcHeight, 1);

            blit.dstSubresource()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(dstMip)
                    .baseArrayLayer(0)
                    .layerCount(1);
            blit.dstOffsets(0).set(0, 0, 0);
            blit.dstOffsets(1).set(dstWidth, dstHeight, 1);

            vkCmdBlitImage(cmd, srcImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    dstImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, blit, filter);
        }
    }

    // ===== Dynamic State (VK 1.3) =====

    @Override
    public void cmdSetDepthTestEnable(long cmdHandle, boolean enabled) {
        vkCmdSetDepthTestEnable(cmdCache.get(cmdHandle), enabled);
    }

    @Override
    public void cmdSetDepthWriteEnable(long cmdHandle, boolean enabled) {
        vkCmdSetDepthWriteEnable(cmdCache.get(cmdHandle), enabled);
    }

    @Override
    public void cmdSetDepthCompareOp(long cmdHandle, int compareOp) {
        vkCmdSetDepthCompareOp(cmdCache.get(cmdHandle), compareOp);
    }

    @Override
    public void cmdSetCullMode(long cmdHandle, int cullMode) {
        vkCmdSetCullMode(cmdCache.get(cmdHandle), cullMode);
    }

    @Override
    public void cmdSetFrontFace(long cmdHandle, int frontFace) {
        vkCmdSetFrontFace(cmdCache.get(cmdHandle), frontFace);
    }

    @Override
    public void cmdSetStencilTestEnable(long cmdHandle, boolean enabled) {
        vkCmdSetStencilTestEnable(cmdCache.get(cmdHandle), enabled);
    }

    @Override
    public void cmdSetStencilOp(long cmdHandle, int faceMask, int failOp, int passOp,
                                int depthFailOp, int compareOp) {
        vkCmdSetStencilOp(cmdCache.get(cmdHandle), faceMask, failOp, passOp, depthFailOp, compareOp);
    }

    @Override
    public void cmdSetStencilCompareMask(long cmdHandle, int faceMask, int mask) {
        vkCmdSetStencilCompareMask(cmdCache.get(cmdHandle), faceMask, mask);
    }

    @Override
    public void cmdSetStencilWriteMask(long cmdHandle, int faceMask, int mask) {
        vkCmdSetStencilWriteMask(cmdCache.get(cmdHandle), faceMask, mask);
    }

    @Override
    public void cmdSetStencilReference(long cmdHandle, int faceMask, int reference) {
        vkCmdSetStencilReference(cmdCache.get(cmdHandle), faceMask, reference);
    }

    // ===== Sync =====

    @Override
    public long createSemaphore(long deviceHandle) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            var pSemaphore = stack.mallocLong(1);
            int result = vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create semaphore: " + result);
            return pSemaphore.get(0);
        }
    }

    @Override
    public long createFence(long deviceHandle, boolean signaled) {
        var device = deviceCache.get(deviceHandle);
        try (var stack = stackPush()) {
            var fenceInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);
            var pFence = stack.mallocLong(1);
            int result = vkCreateFence(device, fenceInfo, null, pFence);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create fence: " + result);
            return pFence.get(0);
        }
    }

    @Override
    public void waitForFences(long deviceHandle, long fence, long timeout) {
        vkWaitForFences(deviceCache.get(deviceHandle), fence, true, timeout);
    }

    @Override
    public void resetFences(long deviceHandle, long fence) {
        vkResetFences(deviceCache.get(deviceHandle), fence);
    }

    @Override
    public void destroySemaphore(long deviceHandle, long semaphore) {
        vkDestroySemaphore(deviceCache.get(deviceHandle), semaphore, null);
    }

    @Override
    public void destroyFence(long deviceHandle, long fence) {
        vkDestroyFence(deviceCache.get(deviceHandle), fence, null);
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queueHandle, long cmdHandle, long waitSemaphore,
                            int waitStageMask, long signalSemaphore, long fence) {
        var queue = queueCache.get(queueHandle);
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(stack.longs(waitSemaphore))
                    .pWaitDstStageMask(stack.ints(waitStageMask))
                    .pCommandBuffers(stack.pointers(cmd))
                    .pSignalSemaphores(stack.longs(signalSemaphore));
            int result = vkQueueSubmit(queue, submitInfo, fence);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to submit queue: " + result);
        }
    }

    @Override
    public void queueSubmitSimple(long queueHandle, long cmdHandle, long fence) {
        var queue = queueCache.get(queueHandle);
        var cmd = cmdCache.get(cmdHandle);
        try (var stack = stackPush()) {
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(cmd));
            int result = vkQueueSubmit(queue, submitInfo, fence);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to submit queue: " + result);
        }
    }

    @Override
    public void queueWaitIdle(long queueHandle) {
        vkQueueWaitIdle(queueCache.get(queueHandle));
    }

    // ===== Cleanup =====

    @Override
    public void destroyDevice(long deviceHandle) {
        var device = deviceCache.remove(deviceHandle);
        if (device != null) {
            // Clean up queue and command buffer caches associated with this device
            queueCache.clear();
            cmdCache.clear();
            vkDestroyDevice(device, null);
        }
    }

    @Override
    public void destroyInstance(long instanceHandle) {
        var instance = instanceCache.remove(instanceHandle);
        if (instance != null) {
            vkDestroyInstance(instance, null);
        }
    }

    // ===== Memory =====

    @Override
    public int findMemoryType(long physicalDeviceHandle, int typeFilter, int properties) {
        // We need a VkInstance to create a VkPhysicalDevice.
        // Find the instance that owns this physical device.
        VkPhysicalDevice physicalDevice = null;
        for (var instance : instanceCache.values()) {
            physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
            break; // Use the first (and typically only) instance
        }
        if (physicalDevice == null) {
            throw new RuntimeException("No VkInstance found for physical device lookup");
        }

        var memProperties = VkPhysicalDeviceMemoryProperties.calloc();
        try {
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);
            for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0 &&
                        (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
            // Fallback: try without DEVICE_LOCAL
            int fallbackProps = properties & ~VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            if (fallbackProps != properties) {
                for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
                    if ((typeFilter & (1 << i)) != 0 &&
                            (memProperties.memoryTypes(i).propertyFlags() & fallbackProps) == fallbackProps) {
                        return i;
                    }
                }
            }
        } finally {
            memProperties.free();
        }
        throw new RuntimeException("Failed to find suitable memory type");
    }

    // ===== Capabilities =====

    @Override
    public int getMaxImageDimension2D(long instanceHandle, long physicalDeviceHandle) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        var props = VkPhysicalDeviceProperties.calloc();
        try {
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            return props.limits().maxImageDimension2D();
        } finally {
            props.free();
        }
    }

    @Override
    public int getMaxFramebufferWidth(long instanceHandle, long physicalDeviceHandle) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        var props = VkPhysicalDeviceProperties.calloc();
        try {
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            return props.limits().maxFramebufferWidth();
        } finally {
            props.free();
        }
    }

    @Override
    public int getMaxFramebufferHeight(long instanceHandle, long physicalDeviceHandle) {
        var instance = instanceCache.get(instanceHandle);
        var physicalDevice = new VkPhysicalDevice(physicalDeviceHandle, instance);
        var props = VkPhysicalDeviceProperties.calloc();
        try {
            vkGetPhysicalDeviceProperties(physicalDevice, props);
            return props.limits().maxFramebufferHeight();
        } finally {
            props.free();
        }
    }
}

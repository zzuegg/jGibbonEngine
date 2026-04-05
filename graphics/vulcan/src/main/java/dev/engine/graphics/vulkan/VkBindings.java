package dev.engine.graphics.vulkan;

import java.nio.ByteBuffer;

/**
 * Abstraction over Vulkan functions and constants.
 *
 * <p>Implementations delegate to a concrete Vulkan loader (e.g. LWJGL).
 * The interface hides struct allocation, MemoryStack, and pointer-based
 * out parameters — callers work with plain longs and records.
 *
 * <p>All Vulkan object handles are represented as {@code long}.
 * Command buffers are also {@code long} handles.
 */
public interface VkBindings {

    // ===== Result Records =====

    record SwapchainResult(long swapchain, int format, int width, int height,
                           long[] images, long[] imageViews) {}

    record BufferAlloc(long buffer, long memory, long size) {}

    record ImageAlloc(long image, long memory, long imageView) {}

    /** Describes a render pass attachment. */
    record AttachmentDesc(int format, boolean clear, boolean store, int finalLayout) {}

    /** Describes a subpass dependency. */
    record SubpassDependencyDesc(int srcSubpass, int dstSubpass,
                                 int srcStageMask, int dstStageMask,
                                 int srcAccessMask, int dstAccessMask,
                                 int dependencyFlags) {}

    /** Result of instance creation — includes the instance handle. */
    record InstanceResult(long instance) {}

    /** Result of device creation — includes the device handle. */
    record DeviceResult(long device) {}

    // ===== Instance / Device =====

    /**
     * Creates a VkInstance.
     *
     * @param validationEnabled whether to enable validation layers
     * @param requiredExtensions instance extensions (e.g. surface extensions)
     * @param debugCallback if non-null, sets up debug messenger; receives (severity, message)
     * @return instance handle
     */
    long createInstance(boolean validationEnabled, String[] requiredExtensions,
                        java.util.function.BiConsumer<Integer, String> debugCallback);

    long[] enumeratePhysicalDevices(long instance);

    String getDeviceName(long instance, long physicalDevice);

    int[] getApiVersion(long instance, long physicalDevice);

    /**
     * Finds a graphics queue family that also supports presentation to the given surface.
     * Returns -1 if none found.
     */
    int findGraphicsQueueFamily(long instance, long physicalDevice, long surface);

    /**
     * Creates a logical device with one graphics queue.
     *
     * @return device handle
     */
    long createDevice(long instance, long physicalDevice, int queueFamily, String[] extensions);

    long getDeviceQueue(long device, long physicalDevice, int queueFamily);

    void deviceWaitIdle(long device);

    // ===== Surface =====
    // Surface creation is toolkit-specific (handled by SurfaceCreator callback)

    void destroySurface(long instance, long surface);

    // ===== Swapchain =====

    /**
     * Creates (or recreates) a swapchain. If oldSwapchain is not VK_NULL_HANDLE,
     * the old swapchain is used as the basis and its image views are destroyed.
     *
     * @return swapchain result with format, dimensions, images, and image views
     */
    SwapchainResult createSwapchain(long device, long physicalDevice, long surface,
                                     int requestedWidth, int requestedHeight, long oldSwapchain);

    /**
     * Creates a swapchain with preferred format and present mode.
     * Falls back to available options if preferences aren't supported.
     */
    default SwapchainResult createSwapchain(long device, long physicalDevice, long surface,
                                             int requestedWidth, int requestedHeight, long oldSwapchain,
                                             int preferredFormat, int preferredPresentMode) {
        return createSwapchain(device, physicalDevice, surface, requestedWidth, requestedHeight, oldSwapchain);
    }

    /**
     * Acquires the next swapchain image.
     * @return image index, or -1 if out of date
     */
    int acquireNextImage(long device, long swapchain, long semaphore);

    /**
     * Presents a swapchain image.
     * @return VK result code
     */
    int queuePresent(long queue, long swapchain, int imageIndex, long waitSemaphore);

    void destroySwapchain(long device, long swapchain);

    // ===== Buffer =====

    BufferAlloc createBuffer(long device, long physicalDevice, long size, int usage, int memProps);

    void destroyBuffer(long device, long buffer);

    void freeMemory(long device, long memory);

    /**
     * Maps device memory and returns the native address pointer.
     */
    long mapMemory(long device, long memory, long offset, long size);

    void unmapMemory(long device, long memory);

    void bindBufferMemory(long device, long buffer, long memory, long offset);

    // ===== Image / Texture =====

    ImageAlloc createImage(long device, long physicalDevice,
                           int width, int height, int depth, int arrayLayers,
                           int mipLevels, int format, int usage,
                           int imageType, int viewType, int aspectMask,
                           int createFlags);

    /**
     * Creates an image without a view (used for depth buffers, etc.).
     */
    record ImageNoView(long image, long memory) {}
    ImageNoView createImageNoView(long device, long physicalDevice,
                                  int width, int height, int depth, int arrayLayers,
                                  int mipLevels, int format, int usage,
                                  int imageType, int createFlags);

    long createImageView(long device, long image, int format, int viewType,
                         int aspectMask, int baseMipLevel, int levelCount,
                         int baseArrayLayer, int layerCount);

    void destroyImage(long device, long image);

    void destroyImageView(long device, long imageView);

    void bindImageMemory(long device, long image, long memory, long offset);

    // ===== Sampler =====

    long createSampler(long device, int magFilter, int minFilter, int mipmapMode,
                       int addressModeU, int addressModeV, int addressModeW,
                       float minLod, float maxLod, float lodBias,
                       boolean anisotropyEnable, float maxAnisotropy,
                       boolean compareEnable, int compareOp,
                       int borderColor);

    void destroySampler(long device, long sampler);

    // ===== Shader =====

    long createShaderModule(long device, byte[] spirv);

    void destroyShaderModule(long device, long module);

    // ===== Render Pass =====

    /**
     * Creates a render pass with the given attachment descriptions.
     * Color attachments come first, then an optional depth attachment.
     */
    long createRenderPass(long device,
                          AttachmentDesc[] colorAttachments,
                          AttachmentDesc depthAttachment,
                          SubpassDependencyDesc[] dependencies);

    void destroyRenderPass(long device, long renderPass);

    int findDepthFormat(long instance, long physicalDevice);

    // ===== Framebuffer =====

    long createFramebuffer(long device, long renderPass, long[] attachments, int width, int height);

    void destroyFramebuffer(long device, long framebuffer);

    // ===== Pipeline =====

    /**
     * Creates a graphics pipeline.
     *
     * @param shaderModules shader module handles
     * @param shaderStages  corresponding VK_SHADER_STAGE_xxx flags
     * @param vertexAttribLocations  attribute locations
     * @param vertexAttribFormats    VK format per attribute
     * @param vertexAttribOffsets    byte offset per attribute
     * @param vertexStride           total vertex stride in bytes
     * @param blendEnabled           whether color blending is enabled
     * @param srcColorFactor         blend src color factor
     * @param dstColorFactor         blend dst color factor
     * @param srcAlphaFactor         blend src alpha factor
     * @param dstAlphaFactor         blend dst alpha factor
     * @param wireframe              if true, polygon mode = LINE
     * @param dynamicStates          VK_DYNAMIC_STATE_xxx values
     */
    long createGraphicsPipeline(long device, long renderPass, long pipelineLayout,
                                long[] shaderModules, int[] shaderStages,
                                int[] vertexAttribLocations, int[] vertexAttribFormats,
                                int[] vertexAttribOffsets, int vertexStride,
                                boolean blendEnabled, int srcColorFactor, int dstColorFactor,
                                int srcAlphaFactor, int dstAlphaFactor,
                                boolean wireframe, int[] dynamicStates);

    long createComputePipeline(long device, long pipelineLayout, long shaderModule);

    void destroyPipeline(long device, long pipeline);

    // ===== Descriptor =====

    /**
     * Creates a descriptor set layout with the given bindings.
     *
     * @param bindings       binding indices
     * @param types          VK_DESCRIPTOR_TYPE_xxx per binding
     * @param stageFlags     VK_SHADER_STAGE_xxx per binding
     * @param counts         descriptor count per binding
     */
    long createDescriptorSetLayout(long device, int[] bindings, int[] types,
                                   int[] stageFlags, int[] counts);

    /**
     * Creates a pipeline layout.
     *
     * @param pushConstantSize size in bytes (0 for none)
     * @param pushConstantStages VK_SHADER_STAGE flags for push constants
     */
    long createPipelineLayout(long device, long descriptorSetLayout,
                              int pushConstantSize, int pushConstantStages);

    long createDescriptorPool(long device, int[] types, int[] descriptorCounts, int maxSets);

    long allocateDescriptorSet(long device, long pool, long layout);

    void resetDescriptorPool(long device, long pool);

    /**
     * Updates descriptor bindings in bulk.
     *
     * @param set             descriptor set
     * @param bufferBindings  binding index for each buffer write
     * @param bufferTypes     VK_DESCRIPTOR_TYPE for each buffer write
     * @param buffers         buffer handles
     * @param bufferOffsets   offsets
     * @param bufferRanges    ranges
     * @param imageBindings   binding index for each image write
     * @param imageViews      image view handles
     * @param imageSamplers   sampler handles
     * @param imageLayouts    image layouts
     */
    void updateDescriptorSets(long device, long set,
                              int[] bufferBindings, int[] bufferTypes,
                              long[] buffers, long[] bufferOffsets, long[] bufferRanges,
                              int[] imageBindings, long[] imageViews,
                              long[] imageSamplers, int[] imageLayouts);

    void destroyDescriptorPool(long device, long pool);

    void destroyDescriptorSetLayout(long device, long layout);

    void destroyPipelineLayout(long device, long pipelineLayout);

    // ===== Command Pool / Buffer =====

    long createCommandPool(long device, int queueFamily, int flags);

    long allocateCommandBuffer(long device, long commandPool);

    void beginCommandBuffer(long cmd, int flags);

    void endCommandBuffer(long cmd);

    void resetCommandBuffer(long cmd);

    void freeCommandBuffer(long device, long pool, long cmd);

    void destroyCommandPool(long device, long commandPool);

    // ===== Command Recording =====

    void cmdBeginRenderPass(long cmd, long renderPass, long framebuffer,
                            int x, int y, int width, int height,
                            float[] colorClearValues, float clearDepth, int clearStencil);

    void cmdEndRenderPass(long cmd);

    void cmdBindPipeline(long cmd, int bindPoint, long pipeline);

    void cmdBindVertexBuffers(long cmd, long buffer);

    void cmdBindIndexBuffer(long cmd, long buffer, int indexType);

    void cmdBindDescriptorSets(long cmd, int bindPoint, long pipelineLayout, int firstSet, long set);

    void cmdDraw(long cmd, int vertexCount, int instanceCount, int firstVertex, int firstInstance);

    void cmdDrawIndexed(long cmd, int indexCount, int instanceCount, int firstIndex,
                        int vertexOffset, int firstInstance);

    void cmdDrawIndirect(long cmd, long buffer, long offset, int drawCount, int stride);

    void cmdDrawIndexedIndirect(long cmd, long buffer, long offset, int drawCount, int stride);

    void cmdDispatch(long cmd, int groupCountX, int groupCountY, int groupCountZ);

    void cmdSetViewport(long cmd, float x, float y, float width, float height,
                        float minDepth, float maxDepth);

    void cmdSetScissor(long cmd, int x, int y, int width, int height);

    void cmdPushConstants(long cmd, long pipelineLayout, int stageFlags,
                          int offset, ByteBuffer data);

    void cmdPipelineBarrier(long cmd, int srcStageMask, int dstStageMask,
                            int srcAccessMask, int dstAccessMask);

    void cmdImageBarrier(long cmd, long image, int oldLayout, int newLayout,
                         int srcStageMask, int dstStageMask,
                         int srcAccessMask, int dstAccessMask,
                         int aspectMask, int baseMipLevel, int levelCount);

    void cmdCopyBufferToImage(long cmd, long buffer, long image,
                              int imageLayout, int width, int height,
                              int aspectMask, int mipLevel);

    void cmdCopyImageToBuffer(long cmd, long image, int imageLayout, long buffer,
                              int x, int y, int width, int height,
                              int aspectMask, int mipLevel);

    void cmdCopyBuffer(long cmd, long src, long dst, long srcOffset, long dstOffset, long size);

    void cmdBlitImage(long cmd, long srcImage, long dstImage,
                      int srcWidth, int srcHeight, int dstWidth, int dstHeight,
                      int srcMip, int dstMip, int filter);

    // ===== Dynamic State (VK 1.3) =====

    void cmdSetDepthTestEnable(long cmd, boolean enabled);

    void cmdSetDepthWriteEnable(long cmd, boolean enabled);

    void cmdSetDepthCompareOp(long cmd, int compareOp);

    void cmdSetCullMode(long cmd, int cullMode);

    void cmdSetFrontFace(long cmd, int frontFace);

    void cmdSetStencilTestEnable(long cmd, boolean enabled);

    void cmdSetStencilOp(long cmd, int faceMask, int failOp, int passOp,
                         int depthFailOp, int compareOp);

    void cmdSetStencilCompareMask(long cmd, int faceMask, int mask);

    void cmdSetStencilWriteMask(long cmd, int faceMask, int mask);

    void cmdSetStencilReference(long cmd, int faceMask, int reference);

    // ===== Sync =====

    long createSemaphore(long device);

    long createFence(long device, boolean signaled);

    void waitForFences(long device, long fence, long timeout);

    void resetFences(long device, long fence);

    void destroySemaphore(long device, long semaphore);

    void destroyFence(long device, long fence);

    // ===== Queue =====

    void queueSubmit(long queue, long cmd, long waitSemaphore,
                     int waitStageMask, long signalSemaphore, long fence);

    /**
     * Submit with no wait/signal semaphores.
     */
    void queueSubmitSimple(long queue, long cmd, long fence);

    void queueWaitIdle(long queue);

    // ===== Cleanup =====

    void destroyDevice(long device);

    void destroyInstance(long instance);

    // ===== Memory =====

    int findMemoryType(long physicalDevice, int typeFilter, int properties);

    // ===== Capabilities =====

    int getMaxImageDimension2D(long instance, long physicalDevice);

    int getMaxFramebufferWidth(long instance, long physicalDevice);

    int getMaxFramebufferHeight(long instance, long physicalDevice);

    // ===== Vulkan Constants =====

    // Null handle
    long VK_NULL_HANDLE = 0L;

    // Boolean
    int VK_FALSE = 0;
    int VK_TRUE = 1;

    // Result codes
    int VK_SUCCESS = 0;
    int VK_SUBOPTIMAL_KHR = 1000001003;
    int VK_ERROR_OUT_OF_DATE_KHR = -1000001004;

    // Queue bits
    int VK_QUEUE_GRAPHICS_BIT = 0x00000001;

    // Buffer usage
    int VK_BUFFER_USAGE_VERTEX_BUFFER_BIT    = 0x00000080;
    int VK_BUFFER_USAGE_INDEX_BUFFER_BIT     = 0x00000040;
    int VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT   = 0x00000010;
    int VK_BUFFER_USAGE_STORAGE_BUFFER_BIT   = 0x00000020;
    int VK_BUFFER_USAGE_TRANSFER_SRC_BIT     = 0x00000001;
    int VK_BUFFER_USAGE_TRANSFER_DST_BIT     = 0x00000002;

    // Memory properties
    int VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT     = 0x00000001;
    int VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT     = 0x00000002;
    int VK_MEMORY_PROPERTY_HOST_COHERENT_BIT    = 0x00000004;

    // Image types
    int VK_IMAGE_TYPE_2D = 1;
    int VK_IMAGE_TYPE_3D = 2;

    // Image view types
    int VK_IMAGE_VIEW_TYPE_2D       = 1;
    int VK_IMAGE_VIEW_TYPE_3D       = 2;
    int VK_IMAGE_VIEW_TYPE_CUBE     = 3;
    int VK_IMAGE_VIEW_TYPE_2D_ARRAY = 5;

    // Image create flags
    int VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT = 0x00000010;

    // Image usage
    int VK_IMAGE_USAGE_TRANSFER_SRC_BIT         = 0x00000001;
    int VK_IMAGE_USAGE_TRANSFER_DST_BIT         = 0x00000002;
    int VK_IMAGE_USAGE_SAMPLED_BIT               = 0x00000004;
    int VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT      = 0x00000010;
    int VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT = 0x00000020;

    // Image layouts
    int VK_IMAGE_LAYOUT_UNDEFINED                        = 0;
    int VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL         = 2;
    int VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL = 3;
    int VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL         = 5;
    int VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL             = 6;
    int VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL             = 7;
    int VK_IMAGE_LAYOUT_PRESENT_SRC_KHR                  = 1000001002;

    // Image aspects
    int VK_IMAGE_ASPECT_COLOR_BIT   = 0x00000001;
    int VK_IMAGE_ASPECT_DEPTH_BIT   = 0x00000002;
    int VK_IMAGE_ASPECT_STENCIL_BIT = 0x00000004;

    // Formats
    int VK_FORMAT_R8_UNORM                = 9;
    int VK_FORMAT_R8G8B8_UNORM            = 23;
    int VK_FORMAT_R8G8B8A8_UNORM          = 37;
    int VK_FORMAT_B8G8R8A8_UNORM          = 44;
    int VK_FORMAT_R8G8B8A8_SRGB           = 43;
    int VK_FORMAT_B8G8R8A8_SRGB           = 50;
    int VK_FORMAT_R16_SFLOAT              = 76;
    int VK_FORMAT_R16G16_SFLOAT           = 83;
    int VK_FORMAT_R16G16B16A16_SFLOAT     = 97;
    int VK_FORMAT_R32_UINT                = 98;
    int VK_FORMAT_R32_SINT                = 99;
    int VK_FORMAT_R32_SFLOAT              = 100;
    int VK_FORMAT_R32G32_SINT             = 101;
    int VK_FORMAT_R32G32_SFLOAT           = 103;
    int VK_FORMAT_R32G32B32_SINT          = 104;
    int VK_FORMAT_R32G32B32_SFLOAT        = 106;
    int VK_FORMAT_R32G32B32A32_SINT       = 107;
    int VK_FORMAT_R32G32B32A32_SFLOAT     = 109;
    int VK_FORMAT_D16_UNORM               = 124;
    int VK_FORMAT_D32_SFLOAT              = 126;
    int VK_FORMAT_D24_UNORM_S8_UINT       = 129;
    int VK_FORMAT_D32_SFLOAT_S8_UINT      = 130;

    // Vertex input formats (additional)
    int VK_FORMAT_R32_SINT_V              = 99;

    // Shader stages
    int VK_SHADER_STAGE_VERTEX_BIT    = 0x00000001;
    int VK_SHADER_STAGE_FRAGMENT_BIT  = 0x00000010;
    int VK_SHADER_STAGE_GEOMETRY_BIT  = 0x00000008;
    int VK_SHADER_STAGE_COMPUTE_BIT   = 0x00000020;

    // Pipeline bind points
    int VK_PIPELINE_BIND_POINT_GRAPHICS = 0;
    int VK_PIPELINE_BIND_POINT_COMPUTE  = 1;

    // Descriptor types
    int VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER       = 6;
    int VK_DESCRIPTOR_TYPE_STORAGE_BUFFER        = 7;
    int VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER = 1;

    // Index types
    int VK_INDEX_TYPE_UINT16 = 0;
    int VK_INDEX_TYPE_UINT32 = 1;

    // Pipeline stages
    int VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT              = 0x00000001;
    int VK_PIPELINE_STAGE_VERTEX_SHADER_BIT            = 0x00000008;
    int VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT          = 0x00000080;
    int VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT     = 0x00000100;
    int VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT  = 0x00000400;
    int VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT           = 0x00000800;
    int VK_PIPELINE_STAGE_TRANSFER_BIT                 = 0x00001000;
    int VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT           = 0x00002000;
    int VK_PIPELINE_STAGE_ALL_COMMANDS_BIT             = 0x00010000;

    // Access flags
    int VK_ACCESS_SHADER_READ_BIT                      = 0x00000020;
    int VK_ACCESS_SHADER_WRITE_BIT                     = 0x00000040;
    int VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT           = 0x00000100;
    int VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT   = 0x00000400;
    int VK_ACCESS_TRANSFER_READ_BIT                    = 0x00000800;
    int VK_ACCESS_TRANSFER_WRITE_BIT                   = 0x00001000;
    int VK_ACCESS_MEMORY_READ_BIT                      = 0x00008000;
    int VK_ACCESS_MEMORY_WRITE_BIT                     = 0x00010000;

    // Command pool / buffer flags
    int VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT = 0x00000002;
    int VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT     = 0x00000001;

    // Blend factors
    int VK_BLEND_FACTOR_ZERO                     = 0;
    int VK_BLEND_FACTOR_ONE                      = 1;
    int VK_BLEND_FACTOR_SRC_ALPHA                = 6;
    int VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA      = 7;
    int VK_BLEND_FACTOR_DST_COLOR                = 8;
    int VK_BLEND_FACTOR_DST_ALPHA                = 10;
    int VK_BLEND_FACTOR_ONE_MINUS_DST_ALPHA      = 11; // not used currently but useful

    // Compare ops
    int VK_COMPARE_OP_NEVER          = 0;
    int VK_COMPARE_OP_LESS           = 1;
    int VK_COMPARE_OP_EQUAL          = 2;
    int VK_COMPARE_OP_LESS_OR_EQUAL  = 3;
    int VK_COMPARE_OP_GREATER        = 4;
    int VK_COMPARE_OP_NOT_EQUAL      = 5;
    int VK_COMPARE_OP_GREATER_OR_EQUAL = 6;
    int VK_COMPARE_OP_ALWAYS         = 7;

    // Stencil ops
    int VK_STENCIL_OP_KEEP                = 0;
    int VK_STENCIL_OP_ZERO                = 1;
    int VK_STENCIL_OP_REPLACE             = 2;
    int VK_STENCIL_OP_INCREMENT_AND_CLAMP = 3;
    int VK_STENCIL_OP_DECREMENT_AND_CLAMP = 4;
    int VK_STENCIL_OP_INVERT              = 5;
    int VK_STENCIL_OP_INCREMENT_AND_WRAP  = 6;
    int VK_STENCIL_OP_DECREMENT_AND_WRAP  = 7;

    // Stencil face mask
    int VK_STENCIL_FACE_FRONT_AND_BACK = 0x00000003;

    // Cull modes
    int VK_CULL_MODE_NONE      = 0;
    int VK_CULL_MODE_FRONT_BIT = 0x00000001;
    int VK_CULL_MODE_BACK_BIT  = 0x00000002;

    // Front face
    int VK_FRONT_FACE_COUNTER_CLOCKWISE = 0;
    int VK_FRONT_FACE_CLOCKWISE         = 1;

    // Filter
    int VK_FILTER_NEAREST = 0;
    int VK_FILTER_LINEAR  = 1;

    // Sampler mipmap mode
    int VK_SAMPLER_MIPMAP_MODE_NEAREST = 0;
    int VK_SAMPLER_MIPMAP_MODE_LINEAR  = 1;

    // Sampler address mode
    int VK_SAMPLER_ADDRESS_MODE_REPEAT          = 0;
    int VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT = 1;
    int VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE   = 2;
    int VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER = 3;

    // Polygon mode
    int VK_POLYGON_MODE_FILL = 0;
    int VK_POLYGON_MODE_LINE = 1;

    // Topology
    int VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST = 3;

    // Sample count
    int VK_SAMPLE_COUNT_1_BIT = 0x00000001;

    // Sharing mode
    int VK_SHARING_MODE_EXCLUSIVE = 0;

    // Tiling
    int VK_IMAGE_TILING_OPTIMAL = 0;

    // Format features
    int VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT = 0x00000200;

    // Fence flags
    int VK_FENCE_CREATE_SIGNALED_BIT = 0x00000001;

    // Subpass
    int VK_SUBPASS_EXTERNAL = (~0);

    // Dependency flags
    int VK_DEPENDENCY_BY_REGION_BIT = 0x00000001;

    // Load/store ops
    int VK_ATTACHMENT_LOAD_OP_CLEAR     = 1;
    int VK_ATTACHMENT_LOAD_OP_DONT_CARE = 2;
    int VK_ATTACHMENT_STORE_OP_STORE     = 0;
    int VK_ATTACHMENT_STORE_OP_DONT_CARE = 1;

    // Dynamic states
    int VK_DYNAMIC_STATE_VIEWPORT             = 0;
    int VK_DYNAMIC_STATE_SCISSOR              = 1;
    int VK_DYNAMIC_STATE_DEPTH_TEST_ENABLE    = 1000267006;
    int VK_DYNAMIC_STATE_DEPTH_WRITE_ENABLE   = 1000267007;
    int VK_DYNAMIC_STATE_DEPTH_COMPARE_OP     = 1000267008;
    int VK_DYNAMIC_STATE_CULL_MODE            = 1000267000;
    int VK_DYNAMIC_STATE_FRONT_FACE           = 1000267001;
    int VK_DYNAMIC_STATE_STENCIL_COMPARE_MASK = 6;
    int VK_DYNAMIC_STATE_STENCIL_WRITE_MASK   = 7;
    int VK_DYNAMIC_STATE_STENCIL_REFERENCE    = 8;
    int VK_DYNAMIC_STATE_STENCIL_TEST_ENABLE  = 1000267010;
    int VK_DYNAMIC_STATE_STENCIL_OP           = 1000267011;

    // Composite alpha
    int VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR = 0x00000001;

    // Present mode
    int VK_PRESENT_MODE_IMMEDIATE_KHR = 0;
    int VK_PRESENT_MODE_MAILBOX_KHR = 1;
    int VK_PRESENT_MODE_FIFO_KHR = 2;

    // Color space
    int VK_COLOR_SPACE_SRGB_NONLINEAR_KHR = 0;

    // Queue family
    int VK_QUEUE_FAMILY_IGNORED = (~0);

    // Border color
    int VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK = 0;
    int VK_BORDER_COLOR_INT_TRANSPARENT_BLACK   = 1;
    int VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK      = 2;
    int VK_BORDER_COLOR_INT_OPAQUE_BLACK        = 3;
    int VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE      = 4;
    int VK_BORDER_COLOR_INT_OPAQUE_WHITE        = 5;

    // Blend ops
    int VK_BLEND_OP_ADD = 0;

    // Color component bits
    int VK_COLOR_COMPONENT_R_BIT = 0x00000001;
    int VK_COLOR_COMPONENT_G_BIT = 0x00000002;
    int VK_COLOR_COMPONENT_B_BIT = 0x00000004;
    int VK_COLOR_COMPONENT_A_BIT = 0x00000008;

    // Component swizzle
    int VK_COMPONENT_SWIZZLE_IDENTITY = 0;

    // Vertex input rate
    int VK_VERTEX_INPUT_RATE_VERTEX = 0;
}

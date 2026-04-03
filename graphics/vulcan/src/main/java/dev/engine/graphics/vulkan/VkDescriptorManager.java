package dev.engine.graphics.vulkan;

/**
 * Manages Vulkan descriptor sets for binding UBOs, SSBOs, and textures to shaders.
 *
 * <p>Simple strategy: one large pool per frame, reset at frame start, allocate on demand.
 * A single descriptor set layout is shared across all pipelines (one binding per UBO slot).
 */
class VkDescriptorManager implements AutoCloseable {

    private static final int MAX_UBO_BINDINGS = 16;
    private static final int MAX_TEXTURE_BINDINGS = 8;
    private static final int TEXTURE_BINDING_OFFSET = MAX_UBO_BINDINGS; // bindings 16-23
    private static final int MAX_SSBO_BINDINGS = 8;
    static final int SSBO_BINDING_OFFSET = TEXTURE_BINDING_OFFSET + MAX_TEXTURE_BINDINGS; // bindings 24-31
    private static final int TOTAL_BINDINGS = MAX_UBO_BINDINGS + MAX_TEXTURE_BINDINGS + MAX_SSBO_BINDINGS;
    private static final int MAX_SETS_PER_FRAME = 256;

    private final VkBindings vk;
    private final long device;
    private final long descriptorSetLayout;
    private final long pipelineLayout;
    private final long[] pools; // one per frame-in-flight
    private final int frameCount;
    private final long dummyBuffer;
    private final long dummyMemory;
    private final long dummyImageView;
    private final long dummyImage;
    private final long dummyImageMemory;
    private final long dummySampler;

    VkDescriptorManager(VkBindings vk, long device, long physicalDevice, int frameCount) {
        this.vk = vk;
        this.device = device;
        this.frameCount = frameCount;

        // Create a small dummy buffer for unused UBO/SSBO descriptor bindings
        var dummyBufAlloc = vk.createBuffer(device, physicalDevice, 16,
                VkBindings.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VkBindings.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        dummyBuffer = dummyBufAlloc.buffer();
        dummyMemory = dummyBufAlloc.memory();

        // Create a 1x1 dummy image + image view + sampler for unused texture bindings
        var dummyImgAlloc = vk.createImage(device, physicalDevice,
                1, 1, 1, 1, 1,
                VkBindings.VK_FORMAT_R8G8B8A8_UNORM,
                VkBindings.VK_IMAGE_USAGE_SAMPLED_BIT,
                VkBindings.VK_IMAGE_TYPE_2D,
                VkBindings.VK_IMAGE_VIEW_TYPE_2D,
                VkBindings.VK_IMAGE_ASPECT_COLOR_BIT,
                0);
        dummyImage = dummyImgAlloc.image();
        dummyImageMemory = dummyImgAlloc.memory();
        dummyImageView = dummyImgAlloc.imageView();

        dummySampler = vk.createSampler(device,
                VkBindings.VK_FILTER_NEAREST, VkBindings.VK_FILTER_NEAREST,
                VkBindings.VK_SAMPLER_MIPMAP_MODE_NEAREST,
                VkBindings.VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VkBindings.VK_SAMPLER_ADDRESS_MODE_REPEAT,
                VkBindings.VK_SAMPLER_ADDRESS_MODE_REPEAT,
                0.0f, 0.0f);

        // Build bindings arrays
        int[] bindings = new int[TOTAL_BINDINGS];
        int[] types = new int[TOTAL_BINDINGS];
        int[] stageFlags = new int[TOTAL_BINDINGS];
        int[] counts = new int[TOTAL_BINDINGS];
        int vertFragStages = VkBindings.VK_SHADER_STAGE_VERTEX_BIT | VkBindings.VK_SHADER_STAGE_FRAGMENT_BIT;

        for (int i = 0; i < MAX_UBO_BINDINGS; i++) {
            bindings[i] = i;
            types[i] = VkBindings.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            stageFlags[i] = vertFragStages;
            counts[i] = 1;
        }
        for (int i = 0; i < MAX_TEXTURE_BINDINGS; i++) {
            int idx = MAX_UBO_BINDINGS + i;
            bindings[idx] = TEXTURE_BINDING_OFFSET + i;
            types[idx] = VkBindings.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            stageFlags[idx] = VkBindings.VK_SHADER_STAGE_FRAGMENT_BIT;
            counts[idx] = 1;
        }
        for (int i = 0; i < MAX_SSBO_BINDINGS; i++) {
            int idx = MAX_UBO_BINDINGS + MAX_TEXTURE_BINDINGS + i;
            bindings[idx] = SSBO_BINDING_OFFSET + i;
            types[idx] = VkBindings.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            stageFlags[idx] = vertFragStages;
            counts[idx] = 1;
        }

        this.descriptorSetLayout = vk.createDescriptorSetLayout(device, bindings, types, stageFlags, counts);

        int pushConstantStages = VkBindings.VK_SHADER_STAGE_VERTEX_BIT
                | VkBindings.VK_SHADER_STAGE_FRAGMENT_BIT
                | VkBindings.VK_SHADER_STAGE_COMPUTE_BIT;
        this.pipelineLayout = vk.createPipelineLayout(device, descriptorSetLayout, 128, pushConstantStages);

        // Create pools (one per frame-in-flight)
        pools = new long[frameCount];
        for (int i = 0; i < frameCount; i++) {
            pools[i] = createPool();
        }
    }

    /** Returns the shared pipeline layout. Used when creating VkPipeline. */
    long pipelineLayout() { return pipelineLayout; }

    /** Returns the descriptor set layout. */
    long descriptorSetLayout() { return descriptorSetLayout; }

    /** Resets the pool for the given frame index. Call at frame start. */
    void resetPool(int frameIndex) {
        vk.resetDescriptorPool(device, pools[frameIndex]);
    }

    /** Allocates a descriptor set pre-filled with dummy buffer/image on all bindings. */
    long allocateSet(int frameIndex) {
        long set = vk.allocateDescriptorSet(device, pools[frameIndex], descriptorSetLayout);

        // Pre-fill all bindings with dummy resources
        int bufCount = MAX_UBO_BINDINGS + MAX_SSBO_BINDINGS;
        int[] bufferBindings = new int[bufCount];
        int[] bufferTypes = new int[bufCount];
        long[] buffers = new long[bufCount];
        long[] bufferOffsets = new long[bufCount];
        long[] bufferRanges = new long[bufCount];

        for (int i = 0; i < MAX_UBO_BINDINGS; i++) {
            bufferBindings[i] = i;
            bufferTypes[i] = VkBindings.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            buffers[i] = dummyBuffer;
            bufferOffsets[i] = 0;
            bufferRanges[i] = 16;
        }
        for (int i = 0; i < MAX_SSBO_BINDINGS; i++) {
            int idx = MAX_UBO_BINDINGS + i;
            bufferBindings[idx] = SSBO_BINDING_OFFSET + i;
            bufferTypes[idx] = VkBindings.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            buffers[idx] = dummyBuffer;
            bufferOffsets[idx] = 0;
            bufferRanges[idx] = 16;
        }

        int[] imageBindings = new int[MAX_TEXTURE_BINDINGS];
        long[] imageViews = new long[MAX_TEXTURE_BINDINGS];
        long[] imageSamplers = new long[MAX_TEXTURE_BINDINGS];
        int[] imageLayouts = new int[MAX_TEXTURE_BINDINGS];
        for (int i = 0; i < MAX_TEXTURE_BINDINGS; i++) {
            imageBindings[i] = TEXTURE_BINDING_OFFSET + i;
            imageViews[i] = dummyImageView;
            imageSamplers[i] = dummySampler;
            imageLayouts[i] = VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }

        vk.updateDescriptorSets(device, set,
                bufferBindings, bufferTypes, buffers, bufferOffsets, bufferRanges,
                imageBindings, imageViews, imageSamplers, imageLayouts);

        return set;
    }

    /** Returns the texture binding offset (added to unit index to get descriptor binding). */
    int textureBindingOffset() { return TEXTURE_BINDING_OFFSET; }

    /** Returns the SSBO binding offset (added to slot index to get descriptor binding). */
    int ssboBindingOffset() { return SSBO_BINDING_OFFSET; }

    /** Updates a uniform buffer binding in a descriptor set. */
    void updateUniformBuffer(long descriptorSet, int binding, long buffer, long size) {
        vk.updateDescriptorSets(device, descriptorSet,
                new int[]{binding},
                new int[]{VkBindings.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER},
                new long[]{buffer}, new long[]{0}, new long[]{size},
                null, null, null, null);
    }

    /** Updates a storage buffer binding in a descriptor set. */
    void updateStorageBuffer(long descriptorSet, int binding, long buffer, long size) {
        vk.updateDescriptorSets(device, descriptorSet,
                new int[]{binding},
                new int[]{VkBindings.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER},
                new long[]{buffer}, new long[]{0}, new long[]{size},
                null, null, null, null);
    }

    private long createPool() {
        return vk.createDescriptorPool(device,
                new int[]{
                        VkBindings.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                        VkBindings.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                        VkBindings.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
                },
                new int[]{
                        MAX_UBO_BINDINGS * MAX_SETS_PER_FRAME,
                        MAX_TEXTURE_BINDINGS * MAX_SETS_PER_FRAME,
                        MAX_SSBO_BINDINGS * MAX_SETS_PER_FRAME
                },
                MAX_SETS_PER_FRAME);
    }

    @Override
    public void close() {
        for (long pool : pools) {
            vk.destroyDescriptorPool(device, pool);
        }
        vk.destroyPipelineLayout(device, pipelineLayout);
        vk.destroyDescriptorSetLayout(device, descriptorSetLayout);
        vk.destroySampler(device, dummySampler);
        vk.destroyImageView(device, dummyImageView);
        vk.destroyImage(device, dummyImage);
        vk.freeMemory(device, dummyImageMemory);
        vk.freeMemory(device, dummyMemory);
        vk.destroyBuffer(device, dummyBuffer);
    }
}

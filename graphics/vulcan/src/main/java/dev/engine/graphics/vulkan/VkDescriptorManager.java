package dev.engine.graphics.vulkan;

import org.lwjgl.vulkan.*;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

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

    private final VkDevice device;
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

    VkDescriptorManager(VkDevice device, VkPhysicalDevice physicalDevice, int frameCount,
                         java.util.function.BiFunction<Integer, Integer, Integer> memoryTypeFinder) {
        this.device = device;
        this.frameCount = frameCount;

        // Create a small dummy buffer for unused UBO descriptor bindings
        try (var stack = stackPush()) {
            var bufInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(16)
                    .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            var pBuf = stack.mallocLong(1);
            vkCreateBuffer(device, bufInfo, null, pBuf);
            dummyBuffer = pBuf.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(device, dummyBuffer, memReqs);
            int memType = memoryTypeFinder.apply(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);
            var pMem = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMem);
            dummyMemory = pMem.get(0);
            vkBindBufferMemory(device, dummyBuffer, dummyMemory, 0);
        }

        // Create a 1x1 dummy image + image view + sampler for unused texture bindings
        try (var stack = stackPush()) {
            var imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .extent(e -> e.width(1).height(1).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            var pImg = stack.mallocLong(1);
            vkCreateImage(device, imageInfo, null, pImg);
            dummyImage = pImg.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, dummyImage, memReqs);
            int memType = memoryTypeFinder.apply(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(memType);
            var pMem = stack.mallocLong(1);
            vkAllocateMemory(device, allocInfo, null, pMem);
            dummyImageMemory = pMem.get(0);
            vkBindImageMemory(device, dummyImage, dummyImageMemory, 0);

            var viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(dummyImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .subresourceRange(sr -> sr.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));
            var pView = stack.mallocLong(1);
            vkCreateImageView(device, viewInfo, null, pView);
            dummyImageView = pView.get(0);

            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .maxLod(0.0f);
            var pSampler = stack.mallocLong(1);
            vkCreateSampler(device, samplerInfo, null, pSampler);
            dummySampler = pSampler.get(0);
        }

        try (var stack = stackPush()) {
            // Create a layout with UBO bindings (0..15) + combined image sampler bindings (16..23)
            var bindings = VkDescriptorSetLayoutBinding.calloc(TOTAL_BINDINGS, stack);
            for (int i = 0; i < MAX_UBO_BINDINGS; i++) {
                bindings.get(i)
                        .binding(i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            for (int i = 0; i < MAX_TEXTURE_BINDINGS; i++) {
                bindings.get(MAX_UBO_BINDINGS + i)
                        .binding(TEXTURE_BINDING_OFFSET + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            }
            for (int i = 0; i < MAX_SSBO_BINDINGS; i++) {
                bindings.get(MAX_UBO_BINDINGS + MAX_TEXTURE_BINDINGS + i)
                        .binding(SSBO_BINDING_OFFSET + i)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
            }

            var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pBindings(bindings);

            var pLayout = stack.mallocLong(1);
            int result = vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create descriptor set layout: " + result);
            this.descriptorSetLayout = pLayout.get(0);

            // Create pipeline layout with this descriptor set layout + push constants
            var pushConstantRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT | VK_SHADER_STAGE_COMPUTE_BIT)
                    .offset(0)
                    .size(128);

            var pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(pushConstantRange);

            var pPipelineLayout = stack.mallocLong(1);
            result = vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create pipeline layout: " + result);
            this.pipelineLayout = pPipelineLayout.get(0);

            // Create pools (one per frame-in-flight)
            pools = new long[frameCount];
            for (int i = 0; i < frameCount; i++) {
                pools[i] = createPool();
            }
        }
    }

    /** Returns the shared pipeline layout. Used when creating VkPipeline. */
    long pipelineLayout() { return pipelineLayout; }

    /** Returns the descriptor set layout. */
    long descriptorSetLayout() { return descriptorSetLayout; }

    /** Resets the pool for the given frame index. Call at frame start. */
    void resetPool(int frameIndex) {
        vkResetDescriptorPool(device, pools[frameIndex], 0);
    }

    /** Allocates a descriptor set pre-filled with dummy buffer/image on all bindings. */
    long allocateSet(int frameIndex) {
        try (var stack = stackPush()) {
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(pools[frameIndex])
                    .pSetLayouts(stack.longs(descriptorSetLayout));

            var pSet = stack.mallocLong(1);
            int result = vkAllocateDescriptorSets(device, allocInfo, pSet);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to allocate descriptor set: " + result);
            long set = pSet.get(0);

            // Pre-fill all UBO bindings with the dummy buffer
            var writes = VkWriteDescriptorSet.calloc(TOTAL_BINDINGS, stack);
            for (int i = 0; i < MAX_UBO_BINDINGS; i++) {
                var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(dummyBuffer).offset(0).range(16);
                writes.get(i).sType$Default().dstSet(set).dstBinding(i)
                        .dstArrayElement(0).descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                        .pBufferInfo(bufferInfo);
            }
            // Pre-fill all texture bindings with the dummy image+sampler
            for (int i = 0; i < MAX_TEXTURE_BINDINGS; i++) {
                var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                        .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .imageView(dummyImageView)
                        .sampler(dummySampler);
                writes.get(MAX_UBO_BINDINGS + i).sType$Default().dstSet(set)
                        .dstBinding(TEXTURE_BINDING_OFFSET + i)
                        .dstArrayElement(0).descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .pImageInfo(imageInfo);
            }
            // Pre-fill all SSBO bindings with the dummy buffer
            for (int i = 0; i < MAX_SSBO_BINDINGS; i++) {
                var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(dummyBuffer).offset(0).range(16);
                writes.get(MAX_UBO_BINDINGS + MAX_TEXTURE_BINDINGS + i).sType$Default().dstSet(set)
                        .dstBinding(SSBO_BINDING_OFFSET + i)
                        .dstArrayElement(0).descriptorCount(1)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .pBufferInfo(bufferInfo);
            }
            vkUpdateDescriptorSets(device, writes, null);
            return set;
        }
    }

    /** Returns the texture binding offset (added to unit index to get descriptor binding). */
    int textureBindingOffset() { return TEXTURE_BINDING_OFFSET; }

    /** Returns the SSBO binding offset (added to slot index to get descriptor binding). */
    int ssboBindingOffset() { return SSBO_BINDING_OFFSET; }

    /** Updates a uniform buffer binding in a descriptor set. */
    void updateUniformBuffer(long descriptorSet, int binding, long buffer, long size) {
        try (var stack = stackPush()) {
            var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer)
                    .offset(0)
                    .range(size);

            var write = VkWriteDescriptorSet.calloc(1, stack)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(binding)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(bufferInfo);

            vkUpdateDescriptorSets(device, write, null);
        }
    }

    /** Updates a storage buffer binding in a descriptor set. */
    void updateStorageBuffer(long descriptorSet, int binding, long buffer, long size) {
        try (var stack = stackPush()) {
            var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer)
                    .offset(0)
                    .range(size);

            var write = VkWriteDescriptorSet.calloc(1, stack)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(binding)
                    .dstArrayElement(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .pBufferInfo(bufferInfo);

            vkUpdateDescriptorSets(device, write, null);
        }
    }

    private long createPool() {
        try (var stack = stackPush()) {
            var poolSizes = VkDescriptorPoolSize.calloc(3, stack);
            poolSizes.get(0)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(MAX_UBO_BINDINGS * MAX_SETS_PER_FRAME);
            poolSizes.get(1)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(MAX_TEXTURE_BINDINGS * MAX_SETS_PER_FRAME);
            poolSizes.get(2)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(MAX_SSBO_BINDINGS * MAX_SETS_PER_FRAME);

            var poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .pPoolSizes(poolSizes)
                    .maxSets(MAX_SETS_PER_FRAME);

            var pPool = stack.mallocLong(1);
            int result = vkCreateDescriptorPool(device, poolInfo, null, pPool);
            if (result != VK_SUCCESS) throw new RuntimeException("Failed to create descriptor pool: " + result);
            return pPool.get(0);
        }
    }

    @Override
    public void close() {
        for (long pool : pools) {
            vkDestroyDescriptorPool(device, pool, null);
        }
        vkDestroyPipelineLayout(device, pipelineLayout, null);
        vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        vkDestroySampler(device, dummySampler, null);
        vkDestroyImageView(device, dummyImageView, null);
        vkDestroyImage(device, dummyImage, null);
        vkFreeMemory(device, dummyImageMemory, null);
        vkFreeMemory(device, dummyMemory, null);
        vkDestroyBuffer(device, dummyBuffer, null);
    }
}

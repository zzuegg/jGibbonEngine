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
import dev.engine.graphics.texture.TextureType;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.renderstate.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.engine.core.memory.NativeMemory;
import dev.engine.core.memory.SegmentNativeMemory;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class VkRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(VkRenderDevice.class);

    private final VkBindings vk;
    private final long instance;
    private final long physicalDevice;
    private final long device;
    private final long graphicsQueue;
    private final long commandPool;
    private final int graphicsQueueFamily;

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
    private long currentDescriptorSet = VkBindings.VK_NULL_HANDLE;
    private boolean descriptorDirty = false;
    private final long[] pendingUboBuffers = new long[16];
    private final long[] pendingUboSizes = new long[16];
    private final long[] pendingSsboBuffers = new long[8];
    private final long[] pendingSsboSizes = new long[8];

    private final AtomicLong frameCounter = new AtomicLong(0);
    private float[] clearColor = {0.05f, 0.05f, 0.08f, 1.0f};

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

    // Pipeline variant cache: keyed by "pipelineIndex_blendModeName_wireframe" -> variant VkPipeline handle
    private final dev.engine.graphics.pipeline.PipelineVariantCache<String> pipelineVariants = new dev.engine.graphics.pipeline.PipelineVariantCache<>();
    private Handle<PipelineResource> currentBoundPipeline = null;
    private boolean currentWireframe = false;
    private BlendMode currentBlendMode = BlendMode.NONE;
    /** Per-attachment blend modes for MRT; null means use {@code currentBlendMode} for all. */
    private BlendMode[] currentBlendModes = null;
    private record PipelineSpec(List<dev.engine.graphics.pipeline.ShaderBinary> binaries, VertexFormat vertexFormat) {}
    private final Map<Integer, PipelineSpec> pipelineSpecs = new HashMap<>();

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
     * @param vk                 the Vulkan bindings implementation
     * @param requiredExtensions instance extensions needed for surface support
     * @param surfaceFactory     given the VkInstance handle, creates and returns a VkSurfaceKHR handle
     */
    public VkRenderDevice(VkBindings vk, String[] requiredExtensions,
                           java.util.function.LongUnaryOperator surfaceFactory,
                           int initialWidth, int initialHeight) {
        this(vk, requiredExtensions, surfaceFactory, initialWidth, initialHeight,
                VkBindings.VK_FORMAT_B8G8R8A8_UNORM, VkBindings.VK_PRESENT_MODE_FIFO_KHR);
    }

    public VkRenderDevice(VkBindings vk, String[] requiredExtensions,
                           java.util.function.LongUnaryOperator surfaceFactory,
                           int initialWidth, int initialHeight,
                           int preferredFormat, int preferredPresentMode) {
        this.vk = vk;

        // --- Create VkInstance ---
        this.instance = vk.createInstance(true, requiredExtensions,
                (severity, msg) -> {
                    if ((severity & 0x00000100) != 0) { // ERROR
                        log.error("[Vulkan Validation] {}", msg);
                    } else {
                        log.warn("[Vulkan Validation] {}", msg);
                    }
                });

        this.surface = surfaceFactory.applyAsLong(instance);

        // --- Pick physical device ---
        long[] physicalDevices = vk.enumeratePhysicalDevices(instance);
        if (physicalDevices.length == 0) {
            throw new RuntimeException("No Vulkan-capable GPUs found");
        }

        long chosen = 0;
        int chosenQueueFamily = -1;
        for (long pd : physicalDevices) {
            int qf = vk.findGraphicsQueueFamily(instance, pd, surface);
            if (qf >= 0) {
                chosen = pd;
                chosenQueueFamily = qf;
                break;
            }
        }
        if (chosen == 0) {
            throw new RuntimeException("No GPU with graphics queue found");
        }
        this.physicalDevice = chosen;
        this.graphicsQueueFamily = chosenQueueFamily;

        // Log device info
        String deviceName = vk.getDeviceName(instance, physicalDevice);
        int[] apiVer = vk.getApiVersion(instance, physicalDevice);
        log.info("Vulkan device: {} (Vulkan {}.{}.{})", deviceName, apiVer[0], apiVer[1], apiVer[2]);

        // --- Create logical device ---
        this.device = vk.createDevice(instance, physicalDevice, graphicsQueueFamily,
                new String[]{"VK_KHR_swapchain"});

        // --- Get graphics queue ---
        this.graphicsQueue = vk.getDeviceQueue(device, physicalDevice, graphicsQueueFamily);

        // --- Create command pool ---
        this.commandPool = vk.createCommandPool(device, graphicsQueueFamily,
                VkBindings.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

        // --- Create swapchain + render pass + framebuffers ---
        this.swapchain = new VkSwapchain(vk, device, physicalDevice, surface);
        swapchain.setPreferences(preferredFormat, preferredPresentMode);
        swapchain.create(initialWidth, initialHeight);

        this.depthFormat = VkRenderPassFactory.findDepthFormat(vk, instance, physicalDevice);
        this.renderPass = VkRenderPassFactory.createColorDepth(vk, device, swapchain.imageFormat(), depthFormat);

        this.framebuffers = new VkFramebufferSet(vk, device, physicalDevice);
        framebuffers.create(swapchain, renderPass, depthFormat);

        // --- Create per-frame resources ---
        this.frames = new VkFrameContext[MAX_FRAMES_IN_FLIGHT];
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            frames[i] = new VkFrameContext(vk, device, commandPool);
        }

        this.descriptorManager = new VkDescriptorManager(vk, device, physicalDevice, MAX_FRAMES_IN_FLIGHT);

        log.info("Vulkan render device initialized (swapchain: {}x{}, {} images)",
                swapchain.width(), swapchain.height(), swapchain.imageCount());
    }

    // --- Buffer operations ---

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        int usageFlags = mapBufferUsage(descriptor.usage());
        int memoryFlags = mapAccessPattern(descriptor.accessPattern());

        var alloc = vk.createBuffer(device, physicalDevice, descriptor.size(), usageFlags, memoryFlags);
        return bufferRegistry.register(new BufferAllocation(alloc.buffer(), alloc.memory(), descriptor.size()));
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> handle) {
        if (!bufferRegistry.isValid(handle)) return;
        var alloc = bufferRegistry.remove(handle);
        if (alloc != null) {
            vk.freeMemory(device, alloc.memory());
            vk.destroyBuffer(device, alloc.buffer());
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

        long dataPtr = vk.mapMemory(device, alloc.memory(), offset, length);
        MemorySegment segment = MemorySegment.ofAddress(dataPtr).reinterpret(length);
        long memory = alloc.memory();
        var gpuMemory = new SegmentNativeMemory(segment);

        return new BufferWriter() {
            @Override
            public NativeMemory memory() {
                return gpuMemory;
            }

            @Override
            public void close() {
                vk.unmapMemory(device, memory);
            }
        };
    }

    // --- Texture operations ---

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        int vkFormat = mapTextureFormat(descriptor.format());
        int mipLevels = computeMipLevels(descriptor);
        boolean isDepth = isDepthFormat(descriptor.format());
        int aspectMask = isDepth ? VkBindings.VK_IMAGE_ASPECT_DEPTH_BIT : VkBindings.VK_IMAGE_ASPECT_COLOR_BIT;
        int usage = VkBindings.VK_IMAGE_USAGE_SAMPLED_BIT | VkBindings.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        if (mipLevels > 1) {
            usage |= VkBindings.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        }

        // Map texture type to Vulkan image/view types
        int imageType = descriptor.type() == TextureType.TEXTURE_3D ? VkBindings.VK_IMAGE_TYPE_3D : VkBindings.VK_IMAGE_TYPE_2D;
        int viewType = switch (descriptor.type().name()) {
            case "TEXTURE_3D"       -> VkBindings.VK_IMAGE_VIEW_TYPE_3D;
            case "TEXTURE_2D_ARRAY" -> VkBindings.VK_IMAGE_VIEW_TYPE_2D_ARRAY;
            case "TEXTURE_CUBE"     -> VkBindings.VK_IMAGE_VIEW_TYPE_CUBE;
            default                 -> VkBindings.VK_IMAGE_VIEW_TYPE_2D;
        };
        int extentDepth = descriptor.type() == TextureType.TEXTURE_3D ? descriptor.depth() : 1;
        int arrayLayers = (descriptor.type() == TextureType.TEXTURE_2D_ARRAY || descriptor.type() == TextureType.TEXTURE_CUBE)
                ? descriptor.layers() : 1;
        int createFlags = descriptor.type() == TextureType.TEXTURE_CUBE ? VkBindings.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT : 0;

        var imgAlloc = vk.createImage(device, physicalDevice,
                descriptor.width(), descriptor.height(), extentDepth, arrayLayers,
                mipLevels, vkFormat, usage, imageType, viewType, aspectMask, createFlags);

        // Transition layout: UNDEFINED -> SHADER_READ_ONLY_OPTIMAL
        transitionImageLayout(imgAlloc.image(), VkBindings.VK_IMAGE_LAYOUT_UNDEFINED,
                VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, aspectMask, mipLevels);

        return textureRegistry.register(new VkTextureAllocation(
                imgAlloc.image(), imgAlloc.memory(), imgAlloc.imageView(), descriptor));
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        var alloc = textureRegistry.get(texture);
        if (alloc == null) return;

        long imageSize = pixels.remaining();
        int mipLevels = computeMipLevels(alloc.desc());
        boolean isDepth = isDepthFormat(alloc.desc().format());
        int aspectMask = isDepth ? VkBindings.VK_IMAGE_ASPECT_DEPTH_BIT : VkBindings.VK_IMAGE_ASPECT_COLOR_BIT;

        // Create staging buffer
        var staging = vk.createBuffer(device, physicalDevice, imageSize,
                VkBindings.VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        // Map and copy pixel data
        long dataPtr = vk.mapMemory(device, staging.memory(), 0, imageSize);
        MemorySegment srcSeg = MemorySegment.ofBuffer(pixels);
        MemorySegment dstSeg = MemorySegment.ofAddress(dataPtr).reinterpret(imageSize);
        dstSeg.copyFrom(srcSeg);
        vk.unmapMemory(device, staging.memory());

        // Execute copy via one-shot command buffer
        executeOneShot(cmd -> {
            // Transition: SHADER_READ_ONLY -> TRANSFER_DST
            vk.cmdImageBarrier(cmd, alloc.image(),
                    VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VkBindings.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, VkBindings.VK_ACCESS_TRANSFER_WRITE_BIT,
                    aspectMask, 0, mipLevels);

            // Copy buffer to image (mip level 0)
            vk.cmdCopyBufferToImage(cmd, staging.buffer(), alloc.image(),
                    VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    alloc.desc().width(), alloc.desc().height(), aspectMask, 0);

            // Transition back: TRANSFER_DST -> SHADER_READ_ONLY
            vk.cmdImageBarrier(cmd, alloc.image(),
                    VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    VkBindings.VK_ACCESS_TRANSFER_WRITE_BIT,
                    VkBindings.VK_ACCESS_SHADER_READ_BIT,
                    aspectMask, 0, mipLevels);
        });

        // Mark mips dirty so they are generated lazily when a mipmap sampler is bound
        if (mipLevels > 1) {
            textureMipsDirty.put(texture.index(), true);
        }

        // Cleanup staging resources
        vk.freeMemory(device, staging.memory());
        vk.destroyBuffer(device, staging.buffer());
    }

    @Override
    public void destroyTexture(Handle<TextureResource> handle) {
        if (!textureRegistry.isValid(handle)) return;
        var alloc = textureRegistry.remove(handle);
        if (alloc != null) {
            vk.destroyImageView(device, alloc.imageView());
            vk.destroyImage(device, alloc.image());
            vk.freeMemory(device, alloc.memory());
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
        var colorAttachments = new ArrayList<VkTextureAllocation>();
        var colorTextureHandles = new ArrayList<Handle<TextureResource>>();

        // Create color attachments
        for (var colorFormat : descriptor.colorAttachments()) {
            int vkFormat = mapTextureFormat(colorFormat);
            int usage = VkBindings.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VkBindings.VK_IMAGE_USAGE_SAMPLED_BIT;

            var imgAlloc = vk.createImage(device, physicalDevice,
                    descriptor.width(), descriptor.height(), 1, 1, 1,
                    vkFormat, usage,
                    VkBindings.VK_IMAGE_TYPE_2D, VkBindings.VK_IMAGE_VIEW_TYPE_2D,
                    VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0);

            // Transition to SHADER_READ_ONLY_OPTIMAL
            transitionImageLayout(imgAlloc.image(), VkBindings.VK_IMAGE_LAYOUT_UNDEFINED,
                    VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 1);

            var texDesc = new TextureDescriptor(descriptor.width(), descriptor.height(), colorFormat);
            var texAlloc = new VkTextureAllocation(imgAlloc.image(), imgAlloc.memory(), imgAlloc.imageView(), texDesc);
            colorAttachments.add(texAlloc);

            var texHandle = textureRegistry.register(texAlloc);
            colorTextureHandles.add(texHandle);
        }

        // Create depth attachment if requested
        VkTextureAllocation depthAttachment = null;
        if (descriptor.depthFormat() != null) {
            int vkDepthFormat = mapTextureFormat(descriptor.depthFormat());
            int depthUsage = VkBindings.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;

            var depthImgAlloc = vk.createImage(device, physicalDevice,
                    descriptor.width(), descriptor.height(), 1, 1, 1,
                    vkDepthFormat, depthUsage,
                    VkBindings.VK_IMAGE_TYPE_2D, VkBindings.VK_IMAGE_VIEW_TYPE_2D,
                    VkBindings.VK_IMAGE_ASPECT_DEPTH_BIT, 0);

            var depthDesc = new TextureDescriptor(descriptor.width(), descriptor.height(), descriptor.depthFormat());
            depthAttachment = new VkTextureAllocation(depthImgAlloc.image(), depthImgAlloc.memory(),
                    depthImgAlloc.imageView(), depthDesc);
        }

        // Create render pass
        long rtRenderPass = createOffscreenRenderPass(descriptor, colorAttachments, depthAttachment);

        // Create framebuffer
        int attachmentCount = colorAttachments.size() + (depthAttachment != null ? 1 : 0);
        long[] attachmentViews = new long[attachmentCount];
        for (int i = 0; i < colorAttachments.size(); i++) {
            attachmentViews[i] = colorAttachments.get(i).imageView();
        }
        if (depthAttachment != null) {
            attachmentViews[colorAttachments.size()] = depthAttachment.imageView();
        }

        long framebuffer = vk.createFramebuffer(device, rtRenderPass, attachmentViews,
                descriptor.width(), descriptor.height());

        var handle = renderTargetRegistry.register(new VkRenderTargetAllocation(
                rtRenderPass, framebuffer, descriptor.width(), descriptor.height(),
                List.copyOf(colorAttachments), depthAttachment, List.copyOf(colorTextureHandles)));

        log.info("Created Vulkan render target {}x{} with {} color attachment(s){}",
                descriptor.width(), descriptor.height(), colorAttachments.size(),
                depthAttachment != null ? " + depth" : "");
        return handle;
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
            vk.destroyFramebuffer(device, rtAlloc.framebuffer());
            vk.destroyRenderPass(device, rtAlloc.renderPass());

            for (int i = 0; i < rtAlloc.colorAttachments().size(); i++) {
                var colorAlloc = rtAlloc.colorAttachments().get(i);
                var texHandle = rtAlloc.colorTextureHandles().get(i);
                textureRegistry.remove(texHandle);
                vk.destroyImageView(device, colorAlloc.imageView());
                vk.destroyImage(device, colorAlloc.image());
                vk.freeMemory(device, colorAlloc.memory());
            }

            if (rtAlloc.depthAttachment() != null) {
                var depthAlloc = rtAlloc.depthAttachment();
                vk.destroyImageView(device, depthAlloc.imageView());
                vk.destroyImage(device, depthAlloc.image());
                vk.freeMemory(device, depthAlloc.memory());
            }
        }
    }

    private long createOffscreenRenderPass(RenderTargetDescriptor descriptor,
                                           List<VkTextureAllocation> colorAttachments,
                                           VkTextureAllocation depthAttachment) {
        var colorDescs = new VkBindings.AttachmentDesc[colorAttachments.size()];
        for (int i = 0; i < colorAttachments.size(); i++) {
            int vkFormat = mapTextureFormat(descriptor.colorAttachments().get(i));
            colorDescs[i] = new VkBindings.AttachmentDesc(
                    vkFormat, true, true, VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }

        VkBindings.AttachmentDesc depthDesc = null;
        if (depthAttachment != null) {
            int vkDepthFmt = mapTextureFormat(descriptor.depthFormat());
            depthDesc = new VkBindings.AttachmentDesc(
                    vkDepthFmt, true, false, VkBindings.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        }

        var deps = new VkBindings.SubpassDependencyDesc[2];
        deps[0] = new VkBindings.SubpassDependencyDesc(
                VkBindings.VK_SUBPASS_EXTERNAL, 0,
                VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | (depthAttachment != null ? VkBindings.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT : 0),
                VkBindings.VK_ACCESS_SHADER_READ_BIT,
                VkBindings.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | (depthAttachment != null ? VkBindings.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT : 0),
                VkBindings.VK_DEPENDENCY_BY_REGION_BIT);
        deps[1] = new VkBindings.SubpassDependencyDesc(
                0, VkBindings.VK_SUBPASS_EXTERNAL,
                VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT,
                VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VkBindings.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                VkBindings.VK_ACCESS_SHADER_READ_BIT,
                VkBindings.VK_DEPENDENCY_BY_REGION_BIT);

        return vk.createRenderPass(device, colorDescs, depthDesc, deps);
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
        int magFilter = mapFilter(descriptor.magFilter());
        int minFilter = mapFilter(descriptor.minFilter());
        int mipmapMode = mapMipmapMode(descriptor.minFilter());
        boolean anisotropyEnable = descriptor.maxAnisotropy() > 1f;
        boolean compareEnable = descriptor.compareFunc() != null;
        int compareOp = compareEnable ? mapCompareOp(descriptor.compareFunc()) : VkBindings.VK_COMPARE_OP_NEVER;
        int borderColor = mapBorderColor(descriptor.borderColor());

        long sampler = vk.createSampler(device, magFilter, minFilter, mipmapMode,
                mapWrapMode(descriptor.wrapS()), mapWrapMode(descriptor.wrapT()),
                mapWrapMode(descriptor.wrapR()),
                descriptor.minLod(), descriptor.maxLod(), descriptor.lodBias(),
                anisotropyEnable, descriptor.maxAnisotropy(),
                compareEnable, compareOp, borderColor);

        return samplerRegistry.register(new VkSamplerAllocation(sampler, descriptor));
    }

    @Override
    public void destroySampler(Handle<SamplerResource> handle) {
        if (!samplerRegistry.isValid(handle)) return;
        var alloc = samplerRegistry.remove(handle);
        if (alloc != null) {
            vk.destroySampler(device, alloc.sampler());
        }
    }

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        if (descriptor.hasSpirv()) {
            log.debug("createPipeline: using pipelineLayout=0x{}", Long.toHexString(descriptorManager.pipelineLayout()));
            long pipeline = VkPipelineFactory.create(vk, device, renderPass,
                    descriptorManager.pipelineLayout(), descriptor.binaries(), descriptor.vertexFormat());
            var handle = pipelineRegistry.register(pipeline);
            pipelineSpecs.put(handle.index(), new PipelineSpec(descriptor.binaries(), descriptor.vertexFormat()));
            return handle;
        }
        log.warn("Vulkan backend received GLSL source pipeline descriptor — ignoring");
        return pipelineRegistry.register(VkBindings.VK_NULL_HANDLE);
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> handle) {
        if (!pipelineRegistry.isValid(handle)) return;
        var pipeline = pipelineRegistry.remove(handle);
        if (pipeline != null && pipeline != VkBindings.VK_NULL_HANDLE) {
            vk.destroyPipeline(device, pipeline);
        }
        // Clean up any pipeline variants for this pipeline
        var prefix = handle.index() + "_";
        pipelineVariants.removeIf(k -> k.startsWith(prefix), p -> vk.destroyPipeline(device, p));
        pipelineSpecs.remove(handle.index());
    }

    @Override
    public Handle<PipelineResource> createComputePipeline(
            dev.engine.graphics.pipeline.ComputePipelineDescriptor descriptor) {
        if (!descriptor.hasSpirv()) {
            throw new UnsupportedOperationException("Vulkan compute requires SPIRV binary");
        }
        long shaderModule = vk.createShaderModule(device, descriptor.binary().spirv());
        long pipeline = vk.createComputePipeline(device, descriptorManager.pipelineLayout(), shaderModule);
        vk.destroyShaderModule(device, shaderModule);
        return pipelineRegistry.register(pipeline);
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
        java.util.Arrays.fill(pendingUboBuffers, VkBindings.VK_NULL_HANDLE);
        java.util.Arrays.fill(pendingUboSizes, 0);
        java.util.Arrays.fill(pendingSsboBuffers, VkBindings.VK_NULL_HANDLE);
        java.util.Arrays.fill(pendingSsboSizes, 0);
        java.util.Arrays.fill(pendingTextureViews, VkBindings.VK_NULL_HANDLE);
        java.util.Arrays.fill(pendingTextureSamplers, VkBindings.VK_NULL_HANDLE);
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
        vk.cmdBeginRenderPass(frame.commandBuffer, renderPass,
                framebuffers.framebuffer(currentImageIndex),
                0, 0, swapchain.width(), swapchain.height(),
                clearColor,
                1.0f, 0);
    }

    @Override
    public void endFrame() {
        var frame = frames[currentFrame];

        // End render pass + submit
        vk.cmdEndRenderPass(frame.commandBuffer);
        frame.submitTo(graphicsQueue);

        // Present
        int presentResult = swapchain.present(graphicsQueue, frame.renderFinishedSemaphore, currentImageIndex);
        if (presentResult == VkBindings.VK_ERROR_OUT_OF_DATE_KHR || presentResult == VkBindings.VK_SUBOPTIMAL_KHR) {
            recreateSwapchain();
        }

        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;

        // Periodically evict unused pipeline variants
        pipelineVariants.evict(frameCounter.get(), p -> vk.destroyPipeline(device, p));
    }

    /**
     * Reads back pixels from the current swapchain image after rendering.
     * Must be called between endFrame() submissions — waits for GPU idle.
     * Returns RGBA8 pixel data as int[] {R, G, B, A} for 0-255 values.
     */
    public int[] readPixel(int x, int y) {
        vk.deviceWaitIdle(device);

        // Create staging buffer
        long pixelSize = 4; // RGBA8
        var staging = vk.createBuffer(device, physicalDevice, pixelSize,
                VkBindings.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        long image = swapchain.image(currentImageIndex >= 0 ? currentImageIndex : 0);

        long cmd = vk.allocateCommandBuffer(device, commandPool);
        vk.beginCommandBuffer(cmd, VkBindings.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        // Transition image: PRESENT_SRC -> TRANSFER_SRC
        vk.cmdImageBarrier(cmd, image,
                VkBindings.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkBindings.VK_ACCESS_MEMORY_READ_BIT,
                VkBindings.VK_ACCESS_TRANSFER_READ_BIT,
                VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1);

        // Copy pixel
        vk.cmdCopyImageToBuffer(cmd, image, VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                staging.buffer(), x, y, 1, 1, VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0);

        // Transition back: TRANSFER_SRC -> PRESENT_SRC
        vk.cmdImageBarrier(cmd, image,
                VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkBindings.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkBindings.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                VkBindings.VK_ACCESS_TRANSFER_READ_BIT,
                VkBindings.VK_ACCESS_MEMORY_READ_BIT,
                VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1);

        vk.endCommandBuffer(cmd);

        vk.queueSubmitSimple(graphicsQueue, cmd, VkBindings.VK_NULL_HANDLE);
        vk.queueWaitIdle(graphicsQueue);

        // Read back pixel
        long dataPtr = vk.mapMemory(device, staging.memory(), 0, pixelSize);
        var seg = MemorySegment.ofAddress(dataPtr).reinterpret(pixelSize);

        // Swapchain format is B8G8R8A8 — BGRA order
        int b = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 0));
        int g = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 1));
        int r = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 2));
        int a = Byte.toUnsignedInt(seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, 3));

        vk.unmapMemory(device, staging.memory());
        vk.freeCommandBuffer(device, commandPool, cmd);
        vk.freeMemory(device, staging.memory());
        vk.destroyBuffer(device, staging.buffer());

        return new int[]{r, g, b, a};
    }

    /**
     * Reads back the entire framebuffer as RGBA8 byte array.
     * Must be called after endFrame(). Waits for GPU idle.
     */
    @Override
    public byte[] readFramebuffer(int width, int height) {
        vk.deviceWaitIdle(device);
        int w = swapchain.width();
        int h = swapchain.height();

        long pixelSize = (long) w * h * 4;
        var staging = vk.createBuffer(device, physicalDevice, pixelSize,
                VkBindings.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

        long image = swapchain.image(currentImageIndex >= 0 ? currentImageIndex : 0);

        long cmd = vk.allocateCommandBuffer(device, commandPool);
        vk.beginCommandBuffer(cmd, VkBindings.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

        vk.cmdImageBarrier(cmd, image,
                VkBindings.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkBindings.VK_ACCESS_MEMORY_READ_BIT,
                VkBindings.VK_ACCESS_TRANSFER_READ_BIT,
                VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1);

        vk.cmdCopyImageToBuffer(cmd, image, VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                staging.buffer(), 0, 0, w, h, VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0);

        vk.cmdImageBarrier(cmd, image,
                VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkBindings.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                VkBindings.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
                VkBindings.VK_ACCESS_TRANSFER_READ_BIT,
                VkBindings.VK_ACCESS_MEMORY_READ_BIT,
                VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, 0, 1);

        vk.endCommandBuffer(cmd);

        vk.queueSubmitSimple(graphicsQueue, cmd, VkBindings.VK_NULL_HANDLE);
        vk.queueWaitIdle(graphicsQueue);

        long dataPtr = vk.mapMemory(device, staging.memory(), 0, pixelSize);
        var seg = MemorySegment.ofAddress(dataPtr).reinterpret(pixelSize);

        // Convert BGRA (swapchain format) to RGBA, no Y-flip needed
        byte[] rgba = new byte[(int) pixelSize];
        for (int i = 0; i < w * h; i++) {
            int off = i * 4;
            rgba[off]     = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off + 2); // R from B position
            rgba[off + 1] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off + 1); // G
            rgba[off + 2] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off);     // B from R position
            rgba[off + 3] = seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, off + 3); // A
        }

        vk.unmapMemory(device, staging.memory());
        vk.freeCommandBuffer(device, commandPool, cmd);
        vk.freeMemory(device, staging.memory());
        vk.destroyBuffer(device, staging.buffer());

        return rgba;
    }

    /** Swapchain width. */
    public int swapchainWidth() { return swapchain.width(); }
    /** Swapchain height. */
    public int swapchainHeight() { return swapchain.height(); }

    private void flushDescriptorSet(long cmd) {
        if (!descriptorDirty) {
            return;
        }
        descriptorDirty = false;

        long set = descriptorManager.allocateSet(currentFrame);
        currentDescriptorSet = set;

        // Count buffer + image writes
        int bufCount = 0;
        for (int i = 0; i < pendingUboBuffers.length; i++) {
            if (pendingUboBuffers[i] != VkBindings.VK_NULL_HANDLE) bufCount++;
        }
        for (int i = 0; i < pendingSsboBuffers.length; i++) {
            if (pendingSsboBuffers[i] != VkBindings.VK_NULL_HANDLE) bufCount++;
        }

        int imgCount = 0;
        for (int i = 0; i < pendingTextureViews.length; i++) {
            if (pendingTextureViews[i] != VkBindings.VK_NULL_HANDLE
                    && pendingTextureSamplers[i] != VkBindings.VK_NULL_HANDLE) {
                imgCount++;
            }
        }

        if (bufCount + imgCount > 0) {
            int[] bufferBindings = new int[bufCount];
            int[] bufferTypes = new int[bufCount];
            long[] buffers = new long[bufCount];
            long[] bufferOffsets = new long[bufCount];
            long[] bufferRanges = new long[bufCount];
            int idx = 0;

            for (int i = 0; i < pendingUboBuffers.length; i++) {
                if (pendingUboBuffers[i] != VkBindings.VK_NULL_HANDLE) {
                    bufferBindings[idx] = i;
                    bufferTypes[idx] = VkBindings.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
                    buffers[idx] = pendingUboBuffers[i];
                    bufferOffsets[idx] = 0;
                    bufferRanges[idx] = pendingUboSizes[i];
                    idx++;
                }
            }

            int ssboOffset = descriptorManager.ssboBindingOffset();
            for (int i = 0; i < pendingSsboBuffers.length; i++) {
                if (pendingSsboBuffers[i] != VkBindings.VK_NULL_HANDLE) {
                    bufferBindings[idx] = ssboOffset + i;
                    bufferTypes[idx] = VkBindings.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
                    buffers[idx] = pendingSsboBuffers[i];
                    bufferOffsets[idx] = 0;
                    bufferRanges[idx] = pendingSsboSizes[i];
                    idx++;
                }
            }

            int[] imageBindings = imgCount > 0 ? new int[imgCount] : null;
            long[] imageViews = imgCount > 0 ? new long[imgCount] : null;
            long[] imageSamplers = imgCount > 0 ? new long[imgCount] : null;
            int[] imageLayouts = imgCount > 0 ? new int[imgCount] : null;
            int imgIdx = 0;
            int texOffset = descriptorManager.textureBindingOffset();
            for (int i = 0; i < pendingTextureViews.length; i++) {
                if (pendingTextureViews[i] != VkBindings.VK_NULL_HANDLE
                        && pendingTextureSamplers[i] != VkBindings.VK_NULL_HANDLE) {
                    imageBindings[imgIdx] = texOffset + i;
                    imageViews[imgIdx] = pendingTextureViews[i];
                    imageSamplers[imgIdx] = pendingTextureSamplers[i];
                    imageLayouts[imgIdx] = VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
                    imgIdx++;
                }
            }

            vk.updateDescriptorSets(device, set,
                    bufferBindings, bufferTypes, buffers, bufferOffsets, bufferRanges,
                    imageBindings, imageViews, imageSamplers, imageLayouts);
        }

        vk.cmdBindDescriptorSets(cmd, VkBindings.VK_PIPELINE_BIND_POINT_GRAPHICS,
                descriptorManager.pipelineLayout(), 0, set);
    }

    private void recreateSwapchain() {
        vk.deviceWaitIdle(device);
        framebuffers.close();
        swapchain.create(swapchain.width(), swapchain.height());
        framebuffers.create(swapchain, renderPass, depthFormat);
    }

    @Override
    public void submit(dev.engine.graphics.command.CommandList commands) {
        if (currentImageIndex < 0) return; // no valid frame
        long cmd = frames[currentFrame].commandBuffer;

        for (var command : commands.commands()) {
            switch (command) {
                case dev.engine.graphics.command.RenderCommand.BindPipeline bp -> {
                    var pipeline = pipelineRegistry.get(bp.pipeline());
                    if (pipeline != null) {
                        vk.cmdBindPipeline(cmd, VkBindings.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
                        currentBoundPipeline = bp.pipeline();
                        currentWireframe = false;
                        currentBlendMode = BlendMode.NONE;
                        currentBlendModes = null;
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindVertexBuffer bvb -> {
                    var alloc = bufferRegistry.get(bvb.buffer());
                    if (alloc != null) {
                        vk.cmdBindVertexBuffers(cmd, alloc.buffer());
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindIndexBuffer bib -> {
                    var alloc = bufferRegistry.get(bib.buffer());
                    if (alloc != null) {
                        vk.cmdBindIndexBuffer(cmd, alloc.buffer(), VkBindings.VK_INDEX_TYPE_UINT32);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Draw draw -> {
                    flushDescriptorSet(cmd);
                    log.debug("vkCmdDraw(vertexCount={}, firstVertex={})", draw.vertexCount(), draw.firstVertex());
                    vk.cmdDraw(cmd, draw.vertexCount(), 1, draw.firstVertex(), 0);
                }
                case dev.engine.graphics.command.RenderCommand.DrawIndexed di -> {
                    flushDescriptorSet(cmd);
                    log.debug("vkCmdDrawIndexed(indexCount={}, firstIndex={})", di.indexCount(), di.firstIndex());
                    vk.cmdDrawIndexed(cmd, di.indexCount(), 1, di.firstIndex(), 0, 0);
                }
                case dev.engine.graphics.command.RenderCommand.DrawInstanced di -> {
                    flushDescriptorSet(cmd);
                    vk.cmdDraw(cmd, di.vertexCount(), di.instanceCount(), di.firstVertex(), di.firstInstance());
                }
                case dev.engine.graphics.command.RenderCommand.DrawIndexedInstanced di -> {
                    flushDescriptorSet(cmd);
                    vk.cmdDrawIndexed(cmd, di.indexCount(), di.instanceCount(), di.firstIndex(), 0, di.firstInstance());
                }
                case dev.engine.graphics.command.RenderCommand.DrawIndirect(var buffer, long offset, int drawCount, int stride) -> {
                    flushDescriptorSet(cmd);
                    var alloc = bufferRegistry.get(buffer);
                    if (alloc != null) {
                        vk.cmdDrawIndirect(cmd, alloc.buffer(), offset, drawCount, stride == 0 ? 16 : stride);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.DrawIndexedIndirect(var buffer, long offset, int drawCount, int stride) -> {
                    flushDescriptorSet(cmd);
                    var alloc = bufferRegistry.get(buffer);
                    if (alloc != null) {
                        vk.cmdDrawIndexedIndirect(cmd, alloc.buffer(), offset, drawCount, stride == 0 ? 20 : stride);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Viewport vp -> {
                    // Negative height flips Y axis to match OpenGL convention
                    vk.cmdSetViewport(cmd, vp.x(), vp.height(), vp.width(), -vp.height(), 0f, 1f);
                    vk.cmdSetScissor(cmd, vp.x(), vp.y(), vp.width(), vp.height());
                }
                case dev.engine.graphics.command.RenderCommand.Scissor sc -> {
                    vk.cmdSetScissor(cmd, sc.x(), sc.y(), sc.width(), sc.height());
                }
                case dev.engine.graphics.command.RenderCommand.Clear c -> {
                    // Store clear color for next render pass begin
                    clearColor = new float[]{c.r(), c.g(), c.b(), c.a()};
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
                case dev.engine.graphics.command.RenderCommand.BindImage bi -> {
                    log.debug("BindImage not yet implemented for Vulkan (needs storage image descriptors)");
                }
                case dev.engine.graphics.command.RenderCommand.SetDepthTest sdt -> {
                    vk.cmdSetDepthTestEnable(cmd, sdt.enabled());
                    vk.cmdSetDepthWriteEnable(cmd, sdt.enabled());
                }
                case dev.engine.graphics.command.RenderCommand.SetBlending sb -> {
                    if (currentBoundPipeline != null) {
                        currentBlendMode = sb.enabled() ? BlendMode.ALPHA : BlendMode.NONE;
                        currentBlendModes = null;
                        rebindPipelineVariant(cmd, buildBlendConfigs(), currentWireframe);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.SetCullFace scf -> {
                    vk.cmdSetCullMode(cmd, scf.enabled() ? VkBindings.VK_CULL_MODE_BACK_BIT : VkBindings.VK_CULL_MODE_NONE);
                }
                case dev.engine.graphics.command.RenderCommand.SetWireframe sw -> {
                    currentWireframe = sw.enabled();
                    if (currentBoundPipeline != null) {
                        rebindPipelineVariant(cmd, buildBlendConfigs(), currentWireframe);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindRenderTarget brt -> {
                    var rtAlloc = renderTargetRegistry.get(brt.renderTarget());
                    if (rtAlloc != null) {
                        vk.cmdEndRenderPass(cmd);

                        int colorCount = rtAlloc.colorAttachments().size();
                        float[] clearColors = new float[colorCount * 4];
                        for (int i = 0; i < colorCount; i++) {
                            clearColors[i * 4 + 3] = 1f; // alpha = 1
                        }
                        float clearDepth = rtAlloc.depthAttachment() != null ? 1.0f : -1.0f;

                        vk.cmdBeginRenderPass(cmd, rtAlloc.renderPass(), rtAlloc.framebuffer(),
                                0, 0, rtAlloc.width(), rtAlloc.height(),
                                clearColors, clearDepth, 0);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.BindDefaultRenderTarget bdrt -> {
                    vk.cmdEndRenderPass(cmd);
                    vk.cmdBeginRenderPass(cmd, renderPass,
                            framebuffers.framebuffer(currentImageIndex),
                            0, 0, swapchain.width(), swapchain.height(),
                            clearColor,
                            1.0f, 0);
                }
                case dev.engine.graphics.command.RenderCommand.SetRenderState state -> {
                    var props = state.properties();
                    if (props.contains(RenderState.DEPTH_TEST)) {
                        vk.cmdSetDepthTestEnable(cmd, props.get(RenderState.DEPTH_TEST));
                    }
                    if (props.contains(RenderState.DEPTH_WRITE)) {
                        vk.cmdSetDepthWriteEnable(cmd, props.get(RenderState.DEPTH_WRITE));
                    }
                    if (props.contains(RenderState.DEPTH_FUNC)) {
                        vk.cmdSetDepthCompareOp(cmd, mapCompareFunc(props.get(RenderState.DEPTH_FUNC)));
                    }
                    if (props.contains(RenderState.CULL_MODE)) {
                        CullMode mode = props.get(RenderState.CULL_MODE);
                        int vkMode = switch (mode.name()) {
                            case "BACK"  -> VkBindings.VK_CULL_MODE_BACK_BIT;
                            case "FRONT" -> VkBindings.VK_CULL_MODE_FRONT_BIT;
                            default      -> VkBindings.VK_CULL_MODE_NONE;
                        };
                        vk.cmdSetCullMode(cmd, vkMode);
                    }
                    if (props.contains(RenderState.FRONT_FACE)) {
                        FrontFace ff = props.get(RenderState.FRONT_FACE);
                        vk.cmdSetFrontFace(cmd,
                                "CCW".equals(ff.name()) ? VkBindings.VK_FRONT_FACE_COUNTER_CLOCKWISE : VkBindings.VK_FRONT_FACE_CLOCKWISE);
                    }
                    if (props.contains(RenderState.STENCIL_TEST)) {
                        vk.cmdSetStencilTestEnable(cmd, props.get(RenderState.STENCIL_TEST));
                    }
                    if (props.contains(RenderState.STENCIL_FUNC)) {
                        int ref = props.contains(RenderState.STENCIL_REF) ? props.get(RenderState.STENCIL_REF) : 0;
                        int mask = props.contains(RenderState.STENCIL_MASK) ? props.get(RenderState.STENCIL_MASK) : 0xFF;
                        vk.cmdSetStencilCompareMask(cmd, VkBindings.VK_STENCIL_FACE_FRONT_AND_BACK, mask);
                        vk.cmdSetStencilWriteMask(cmd, VkBindings.VK_STENCIL_FACE_FRONT_AND_BACK, mask);
                        vk.cmdSetStencilReference(cmd, VkBindings.VK_STENCIL_FACE_FRONT_AND_BACK, ref);
                    }
                    if (props.contains(RenderState.STENCIL_FAIL)) {
                        StencilOp fail = props.get(RenderState.STENCIL_FAIL);
                        StencilOp depthFail = props.contains(RenderState.STENCIL_DEPTH_FAIL) ? props.get(RenderState.STENCIL_DEPTH_FAIL) : StencilOp.KEEP;
                        StencilOp pass = props.contains(RenderState.STENCIL_PASS) ? props.get(RenderState.STENCIL_PASS) : StencilOp.KEEP;
                        vk.cmdSetStencilOp(cmd, VkBindings.VK_STENCIL_FACE_FRONT_AND_BACK,
                                mapStencilOp(fail), mapStencilOp(pass), mapStencilOp(depthFail),
                                mapCompareFunc(props.contains(RenderState.STENCIL_FUNC) ? props.get(RenderState.STENCIL_FUNC) : CompareFunc.ALWAYS));
                    }
                    if (props.contains(RenderState.BLEND_MODE) && currentBoundPipeline != null) {
                        currentBlendMode = props.get(RenderState.BLEND_MODE);
                        currentBlendModes = null; // clear per-attachment overrides
                        rebindPipelineVariant(cmd, buildBlendConfigs(), currentWireframe);
                    }
                    if (props.contains(RenderState.BLEND_MODES) && currentBoundPipeline != null) {
                        currentBlendModes = props.get(RenderState.BLEND_MODES);
                        rebindPipelineVariant(cmd, buildBlendConfigs(), currentWireframe);
                    }
                    if (props.contains(RenderState.WIREFRAME) && currentBoundPipeline != null) {
                        currentWireframe = props.get(RenderState.WIREFRAME);
                        rebindPipelineVariant(cmd, buildBlendConfigs(), currentWireframe);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.PushConstants(var data) -> {
                    data.rewind();
                    vk.cmdPushConstants(cmd, descriptorManager.pipelineLayout(),
                            VkBindings.VK_SHADER_STAGE_VERTEX_BIT | VkBindings.VK_SHADER_STAGE_FRAGMENT_BIT,
                            0, data);
                }
                case dev.engine.graphics.command.RenderCommand.BindComputePipeline(var pipeline) -> {
                    var vkPipeline = pipelineRegistry.get(pipeline);
                    if (vkPipeline != null) {
                        vk.cmdBindPipeline(cmd, VkBindings.VK_PIPELINE_BIND_POINT_COMPUTE, vkPipeline);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.Dispatch(int gx, int gy, int gz) -> {
                    if (descriptorDirty) {
                        flushDescriptorSet(cmd);
                    }
                    if (currentDescriptorSet != VkBindings.VK_NULL_HANDLE) {
                        vk.cmdBindDescriptorSets(cmd, VkBindings.VK_PIPELINE_BIND_POINT_COMPUTE,
                                descriptorManager.pipelineLayout(), 0, currentDescriptorSet);
                    }
                    vk.cmdDispatch(cmd, gx, gy, gz);
                }
                case dev.engine.graphics.command.RenderCommand.CopyBuffer(var src, var dst, long srcOff, long dstOff, long size) -> {
                    var srcAlloc = bufferRegistry.get(src);
                    var dstAlloc = bufferRegistry.get(dst);
                    if (srcAlloc != null && dstAlloc != null) {
                        vk.cmdCopyBuffer(cmd, srcAlloc.buffer(), dstAlloc.buffer(), srcOff, dstOff, size);
                    }
                }
                case dev.engine.graphics.command.RenderCommand.CopyTexture(var src, var dst, int sx, int sy, int dx, int dy, int w, int h, int srcMip, int dstMip) -> {
                    log.debug("CopyTexture: requires render pass pause — not yet implemented inside render pass");
                }
                case dev.engine.graphics.command.RenderCommand.BlitTexture(var src, var dst,
                        int sx0, int sy0, int sx1, int sy1,
                        int dx0, int dy0, int dx1, int dy1, boolean linear) -> {
                    log.debug("BlitTexture: requires render pass pause — not yet implemented inside render pass");
                }
                case dev.engine.graphics.command.RenderCommand.MemoryBarrier(var scope) -> {
                    int srcStage, dstStage, srcAccess, dstAccess;
                    if (scope == dev.engine.graphics.renderstate.BarrierScope.STORAGE_BUFFER) {
                        srcStage = VkBindings.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                        dstStage = VkBindings.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT | VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                        srcAccess = VkBindings.VK_ACCESS_SHADER_WRITE_BIT;
                        dstAccess = VkBindings.VK_ACCESS_SHADER_READ_BIT;
                    } else if (scope == dev.engine.graphics.renderstate.BarrierScope.TEXTURE) {
                        srcStage = VkBindings.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
                        dstStage = VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                        srcAccess = VkBindings.VK_ACCESS_SHADER_WRITE_BIT;
                        dstAccess = VkBindings.VK_ACCESS_SHADER_READ_BIT;
                    } else {
                        srcStage = VkBindings.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                        dstStage = VkBindings.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                        srcAccess = VkBindings.VK_ACCESS_MEMORY_WRITE_BIT;
                        dstAccess = VkBindings.VK_ACCESS_MEMORY_READ_BIT;
                    }
                    vk.cmdPipelineBarrier(cmd, srcStage, dstStage, srcAccess, dstAccess);
                }
            }
        }
    }

    /**
     * Builds the per-attachment {@link VkPipelineFactory.BlendConfig} array from the current blend state.
     * If {@code currentBlendModes} is set, each entry maps to one attachment; otherwise
     * {@code currentBlendMode} is used for all attachments (single-element array).
     */
    private VkPipelineFactory.BlendConfig[] buildBlendConfigs() {
        if (currentBlendModes != null && currentBlendModes.length > 0) {
            VkPipelineFactory.BlendConfig[] configs = new VkPipelineFactory.BlendConfig[currentBlendModes.length];
            for (int i = 0; i < currentBlendModes.length; i++) {
                configs[i] = VkPipelineFactory.BlendConfig.fromBlendMode(currentBlendModes[i]);
            }
            return configs;
        }
        return new VkPipelineFactory.BlendConfig[]{VkPipelineFactory.BlendConfig.fromBlendMode(currentBlendMode)};
    }

    /**
     * Rebinds the current pipeline with the given per-attachment blend configs and wireframe state,
     * creating a new pipeline variant if needed.
     */
    private void rebindPipelineVariant(long cmd, VkPipelineFactory.BlendConfig[] blendConfigs, boolean wireframe) {
        // Build a stable variant key from the blend config names + wireframe
        var keyBuilder = new StringBuilder();
        keyBuilder.append(currentBoundPipeline.index()).append('_');
        if (currentBlendModes != null) {
            for (var m : currentBlendModes) keyBuilder.append(m.name()).append(',');
        } else {
            keyBuilder.append(currentBlendMode.name());
        }
        keyBuilder.append('_').append(wireframe);
        var variantKey = keyBuilder.toString();
        long variantPipeline = pipelineVariants.getOrCreate(variantKey, frameCounter.get(), k -> {
            var spec = pipelineSpecs.get(currentBoundPipeline.index());
            if (spec == null) return pipelineRegistry.get(currentBoundPipeline);
            return VkPipelineFactory.create(vk, device, renderPass,
                    descriptorManager.pipelineLayout(), spec.binaries(), spec.vertexFormat(), blendConfigs, wireframe);
        });
        vk.cmdBindPipeline(cmd, VkBindings.VK_PIPELINE_BIND_POINT_GRAPHICS, variantPipeline);
    }

    // --- Capabilities ---

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(DeviceCapability<T> capability) {
        return switch (capability.name()) {
            case "MAX_TEXTURE_SIZE" -> (T) Integer.valueOf(vk.getMaxImageDimension2D(instance, physicalDevice));
            case "MAX_FRAMEBUFFER_WIDTH" -> (T) Integer.valueOf(vk.getMaxFramebufferWidth(instance, physicalDevice));
            case "MAX_FRAMEBUFFER_HEIGHT" -> (T) Integer.valueOf(vk.getMaxFramebufferHeight(instance, physicalDevice));
            case "BACKEND_NAME" -> (T) "Vulkan";
            case "DEVICE_NAME" -> (T) vk.getDeviceName(instance, physicalDevice);
            case "TEXTURE_BINDING_OFFSET" -> (T) Integer.valueOf(VkDescriptorManager.TEXTURE_BINDING_OFFSET);
            case "SSBO_BINDING_OFFSET" -> (T) Integer.valueOf(VkDescriptorManager.SSBO_BINDING_OFFSET);
            default -> null;
        };
    }

    // --- Cleanup ---

    @Override
    public void close() {
        vk.deviceWaitIdle(device);

        // Destroy pipeline variants (blend + wireframe)
        pipelineVariants.clear(p -> vk.destroyPipeline(device, p));
        pipelineSpecs.clear();

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
            vk.destroyFramebuffer(device, rtAlloc.framebuffer());
            vk.destroyRenderPass(device, rtAlloc.renderPass());
            for (var texHandle : rtAlloc.colorTextureHandles()) {
                textureRegistry.remove(texHandle);
            }
            for (var colorAlloc : rtAlloc.colorAttachments()) {
                vk.destroyImageView(device, colorAlloc.imageView());
                vk.destroyImage(device, colorAlloc.image());
                vk.freeMemory(device, colorAlloc.memory());
            }
            if (rtAlloc.depthAttachment() != null) {
                vk.destroyImageView(device, rtAlloc.depthAttachment().imageView());
                vk.destroyImage(device, rtAlloc.depthAttachment().image());
                vk.freeMemory(device, rtAlloc.depthAttachment().memory());
            }
        });

        // Destroy remaining textures
        textureRegistry.destroyAll(alloc -> {
            vk.destroyImageView(device, alloc.imageView());
            vk.destroyImage(device, alloc.image());
            vk.freeMemory(device, alloc.memory());
        });

        // Destroy remaining samplers
        samplerRegistry.destroyAll(alloc -> vk.destroySampler(device, alloc.sampler()));

        // Destroy remaining buffers
        bufferRegistry.destroyAll(alloc -> {
            vk.freeMemory(device, alloc.memory());
            vk.destroyBuffer(device, alloc.buffer());
        });

        for (var frame : frames) frame.close();
        descriptorManager.close();
        framebuffers.close();
        vk.destroyRenderPass(device, renderPass);
        swapchain.close();
        vk.destroyCommandPool(device, commandPool);
        vk.destroyDevice(device);
        vk.destroySurface(instance, surface);
        vk.destroyInstance(instance);

        log.info("Vulkan render device destroyed");
    }

    // --- Helpers ---

    private int mapBufferUsage(BufferUsage usage) {
        return switch (usage.name()) {
            case "VERTEX" -> VkBindings.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
            case "INDEX" -> VkBindings.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
            case "UNIFORM" -> VkBindings.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
            case "STORAGE" -> VkBindings.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
            default -> VkBindings.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        };
    }

    private int mapAccessPattern(AccessPattern pattern) {
        return switch (pattern.name()) {
            case "STATIC" -> VkBindings.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
            case "DYNAMIC", "STREAM" -> VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            default -> VkBindings.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkBindings.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        };
    }

    private void executeOneShot(java.util.function.LongConsumer recorder) {
        long cmd = vk.allocateCommandBuffer(device, commandPool);
        vk.beginCommandBuffer(cmd, VkBindings.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
        recorder.accept(cmd);
        vk.endCommandBuffer(cmd);

        vk.queueSubmitSimple(graphicsQueue, cmd, VkBindings.VK_NULL_HANDLE);
        vk.queueWaitIdle(graphicsQueue);
        vk.freeCommandBuffer(device, commandPool, cmd);
    }

    private void transitionImageLayout(long image, int oldLayout, int newLayout, int aspectMask, int mipLevels) {
        executeOneShot(cmd -> {
            int srcAccess, dstAccess, srcStage, dstStage;
            if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VkBindings.VK_ACCESS_SHADER_READ_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VkBindings.VK_ACCESS_TRANSFER_WRITE_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                srcAccess = VkBindings.VK_ACCESS_TRANSFER_WRITE_BIT;
                dstAccess = VkBindings.VK_ACCESS_SHADER_READ_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VkBindings.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VkBindings.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL && newLayout == VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                srcAccess = VkBindings.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                dstAccess = VkBindings.VK_ACCESS_SHADER_READ_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VkBindings.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                srcAccess = 0;
                dstAccess = VkBindings.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL && newLayout == VkBindings.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
                srcAccess = VkBindings.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
                dstAccess = VkBindings.VK_ACCESS_MEMORY_READ_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
            } else if (oldLayout == VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL && newLayout == VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
                srcAccess = VkBindings.VK_ACCESS_SHADER_READ_BIT;
                dstAccess = VkBindings.VK_ACCESS_TRANSFER_READ_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT;
            } else {
                // Fallback: overly conservative barrier for unhandled transitions
                srcAccess = VkBindings.VK_ACCESS_MEMORY_WRITE_BIT;
                dstAccess = VkBindings.VK_ACCESS_MEMORY_READ_BIT | VkBindings.VK_ACCESS_MEMORY_WRITE_BIT;
                srcStage = VkBindings.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                dstStage = VkBindings.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
            }
            vk.cmdImageBarrier(cmd, image, oldLayout, newLayout, srcStage, dstStage,
                    srcAccess, dstAccess, aspectMask, 0, mipLevels);
        });
    }

    private int mapTextureFormat(dev.engine.graphics.texture.TextureFormat format) {
        return switch (format.name()) {
            case "RGBA8" -> VkBindings.VK_FORMAT_R8G8B8A8_UNORM;
            case "RGB8" -> VkBindings.VK_FORMAT_R8G8B8_UNORM;
            case "R8" -> VkBindings.VK_FORMAT_R8_UNORM;
            case "DEPTH24" -> VkBindings.VK_FORMAT_D24_UNORM_S8_UINT;
            case "DEPTH32F" -> VkBindings.VK_FORMAT_D32_SFLOAT;
            case "RGBA16F" -> VkBindings.VK_FORMAT_R16G16B16A16_SFLOAT;
            case "RGBA32F" -> VkBindings.VK_FORMAT_R32G32B32A32_SFLOAT;
            case "RG16F" -> VkBindings.VK_FORMAT_R16G16_SFLOAT;
            case "RG32F" -> VkBindings.VK_FORMAT_R32G32_SFLOAT;
            case "R16F" -> VkBindings.VK_FORMAT_R16_SFLOAT;
            case "R32F" -> VkBindings.VK_FORMAT_R32_SFLOAT;
            case "R32UI" -> VkBindings.VK_FORMAT_R32_UINT;
            case "R32I" -> VkBindings.VK_FORMAT_R32_SINT;
            case "DEPTH24_STENCIL8" -> VkBindings.VK_FORMAT_D24_UNORM_S8_UINT;
            case "DEPTH32F_STENCIL8" -> VkBindings.VK_FORMAT_D32_SFLOAT_S8_UINT;
            default -> VkBindings.VK_FORMAT_R8G8B8A8_UNORM;
        };
    }

    private boolean isDepthFormat(dev.engine.graphics.texture.TextureFormat format) {
        return switch (format.name()) {
            case "DEPTH24", "DEPTH32F", "DEPTH24_STENCIL8", "DEPTH32F_STENCIL8" -> true;
            default -> false;
        };
    }

    private static int mapCompareFunc(CompareFunc func) {
        return switch (func.name()) {
            case "LESS"      -> VkBindings.VK_COMPARE_OP_LESS;
            case "LEQUAL"    -> VkBindings.VK_COMPARE_OP_LESS_OR_EQUAL;
            case "GREATER"   -> VkBindings.VK_COMPARE_OP_GREATER;
            case "GEQUAL"    -> VkBindings.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case "EQUAL"     -> VkBindings.VK_COMPARE_OP_EQUAL;
            case "NOT_EQUAL" -> VkBindings.VK_COMPARE_OP_NOT_EQUAL;
            case "ALWAYS"    -> VkBindings.VK_COMPARE_OP_ALWAYS;
            case "NEVER"     -> VkBindings.VK_COMPARE_OP_NEVER;
            default          -> VkBindings.VK_COMPARE_OP_LESS;
        };
    }

    private static int mapStencilOp(StencilOp op) {
        return switch (op.name()) {
            case "KEEP"      -> VkBindings.VK_STENCIL_OP_KEEP;
            case "ZERO"      -> VkBindings.VK_STENCIL_OP_ZERO;
            case "REPLACE"   -> VkBindings.VK_STENCIL_OP_REPLACE;
            case "INCR"      -> VkBindings.VK_STENCIL_OP_INCREMENT_AND_CLAMP;
            case "DECR"      -> VkBindings.VK_STENCIL_OP_DECREMENT_AND_CLAMP;
            case "INVERT"    -> VkBindings.VK_STENCIL_OP_INVERT;
            case "INCR_WRAP" -> VkBindings.VK_STENCIL_OP_INCREMENT_AND_WRAP;
            case "DECR_WRAP" -> VkBindings.VK_STENCIL_OP_DECREMENT_AND_WRAP;
            default          -> VkBindings.VK_STENCIL_OP_KEEP;
        };
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
            case "LINEAR", "LINEAR_MIPMAP_LINEAR", "LINEAR_MIPMAP_NEAREST" -> VkBindings.VK_FILTER_LINEAR;
            default -> VkBindings.VK_FILTER_NEAREST;
        };
    }

    private int mapMipmapMode(dev.engine.graphics.sampler.FilterMode mode) {
        return switch (mode.name()) {
            case "LINEAR_MIPMAP_LINEAR", "NEAREST_MIPMAP_LINEAR" -> VkBindings.VK_SAMPLER_MIPMAP_MODE_LINEAR;
            default -> VkBindings.VK_SAMPLER_MIPMAP_MODE_NEAREST;
        };
    }

    private int mapWrapMode(dev.engine.graphics.sampler.WrapMode mode) {
        return switch (mode.name()) {
            case "CLAMP_TO_EDGE" -> VkBindings.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
            case "MIRRORED_REPEAT" -> VkBindings.VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
            case "CLAMP_TO_BORDER" -> VkBindings.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER;
            default -> VkBindings.VK_SAMPLER_ADDRESS_MODE_REPEAT;
        };
    }

    private int mapCompareOp(dev.engine.graphics.sampler.CompareFunc func) {
        return switch (func.name()) {
            case "NEVER" -> VkBindings.VK_COMPARE_OP_NEVER;
            case "LESS" -> VkBindings.VK_COMPARE_OP_LESS;
            case "EQUAL" -> VkBindings.VK_COMPARE_OP_EQUAL;
            case "LESS_EQUAL" -> VkBindings.VK_COMPARE_OP_LESS_OR_EQUAL;
            case "GREATER" -> VkBindings.VK_COMPARE_OP_GREATER;
            case "NOT_EQUAL" -> VkBindings.VK_COMPARE_OP_NOT_EQUAL;
            case "GREATER_EQUAL" -> VkBindings.VK_COMPARE_OP_GREATER_OR_EQUAL;
            case "ALWAYS" -> VkBindings.VK_COMPARE_OP_ALWAYS;
            default -> VkBindings.VK_COMPARE_OP_LESS;
        };
    }

    private int mapBorderColor(dev.engine.graphics.sampler.BorderColor color) {
        if (color == null) return VkBindings.VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK;
        return switch (color.name()) {
            case "OPAQUE_BLACK" -> VkBindings.VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK;
            case "OPAQUE_WHITE" -> VkBindings.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE;
            default -> VkBindings.VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK;
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
        var name = desc.minFilter().name();
        return name.contains("MIPMAP");
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
                vk.cmdImageBarrier(cmd, alloc.image(),
                        VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VkBindings.VK_ACCESS_SHADER_READ_BIT,
                        VkBindings.VK_ACCESS_TRANSFER_READ_BIT,
                        VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, i - 1, 1);

                // Transition level i from UNDEFINED to TRANSFER_DST
                vk.cmdImageBarrier(cmd, alloc.image(),
                        VkBindings.VK_IMAGE_LAYOUT_UNDEFINED,
                        VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VkBindings.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                        VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, VkBindings.VK_ACCESS_TRANSFER_WRITE_BIT,
                        VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, i, 1);

                int nextWidth = Math.max(1, mipWidth / 2);
                int nextHeight = Math.max(1, mipHeight / 2);

                vk.cmdBlitImage(cmd, alloc.image(), alloc.image(),
                        mipWidth, mipHeight, nextWidth, nextHeight,
                        i - 1, i, VkBindings.VK_FILTER_LINEAR);

                // Transition level i-1 back to SHADER_READ_ONLY
                vk.cmdImageBarrier(cmd, alloc.image(),
                        VkBindings.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        VkBindings.VK_ACCESS_TRANSFER_READ_BIT,
                        VkBindings.VK_ACCESS_SHADER_READ_BIT,
                        VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, i - 1, 1);

                // Transition level i to SHADER_READ_ONLY
                vk.cmdImageBarrier(cmd, alloc.image(),
                        VkBindings.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VkBindings.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VkBindings.VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VkBindings.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        VkBindings.VK_ACCESS_TRANSFER_WRITE_BIT,
                        VkBindings.VK_ACCESS_SHADER_READ_BIT,
                        VkBindings.VK_IMAGE_ASPECT_COLOR_BIT, i, 1);

                mipWidth = nextWidth;
                mipHeight = nextHeight;
            }
        });

        textureMipsDirty.put(textureHandle.index(), false);
    }
}

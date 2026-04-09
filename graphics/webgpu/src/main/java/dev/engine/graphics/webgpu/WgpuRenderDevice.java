package dev.engine.graphics.webgpu;

import dev.engine.core.handle.Handle;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.CapabilityRegistry;
import dev.engine.graphics.DeviceCapability;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.SamplerResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.buffer.StreamingBuffer;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.command.RenderCommand;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.pipeline.ShaderCompilationException;
import dev.engine.graphics.renderstate.*;
import dev.engine.graphics.resource.ResourceRegistry;
import dev.engine.graphics.sampler.FilterMode;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.sampler.WrapMode;
import dev.engine.graphics.sync.GpuFence;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import dev.engine.graphics.texture.TextureType;
import dev.engine.graphics.texture.MipMode;
import dev.engine.graphics.window.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.engine.core.memory.NativeMemory;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * WebGPU render device backed by {@link WgpuBindings}.
 *
 * <p>Takes a {@link WindowHandle} (from GLFW toolkit) and creates an offscreen
 * rendering context. All rendering goes to an offscreen render target, which
 * supports readback via {@link #readFramebuffer(int, int)}.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Setup: Instance -> Adapter -> Device -> Queue (no surface)</li>
 *   <li>Per-frame: CommandEncoder -> RenderPassEncoder -> CommandBuffer -> Queue.submit()</li>
 *   <li>Bind groups created on-demand per draw call with currently bound resources</li>
 *   <li>Pipeline layout created explicitly from WGSL binding reflection</li>
 * </ul>
 */
public class WgpuRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(WgpuRenderDevice.class);

    // ── Native resource records ───────────────────────────────────────

    private record WgpuBuffer(long nativeBuffer, long size) {}
    private record WgpuTexture(long nativeTexture, long view,
                               TextureDescriptor desc, int wgpuFormat) {}
    private record WgpuVertexInput(VertexFormat format) {}
    private record WgpuSampler(long nativeSampler, SamplerDescriptor desc) {}
    private enum WgpuBindingType { UNIFORM, TEXTURE, SAMPLER, STORAGE }
    private record WgpuPipeline(long nativePipeline,
                                long bindGroupLayout,
                                Map<Integer, WgpuBindingType> bindingTypes,
                                List<ShaderSource> shaderSources,
                                VertexFormat vertexFormat) {}
    private record WgpuRenderTarget(
            List<Handle<TextureResource>> colorTextures,
            Handle<TextureResource> depthTexture,
            int width, int height
    ) {}

    // ── Resource registries ───────────────────────────────────────────

    private final ResourceRegistry<BufferResource, WgpuBuffer> buffers = new ResourceRegistry<>("buffer");
    private final ResourceRegistry<TextureResource, WgpuTexture> textures = new ResourceRegistry<>("texture");
    private final ResourceRegistry<VertexInputResource, WgpuVertexInput> vertexInputs = new ResourceRegistry<>("vertex-input");
    private final ResourceRegistry<SamplerResource, WgpuSampler> samplers = new ResourceRegistry<>("sampler");
    private final ResourceRegistry<PipelineResource, WgpuPipeline> pipelines = new ResourceRegistry<>("pipeline");
    private final ResourceRegistry<RenderTargetResource, WgpuRenderTarget> renderTargets = new ResourceRegistry<>("render-target");

    private final AtomicLong frameCounter = new AtomicLong(0);

    // ── Bindings ─────────────────────────────────────────────────────

    private final WindowHandle window;
    private final WgpuBindings gpu;

    // ── Native state ──────────────────────────────────────────────────

    private final boolean nativeAvailable;
    private long wgpuInstance;
    private long wgpuAdapter;
    private long wgpuDevice;
    private long wgpuQueue;
    private final CapabilityRegistry capabilities = new CapabilityRegistry();
    private WgpuBindings.DeviceLimits deviceLimits;

    // ── Per-frame state ───────────────────────────────────────────────

    private long commandEncoder;
    private long renderPassEncoder;

    // ── Current render target tracking ────────────────────────────────

    private Handle<RenderTargetResource> currentRenderTarget;

    // ── Default offscreen render target (lazy, resized on viewport) ──

    private Handle<RenderTargetResource> defaultRenderTarget;

    /** Surface handle for canvas presentation (0 = headless/offscreen). */
    private long surfaceHandle;

    /** Optional canvas texture view override — when set, the default RT's color attachment uses this instead. */
    private long canvasTextureViewOverride = 0;
    private int defaultRtWidth;
    private int defaultRtHeight;

    // ── Pending bind state (flushed before each draw) ─────────────────

    @SuppressWarnings("unchecked")
    private final Handle<BufferResource>[] boundUbos = new Handle[32];
    @SuppressWarnings("unchecked")
    private final Handle<TextureResource>[] boundTextures = new Handle[32];
    @SuppressWarnings("unchecked")
    private final Handle<SamplerResource>[] boundSamplers = new Handle[32];
    @SuppressWarnings("unchecked")
    private final Handle<BufferResource>[] boundSsbos = new Handle[16];
    private Handle<PipelineResource> currentPipeline;
    private boolean bindingsDirty;
    private boolean bindGroupValid;
    private long currentBindGroup;

    // ── Render state tracking (for pipeline-baked state in WebGPU) ────

    private boolean depthTestEnabled = true;
    private boolean depthWriteEnabled = true;
    private boolean blendEnabled = false;
    private int wgpuCullMode = WgpuBindings.CULL_MODE_BACK;
    private int wgpuFrontFace = WgpuBindings.FRONT_FACE_CCW;
    private CompareFunc depthFunc = CompareFunc.LESS;

    // Stencil state
    private boolean stencilTestEnabled = false;
    private CompareFunc stencilFunc = CompareFunc.ALWAYS;
    private int stencilRef = 0;
    private int stencilMask = 0xFF;
    private StencilOp stencilPassOp = StencilOp.KEEP;
    private StencilOp stencilFailOp = StencilOp.KEEP;
    private StencilOp stencilDepthFailOp = StencilOp.KEEP;

    // Blend mode tracking
    private BlendMode currentBlendMode = BlendMode.NONE;

    // ── Pipeline variant cache (WebGPU pipelines are immutable) ──────

    /**
     * Key for pipeline variants — captures all state baked into the pipeline.
     * When render state changes mid-frame, we look up or create a variant pipeline.
     */
    private record PipelineStateKey(
            int basePipelineIndex,
            String blendMode,
            int cullMode,
            int frontFace,
            boolean depthTest,
            boolean depthWrite,
            String depthFunc,
            boolean stencilTest,
            String stencilFunc,
            int stencilRef,
            int stencilMask,
            String stencilPassOp,
            String stencilFailOp,
            String stencilDepthFailOp,
            int colorTargetFormat
    ) {}

    private final dev.engine.graphics.pipeline.PipelineVariantCache<PipelineStateKey> pipelineVariants =
            new dev.engine.graphics.pipeline.PipelineVariantCache<>();
    private long wgpuFrameCounter = 0;
    private boolean pipelineStateDirty = false;
    /** Tracks the pipeline that was last set on the render pass encoder. */
    private long currentActivePipeline = 0;

    // ── Constructor ───────────────────────────────────────────────────

    /**
     * Creates a WebGPU render device in offscreen mode.
     *
     * @param window the GLFW window handle (kept for API compatibility)
     * @param gpu    the WebGPU bindings implementation
     */
    /** Factory for creating NativeMemory instances. Desktop: SegmentNativeMemory, Web: ByteBufferNativeMemory. */
    private final IntFunction<NativeMemory> memoryFactory;
    private final float configMaxAnisotropy;

    public WgpuRenderDevice(WindowHandle window, WgpuBindings gpu) {
        this(window, gpu, false, null);
    }

    public WgpuRenderDevice(WindowHandle window, WgpuBindings gpu, boolean presentToSurface) {
        this(window, gpu, presentToSurface, null);
    }

    public WgpuRenderDevice(WindowHandle window, WgpuBindings gpu, boolean presentToSurface, dev.engine.graphics.GraphicsConfig config) {
        this.configMaxAnisotropy = config != null ? config.maxAnisotropy() : 1f;
        this.memoryFactory = WgpuRenderDevice::createDefaultMemory;
        this.window = window;
        this.gpu = gpu;
        boolean available = false;
        try {
            available = gpu.initialize();
        } catch (Throwable t) {
            log.warn("WebGPU native library not available: {}", t.getMessage());
        }
        this.nativeAvailable = available;

        if (nativeAvailable) {
            wgpuInstance = gpu.createInstance();

            wgpuAdapter = gpu.instanceRequestAdapter(wgpuInstance);
            if (wgpuAdapter == 0) {
                throw new RuntimeException("Failed to get WebGPU adapter");
            }

            wgpuDevice = gpu.adapterRequestDevice(wgpuInstance, wgpuAdapter);
            if (wgpuDevice == 0) {
                throw new RuntimeException("Failed to get WebGPU device");
            }

            // Process events to ensure device callbacks have completed before querying state
            gpu.instanceProcessEvents(wgpuInstance);

            wgpuQueue = gpu.deviceGetQueue(wgpuDevice);
            // deviceGetLimits is broken in jwebgpu 0.1.15 (struct layout mismatch
            // causes native SIGABRT). Skip it — queryCapability() uses safe defaults.
            deviceLimits = null;

            // Configure presentation surface if requested
            if (presentToSurface) {
                try {
                    if (config != null) {
                        int wgpuPresentMode = switch (config.presentMode()) {
                            case FIFO -> WgpuBindings.PRESENT_MODE_FIFO;
                            case IMMEDIATE -> WgpuBindings.PRESENT_MODE_IMMEDIATE;
                            case MAILBOX -> WgpuBindings.PRESENT_MODE_MAILBOX;
                        };
                        gpu.setPresentMode(wgpuPresentMode);
                    }
                    surfaceHandle = gpu.configureSurface(wgpuInstance, wgpuDevice, window);
                } catch (Throwable t) {
                    log.warn("WebGPU surface creation failed: {} — rendering offscreen", t.getMessage());
                    surfaceHandle = 0;
                }
            }
            if (surfaceHandle != 0) {
                log.info("WebGPU device created with presentation surface");
            } else {
                log.info("WebGPU device created (offscreen mode)");
            }
        } else {
            wgpuInstance = 0;
            wgpuAdapter = 0;
            wgpuDevice = 0;
            wgpuQueue = 0;
            log.warn("WebGPU not available; WebGPU device running without native backend");
        }
        registerCapabilities();
    }

    private void registerCapabilities() {
        // Limits — deviceGetLimits is broken in jwebgpu (SIGABRT), so use safe defaults
        capabilities.register(DeviceCapability.MAX_TEXTURE_SIZE, () ->
                deviceLimits != null ? deviceLimits.maxTextureDimension2D() : 8192);
        capabilities.register(DeviceCapability.MAX_FRAMEBUFFER_WIDTH, () ->
                deviceLimits != null ? deviceLimits.maxTextureDimension2D() : 8192);
        capabilities.register(DeviceCapability.MAX_FRAMEBUFFER_HEIGHT, () ->
                deviceLimits != null ? deviceLimits.maxTextureDimension2D() : 8192);
        capabilities.register(DeviceCapability.MAX_ANISOTROPY, () ->
                deviceLimits != null ? deviceLimits.maxSamplerAnisotropy() : 16.0f);
        capabilities.register(DeviceCapability.MAX_UNIFORM_BUFFER_SIZE, () ->
                deviceLimits != null ? deviceLimits.maxUniformBufferBindingSize() : 65536);
        capabilities.register(DeviceCapability.MAX_STORAGE_BUFFER_SIZE, () ->
                deviceLimits != null ? deviceLimits.maxStorageBufferBindingSize() : 134217728);

        // Features
        capabilities.registerStatic(DeviceCapability.COMPUTE_SHADERS, false);
        capabilities.registerStatic(DeviceCapability.GEOMETRY_SHADERS, false);
        capabilities.registerStatic(DeviceCapability.TESSELLATION, false);
        capabilities.registerStatic(DeviceCapability.ANISOTROPIC_FILTERING, true);
        capabilities.registerStatic(DeviceCapability.BINDLESS_TEXTURES, false);

        // Device info
        capabilities.registerStatic(DeviceCapability.BACKEND_NAME, "WebGPU");
        capabilities.registerStatic(DeviceCapability.SHADER_TARGET, 28); // ShaderCompiler.TARGET_WGSL
        capabilities.registerStatic(DeviceCapability.DEVICE_NAME, "WebGPU");
        capabilities.registerStatic(DeviceCapability.API_VERSION, "WebGPU");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Buffer
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        if (nativeAvailable) {
            int usage = mapBufferUsage(descriptor.usage()) | WgpuBindings.BUFFER_USAGE_COPY_DST;
            long size = descriptor.size();
            // WebGPU requires uniform buffers to be at least 16 bytes
            if (size < 16 && (usage & WgpuBindings.BUFFER_USAGE_UNIFORM) != 0) {
                size = 16;
            }
            long buf = gpu.deviceCreateBuffer(wgpuDevice, size, usage);
            return buffers.register(new WgpuBuffer(buf, size));
        }
        return buffers.register(new WgpuBuffer(0, descriptor.size()));
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!buffers.isValid(buffer)) return;
        var buf = buffers.remove(buffer);
        if (buf != null && nativeAvailable && buf.nativeBuffer() != 0) {
            gpu.bufferRelease(buf.nativeBuffer());
        }
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> buffer) {
        return buffers.isValid(buffer);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        var buf = buffers.get(buffer);
        long size = buf != null ? buf.size() : 0L;
        return writeBuffer(buffer, 0, size);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        var buf = buffers.get(buffer);
        var staging = ByteBuffer.allocate((int) length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        var gpuMemory = memoryFactory.apply((int) length);
        return new BufferWriter() {
            @Override
            public NativeMemory memory() { return gpuMemory; }

            @Override
            public void close() {
                if (nativeAvailable && buf != null && buf.nativeBuffer() != 0) {
                    // Bulk copy from NativeMemory to direct ByteBuffer for upload
                    // (native wgpu requires a stable pointer — heap buffers may SIGABRT)
                    ByteBuffer upload = ByteBuffer.allocateDirect((int) length).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    for (int i = 0; i < (int) length / 4; i++) {
                        upload.putFloat(i * 4, gpuMemory.getFloat(i * 4L));
                    }
                    // Copy remaining bytes
                    for (int i = ((int) length / 4) * 4; i < (int) length; i++) {
                        upload.put(i, gpuMemory.getByte(i));
                    }
                    gpu.queueWriteBuffer(wgpuQueue, buf.nativeBuffer(), (int) offset, upload, (int) length);
                }
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // Texture
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        int wgpuFormat = mapTextureFormat(descriptor.format());
        int usage = WgpuBindings.TEXTURE_USAGE_TEXTURE_BINDING
                | WgpuBindings.TEXTURE_USAGE_COPY_DST
                | WgpuBindings.TEXTURE_USAGE_COPY_SRC;

        if (nativeAvailable) {
            var tex = createWgpuTexture(descriptor, wgpuFormat, usage);
            var view = createDefaultView(tex, descriptor, wgpuFormat);
            return textures.register(new WgpuTexture(tex, view, descriptor, wgpuFormat));
        }
        return textures.register(new WgpuTexture(0, 0, descriptor, wgpuFormat));
    }

    private Handle<TextureResource> createTextureForRenderTarget(TextureDescriptor descriptor, int extraUsage) {
        int wgpuFormat = mapTextureFormat(descriptor.format());
        int usage = WgpuBindings.TEXTURE_USAGE_COPY_SRC | extraUsage;

        if (nativeAvailable) {
            var tex = createWgpuTexture(descriptor, wgpuFormat, usage);
            var view = createDefaultView(tex, descriptor, wgpuFormat);
            return textures.register(new WgpuTexture(tex, view, descriptor, wgpuFormat));
        }
        return textures.register(new WgpuTexture(0, 0, descriptor, wgpuFormat));
    }

    private long createDefaultView(long tex, TextureDescriptor descriptor, int wgpuFormat) {
        int viewDimension;
        int arrayLayerCount;

        if (descriptor.type() == TextureType.TEXTURE_3D) {
            viewDimension = WgpuBindings.TEXTURE_VIEW_DIMENSION_3D;
            arrayLayerCount = 1;
        } else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) {
            viewDimension = WgpuBindings.TEXTURE_VIEW_DIMENSION_2D_ARRAY;
            arrayLayerCount = descriptor.layers();
        } else if (descriptor.type() == TextureType.TEXTURE_CUBE) {
            viewDimension = WgpuBindings.TEXTURE_VIEW_DIMENSION_CUBE;
            arrayLayerCount = 6;
        } else {
            viewDimension = WgpuBindings.TEXTURE_VIEW_DIMENSION_2D;
            arrayLayerCount = 1;
        }

        return gpu.textureCreateView(tex, wgpuFormat, viewDimension, arrayLayerCount);
    }

    private long createWgpuTexture(TextureDescriptor descriptor, int wgpuFormat, int usage) {
        int depthOrLayers = 1;
        if (descriptor.type() == TextureType.TEXTURE_3D) depthOrLayers = descriptor.depth();
        else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) depthOrLayers = descriptor.layers();
        else if (descriptor.type() == TextureType.TEXTURE_CUBE) depthOrLayers = 6;

        int dimension = descriptor.type() == TextureType.TEXTURE_3D
                ? WgpuBindings.TEXTURE_DIMENSION_3D : WgpuBindings.TEXTURE_DIMENSION_2D;

        return gpu.deviceCreateTexture(wgpuDevice, descriptor.width(), descriptor.height(),
                depthOrLayers, wgpuFormat, dimension, usage);
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        if (!nativeAvailable) return;
        var tex = textures.get(texture);
        if (tex == null || tex.nativeTexture() == 0) return;

        var desc = tex.desc();
        int bytesPerRow = desc.width() * bytesPerPixel(desc.format());

        int depthOrLayers = 1;
        if (desc.type() == TextureType.TEXTURE_3D) depthOrLayers = desc.depth();
        else if (desc.type() == TextureType.TEXTURE_2D_ARRAY) depthOrLayers = desc.layers();
        else if (desc.type() == TextureType.TEXTURE_CUBE) depthOrLayers = 6;

        // Ensure we have a direct ByteBuffer for native wgpu (heap buffers may SIGABRT).
        // The TeaVM bindings extract byte[] internally, so this is a no-op cost on web.
        ByteBuffer direct;
        if (pixels.isDirect()) {
            direct = pixels;
        } else {
            direct = ByteBuffer.allocateDirect(pixels.remaining());
            direct.put(pixels.duplicate());
            direct.flip();
        }

        gpu.queueWriteTexture(wgpuQueue, tex.nativeTexture(),
                desc.width(), desc.height(), depthOrLayers, bytesPerRow, direct);
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!textures.isValid(texture)) return;
        var tex = textures.remove(texture);
        if (tex != null && nativeAvailable) {
            if (tex.view() != 0) gpu.textureViewRelease(tex.view());
            if (tex.nativeTexture() != 0) gpu.textureRelease(tex.nativeTexture());
        }
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> texture) {
        return textures.isValid(texture);
    }

    @Override
    public long getBindlessTextureHandle(Handle<TextureResource> texture) {
        return 0L; // WebGPU does not support bindless textures
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render Target
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        var colorTextures = new ArrayList<Handle<TextureResource>>();

        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            var format = descriptor.colorAttachments().get(i);
            var texDesc = new TextureDescriptor(descriptor.width(), descriptor.height(), format, MipMode.NONE);
            var texHandle = createTextureForRenderTarget(texDesc,
                    WgpuBindings.TEXTURE_USAGE_RENDER_ATTACHMENT
                            | WgpuBindings.TEXTURE_USAGE_TEXTURE_BINDING);
            colorTextures.add(texHandle);
        }

        Handle<TextureResource> depthTexture = null;
        if (descriptor.depthFormat() != null) {
            var depthDesc = new TextureDescriptor(descriptor.width(), descriptor.height(),
                    descriptor.depthFormat(), MipMode.NONE);
            depthTexture = createTextureForRenderTarget(depthDesc,
                    WgpuBindings.TEXTURE_USAGE_RENDER_ATTACHMENT);
        }

        return renderTargets.register(new WgpuRenderTarget(
                colorTextures, depthTexture, descriptor.width(), descriptor.height()));
    }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) {
        var rt = renderTargets.get(renderTarget);
        return rt != null ? rt.colorTextures().get(index) : null;
    }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> renderTarget) {
        if (!renderTargets.isValid(renderTarget)) return;
        var rt = renderTargets.remove(renderTarget);
        if (rt != null) {
            for (var tex : rt.colorTextures()) destroyTexture(tex);
            if (rt.depthTexture() != null) destroyTexture(rt.depthTexture());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Vertex Input
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        return vertexInputs.register(new WgpuVertexInput(format));
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        if (!vertexInputs.isValid(vertexInput)) return;
        vertexInputs.remove(vertexInput);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Sampler
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        if (nativeAvailable) {
            int compare = descriptor.compareFunc() != null
                    ? mapCompareFunction(descriptor.compareFunc()) : 0; // 0 = undefined/disabled in WebGPU
            float aniso = Math.min(descriptor.maxAnisotropy(), configMaxAnisotropy);
            long sampler = gpu.deviceCreateSampler(wgpuDevice,
                    mapWrapMode(descriptor.wrapS()),
                    mapWrapMode(descriptor.wrapT()),
                    mapWrapMode(descriptor.wrapR()),
                    mapFilterMode(descriptor.magFilter()),
                    mapFilterMode(descriptor.minFilter()),
                    mapMipmapFilterMode(descriptor.minFilter()),
                    descriptor.minLod(), descriptor.maxLod(),
                    compare, aniso);
            return samplers.register(new WgpuSampler(sampler, descriptor));
        }
        return samplers.register(new WgpuSampler(0, descriptor));
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplers.isValid(sampler)) return;
        var s = samplers.remove(sampler);
        if (s != null && nativeAvailable && s.nativeSampler() != 0) {
            gpu.samplerRelease(s.nativeSampler());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Pipeline
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        if (!nativeAvailable) {
            return pipelines.register(new WgpuPipeline(0, 0, Map.of(),
                    descriptor.shaders(), descriptor.vertexFormat()));
        }

        // Find vertex and fragment shader sources
        ShaderSource vertexShader = null;
        ShaderSource fragmentShader = null;
        for (var shader : descriptor.shaders()) {
            if (shader.stage() == ShaderStage.VERTEX) vertexShader = shader;
            else if (shader.stage() == ShaderStage.FRAGMENT) fragmentShader = shader;
        }

        if (vertexShader == null) {
            throw new ShaderCompilationException("WebGPU pipeline requires a vertex shader");
        }

        // Extract bindings from WGSL
        var bindingTypes = new TreeMap<Integer, WgpuBindingType>();
        extractWgslBindings(vertexShader.source(), bindingTypes);
        if (fragmentShader != null) {
            extractWgslBindings(fragmentShader.source(), bindingTypes);
        }

        // Create shader modules
        long vertModule = createShaderModule(vertexShader.source());
        long fragModule = 0;
        if (fragmentShader != null) {
            fragModule = createShaderModule(fragmentShader.source());
        }

        // Build explicit bind group layout from binding types
        long bgLayout = createBindGroupLayout(bindingTypes);

        // Build pipeline layout
        long pipelineLayout = gpu.deviceCreatePipelineLayout(wgpuDevice, new long[]{bgLayout});

        // Build render pipeline
        long pipeline = buildRenderPipeline(pipelineLayout, vertModule, vertexShader,
                fragModule, fragmentShader, descriptor.vertexFormat());

        // Release shader modules
        gpu.shaderModuleRelease(vertModule);
        if (fragModule != 0) gpu.shaderModuleRelease(fragModule);

        // Release pipeline layout (pipeline retains reference)
        gpu.pipelineLayoutRelease(pipelineLayout);

        log.debug("Pipeline created with {} bind group bindings: {}", bindingTypes.size(), bindingTypes);
        return pipelines.register(new WgpuPipeline(pipeline, bgLayout, bindingTypes,
                descriptor.shaders(), descriptor.vertexFormat()));
    }

    private long createShaderModule(String wgslSource) {
        if (wgslSource == null || wgslSource.isEmpty()) {
            throw new ShaderCompilationException("WGSL shader source is null or empty");
        }
        long module = gpu.deviceCreateShaderModule(wgpuDevice, wgslSource);
        if (!gpu.shaderModuleIsValid(module)) {
            throw new ShaderCompilationException("WebGPU shader module creation failed");
        }
        return module;
    }

    private long createBindGroupLayout(Map<Integer, WgpuBindingType> bindingTypes) {
        int visibility = WgpuBindings.SHADER_STAGE_VERTEX | WgpuBindings.SHADER_STAGE_FRAGMENT;

        var entries = new WgpuBindings.BindGroupLayoutEntry[bindingTypes.size()];
        int i = 0;
        for (var entry : bindingTypes.entrySet()) {
            var type = switch (entry.getValue()) {
                case UNIFORM -> WgpuBindings.BindingType.UNIFORM_BUFFER;
                case STORAGE -> WgpuBindings.BindingType.READ_ONLY_STORAGE_BUFFER;
                case TEXTURE -> WgpuBindings.BindingType.SAMPLED_TEXTURE;
                case SAMPLER -> WgpuBindings.BindingType.FILTERING_SAMPLER;
            };
            entries[i++] = new WgpuBindings.BindGroupLayoutEntry(entry.getKey(), visibility, type);
        }

        return gpu.deviceCreateBindGroupLayout(wgpuDevice, entries);
    }

    /**
     * Extracts bind group 0 binding indices and their types from WGSL source.
     */
    private static final Pattern WGSL_TYPED_BINDING_PATTERN =
            Pattern.compile(
                    "(?:@binding\\((\\d+)\\)\\s+@group\\(0\\)|@group\\(0\\)\\s+@binding\\((\\d+)\\))\\s+var(?:<(\\w+)(?:,\\s*\\w+)?>)?\\s+\\w+\\s*:\\s*(\\w+)");

    private static void extractWgslBindings(String wgslSource, Map<Integer, WgpuBindingType> bindingTypes) {
        if (wgslSource == null) return;
        var matcher = WGSL_TYPED_BINDING_PATTERN.matcher(wgslSource);
        while (matcher.find()) {
            String bindingStr = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (bindingStr == null) continue;
            int binding = Integer.parseInt(bindingStr);

            String addressSpace = matcher.group(3);
            String typeName = matcher.group(4);

            WgpuBindingType type;
            if ("uniform".equals(addressSpace)) {
                type = WgpuBindingType.UNIFORM;
            } else if ("storage".equals(addressSpace)) {
                type = WgpuBindingType.STORAGE;
            } else if (typeName != null && typeName.startsWith("texture")) {
                type = WgpuBindingType.TEXTURE;
            } else if (typeName != null && typeName.startsWith("sampler")) {
                type = WgpuBindingType.SAMPLER;
            } else {
                type = WgpuBindingType.UNIFORM;
            }

            bindingTypes.putIfAbsent(binding, type);
        }
    }

    /**
     * Builds a render pipeline with the current render state.
     */
    private long buildRenderPipeline(long pipelineLayout, long vertModule, ShaderSource vertexShader,
                                     long fragModule, ShaderSource fragmentShader,
                                     VertexFormat vertexFormat) {
        WgpuBindings.VertexBufferLayoutDesc vbl = null;
        if (vertexFormat != null) {
            var attrs = vertexFormat.attributes();
            var attrDescs = new WgpuBindings.VertexAttributeDesc[attrs.size()];
            for (int i = 0; i < attrs.size(); i++) {
                var attr = attrs.get(i);
                attrDescs[i] = new WgpuBindings.VertexAttributeDesc(
                        mapVertexFormat(attr), attr.offset(), attr.location());
            }
            vbl = new WgpuBindings.VertexBufferLayoutDesc(
                    vertexFormat.stride(), WgpuBindings.VERTEX_STEP_MODE_VERTEX, attrDescs);
        }

        // Map blend state
        int blendColorSrc, blendColorDst, blendAlphaSrc, blendAlphaDst;
        getBlendFactors(currentBlendMode);
        var blend = getBlendFactors(currentBlendMode);

        // Map stencil state
        var stencilFront = buildStencilFaceState(stencilTestEnabled);
        var stencilBack = buildStencilFaceState(stencilTestEnabled);

        var desc = new WgpuBindings.RenderPipelineDescriptor(
                pipelineLayout,
                vertModule,
                vertexShader.entryPoint(),
                fragModule,
                fragmentShader != null ? fragmentShader.entryPoint() : null,
                vbl,
                WgpuBindings.PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                wgpuFrontFace,
                wgpuCullMode,
                WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8,
                depthWriteEnabled ? WgpuBindings.OPTIONAL_BOOL_TRUE : WgpuBindings.OPTIONAL_BOOL_FALSE,
                depthTestEnabled ? mapCompareFunc(depthFunc) : WgpuBindings.COMPARE_ALWAYS,
                stencilMask,
                stencilMask,
                stencilFront,
                stencilBack,
                currentColorTargetFormat(),
                blend[0], blend[1], WgpuBindings.BLEND_OP_ADD,
                blend[2], blend[3], WgpuBindings.BLEND_OP_ADD
        );

        return gpu.deviceCreateRenderPipeline(wgpuDevice, desc);
    }

    private int currentColorTargetFormat() {
        if (currentRenderTarget != null) {
            var rt = renderTargets.get(currentRenderTarget);
            if (rt != null && !rt.colorTextures().isEmpty()) {
                var colorTex = textures.get(rt.colorTextures().getFirst());
                if (colorTex != null) return colorTex.wgpuFormat();
            }
        }
        return surfaceHandle != 0
                ? gpu.surfaceFormat()
                : WgpuBindings.TEXTURE_FORMAT_RGBA8_UNORM;
    }

    private WgpuBindings.StencilFaceState buildStencilFaceState(boolean enabled) {
        if (enabled) {
            return new WgpuBindings.StencilFaceState(
                    mapCompareFunc(stencilFunc),
                    mapStencilOp(stencilPassOp),
                    mapStencilOp(stencilFailOp),
                    mapStencilOp(stencilDepthFailOp));
        } else {
            return new WgpuBindings.StencilFaceState(
                    WgpuBindings.COMPARE_ALWAYS,
                    WgpuBindings.STENCIL_OP_KEEP,
                    WgpuBindings.STENCIL_OP_KEEP,
                    WgpuBindings.STENCIL_OP_KEEP);
        }
    }

    /** Returns [colorSrc, colorDst, alphaSrc, alphaDst]. */
    private static int[] getBlendFactors(BlendMode mode) {
        if (mode == BlendMode.ALPHA) {
            return new int[]{
                    WgpuBindings.BLEND_FACTOR_SRC_ALPHA, WgpuBindings.BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ONE_MINUS_SRC_ALPHA};
        } else if (mode == BlendMode.ADDITIVE) {
            return new int[]{
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ONE,
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ONE};
        } else if (mode == BlendMode.MULTIPLY) {
            return new int[]{
                    WgpuBindings.BLEND_FACTOR_DST, WgpuBindings.BLEND_FACTOR_ZERO,
                    WgpuBindings.BLEND_FACTOR_DST_ALPHA, WgpuBindings.BLEND_FACTOR_ZERO};
        } else if (mode == BlendMode.PREMULTIPLIED) {
            return new int[]{
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ONE_MINUS_SRC_ALPHA};
        } else {
            // NONE
            return new int[]{
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ZERO,
                    WgpuBindings.BLEND_FACTOR_ONE, WgpuBindings.BLEND_FACTOR_ZERO};
        }
    }

    /**
     * Builds a pipeline variant with current render state using the shader sources
     * and vertex format from the base pipeline.
     */
    private long buildPipelineVariant(WgpuPipeline basePipeline) {
        ShaderSource vertexShader = null;
        ShaderSource fragmentShader = null;
        for (var shader : basePipeline.shaderSources()) {
            if (shader.stage() == ShaderStage.VERTEX) vertexShader = shader;
            else if (shader.stage() == ShaderStage.FRAGMENT) fragmentShader = shader;
        }

        if (vertexShader == null) {
            throw new ShaderCompilationException("WebGPU pipeline variant requires a vertex shader");
        }

        long vertModule = createShaderModule(vertexShader.source());
        long fragModule = 0;
        if (fragmentShader != null) {
            fragModule = createShaderModule(fragmentShader.source());
        }

        long pipelineLayout = gpu.deviceCreatePipelineLayout(wgpuDevice,
                new long[]{basePipeline.bindGroupLayout()});

        long pipeline = buildRenderPipeline(pipelineLayout, vertModule, vertexShader,
                fragModule, fragmentShader, basePipeline.vertexFormat());

        gpu.shaderModuleRelease(vertModule);
        if (fragModule != 0) gpu.shaderModuleRelease(fragModule);
        gpu.pipelineLayoutRelease(pipelineLayout);

        log.debug("Pipeline variant created: blend={}, cull={}, frontFace={}, depthTest={}, depthWrite={}, depthFunc={}",
                currentBlendMode, wgpuCullMode, wgpuFrontFace, depthTestEnabled, depthWriteEnabled, depthFunc);
        return pipeline;
    }

    /**
     * Returns the appropriate pipeline variant for the current render state,
     * creating one if needed. Returns 0 if no pipeline is bound.
     */
    private long getOrCreateVariant(Handle<PipelineResource> basePipelineHandle) {
        var basePipeline = pipelines.get(basePipelineHandle);
        if (basePipeline == null || basePipeline.nativePipeline() == 0) return 0;

        var key = new PipelineStateKey(
                basePipelineHandle.index(),
                currentBlendMode.name(),
                wgpuCullMode,
                wgpuFrontFace,
                depthTestEnabled,
                depthWriteEnabled,
                depthFunc.name(),
                stencilTestEnabled,
                stencilFunc.name(),
                stencilRef,
                stencilMask,
                stencilPassOp.name(),
                stencilFailOp.name(),
                stencilDepthFailOp.name(),
                currentColorTargetFormat()
        );

        return pipelineVariants.getOrCreate(key, wgpuFrameCounter, k -> buildPipelineVariant(basePipeline));
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelines.isValid(pipeline)) return;
        var p = pipelines.remove(pipeline);
        if (p != null && nativeAvailable) {
            if (p.nativePipeline() != 0) gpu.renderPipelineRelease(p.nativePipeline());
            if (p.bindGroupLayout() != 0) gpu.bindGroupLayoutRelease(p.bindGroupLayout());
        }
        // Clean up any pipeline variants for this pipeline
        pipelineVariants.removeIf(
                k -> k.basePipelineIndex() == pipeline.index(),
                variant -> { if (nativeAvailable) { gpu.renderPipelineRelease(variant); } });
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> pipeline) {
        return pipelines.isValid(pipeline);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Streaming Buffer
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public StreamingBuffer createStreamingBuffer(long frameSize, int frameCount, BufferUsage usage) {
        return new WgpuStreamingBuffer(this, frameSize, frameCount, usage);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fence
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public GpuFence createFence() {
        return new GpuFence() {
            @Override public boolean isSignaled() { return true; }
            @Override public void waitFor() {}
            @Override public boolean waitFor(long timeoutNanos) { return true; }
            @Override public void close() {}
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // Default Render Target
    // ═══════════════════════════════════════════════════════════════════

    private void ensureDefaultRenderTarget(int width, int height) {
        if (width <= 0 || height <= 0) return;
        if (defaultRenderTarget != null && defaultRtWidth == width && defaultRtHeight == height) return;

        if (defaultRenderTarget != null) {
            destroyRenderTarget(defaultRenderTarget);
        }
        // Match the surface format when presenting, RGBA8 otherwise
        TextureFormat colorFormat;
        if (surfaceHandle != 0) {
            colorFormat = gpu.surfaceFormat() == WgpuBindings.TEXTURE_FORMAT_BGRA8_UNORM
                    ? TextureFormat.BGRA8 : TextureFormat.RGBA8;
        } else {
            colorFormat = TextureFormat.RGBA8;
        }
        defaultRenderTarget = createRenderTarget(
                RenderTargetDescriptor.colorDepth(width, height,
                        colorFormat, TextureFormat.DEPTH24_STENCIL8));
        defaultRtWidth = width;
        defaultRtHeight = height;
        log.debug("Created default render target {}x{} ({})", width, height, colorFormat);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Frame Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void beginFrame() {
        frameCounter.incrementAndGet();
        if (!nativeAvailable) return;

        commandEncoder = gpu.deviceCreateCommandEncoder(wgpuDevice);

        // Acquire surface texture view for canvas presentation
        if (gpu.hasSurface() && surfaceHandle != 0) {
            // Ensure default RT matches current window/canvas size
            ensureDefaultRenderTarget(window.width(), window.height());
            canvasTextureViewOverride = gpu.getSurfaceTextureView(surfaceHandle);
        }

        // Reset render pass
        renderPassEncoder = 0;
        currentRenderTarget = defaultRenderTarget;
        currentPipeline = null;
        bindingsDirty = true;
        pipelineStateDirty = false;
        currentActivePipeline = 0;

        // Clear binding state
        java.util.Arrays.fill(boundUbos, null);
        java.util.Arrays.fill(boundTextures, null);
        java.util.Arrays.fill(boundSamplers, null);
        java.util.Arrays.fill(boundSsbos, null);
    }

    @Override
    public void endFrame() {
        if (!nativeAvailable) return;

        endCurrentRenderPass();

        // Finish and submit command buffer
        long commandBuffer = gpu.commandEncoderFinish(commandEncoder);
        gpu.queueSubmit(wgpuQueue, commandBuffer);

        // Release
        gpu.commandBufferRelease(commandBuffer);
        gpu.commandEncoderRelease(commandEncoder);
        commandEncoder = 0;

        if (currentBindGroup != 0) {
            gpu.bindGroupRelease(currentBindGroup);
            currentBindGroup = 0;
        }

        // Release surface texture view and present
        if (canvasTextureViewOverride != 0) {
            gpu.releaseSurfaceTextureView(canvasTextureViewOverride);
            canvasTextureViewOverride = 0;
        }
        if (surfaceHandle != 0) {
            gpu.surfacePresent(surfaceHandle);
        }

        wgpuFrameCounter++;
        pipelineVariants.evict(wgpuFrameCounter, p -> gpu.renderPipelineRelease(p));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Command Submission
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void submit(CommandList commands) {
        for (var command : commands.commands()) {
            executeCommand(command);
        }
    }

    private void executeCommand(RenderCommand command) {
        switch (command) {
            case RenderCommand.BindRenderTarget cmd -> {
                endCurrentRenderPass();
                currentRenderTarget = cmd.renderTarget();
                beginRenderPass();
            }
            case RenderCommand.BindDefaultRenderTarget cmd -> {
                endCurrentRenderPass();
                currentRenderTarget = defaultRenderTarget;
            }
            case RenderCommand.Clear cmd -> {
                endCurrentRenderPass();
                if (currentRenderTarget != null) {
                    beginRenderPassWithClear(cmd.r(), cmd.g(), cmd.b(), cmd.a());
                }
            }
            case RenderCommand.Viewport cmd -> {
                if (nativeAvailable) {
                    ensureDefaultRenderTarget(cmd.width(), cmd.height());
                    if (currentRenderTarget == null) {
                        currentRenderTarget = defaultRenderTarget;
                    }
                }
                if (renderPassEncoder != 0 && nativeAvailable) {
                    gpu.renderPassSetViewport(renderPassEncoder,
                            (float) cmd.x(), (float) cmd.y(),
                            (float) cmd.width(), (float) cmd.height(), 0.0f, 1.0f);
                }
            }
            case RenderCommand.Scissor cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    // Clamp scissor rect to render target dimensions — WebGPU validation
                    // rejects scissors larger than the render target (unlike GL/VK which clamp silently)
                    var rt = currentRenderTarget != null ? renderTargets.get(currentRenderTarget) : null;
                    int rtW = rt != null ? rt.width() : cmd.width();
                    int rtH = rt != null ? rt.height() : cmd.height();
                    int sx = Math.max(0, cmd.x());
                    int sy = Math.max(0, cmd.y());
                    int sw = Math.min(cmd.width(), rtW - sx);
                    int sh = Math.min(cmd.height(), rtH - sy);
                    if (sw > 0 && sh > 0) {
                        gpu.renderPassSetScissorRect(renderPassEncoder, sx, sy, sw, sh);
                    }
                }
            }
            case RenderCommand.BindPipeline cmd -> {
                currentPipeline = cmd.pipeline();
                if (renderPassEncoder != 0 && nativeAvailable) {
                    var p = pipelines.get(cmd.pipeline());
                    if (p != null && p.nativePipeline() != 0) {
                        gpu.renderPassSetPipeline(renderPassEncoder, p.nativePipeline());
                        currentActivePipeline = p.nativePipeline();
                    }
                }
                bindingsDirty = true;
                pipelineStateDirty = true;
            }
            case RenderCommand.BindVertexBuffer cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    var buf = buffers.get(cmd.buffer());
                    if (buf != null && buf.nativeBuffer() != 0) {
                        gpu.renderPassSetVertexBuffer(renderPassEncoder,
                                0, buf.nativeBuffer(), 0, (int) buf.size());
                    }
                }
            }
            case RenderCommand.BindIndexBuffer cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    var buf = buffers.get(cmd.buffer());
                    if (buf != null && buf.nativeBuffer() != 0) {
                        gpu.renderPassSetIndexBuffer(renderPassEncoder,
                                buf.nativeBuffer(), WgpuBindings.INDEX_FORMAT_UINT32,
                                0, (int) buf.size());
                    }
                }
            }
            case RenderCommand.BindUniformBuffer cmd -> {
                boundUbos[cmd.binding()] = cmd.buffer();
                bindingsDirty = true;
            }
            case RenderCommand.BindTexture cmd -> {
                if (cmd.unit() < boundTextures.length) {
                    boundTextures[cmd.unit()] = cmd.texture();
                    bindingsDirty = true;
                }
            }
            case RenderCommand.BindSampler cmd -> {
                if (cmd.unit() < boundSamplers.length) {
                    boundSamplers[cmd.unit()] = cmd.sampler();
                    bindingsDirty = true;
                }
            }
            case RenderCommand.BindStorageBuffer cmd -> {
                if (cmd.binding() < boundSsbos.length) {
                    boundSsbos[cmd.binding()] = cmd.buffer();
                    bindingsDirty = true;
                }
            }
            case RenderCommand.Draw cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    flushPipelineVariant();
                    flushBindings();
                    if (bindGroupValid) gpu.renderPassDraw(renderPassEncoder, cmd.vertexCount(), 1, cmd.firstVertex(), 0);
                }
            }
            case RenderCommand.DrawIndexed cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    flushPipelineVariant();
                    flushBindings();
                    if (bindGroupValid) gpu.renderPassDrawIndexed(renderPassEncoder, cmd.indexCount(), 1, cmd.firstIndex(), 0, 0);
                }
            }
            case RenderCommand.DrawInstanced cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    flushPipelineVariant();
                    flushBindings();
                    if (bindGroupValid) gpu.renderPassDraw(renderPassEncoder, cmd.vertexCount(), cmd.instanceCount(),
                            cmd.firstVertex(), cmd.firstInstance());
                }
            }
            case RenderCommand.DrawIndexedInstanced cmd -> {
                if (renderPassEncoder != 0 && nativeAvailable) {
                    flushPipelineVariant();
                    flushBindings();
                    if (bindGroupValid) gpu.renderPassDrawIndexed(renderPassEncoder, cmd.indexCount(), cmd.instanceCount(),
                            cmd.firstIndex(), 0, cmd.firstInstance());
                }
            }
            case RenderCommand.SetRenderState cmd -> {
                var props = cmd.properties();
                if (props.contains(RenderState.DEPTH_TEST)) depthTestEnabled = props.get(RenderState.DEPTH_TEST);
                if (props.contains(RenderState.DEPTH_WRITE)) depthWriteEnabled = props.get(RenderState.DEPTH_WRITE);
                if (props.contains(RenderState.DEPTH_FUNC)) depthFunc = props.get(RenderState.DEPTH_FUNC);
                if (props.contains(RenderState.CULL_MODE)) {
                    var mode = props.get(RenderState.CULL_MODE);
                    if (mode == CullMode.NONE) wgpuCullMode = WgpuBindings.CULL_MODE_NONE;
                    else if (mode == CullMode.FRONT) wgpuCullMode = WgpuBindings.CULL_MODE_FRONT;
                    else wgpuCullMode = WgpuBindings.CULL_MODE_BACK;
                }
                if (props.contains(RenderState.FRONT_FACE)) {
                    wgpuFrontFace = props.get(RenderState.FRONT_FACE) == FrontFace.CCW
                            ? WgpuBindings.FRONT_FACE_CCW : WgpuBindings.FRONT_FACE_CW;
                }
                if (props.contains(RenderState.BLEND_MODE)) {
                    var mode = props.get(RenderState.BLEND_MODE);
                    currentBlendMode = mode;
                    blendEnabled = mode != BlendMode.NONE;
                }
                // Stencil state
                if (props.contains(RenderState.STENCIL_TEST)) stencilTestEnabled = props.get(RenderState.STENCIL_TEST);
                if (props.contains(RenderState.STENCIL_FUNC)) stencilFunc = props.get(RenderState.STENCIL_FUNC);
                if (props.contains(RenderState.STENCIL_REF)) stencilRef = props.get(RenderState.STENCIL_REF);
                if (props.contains(RenderState.STENCIL_MASK)) stencilMask = props.get(RenderState.STENCIL_MASK);
                if (props.contains(RenderState.STENCIL_PASS)) stencilPassOp = props.get(RenderState.STENCIL_PASS);
                if (props.contains(RenderState.STENCIL_FAIL)) stencilFailOp = props.get(RenderState.STENCIL_FAIL);
                if (props.contains(RenderState.STENCIL_DEPTH_FAIL)) stencilDepthFailOp = props.get(RenderState.STENCIL_DEPTH_FAIL);
                pipelineStateDirty = true;
            }
            case RenderCommand.PushConstants cmd -> {
                log.warn("PushConstants not supported on WebGPU — command ignored");
            }
            case RenderCommand.BindComputePipeline cmd -> {
                log.warn("BindComputePipeline not supported on WebGPU — command ignored");
            }
            case RenderCommand.Dispatch cmd -> {
                log.warn("Dispatch not supported on WebGPU — command ignored");
            }
            case RenderCommand.BindImage cmd -> {
                log.warn("BindImage not supported on WebGPU — command ignored");
            }
            case RenderCommand.MemoryBarrier cmd -> {
                // WebGPU handles barriers implicitly
            }
            case RenderCommand.CopyBuffer cmd -> {
                if (nativeAvailable && commandEncoder != 0) {
                    var src = buffers.get(cmd.src());
                    var dst = buffers.get(cmd.dst());
                    if (src != null && dst != null
                            && src.nativeBuffer() != 0 && dst.nativeBuffer() != 0) {
                        gpu.commandEncoderCopyBufferToBuffer(commandEncoder,
                                src.nativeBuffer(), (int) cmd.srcOffset(),
                                dst.nativeBuffer(), (int) cmd.dstOffset(), (int) cmd.size());
                    }
                }
            }
            case RenderCommand.CopyTexture cmd -> {
                log.warn("CopyTexture not yet implemented for WebGPU — command ignored");
            }
            case RenderCommand.BlitTexture cmd -> {
                log.warn("BlitTexture not yet implemented for WebGPU — command ignored");
            }
            case RenderCommand.DrawIndirect cmd -> {
                log.warn("DrawIndirect not yet implemented for WebGPU — command ignored");
            }
            case RenderCommand.DrawIndexedIndirect cmd -> {
                log.warn("DrawIndexedIndirect not yet implemented for WebGPU — command ignored");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Render Pass Management
    // ═══════════════════════════════════════════════════════════════════

    private void beginRenderPass() {
        beginRenderPassWithClear(0.0f, 0.0f, 0.0f, 1.0f);
    }

    private void beginRenderPassWithClear(float r, float g, float b, float a) {
        if (!nativeAvailable || commandEncoder == 0 || currentRenderTarget == null) return;

        var rt = renderTargets.get(currentRenderTarget);
        if (rt == null) return;

        // Build color attachments
        var colorAttachments = new WgpuBindings.ColorAttachment[rt.colorTextures().size()];
        for (int i = 0; i < rt.colorTextures().size(); i++) {
            // Use canvas texture view if available and rendering to default RT
            long colorView;
            if (canvasTextureViewOverride != 0 && currentRenderTarget == defaultRenderTarget && i == 0) {
                colorView = canvasTextureViewOverride;
            } else {
                var colorTex = textures.get(rt.colorTextures().get(i));
                colorView = colorTex.view();
            }
            colorAttachments[i] = new WgpuBindings.ColorAttachment(colorView, r, g, b, a);
        }

        // Build depth/stencil attachment
        WgpuBindings.DepthStencilAttachment depthAttachment = null;
        if (rt.depthTexture() != null) {
            var depthTex = textures.get(rt.depthTexture());
            depthAttachment = new WgpuBindings.DepthStencilAttachment(depthTex.view(), 1.0f, 0);
        }

        var desc = new WgpuBindings.RenderPassDescriptor(colorAttachments, depthAttachment);
        renderPassEncoder = gpu.commandEncoderBeginRenderPass(commandEncoder, desc);

        // Set default viewport
        gpu.renderPassSetViewport(renderPassEncoder, 0, 0, rt.width(), rt.height(), 0.0f, 1.0f);
        gpu.renderPassSetScissorRect(renderPassEncoder, 0, 0, rt.width(), rt.height());

        // Set stencil reference
        if (stencilTestEnabled) {
            gpu.renderPassSetStencilReference(renderPassEncoder, stencilRef);
        }

        // Re-bind current pipeline if any
        if (currentPipeline != null) {
            var p = pipelines.get(currentPipeline);
            if (p != null && p.nativePipeline() != 0) {
                gpu.renderPassSetPipeline(renderPassEncoder, p.nativePipeline());
            }
        }
    }

    private void endCurrentRenderPass() {
        if (renderPassEncoder != 0 && nativeAvailable) {
            gpu.renderPassEnd(renderPassEncoder);
            gpu.renderPassRelease(renderPassEncoder);
            renderPassEncoder = 0;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Bind Group Management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * If render state has changed since the last draw, finds or creates the
     * appropriate pipeline variant and rebinds it on the render pass encoder.
     */
    private void flushPipelineVariant() {
        if (!pipelineStateDirty || currentPipeline == null) return;
        pipelineStateDirty = false;

        long variant = getOrCreateVariant(currentPipeline);
        if (variant != 0 && variant != currentActivePipeline) {
            gpu.renderPassSetPipeline(renderPassEncoder, variant);
            currentActivePipeline = variant;
            bindingsDirty = true; // rebind after pipeline change
        }
    }

    private void flushBindings() {
        if (!bindingsDirty || renderPassEncoder == 0 || !nativeAvailable) return;
        bindingsDirty = false;

        if (currentPipeline == null) return;
        var pipelineInfo = pipelines.get(currentPipeline);
        if (pipelineInfo == null || pipelineInfo.nativePipeline() == 0) return;

        var bindingTypes = pipelineInfo.bindingTypes();
        if (bindingTypes.isEmpty()) return;

        // Release previous bind group
        if (currentBindGroup != 0) {
            gpu.bindGroupRelease(currentBindGroup);
            currentBindGroup = 0;
        }

        var entries = new WgpuBindings.BindGroupEntry[bindingTypes.size()];
        int i = 0;
        // Textures/samplers use sequential indices — they're bound to units 0,1,2
        // via rec.bindTexture(unit, tex) but WGSL bindings may differ.
        // UBOs use direct binding-index lookup — they're bound via
        // rec.bindUniformBuffer(binding, buf) with shader-specific binding.
        int texIdx = 0;
        int smpIdx = 0;
        for (var entry : bindingTypes.entrySet()) {
            int binding = entry.getKey();
            var type = entry.getValue();

            switch (type) {
                case UNIFORM -> {
                    if (binding < boundUbos.length && boundUbos[binding] != null) {
                        var buf = buffers.get(boundUbos[binding]);
                        if (buf != null && buf.nativeBuffer() != 0) {
                            entries[i] = new WgpuBindings.BindGroupEntry(binding,
                                    WgpuBindings.BindingResourceType.BUFFER,
                                    buf.nativeBuffer(), 0, buf.size());
                        }
                    }
                }
                case TEXTURE -> {
                    // Use sequential index — textures are bound to units 0,1,2...
                    if (texIdx < boundTextures.length && boundTextures[texIdx] != null) {
                        var tex = textures.get(boundTextures[texIdx]);
                        if (tex != null && tex.view() != 0) {
                            entries[i] = new WgpuBindings.BindGroupEntry(binding,
                                    WgpuBindings.BindingResourceType.TEXTURE_VIEW,
                                    tex.view(), 0, 0);
                        }
                    }
                    texIdx++;
                }
                case SAMPLER -> {
                    // Use sequential index — samplers are bound to units 0,1,2...
                    if (smpIdx < boundSamplers.length && boundSamplers[smpIdx] != null) {
                        var smp = samplers.get(boundSamplers[smpIdx]);
                        if (smp != null && smp.nativeSampler() != 0) {
                            entries[i] = new WgpuBindings.BindGroupEntry(binding,
                                    WgpuBindings.BindingResourceType.SAMPLER,
                                    smp.nativeSampler(), 0, 0);
                        }
                    }
                    smpIdx++;
                }
                case STORAGE -> {
                    if (binding < boundSsbos.length && boundSsbos[binding] != null) {
                        var buf = buffers.get(boundSsbos[binding]);
                        if (buf != null && buf.nativeBuffer() != 0) {
                            entries[i] = new WgpuBindings.BindGroupEntry(binding,
                                    WgpuBindings.BindingResourceType.BUFFER,
                                    buf.nativeBuffer(), 0, buf.size());
                        }
                    }
                }
            }

            // Skip if resource not bound — wgpu-native panics on null handles
            if (entries[i] == null) {
                log.warn("Bind group entry {} ({}) has no bound resource — skipping draw", binding, type);
                bindGroupValid = false;
                return;
            }
            i++;
        }

        currentBindGroup = gpu.deviceCreateBindGroup(wgpuDevice, pipelineInfo.bindGroupLayout(), entries);
        gpu.renderPassSetBindGroup(renderPassEncoder, 0, currentBindGroup);
        bindGroupValid = true;

        // Set stencil reference if needed
        if (stencilTestEnabled) {
            gpu.renderPassSetStencilReference(renderPassEncoder, stencilRef);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Readback
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public byte[] readFramebuffer(int width, int height) {
        var readTarget = currentRenderTarget != null ? currentRenderTarget : defaultRenderTarget;
        if (!nativeAvailable || readTarget == null) return null;

        var rt = renderTargets.get(readTarget);
        if (rt == null || rt.colorTextures().isEmpty()) return null;

        var colorTex = textures.get(rt.colorTextures().getFirst());
        if (colorTex == null || colorTex.nativeTexture() == 0) return null;

        // Create staging buffer
        int bytesPerRow = alignTo256(width * 4);
        long bufferSize = (long) bytesPerRow * height;

        int stagingUsage = WgpuBindings.BUFFER_USAGE_MAP_READ | WgpuBindings.BUFFER_USAGE_COPY_DST;
        long stagingBuf = gpu.deviceCreateBuffer(wgpuDevice, bufferSize, stagingUsage);

        // Create command encoder for copy
        long encoder = gpu.deviceCreateCommandEncoder(wgpuDevice);

        gpu.commandEncoderCopyTextureToBuffer(encoder, colorTex.nativeTexture(), stagingBuf,
                width, height, bytesPerRow, height);

        // Finish and submit
        long cmdBuf = gpu.commandEncoderFinish(encoder);
        gpu.queueSubmit(wgpuQueue, cmdBuf);
        gpu.commandBufferRelease(cmdBuf);
        gpu.commandEncoderRelease(encoder);

        // Map the staging buffer synchronously
        gpu.bufferMapReadSync(wgpuInstance, stagingBuf, (int) bufferSize, 1000);

        // Read back via getConstMappedRange
        ByteBuffer mapped = ByteBuffer.allocateDirect((int) bufferSize);
        gpu.bufferGetConstMappedRange(stagingBuf, 0, (int) bufferSize, mapped);

        // Copy to byte array, handling row alignment
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            int srcOffset = y * bytesPerRow;
            int dstOffset = y * width * 4;
            for (int x = 0; x < width * 4; x++) {
                rgba[dstOffset + x] = mapped.get(srcOffset + x);
            }
        }

        gpu.bufferUnmap(stagingBuf);
        gpu.bufferRelease(stagingBuf);

        return rgba;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capabilities
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public <T> T queryCapability(DeviceCapability<T> capability) {
        return capabilities.query(capability);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void close() {
        // Destroy pipeline variants
        pipelineVariants.clear(p -> { if (nativeAvailable) { gpu.renderPipelineRelease(p); } });

        if (defaultRenderTarget != null) {
            destroyRenderTarget(defaultRenderTarget);
            defaultRenderTarget = null;
        }
        if (nativeAvailable) {
            if (wgpuDevice != 0) {
                gpu.deviceRelease(wgpuDevice);
                wgpuDevice = 0;
            }
            if (wgpuAdapter != 0) {
                gpu.adapterRelease(wgpuAdapter);
                wgpuAdapter = 0;
            }
            if (wgpuInstance != 0) {
                gpu.instanceRelease(wgpuInstance);
                wgpuInstance = 0;
            }
            log.info("WebGPU device released");
        }
        log.info("WgpuRenderDevice closed");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Format Mapping Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static int mapBufferUsage(BufferUsage usage) {
        if (usage == BufferUsage.VERTEX) return WgpuBindings.BUFFER_USAGE_VERTEX;
        if (usage == BufferUsage.INDEX) return WgpuBindings.BUFFER_USAGE_INDEX;
        if (usage == BufferUsage.UNIFORM) return WgpuBindings.BUFFER_USAGE_UNIFORM;
        if (usage == BufferUsage.STORAGE) return WgpuBindings.BUFFER_USAGE_STORAGE;
        return WgpuBindings.BUFFER_USAGE_VERTEX;
    }

    static int mapTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return WgpuBindings.TEXTURE_FORMAT_RGBA8_UNORM;
        if (format == TextureFormat.BGRA8) return WgpuBindings.TEXTURE_FORMAT_BGRA8_UNORM;
        if (format == TextureFormat.DEPTH32F) return WgpuBindings.TEXTURE_FORMAT_DEPTH32_FLOAT;
        if (format == TextureFormat.DEPTH24) return WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS;
        if (format == TextureFormat.DEPTH24_STENCIL8) return WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8;
        if (format == TextureFormat.RGB8) {
            log.warn("WebGPU does not support RGB8 — mapping to RGBA8. Pixel data must be 4 bytes/pixel.");
            return WgpuBindings.TEXTURE_FORMAT_RGBA8_UNORM;
        }
        if (format == TextureFormat.R8) return WgpuBindings.TEXTURE_FORMAT_R8_UNORM;
        if (format == TextureFormat.RGBA16F) return WgpuBindings.TEXTURE_FORMAT_RGBA16_FLOAT;
        if (format == TextureFormat.RGBA32F) return WgpuBindings.TEXTURE_FORMAT_RGBA32_FLOAT;
        if (format == TextureFormat.RG16F) return WgpuBindings.TEXTURE_FORMAT_RG16_FLOAT;
        if (format == TextureFormat.RG32F) return WgpuBindings.TEXTURE_FORMAT_RG32_FLOAT;
        if (format == TextureFormat.R16F) return WgpuBindings.TEXTURE_FORMAT_R16_FLOAT;
        if (format == TextureFormat.R32F) return WgpuBindings.TEXTURE_FORMAT_R32_FLOAT;
        if (format == TextureFormat.R32UI) return WgpuBindings.TEXTURE_FORMAT_R32_UINT;
        if (format == TextureFormat.R32I) return WgpuBindings.TEXTURE_FORMAT_R32_SINT;
        if (format == TextureFormat.DEPTH32F_STENCIL8) {
            log.warn("WebGPU does not support DEPTH32F_STENCIL8 — falling back to DEPTH24_PLUS_STENCIL8");
            return WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8;
        }
        log.warn("Unsupported texture format '{}' on WebGPU — falling back to RGBA8_UNORM", format.name());
        return WgpuBindings.TEXTURE_FORMAT_RGBA8_UNORM;
    }

    private static int mapVertexFormat(VertexAttribute attr) {
        if (attr.componentType() == ComponentType.FLOAT) {
            return switch (attr.componentCount()) {
                case 1 -> WgpuBindings.VERTEX_FORMAT_FLOAT32;
                case 2 -> WgpuBindings.VERTEX_FORMAT_FLOAT32X2;
                case 3 -> WgpuBindings.VERTEX_FORMAT_FLOAT32X3;
                case 4 -> WgpuBindings.VERTEX_FORMAT_FLOAT32X4;
                default -> WgpuBindings.VERTEX_FORMAT_FLOAT32X4;
            };
        }
        if (attr.componentType() == ComponentType.UNSIGNED_BYTE || attr.componentType() == ComponentType.BYTE) {
            if (attr.componentCount() == 4 && attr.normalized()) {
                return WgpuBindings.VERTEX_FORMAT_UNORM8X4;
            }
        }
        log.warn("Unsupported vertex attribute type {} x{} on WebGPU — falling back to FLOAT32X4",
                attr.componentType().name(), attr.componentCount());
        return WgpuBindings.VERTEX_FORMAT_FLOAT32X4;
    }

    private static int mapFilterMode(FilterMode mode) {
        if (mode == FilterMode.NEAREST || mode == FilterMode.NEAREST_MIPMAP_NEAREST
                || mode == FilterMode.NEAREST_MIPMAP_LINEAR) {
            return WgpuBindings.FILTER_MODE_NEAREST;
        }
        return WgpuBindings.FILTER_MODE_LINEAR;
    }

    private static int mapMipmapFilterMode(FilterMode mode) {
        if (mode == FilterMode.LINEAR_MIPMAP_LINEAR || mode == FilterMode.NEAREST_MIPMAP_LINEAR) {
            return WgpuBindings.MIPMAP_FILTER_MODE_LINEAR;
        }
        return WgpuBindings.MIPMAP_FILTER_MODE_NEAREST;
    }

    private static int mapWrapMode(WrapMode mode) {
        if (mode == WrapMode.REPEAT) return WgpuBindings.ADDRESS_MODE_REPEAT;
        if (mode == WrapMode.CLAMP_TO_EDGE) return WgpuBindings.ADDRESS_MODE_CLAMP_TO_EDGE;
        if (mode == WrapMode.MIRRORED_REPEAT) return WgpuBindings.ADDRESS_MODE_MIRROR_REPEAT;
        return WgpuBindings.ADDRESS_MODE_REPEAT;
    }

    private static int mapCompareFunction(dev.engine.graphics.sampler.CompareFunc func) {
        return switch (func.name()) {
            case "NEVER" -> WgpuBindings.COMPARE_NEVER;
            case "LESS" -> WgpuBindings.COMPARE_LESS;
            case "EQUAL" -> WgpuBindings.COMPARE_EQUAL;
            case "LESS_EQUAL" -> WgpuBindings.COMPARE_LESS_EQUAL;
            case "GREATER" -> WgpuBindings.COMPARE_GREATER;
            case "NOT_EQUAL" -> WgpuBindings.COMPARE_NOT_EQUAL;
            case "GREATER_EQUAL" -> WgpuBindings.COMPARE_GREATER_EQUAL;
            case "ALWAYS" -> WgpuBindings.COMPARE_ALWAYS;
            default -> WgpuBindings.COMPARE_LESS;
        };
    }

    private static int mapCompareFunc(CompareFunc func) {
        if (func == CompareFunc.LESS) return WgpuBindings.COMPARE_LESS;
        if (func == CompareFunc.LEQUAL) return WgpuBindings.COMPARE_LESS_EQUAL;
        if (func == CompareFunc.GREATER) return WgpuBindings.COMPARE_GREATER;
        if (func == CompareFunc.GEQUAL) return WgpuBindings.COMPARE_GREATER_EQUAL;
        if (func == CompareFunc.EQUAL) return WgpuBindings.COMPARE_EQUAL;
        if (func == CompareFunc.NOT_EQUAL) return WgpuBindings.COMPARE_NOT_EQUAL;
        if (func == CompareFunc.ALWAYS) return WgpuBindings.COMPARE_ALWAYS;
        if (func == CompareFunc.NEVER) return WgpuBindings.COMPARE_NEVER;
        return WgpuBindings.COMPARE_LESS;
    }

    private static int mapStencilOp(StencilOp op) {
        if (op == StencilOp.KEEP) return WgpuBindings.STENCIL_OP_KEEP;
        if (op == StencilOp.ZERO) return WgpuBindings.STENCIL_OP_ZERO;
        if (op == StencilOp.REPLACE) return WgpuBindings.STENCIL_OP_REPLACE;
        if (op == StencilOp.INCR) return WgpuBindings.STENCIL_OP_INCREMENT_CLAMP;
        if (op == StencilOp.DECR) return WgpuBindings.STENCIL_OP_DECREMENT_CLAMP;
        if (op == StencilOp.INVERT) return WgpuBindings.STENCIL_OP_INVERT;
        if (op == StencilOp.INCR_WRAP) return WgpuBindings.STENCIL_OP_INCREMENT_WRAP;
        if (op == StencilOp.DECR_WRAP) return WgpuBindings.STENCIL_OP_DECREMENT_WRAP;
        return WgpuBindings.STENCIL_OP_KEEP;
    }

    private static int bytesPerPixel(TextureFormat format) {
        if (format == TextureFormat.RGBA8 || format == TextureFormat.BGRA8
                || format == TextureFormat.DEPTH32F || format == TextureFormat.DEPTH24_STENCIL8) return 4;
        if (format == TextureFormat.RGB8) return 3;
        if (format == TextureFormat.R8) return 1;
        if (format == TextureFormat.RGBA16F) return 8;
        if (format == TextureFormat.RGBA32F) return 16;
        if (format == TextureFormat.RG16F) return 4;
        if (format == TextureFormat.RG32F) return 8;
        if (format == TextureFormat.R16F) return 2;
        if (format == TextureFormat.R32F || format == TextureFormat.R32UI || format == TextureFormat.R32I) return 4;
        return 4;
    }

    /**
     * Sets a canvas texture view to use as the color attachment for the default render target.
     * Call before renderFrame() each frame. Set to 0 to use the internal offscreen RT.
     */
    public void setCanvasTextureView(long textureView) {
        this.canvasTextureViewOverride = textureView;
    }

    private static int alignTo256(int value) {
        return (value + 255) & ~255;
    }

    /**
     * Default NativeMemory factory using ByteBuffer (works on all platforms).
     */
    private static NativeMemory createDefaultMemory(int size) {
        var bb = java.nio.ByteBuffer.allocate(size).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        return new NativeMemory() {
            @Override public void putFloat(long offset, float value) { bb.putFloat((int) offset, value); }
            @Override public void putInt(long offset, int value) { bb.putInt((int) offset, value); }
            @Override public void putByte(long offset, byte value) { bb.put((int) offset, value); }
            @Override public void putShort(long offset, short value) { bb.putShort((int) offset, value); }
            @Override public void putLong(long offset, long value) { bb.putLong((int) offset, value); }
            @Override public void putDouble(long offset, double value) { bb.putDouble((int) offset, value); }
            @Override public float getFloat(long offset) { return bb.getFloat((int) offset); }
            @Override public int getInt(long offset) { return bb.getInt((int) offset); }
            @Override public byte getByte(long offset) { return bb.get((int) offset); }
            @Override public short getShort(long offset) { return bb.getShort((int) offset); }
            @Override public long getLong(long offset) { return bb.getLong((int) offset); }
            @Override public double getDouble(long offset) { return bb.getDouble((int) offset); }
            @Override public long size() { return size; }
            @Override public void putFloatArray(long offset, float[] data) { for (int i = 0; i < data.length; i++) bb.putFloat((int) offset + i * 4, data[i]); }
            @Override public void putIntArray(long offset, int[] data) { for (int i = 0; i < data.length; i++) bb.putInt((int) offset + i * 4, data[i]); }
            @Override public NativeMemory slice(long offset, long length) { return createDefaultMemory((int) length); }
            @Override public void copyFrom(NativeMemory src) { for (long i = 0; i < Math.min(size, src.size()); i++) bb.put((int) i, src.getByte(i)); }
        };
    }
}

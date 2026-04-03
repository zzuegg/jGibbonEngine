package dev.engine.graphics.webgpu;

import com.github.xpenatan.webgpu.*;
import dev.engine.core.handle.Handle;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.BufferResource;
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * WebGPU render device backed by jWebGPU (com.github.xpenatan.jWebGPU).
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

    private record WgpuBuffer(WGPUBuffer nativeBuffer, long size) {}
    private record WgpuTexture(WGPUTexture nativeTexture, WGPUTextureView view,
                               TextureDescriptor desc, WGPUTextureFormat wgpuFormat) {}
    private record WgpuVertexInput(VertexFormat format) {}
    private record WgpuSampler(WGPUSampler nativeSampler, SamplerDescriptor desc) {}
    private enum WgpuBindingType { UNIFORM, TEXTURE, SAMPLER, STORAGE }
    private record WgpuPipeline(WGPURenderPipeline nativePipeline,
                                WGPUBindGroupLayout bindGroupLayout,
                                Map<Integer, WgpuBindingType> bindingTypes) {}
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

    // ── Native state ──────────────────────────────────────────────────

    private final boolean nativeAvailable;
    private WGPUInstance wgpuInstance;
    private WGPUAdapter wgpuAdapter;
    private WGPUDevice wgpuDevice;
    private WGPUQueue wgpuQueue;

    // ── Per-frame state ───────────────────────────────────────────────

    private WGPUCommandEncoder commandEncoder;
    private WGPURenderPassEncoder renderPassEncoder;

    // ── Current render target tracking ────────────────────────────────

    private Handle<RenderTargetResource> currentRenderTarget;

    // ── Default offscreen render target (lazy, resized on viewport) ──

    private Handle<RenderTargetResource> defaultRenderTarget;
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
    private WGPUBindGroup currentBindGroup;

    // ── Render state tracking (for pipeline-baked state in WebGPU) ────

    private boolean depthTestEnabled = true;
    private boolean depthWriteEnabled = true;
    private boolean blendEnabled = false;
    private WGPUCullMode wgpuCullMode = WGPUCullMode.Back;
    private WGPUFrontFace wgpuFrontFace = WGPUFrontFace.CCW;
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

    // ── Constructor ───────────────────────────────────────────────────

    /**
     * Creates a WebGPU render device in offscreen mode.
     *
     * @param window the GLFW window handle (kept for API compatibility)
     */
    public WgpuRenderDevice(WindowHandle window) {
        boolean available = false;
        try {
            initJWebGPU();
            available = true;
        } catch (Throwable t) {
            log.warn("jWebGPU native library not available: {}", t.getMessage());
        }
        this.nativeAvailable = available;

        if (nativeAvailable) {
            wgpuInstance = WGPU.setupInstance();

            // Request adapter synchronously
            var adapterHolder = new WGPUAdapter[1];
            var adapterOpts = WGPURequestAdapterOptions.obtain();
            wgpuInstance.requestAdapter(adapterOpts, WGPUCallbackMode.AllowSpontaneous,
                    new WGPURequestAdapterCallback() {
                        @Override
                        protected void onCallback(WGPURequestAdapterStatus status,
                                                  WGPUAdapter adapter, String message) {
                            adapterHolder[0] = adapter;
                        }
                    });
            wgpuInstance.processEvents();
            wgpuAdapter = adapterHolder[0];

            if (wgpuAdapter == null) {
                throw new RuntimeException("Failed to get WebGPU adapter");
            }

            // Request device synchronously
            var deviceHolder = new WGPUDevice[1];
            var deviceDesc = WGPUDeviceDescriptor.obtain();
            wgpuAdapter.requestDevice(deviceDesc, WGPUCallbackMode.AllowSpontaneous,
                    new WGPURequestDeviceCallback() {
                        @Override
                        protected void onCallback(WGPURequestDeviceStatus status,
                                                  WGPUDevice device, String message) {
                            deviceHolder[0] = device;
                        }
                    },
                    new WGPUUncapturedErrorCallback() {
                        @Override
                        protected void onCallback(WGPUErrorType type, String message) {
                            log.error("WebGPU error ({}): {}", type, message);
                        }
                    });
            wgpuInstance.processEvents();
            wgpuDevice = deviceHolder[0];

            if (wgpuDevice == null) {
                throw new RuntimeException("Failed to get WebGPU device");
            }

            wgpuQueue = wgpuDevice.getQueue();
            log.info("WebGPU device created (offscreen mode) via jWebGPU");
        } else {
            wgpuInstance = null;
            wgpuAdapter = null;
            wgpuDevice = null;
            wgpuQueue = null;
            log.warn("jWebGPU not available; WebGPU device running without native backend");
        }
    }

    private static final AtomicBoolean jwebgpuInitialized = new AtomicBoolean(false);

    private static void initJWebGPU() {
        if (jwebgpuInitialized.compareAndSet(false, true)) {
            JWebGPULoader.init((result, error) -> {
                if (!result) {
                    throw new RuntimeException("Failed to load jWebGPU native library",
                            error);
                }
            });
        }
    }

    /**
     * Returns true if the native WebGPU backend is available.
     */
    public static boolean isAvailable() {
        try {
            initJWebGPU();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Buffer
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        if (nativeAvailable) {
            var bufDesc = WGPUBufferDescriptor.obtain();
            bufDesc.setSize(descriptor.size());
            // Combine usage flags via setValue on CUSTOM
            int usage = mapBufferUsage(descriptor.usage()).getValue()
                    | WGPUBufferUsage.CopyDst.getValue();
            bufDesc.setUsage(WGPUBufferUsage.CUSTOM.setValue(usage));

            var buf = wgpuDevice.createBuffer(bufDesc);
            return buffers.register(new WgpuBuffer(buf, descriptor.size()));
        }
        return buffers.register(new WgpuBuffer(null, descriptor.size()));
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!buffers.isValid(buffer)) return;
        var buf = buffers.remove(buffer);
        if (buf != null && nativeAvailable && buf.nativeBuffer() != null) {
            buf.nativeBuffer().release();
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
        var arena = Arena.ofConfined();
        var segment = arena.allocate(length);
        return new BufferWriter() {
            @Override
            public MemorySegment segment() { return segment; }

            @Override
            public void close() {
                if (nativeAvailable && buf != null && buf.nativeBuffer() != null) {
                    // Copy from MemorySegment to a direct ByteBuffer for jWebGPU
                    ByteBuffer bb = ByteBuffer.allocateDirect((int) length);
                    for (int i = 0; i < length; i++) {
                        bb.put(segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i));
                    }
                    bb.flip();
                    wgpuQueue.writeBuffer(buf.nativeBuffer(), (int) offset, bb, (int) length);
                }
                arena.close();
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // Texture
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        WGPUTextureFormat wgpuFormat = mapTextureFormat(descriptor.format());
        int usage = WGPUTextureUsage.TextureBinding.getValue()
                | WGPUTextureUsage.CopyDst.getValue()
                | WGPUTextureUsage.CopySrc.getValue();

        if (nativeAvailable) {
            var tex = createWgpuTexture(descriptor, wgpuFormat, usage);
            var view = createDefaultView(tex, descriptor, wgpuFormat);
            return textures.register(new WgpuTexture(tex, view, descriptor, wgpuFormat));
        }
        return textures.register(new WgpuTexture(null, null, descriptor, wgpuFormat));
    }

    private Handle<TextureResource> createTextureForRenderTarget(TextureDescriptor descriptor, int extraUsage) {
        WGPUTextureFormat wgpuFormat = mapTextureFormat(descriptor.format());
        int usage = WGPUTextureUsage.CopySrc.getValue() | extraUsage;

        if (nativeAvailable) {
            var tex = createWgpuTexture(descriptor, wgpuFormat, usage);
            var view = createDefaultView(tex, descriptor, wgpuFormat);
            return textures.register(new WgpuTexture(tex, view, descriptor, wgpuFormat));
        }
        return textures.register(new WgpuTexture(null, null, descriptor, wgpuFormat));
    }

    private WGPUTextureView createDefaultView(WGPUTexture tex, TextureDescriptor descriptor,
                                               WGPUTextureFormat wgpuFormat) {
        var viewDesc = WGPUTextureViewDescriptor.obtain();
        viewDesc.setFormat(wgpuFormat);
        viewDesc.setMipLevelCount(1);
        viewDesc.setBaseMipLevel(0);
        viewDesc.setBaseArrayLayer(0);

        boolean isDepth = descriptor.format() == TextureFormat.DEPTH32F
                || descriptor.format() == TextureFormat.DEPTH24
                || descriptor.format() == TextureFormat.DEPTH24_STENCIL8
                || descriptor.format() == TextureFormat.DEPTH32F_STENCIL8;

        if (descriptor.type() == TextureType.TEXTURE_3D) {
            viewDesc.setDimension(WGPUTextureViewDimension._3D);
            viewDesc.setArrayLayerCount(1);
        } else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) {
            viewDesc.setDimension(WGPUTextureViewDimension._2DArray);
            viewDesc.setArrayLayerCount(descriptor.layers());
        } else if (descriptor.type() == TextureType.TEXTURE_CUBE) {
            viewDesc.setDimension(WGPUTextureViewDimension.Cube);
            viewDesc.setArrayLayerCount(6);
        } else {
            viewDesc.setDimension(WGPUTextureViewDimension._2D);
            viewDesc.setArrayLayerCount(1);
        }

        if (isDepth) {
            viewDesc.setAspect(WGPUTextureAspect.All);
        } else {
            viewDesc.setAspect(WGPUTextureAspect.All);
        }

        var view = new WGPUTextureView();
        tex.createView(viewDesc, view);
        return view;
    }

    private WGPUTexture createWgpuTexture(TextureDescriptor descriptor, WGPUTextureFormat wgpuFormat, int usage) {
        var texDesc = WGPUTextureDescriptor.obtain();
        texDesc.setUsage(WGPUTextureUsage.CUSTOM.setValue(usage));
        texDesc.setDimension(descriptor.type() == TextureType.TEXTURE_3D
                ? WGPUTextureDimension._3D : WGPUTextureDimension._2D);
        texDesc.setFormat(wgpuFormat);
        texDesc.setMipLevelCount(1);
        texDesc.setSampleCount(1);

        var size = texDesc.getSize();
        size.setWidth(descriptor.width());
        size.setHeight(descriptor.height());
        int depthOrLayers = 1;
        if (descriptor.type() == TextureType.TEXTURE_3D) depthOrLayers = descriptor.depth();
        else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) depthOrLayers = descriptor.layers();
        else if (descriptor.type() == TextureType.TEXTURE_CUBE) depthOrLayers = 6;
        size.setDepthOrArrayLayers(depthOrLayers);

        var tex = new WGPUTexture();
        wgpuDevice.createTexture(texDesc, tex);
        return tex;
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        if (!nativeAvailable) return;
        var tex = textures.get(texture);
        if (tex == null || tex.nativeTexture() == null) return;

        var desc = tex.desc();
        int bytesPerRow = desc.width() * bytesPerPixel(desc.format());

        var destination = WGPUTexelCopyTextureInfo.obtain();
        destination.setTexture(tex.nativeTexture());
        destination.setMipLevel(0);
        destination.setAspect(WGPUTextureAspect.All);

        var dataLayout = WGPUTexelCopyBufferLayout.obtain();
        dataLayout.setOffset(0);
        dataLayout.setBytesPerRow(bytesPerRow);
        dataLayout.setRowsPerImage(desc.height());

        var writeSize = WGPUExtent3D.obtain();
        writeSize.setWidth(desc.width());
        writeSize.setHeight(desc.height());
        int depthOrLayers = 1;
        if (desc.type() == TextureType.TEXTURE_3D) depthOrLayers = desc.depth();
        else if (desc.type() == TextureType.TEXTURE_2D_ARRAY) depthOrLayers = desc.layers();
        else if (desc.type() == TextureType.TEXTURE_CUBE) depthOrLayers = 6;
        writeSize.setDepthOrArrayLayers(depthOrLayers);

        // Ensure we have a direct ByteBuffer
        ByteBuffer direct;
        if (pixels.isDirect()) {
            direct = pixels;
        } else {
            direct = ByteBuffer.allocateDirect(pixels.remaining());
            direct.put(pixels.duplicate());
            direct.flip();
        }

        wgpuQueue.writeTexture(destination, direct, direct.remaining(), dataLayout, writeSize);
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!textures.isValid(texture)) return;
        var tex = textures.remove(texture);
        if (tex != null && nativeAvailable) {
            if (tex.view() != null) tex.view().release();
            if (tex.nativeTexture() != null) tex.nativeTexture().release();
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
                    WGPUTextureUsage.RenderAttachment.getValue()
                            | WGPUTextureUsage.TextureBinding.getValue());
            colorTextures.add(texHandle);
        }

        Handle<TextureResource> depthTexture = null;
        if (descriptor.depthFormat() != null) {
            var depthDesc = new TextureDescriptor(descriptor.width(), descriptor.height(),
                    descriptor.depthFormat(), MipMode.NONE);
            depthTexture = createTextureForRenderTarget(depthDesc,
                    WGPUTextureUsage.RenderAttachment.getValue());
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
            var desc = WGPUSamplerDescriptor.obtain();
            desc.setAddressModeU(mapWrapMode(descriptor.wrapS()));
            desc.setAddressModeV(mapWrapMode(descriptor.wrapT()));
            desc.setAddressModeW(mapWrapMode(descriptor.wrapS()));
            desc.setMagFilter(mapFilterMode(descriptor.magFilter()));
            desc.setMinFilter(mapFilterMode(descriptor.minFilter()));
            desc.setMipmapFilter(mapMipmapFilterMode(descriptor.minFilter()));
            desc.setLodMinClamp(0.0f);
            desc.setLodMaxClamp(32.0f);
            desc.setMaxAnisotropy(1);

            var sampler = new WGPUSampler();
            wgpuDevice.createSampler(desc, sampler);
            return samplers.register(new WgpuSampler(sampler, descriptor));
        }
        return samplers.register(new WgpuSampler(null, descriptor));
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplers.isValid(sampler)) return;
        var s = samplers.remove(sampler);
        if (s != null && nativeAvailable && s.nativeSampler() != null) {
            s.nativeSampler().release();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Pipeline
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        if (!nativeAvailable) {
            return pipelines.register(new WgpuPipeline(null, null, Map.of()));
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
        var vertModule = createShaderModule(vertexShader.source());
        WGPUShaderModule fragModule = null;
        if (fragmentShader != null) {
            fragModule = createShaderModule(fragmentShader.source());
        }

        // Build explicit bind group layout from binding types
        var bgLayout = createBindGroupLayout(bindingTypes);

        // Build pipeline layout
        var pipelineLayout = new WGPUPipelineLayout();
        var plDesc = WGPUPipelineLayoutDescriptor.obtain();
        var bgLayouts = WGPUVectorBindGroupLayout.obtain();
        bgLayouts.push_back(bgLayout);
        plDesc.setBindGroupLayouts(bgLayouts);
        wgpuDevice.createPipelineLayout(plDesc, pipelineLayout);

        // Build render pipeline descriptor
        var rpDesc = new WGPURenderPipelineDescriptor();
        rpDesc.setLayout(pipelineLayout);

        // Vertex state
        var vertexState = rpDesc.getVertex();
        vertexState.setModule(vertModule);
        vertexState.setEntryPoint(vertexShader.entryPoint());
        vertexState.setConstants(WGPUVectorConstantEntry.NULL);

        if (descriptor.vertexFormat() != null) {
            var format = descriptor.vertexFormat();
            var attrs = format.attributes();

            var attrVec = WGPUVectorVertexAttribute.obtain();
            for (var attr : attrs) {
                var wgpuAttr = WGPUVertexAttribute.obtain();
                wgpuAttr.setFormat(mapVertexFormat(attr));
                wgpuAttr.setOffset(attr.offset());
                wgpuAttr.setShaderLocation(attr.location());
                attrVec.push_back(wgpuAttr);
            }

            var bufLayout = WGPUVertexBufferLayout.obtain();
            bufLayout.setArrayStride(format.stride());
            bufLayout.setStepMode(WGPUVertexStepMode.Vertex);
            bufLayout.setAttributes(attrVec);

            var bufLayoutVec = WGPUVectorVertexBufferLayout.obtain();
            bufLayoutVec.push_back(bufLayout);
            vertexState.setBuffers(bufLayoutVec);
        } else {
            vertexState.setBuffers(WGPUVectorVertexBufferLayout.NULL);
        }

        // Primitive state
        var primitiveState = rpDesc.getPrimitive();
        primitiveState.setTopology(WGPUPrimitiveTopology.TriangleList);
        primitiveState.setStripIndexFormat(WGPUIndexFormat.Undefined);
        primitiveState.setFrontFace(wgpuFrontFace);
        primitiveState.setCullMode(wgpuCullMode);

        // Depth stencil state
        var depthStencil = WGPUDepthStencilState.obtain();
        depthStencil.setFormat(WGPUTextureFormat.Depth24PlusStencil8);
        depthStencil.setDepthWriteEnabled(depthWriteEnabled ? WGPUOptionalBool.True : WGPUOptionalBool.False);
        depthStencil.setDepthCompare(mapCompareFunc(depthFunc));
        depthStencil.setStencilReadMask(stencilMask);
        depthStencil.setStencilWriteMask(stencilMask);

        // Stencil face states
        var stencilFront = depthStencil.getStencilFront();
        var stencilBack = depthStencil.getStencilBack();
        if (stencilTestEnabled) {
            stencilFront.setCompare(mapCompareFunc(stencilFunc));
            stencilFront.setPassOp(mapStencilOp(stencilPassOp));
            stencilFront.setFailOp(mapStencilOp(stencilFailOp));
            stencilFront.setDepthFailOp(mapStencilOp(stencilDepthFailOp));
            stencilBack.setCompare(mapCompareFunc(stencilFunc));
            stencilBack.setPassOp(mapStencilOp(stencilPassOp));
            stencilBack.setFailOp(mapStencilOp(stencilFailOp));
            stencilBack.setDepthFailOp(mapStencilOp(stencilDepthFailOp));
        } else {
            stencilFront.setCompare(WGPUCompareFunction.Always);
            stencilFront.setPassOp(WGPUStencilOperation.Keep);
            stencilFront.setFailOp(WGPUStencilOperation.Keep);
            stencilFront.setDepthFailOp(WGPUStencilOperation.Keep);
            stencilBack.setCompare(WGPUCompareFunction.Always);
            stencilBack.setPassOp(WGPUStencilOperation.Keep);
            stencilBack.setFailOp(WGPUStencilOperation.Keep);
            stencilBack.setDepthFailOp(WGPUStencilOperation.Keep);
        }

        rpDesc.setDepthStencil(depthStencil);

        // Multisample state
        var multisample = rpDesc.getMultisample();
        multisample.setCount(1);
        multisample.setMask(0xFFFFFFFF);
        multisample.setAlphaToCoverageEnabled(false);

        // Fragment state
        if (fragModule != null) {
            var fragState = WGPUFragmentState.obtain();
            fragState.setNextInChain(WGPUChainedStruct.NULL);
            fragState.setModule(fragModule);
            fragState.setEntryPoint(fragmentShader.entryPoint());
            fragState.setConstants(WGPUVectorConstantEntry.NULL);

            var colorTarget = WGPUColorTargetState.obtain();
            colorTarget.setFormat(WGPUTextureFormat.RGBA8Unorm);
            colorTarget.setWriteMask(WGPUColorWriteMask.All);

            // Blend state
            var blendState = WGPUBlendState.obtain();
            configureBlendState(blendState, currentBlendMode);
            colorTarget.setBlend(blendState);

            var colorTargets = WGPUVectorColorTargetState.obtain();
            colorTargets.push_back(colorTarget);
            fragState.setTargets(colorTargets);
            rpDesc.setFragment(fragState);
        }

        var pipeline = new WGPURenderPipeline();
        wgpuDevice.createRenderPipeline(rpDesc, pipeline);

        // Release shader modules
        vertModule.release();
        if (fragModule != null) fragModule.release();

        // Release pipeline layout (pipeline retains reference)
        pipelineLayout.release();

        log.debug("Pipeline created with {} bind group bindings: {}", bindingTypes.size(), bindingTypes);
        return pipelines.register(new WgpuPipeline(pipeline, bgLayout, bindingTypes));
    }

    private WGPUShaderModule createShaderModule(String wgslSource) {
        if (wgslSource == null || wgslSource.isEmpty()) {
            throw new ShaderCompilationException("WGSL shader source is null or empty");
        }
        var wgslDesc = WGPUShaderSourceWGSL.obtain();
        wgslDesc.setCode(wgslSource);
        wgslDesc.getChain().setSType(WGPUSType.ShaderSourceWGSL);

        var shaderDesc = WGPUShaderModuleDescriptor.obtain();
        shaderDesc.setNextInChain(wgslDesc.getChain());

        var module = new WGPUShaderModule();
        wgpuDevice.createShaderModule(shaderDesc, module);

        if (!module.isValid()) {
            throw new ShaderCompilationException("WebGPU shader module creation failed");
        }
        return module;
    }

    private WGPUBindGroupLayout createBindGroupLayout(Map<Integer, WgpuBindingType> bindingTypes) {
        var entries = WGPUVectorBindGroupLayoutEntry.obtain();

        int visibility = WGPUShaderStage.Vertex.getValue() | WGPUShaderStage.Fragment.getValue();

        for (var entry : bindingTypes.entrySet()) {
            var layoutEntry = WGPUBindGroupLayoutEntry.obtain();
            layoutEntry.setBinding(entry.getKey());
            layoutEntry.setVisibility(WGPUShaderStage.CUSTOM.setValue(visibility));

            switch (entry.getValue()) {
                case UNIFORM -> {
                    var bufLayout = WGPUBufferBindingLayout.obtain();
                    bufLayout.setType(WGPUBufferBindingType.Uniform);
                    bufLayout.setMinBindingSize(0);
                    layoutEntry.setBuffer(bufLayout);
                }
                case STORAGE -> {
                    var bufLayout = WGPUBufferBindingLayout.obtain();
                    bufLayout.setType(WGPUBufferBindingType.ReadOnlyStorage);
                    bufLayout.setMinBindingSize(0);
                    layoutEntry.setBuffer(bufLayout);
                }
                case TEXTURE -> {
                    var texLayout = WGPUTextureBindingLayout.obtain();
                    texLayout.setSampleType(WGPUTextureSampleType.Float);
                    texLayout.setViewDimension(WGPUTextureViewDimension._2D);
                    layoutEntry.setTexture(texLayout);
                }
                case SAMPLER -> {
                    var smpLayout = WGPUSamplerBindingLayout.obtain();
                    smpLayout.setType(WGPUSamplerBindingType.Filtering);
                    layoutEntry.setSampler(smpLayout);
                }
            }

            entries.push_back(layoutEntry);
        }

        var bgLayoutDesc = WGPUBindGroupLayoutDescriptor.obtain();
        bgLayoutDesc.setEntries(entries);

        var bgLayout = new WGPUBindGroupLayout();
        wgpuDevice.createBindGroupLayout(bgLayoutDesc, bgLayout);
        return bgLayout;
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

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelines.isValid(pipeline)) return;
        var p = pipelines.remove(pipeline);
        if (p != null && nativeAvailable) {
            if (p.nativePipeline() != null) p.nativePipeline().release();
            if (p.bindGroupLayout() != null) p.bindGroupLayout().release();
        }
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
        defaultRenderTarget = createRenderTarget(
                RenderTargetDescriptor.colorDepth(width, height,
                        TextureFormat.RGBA8, TextureFormat.DEPTH24_STENCIL8));
        defaultRtWidth = width;
        defaultRtHeight = height;
        log.debug("Created default render target {}x{}", width, height);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Frame Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void beginFrame() {
        frameCounter.incrementAndGet();
        if (!nativeAvailable) return;

        // Create command encoder
        var encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        commandEncoder = WGPUCommandEncoder.obtain();
        wgpuDevice.createCommandEncoder(encoderDesc, commandEncoder);

        // Reset render pass
        renderPassEncoder = null;
        currentRenderTarget = defaultRenderTarget;
        currentPipeline = null;
        bindingsDirty = true;

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
        var cmdBufDesc = WGPUCommandBufferDescriptor.obtain();
        var commandBuffer = WGPUCommandBuffer.obtain();
        commandEncoder.finish(cmdBufDesc, commandBuffer);

        wgpuQueue.submit(commandBuffer);

        // Release
        commandBuffer.release();
        commandEncoder.release();
        commandEncoder = null;

        if (currentBindGroup != null) {
            currentBindGroup.release();
            currentBindGroup = null;
        }
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
                if (renderPassEncoder != null && nativeAvailable) {
                    renderPassEncoder.setViewport(
                            (float) cmd.x(), (float) cmd.y(),
                            (float) cmd.width(), (float) cmd.height(), 0.0f, 1.0f);
                }
            }
            case RenderCommand.Scissor cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    renderPassEncoder.setScissorRect(
                            cmd.x(), cmd.y(), cmd.width(), cmd.height());
                }
            }
            case RenderCommand.BindPipeline cmd -> {
                currentPipeline = cmd.pipeline();
                if (renderPassEncoder != null && nativeAvailable) {
                    var p = pipelines.get(cmd.pipeline());
                    if (p != null && p.nativePipeline() != null) {
                        renderPassEncoder.setPipeline(p.nativePipeline());
                    }
                }
                bindingsDirty = true;
            }
            case RenderCommand.BindVertexBuffer cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    var buf = buffers.get(cmd.buffer());
                    if (buf != null && buf.nativeBuffer() != null) {
                        renderPassEncoder.setVertexBuffer(
                                0, buf.nativeBuffer(), 0, (int) buf.size());
                    }
                }
            }
            case RenderCommand.BindIndexBuffer cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    var buf = buffers.get(cmd.buffer());
                    if (buf != null && buf.nativeBuffer() != null) {
                        renderPassEncoder.setIndexBuffer(
                                buf.nativeBuffer(), WGPUIndexFormat.Uint32, 0, (int) buf.size());
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
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    renderPassEncoder.draw(cmd.vertexCount(), 1, cmd.firstVertex(), 0);
                }
            }
            case RenderCommand.DrawIndexed cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    renderPassEncoder.drawIndexed(cmd.indexCount(), 1, cmd.firstIndex(), 0, 0);
                }
            }
            case RenderCommand.DrawInstanced cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    renderPassEncoder.draw(cmd.vertexCount(), cmd.instanceCount(),
                            cmd.firstVertex(), cmd.firstInstance());
                }
            }
            case RenderCommand.DrawIndexedInstanced cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    renderPassEncoder.drawIndexed(cmd.indexCount(), cmd.instanceCount(),
                            cmd.firstIndex(), 0, cmd.firstInstance());
                }
            }
            case RenderCommand.SetDepthTest cmd -> {
                depthTestEnabled = cmd.enabled();
            }
            case RenderCommand.SetBlending cmd -> {
                blendEnabled = cmd.enabled();
                currentBlendMode = cmd.enabled() ? BlendMode.ALPHA : BlendMode.NONE;
            }
            case RenderCommand.SetCullFace cmd -> {
                wgpuCullMode = cmd.enabled() ? WGPUCullMode.Back : WGPUCullMode.None;
            }
            case RenderCommand.SetWireframe cmd -> {
                // WebGPU does not support wireframe natively
            }
            case RenderCommand.SetRenderState cmd -> {
                var props = cmd.properties();
                if (props.contains(RenderState.DEPTH_TEST)) depthTestEnabled = props.get(RenderState.DEPTH_TEST);
                if (props.contains(RenderState.DEPTH_WRITE)) depthWriteEnabled = props.get(RenderState.DEPTH_WRITE);
                if (props.contains(RenderState.DEPTH_FUNC)) depthFunc = props.get(RenderState.DEPTH_FUNC);
                if (props.contains(RenderState.CULL_MODE)) {
                    var mode = props.get(RenderState.CULL_MODE);
                    if (mode == CullMode.NONE) wgpuCullMode = WGPUCullMode.None;
                    else if (mode == CullMode.FRONT) wgpuCullMode = WGPUCullMode.Front;
                    else wgpuCullMode = WGPUCullMode.Back;
                }
                if (props.contains(RenderState.FRONT_FACE)) {
                    wgpuFrontFace = props.get(RenderState.FRONT_FACE) == FrontFace.CCW
                            ? WGPUFrontFace.CCW : WGPUFrontFace.CW;
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
            }
            case RenderCommand.PushConstants cmd -> {
                log.trace("PushConstants not yet implemented for WebGPU");
            }
            case RenderCommand.BindComputePipeline cmd -> {
                log.trace("BindComputePipeline not yet implemented for WebGPU");
            }
            case RenderCommand.Dispatch cmd -> {
                log.trace("Dispatch not yet implemented for WebGPU");
            }
            case RenderCommand.BindImage cmd -> {
                log.trace("BindImage not yet implemented for WebGPU");
            }
            case RenderCommand.MemoryBarrier cmd -> {
                // WebGPU handles barriers implicitly
            }
            case RenderCommand.CopyBuffer cmd -> {
                if (nativeAvailable && commandEncoder != null) {
                    var src = buffers.get(cmd.src());
                    var dst = buffers.get(cmd.dst());
                    if (src != null && dst != null
                            && src.nativeBuffer() != null && dst.nativeBuffer() != null) {
                        commandEncoder.copyBufferToBuffer(
                                src.nativeBuffer(), (int) cmd.srcOffset(),
                                dst.nativeBuffer(), (int) cmd.dstOffset(), (int) cmd.size());
                    }
                }
            }
            case RenderCommand.CopyTexture cmd -> {
                log.trace("CopyTexture not yet implemented for WebGPU");
            }
            case RenderCommand.BlitTexture cmd -> {
                log.trace("BlitTexture not yet implemented for WebGPU");
            }
            case RenderCommand.DrawIndirect cmd -> {
                log.trace("DrawIndirect not yet implemented for WebGPU");
            }
            case RenderCommand.DrawIndexedIndirect cmd -> {
                log.trace("DrawIndexedIndirect not yet implemented for WebGPU");
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
        if (!nativeAvailable || commandEncoder == null || currentRenderTarget == null) return;

        var rt = renderTargets.get(currentRenderTarget);
        if (rt == null) return;

        var rpDesc = WGPURenderPassDescriptor.obtain();

        // Color attachments
        var colorVec = WGPUVectorRenderPassColorAttachment.obtain();
        for (var texHandle : rt.colorTextures()) {
            var colorTex = textures.get(texHandle);
            var colorAttachment = WGPURenderPassColorAttachment.obtain();
            colorAttachment.setView(colorTex.view());
            colorAttachment.setResolveTarget(WGPUTextureView.NULL);
            colorAttachment.setLoadOp(WGPULoadOp.Clear);
            colorAttachment.setStoreOp(WGPUStoreOp.Store);
            colorAttachment.getClearValue().setColor(r, g, b, a);
            colorVec.push_back(colorAttachment);
        }
        rpDesc.setColorAttachments(colorVec);

        // Depth/stencil attachment
        if (rt.depthTexture() != null) {
            var depthTex = textures.get(rt.depthTexture());
            var depthAttachment = WGPURenderPassDepthStencilAttachment.obtain();
            depthAttachment.setView(depthTex.view());
            depthAttachment.setDepthLoadOp(WGPULoadOp.Clear);
            depthAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            depthAttachment.setDepthClearValue(1.0f);
            depthAttachment.setDepthReadOnly(false);
            depthAttachment.setStencilLoadOp(WGPULoadOp.Clear);
            depthAttachment.setStencilStoreOp(WGPUStoreOp.Store);
            depthAttachment.setStencilClearValue(0);
            depthAttachment.setStencilReadOnly(false);
            rpDesc.setDepthStencilAttachment(depthAttachment);
        } else {
            rpDesc.setDepthStencilAttachment(WGPURenderPassDepthStencilAttachment.NULL);
        }
        rpDesc.setTimestampWrites(WGPURenderPassTimestampWrites.NULL);

        renderPassEncoder = WGPURenderPassEncoder.obtain();
        commandEncoder.beginRenderPass(rpDesc, renderPassEncoder);

        // Set default viewport
        renderPassEncoder.setViewport(0, 0, rt.width(), rt.height(), 0.0f, 1.0f);
        renderPassEncoder.setScissorRect(0, 0, rt.width(), rt.height());

        // Set stencil reference
        if (stencilTestEnabled) {
            renderPassEncoder.setStencilReference(stencilRef);
        }

        // Re-bind current pipeline if any
        if (currentPipeline != null) {
            var p = pipelines.get(currentPipeline);
            if (p != null && p.nativePipeline() != null) {
                renderPassEncoder.setPipeline(p.nativePipeline());
            }
        }
    }

    private void endCurrentRenderPass() {
        if (renderPassEncoder != null && nativeAvailable) {
            renderPassEncoder.end();
            renderPassEncoder.release();
            renderPassEncoder = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Bind Group Management
    // ═══════════════════════════════════════════════════════════════════

    private void flushBindings() {
        if (!bindingsDirty || renderPassEncoder == null || !nativeAvailable) return;
        bindingsDirty = false;

        if (currentPipeline == null) return;
        var pipelineInfo = pipelines.get(currentPipeline);
        if (pipelineInfo == null || pipelineInfo.nativePipeline() == null) return;

        var bindingTypes = pipelineInfo.bindingTypes();
        if (bindingTypes.isEmpty()) return;

        // Release previous bind group
        if (currentBindGroup != null) {
            currentBindGroup.release();
            currentBindGroup = null;
        }

        var entries = WGPUVectorBindGroupEntry.obtain();

        // Track Nth texture/sampler
        int texIdx = 0;
        int smpIdx = 0;

        var boundTexList = new ArrayList<Handle<TextureResource>>();
        var boundSmpList = new ArrayList<Handle<SamplerResource>>();
        for (var bt : boundTextures) { if (bt != null) boundTexList.add(bt); }
        for (var bs : boundSamplers) { if (bs != null) boundSmpList.add(bs); }

        for (var entry : bindingTypes.entrySet()) {
            int binding = entry.getKey();
            var type = entry.getValue();

            var bgEntry = WGPUBindGroupEntry.obtain();
            bgEntry.reset();
            bgEntry.setBinding(binding);

            switch (type) {
                case UNIFORM -> {
                    if (binding < boundUbos.length && boundUbos[binding] != null) {
                        var buf = buffers.get(boundUbos[binding]);
                        if (buf != null && buf.nativeBuffer() != null) {
                            bgEntry.setBuffer(buf.nativeBuffer());
                            bgEntry.setOffset(0);
                            bgEntry.setSize(buf.size());
                        }
                    }
                }
                case TEXTURE -> {
                    if (texIdx < boundTexList.size()) {
                        var tex = textures.get(boundTexList.get(texIdx));
                        if (tex != null && tex.view() != null) {
                            bgEntry.setTextureView(tex.view());
                        }
                        texIdx++;
                    }
                }
                case SAMPLER -> {
                    if (smpIdx < boundSmpList.size()) {
                        var smp = samplers.get(boundSmpList.get(smpIdx));
                        if (smp != null && smp.nativeSampler() != null) {
                            bgEntry.setSampler(smp.nativeSampler());
                        }
                        smpIdx++;
                    }
                }
                case STORAGE -> {
                    if (binding < boundSsbos.length && boundSsbos[binding] != null) {
                        var buf = buffers.get(boundSsbos[binding]);
                        if (buf != null && buf.nativeBuffer() != null) {
                            bgEntry.setBuffer(buf.nativeBuffer());
                            bgEntry.setOffset(0);
                            bgEntry.setSize(buf.size());
                        }
                    }
                }
            }

            entries.push_back(bgEntry);
        }

        var bgDesc = WGPUBindGroupDescriptor.obtain();
        bgDesc.setLayout(pipelineInfo.bindGroupLayout());
        bgDesc.setEntries(entries);

        currentBindGroup = new WGPUBindGroup();
        wgpuDevice.createBindGroup(bgDesc, currentBindGroup);

        renderPassEncoder.setBindGroup(0, currentBindGroup);

        // Set stencil reference if needed
        if (stencilTestEnabled) {
            renderPassEncoder.setStencilReference(stencilRef);
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
        if (colorTex == null || colorTex.nativeTexture() == null) return null;

        // Create staging buffer
        int bytesPerRow = alignTo256(width * 4);
        long bufferSize = (long) bytesPerRow * height;

        var stagingDesc = WGPUBufferDescriptor.obtain();
        stagingDesc.setSize(bufferSize);
        int stagingUsage = WGPUBufferUsage.MapRead.getValue() | WGPUBufferUsage.CopyDst.getValue();
        stagingDesc.setUsage(WGPUBufferUsage.CUSTOM.setValue(stagingUsage));

        var stagingBuf = wgpuDevice.createBuffer(stagingDesc);

        // Create command encoder for copy
        var encDesc = WGPUCommandEncoderDescriptor.obtain();
        var encoder = WGPUCommandEncoder.obtain();
        wgpuDevice.createCommandEncoder(encDesc, encoder);

        // Source texture info
        var srcInfo = WGPUTexelCopyTextureInfo.obtain();
        srcInfo.setTexture(colorTex.nativeTexture());
        srcInfo.setMipLevel(0);
        srcInfo.setAspect(WGPUTextureAspect.All);

        // Dest buffer info
        var dstInfo = WGPUTexelCopyBufferInfo.obtain();
        dstInfo.setBuffer(stagingBuf);
        var layout = dstInfo.getLayout();
        layout.setOffset(0);
        layout.setBytesPerRow(bytesPerRow);
        layout.setRowsPerImage(height);

        var copySize = WGPUExtent3D.obtain();
        copySize.setWidth(width);
        copySize.setHeight(height);
        copySize.setDepthOrArrayLayers(1);

        encoder.copyTextureToBuffer(srcInfo, dstInfo, copySize);

        // Finish and submit
        var cmdBufDesc = WGPUCommandBufferDescriptor.obtain();
        var cmdBuf = WGPUCommandBuffer.obtain();
        encoder.finish(cmdBufDesc, cmdBuf);
        wgpuQueue.submit(cmdBuf);
        cmdBuf.release();
        encoder.release();

        // Map the staging buffer synchronously
        final boolean[] mapDone = {false};
        stagingBuf.mapAsync(WGPUMapMode.Read, 0, (int) bufferSize,
                WGPUCallbackMode.AllowSpontaneous,
                new WGPUBufferMapCallback() {
                    @Override
                    protected void onCallback(WGPUMapAsyncStatus status, String message) {
                        mapDone[0] = true;
                    }
                });

        // Poll until map completes
        for (int i = 0; i < 1000 && !mapDone[0]; i++) {
            wgpuInstance.processEvents();
        }

        // Read back via getConstMappedRange
        ByteBuffer mapped = ByteBuffer.allocateDirect((int) bufferSize);
        stagingBuf.getConstMappedRange(0, (int) bufferSize, mapped);

        // Copy to byte array, handling row alignment
        byte[] rgba = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            int srcOffset = y * bytesPerRow;
            int dstOffset = y * width * 4;
            for (int x = 0; x < width * 4; x++) {
                rgba[dstOffset + x] = mapped.get(srcOffset + x);
            }
        }

        stagingBuf.unmap();
        stagingBuf.release();

        return rgba;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Capabilities
    // ═══════════════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(DeviceCapability<T> capability) {
        if (capability == DeviceCapability.MAX_TEXTURE_SIZE) return (T) Integer.valueOf(8192);
        if (capability == DeviceCapability.MAX_FRAMEBUFFER_WIDTH) return (T) Integer.valueOf(8192);
        if (capability == DeviceCapability.MAX_FRAMEBUFFER_HEIGHT) return (T) Integer.valueOf(8192);
        if (capability == DeviceCapability.BACKEND_NAME) return (T) "WebGPU";
        if (capability == DeviceCapability.DEVICE_NAME) return (T) "jWebGPU";
        if (capability == DeviceCapability.API_VERSION) return (T) "WebGPU";
        if (capability == DeviceCapability.COMPUTE_SHADERS) return (T) Boolean.TRUE;
        if (capability == DeviceCapability.GEOMETRY_SHADERS) return (T) Boolean.FALSE;
        if (capability == DeviceCapability.TESSELLATION) return (T) Boolean.FALSE;
        if (capability == DeviceCapability.ANISOTROPIC_FILTERING) return (T) Boolean.TRUE;
        if (capability == DeviceCapability.BINDLESS_TEXTURES) return (T) Boolean.FALSE;
        if (capability == DeviceCapability.MAX_UNIFORM_BUFFER_SIZE) return (T) Integer.valueOf(65536);
        if (capability == DeviceCapability.MAX_STORAGE_BUFFER_SIZE) return (T) Integer.valueOf(134217728);
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void close() {
        if (defaultRenderTarget != null) {
            destroyRenderTarget(defaultRenderTarget);
            defaultRenderTarget = null;
        }
        if (nativeAvailable) {
            if (wgpuDevice != null) {
                wgpuDevice.release();
                wgpuDevice = null;
            }
            if (wgpuAdapter != null) {
                wgpuAdapter.release();
                wgpuAdapter = null;
            }
            if (wgpuInstance != null) {
                wgpuInstance.release();
                wgpuInstance = null;
            }
            log.info("WebGPU device released");
        }
        log.info("WgpuRenderDevice closed");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Format Mapping Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static WGPUBufferUsage mapBufferUsage(BufferUsage usage) {
        if (usage == BufferUsage.VERTEX) return WGPUBufferUsage.Vertex;
        if (usage == BufferUsage.INDEX) return WGPUBufferUsage.Index;
        if (usage == BufferUsage.UNIFORM) return WGPUBufferUsage.Uniform;
        if (usage == BufferUsage.STORAGE) return WGPUBufferUsage.Storage;
        return WGPUBufferUsage.Vertex;
    }

    static WGPUTextureFormat mapTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return WGPUTextureFormat.RGBA8Unorm;
        if (format == TextureFormat.BGRA8) return WGPUTextureFormat.BGRA8Unorm;
        if (format == TextureFormat.DEPTH32F) return WGPUTextureFormat.Depth32Float;
        if (format == TextureFormat.DEPTH24) return WGPUTextureFormat.Depth24Plus;
        if (format == TextureFormat.DEPTH24_STENCIL8) return WGPUTextureFormat.Depth24PlusStencil8;
        if (format == TextureFormat.RGB8) return WGPUTextureFormat.RGBA8Unorm;
        if (format == TextureFormat.R8) return WGPUTextureFormat.R8Unorm;
        if (format == TextureFormat.RGBA16F) return WGPUTextureFormat.RGBA16Float;
        if (format == TextureFormat.RGBA32F) return WGPUTextureFormat.RGBA32Float;
        if (format == TextureFormat.RG16F) return WGPUTextureFormat.RG16Float;
        if (format == TextureFormat.RG32F) return WGPUTextureFormat.RG32Float;
        if (format == TextureFormat.R16F) return WGPUTextureFormat.R16Float;
        if (format == TextureFormat.R32F) return WGPUTextureFormat.R32Float;
        if (format == TextureFormat.R32UI) return WGPUTextureFormat.R32Uint;
        if (format == TextureFormat.R32I) return WGPUTextureFormat.R32Sint;
        return WGPUTextureFormat.RGBA8Unorm;
    }

    private static WGPUVertexFormat mapVertexFormat(VertexAttribute attr) {
        if (attr.componentType() == ComponentType.FLOAT) {
            return switch (attr.componentCount()) {
                case 1 -> WGPUVertexFormat.Float32;
                case 2 -> WGPUVertexFormat.Float32x2;
                case 3 -> WGPUVertexFormat.Float32x3;
                case 4 -> WGPUVertexFormat.Float32x4;
                default -> WGPUVertexFormat.Float32x4;
            };
        }
        return WGPUVertexFormat.Float32x4;
    }

    private static WGPUFilterMode mapFilterMode(FilterMode mode) {
        if (mode == FilterMode.NEAREST || mode == FilterMode.NEAREST_MIPMAP_NEAREST) {
            return WGPUFilterMode.Nearest;
        }
        return WGPUFilterMode.Linear;
    }

    private static WGPUMipmapFilterMode mapMipmapFilterMode(FilterMode mode) {
        if (mode == FilterMode.LINEAR_MIPMAP_LINEAR) return WGPUMipmapFilterMode.Linear;
        if (mode == FilterMode.NEAREST_MIPMAP_NEAREST) return WGPUMipmapFilterMode.Nearest;
        return WGPUMipmapFilterMode.Nearest;
    }

    private static WGPUAddressMode mapWrapMode(WrapMode mode) {
        if (mode == WrapMode.REPEAT) return WGPUAddressMode.Repeat;
        if (mode == WrapMode.CLAMP_TO_EDGE) return WGPUAddressMode.ClampToEdge;
        if (mode == WrapMode.MIRRORED_REPEAT) return WGPUAddressMode.MirrorRepeat;
        return WGPUAddressMode.Repeat;
    }

    private static WGPUCompareFunction mapCompareFunc(CompareFunc func) {
        if (func == CompareFunc.LESS) return WGPUCompareFunction.Less;
        if (func == CompareFunc.LEQUAL) return WGPUCompareFunction.LessEqual;
        if (func == CompareFunc.GREATER) return WGPUCompareFunction.Greater;
        if (func == CompareFunc.GEQUAL) return WGPUCompareFunction.GreaterEqual;
        if (func == CompareFunc.EQUAL) return WGPUCompareFunction.Equal;
        if (func == CompareFunc.NOT_EQUAL) return WGPUCompareFunction.NotEqual;
        if (func == CompareFunc.ALWAYS) return WGPUCompareFunction.Always;
        if (func == CompareFunc.NEVER) return WGPUCompareFunction.Never;
        return WGPUCompareFunction.Less;
    }

    private static WGPUStencilOperation mapStencilOp(StencilOp op) {
        if (op == StencilOp.KEEP) return WGPUStencilOperation.Keep;
        if (op == StencilOp.ZERO) return WGPUStencilOperation.Zero;
        if (op == StencilOp.REPLACE) return WGPUStencilOperation.Replace;
        if (op == StencilOp.INCR) return WGPUStencilOperation.IncrementClamp;
        if (op == StencilOp.DECR) return WGPUStencilOperation.DecrementClamp;
        if (op == StencilOp.INVERT) return WGPUStencilOperation.Invert;
        if (op == StencilOp.INCR_WRAP) return WGPUStencilOperation.IncrementWrap;
        if (op == StencilOp.DECR_WRAP) return WGPUStencilOperation.DecrementWrap;
        return WGPUStencilOperation.Keep;
    }

    private static void configureBlendState(WGPUBlendState blendState, BlendMode mode) {
        var color = blendState.getColor();
        var alpha = blendState.getAlpha();

        if (mode == BlendMode.ALPHA) {
            color.setOperation(WGPUBlendOperation.Add);
            color.setSrcFactor(WGPUBlendFactor.SrcAlpha);
            color.setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
            alpha.setOperation(WGPUBlendOperation.Add);
            alpha.setSrcFactor(WGPUBlendFactor.One);
            alpha.setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
        } else if (mode == BlendMode.ADDITIVE) {
            color.setOperation(WGPUBlendOperation.Add);
            color.setSrcFactor(WGPUBlendFactor.One);
            color.setDstFactor(WGPUBlendFactor.One);
            alpha.setOperation(WGPUBlendOperation.Add);
            alpha.setSrcFactor(WGPUBlendFactor.One);
            alpha.setDstFactor(WGPUBlendFactor.One);
        } else if (mode == BlendMode.MULTIPLY) {
            color.setOperation(WGPUBlendOperation.Add);
            color.setSrcFactor(WGPUBlendFactor.Dst);
            color.setDstFactor(WGPUBlendFactor.Zero);
            alpha.setOperation(WGPUBlendOperation.Add);
            alpha.setSrcFactor(WGPUBlendFactor.DstAlpha);
            alpha.setDstFactor(WGPUBlendFactor.Zero);
        } else if (mode == BlendMode.PREMULTIPLIED) {
            color.setOperation(WGPUBlendOperation.Add);
            color.setSrcFactor(WGPUBlendFactor.One);
            color.setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
            alpha.setOperation(WGPUBlendOperation.Add);
            alpha.setSrcFactor(WGPUBlendFactor.One);
            alpha.setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
        } else {
            // NONE — no blending
            color.setOperation(WGPUBlendOperation.Add);
            color.setSrcFactor(WGPUBlendFactor.One);
            color.setDstFactor(WGPUBlendFactor.Zero);
            alpha.setOperation(WGPUBlendOperation.Add);
            alpha.setSrcFactor(WGPUBlendFactor.One);
            alpha.setDstFactor(WGPUBlendFactor.Zero);
        }
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

    private static int alignTo256(int value) {
        return (value + 255) & ~255;
    }
}

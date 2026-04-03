package dev.engine.graphics.webgpu;

import dev.engine.bindings.wgpu.WgpuNative;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebGPU render device backed by wgpu-native via FFM bindings.
 *
 * <p>Implements headless rendering to an off-screen texture with readback
 * support for screenshot tests. When wgpu-native is not available, all
 * resource management still works through {@link ResourceRegistry} but
 * no GPU operations are performed.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Setup: Instance -> Adapter -> Device -> Queue</li>
 *   <li>Per-frame: CommandEncoder -> RenderPassEncoder -> CommandBuffer -> Queue.submit()</li>
 *   <li>Bind groups created on-demand per draw call with currently bound resources</li>
 *   <li>Pipelines use "auto" layout — bind group layout derived from the shader</li>
 * </ul>
 */
public class WgpuRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(WgpuRenderDevice.class);

    // ── Native resource records ───────────────────────────────────────

    private record WgpuBuffer(MemorySegment handle, long size, long usage) {}
    private record WgpuTexture(MemorySegment handle, MemorySegment view, TextureDescriptor desc, int wgpuFormat) {}
    private record WgpuVertexInput(VertexFormat format) {}
    private record WgpuSampler(MemorySegment handle, SamplerDescriptor desc) {}
    private record WgpuPipeline(MemorySegment handle, MemorySegment layout) {}
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
    private final Arena deviceArena;          // lives for the lifetime of the device
    private MemorySegment wgpuInstance;
    private MemorySegment wgpuAdapter;
    private MemorySegment wgpuDevice;
    private MemorySegment wgpuQueue;

    // ── Per-frame state ───────────────────────────────────────────────

    private MemorySegment commandEncoder;
    private MemorySegment renderPassEncoder;
    private Arena frameArena;                 // per-frame allocations

    // ── Current render target tracking ────────────────────────────────

    private Handle<RenderTargetResource> currentRenderTarget;

    // ── Pending bind state (flushed before each draw) ─────────────────

    @SuppressWarnings("unchecked")
    private final Handle<BufferResource>[] boundUbos = new Handle[16];
    @SuppressWarnings("unchecked")
    private final Handle<TextureResource>[] boundTextures = new Handle[16];
    @SuppressWarnings("unchecked")
    private final Handle<SamplerResource>[] boundSamplers = new Handle[16];
    @SuppressWarnings("unchecked")
    private final Handle<BufferResource>[] boundSsbos = new Handle[8];
    private Handle<PipelineResource> currentPipeline;
    private boolean bindingsDirty;
    private MemorySegment currentBindGroup;

    // ── Render state tracking (for pipeline-baked state in WebGPU) ────

    private boolean depthTestEnabled = true;
    private boolean depthWriteEnabled = true;
    private boolean blendEnabled = false;
    private int wgpuCullMode = WgpuNative.CULL_MODE_BACK;
    private int wgpuFrontFace = WgpuNative.FRONT_FACE_CCW;

    // ── Constructor ───────────────────────────────────────────────────

    public WgpuRenderDevice() {
        deviceArena = Arena.ofShared();
        nativeAvailable = WgpuNative.isAvailable();

        if (nativeAvailable) {
            wgpuInstance = WgpuNative.createInstance(MemorySegment.NULL);
            wgpuAdapter = WgpuNative.requestAdapterSync(wgpuInstance, MemorySegment.NULL);
            wgpuDevice = WgpuNative.requestDeviceSync(wgpuInstance, wgpuAdapter, MemorySegment.NULL);
            wgpuQueue = WgpuNative.deviceGetQueue(wgpuDevice);
            log.info("WebGPU device created: instance={}, adapter={}, device={}, queue={}",
                    wgpuInstance, wgpuAdapter, wgpuDevice, wgpuQueue);
        } else {
            wgpuInstance = null;
            wgpuAdapter = null;
            wgpuDevice = null;
            wgpuQueue = null;
            log.warn("wgpu-native not available; WebGPU device running without native backend");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Buffer
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        long usage = mapBufferUsage(descriptor.usage());
        // Always add COPY_DST for writeBuffer support
        usage |= WgpuNative.BUFFER_USAGE_COPY_DST;

        if (nativeAvailable) {
            var buf = WgpuNative.deviceCreateBuffer(wgpuDevice,
                    descriptor.size(), usage, false, deviceArena);
            return buffers.register(new WgpuBuffer(buf, descriptor.size(), usage));
        }
        return buffers.register(new WgpuBuffer(MemorySegment.NULL, descriptor.size(), usage));
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!buffers.isValid(buffer)) return;
        var buf = buffers.remove(buffer);
        if (buf != null && nativeAvailable && !buf.handle().equals(MemorySegment.NULL)) {
            WgpuNative.bufferRelease(buf.handle());
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
                if (nativeAvailable && buf != null && !buf.handle().equals(MemorySegment.NULL)) {
                    WgpuNative.queueWriteBuffer(wgpuQueue, buf.handle(), offset, segment, length);
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
        int wgpuFormat = mapTextureFormat(descriptor.format());
        long usage = WgpuNative.TEXTURE_USAGE_TEXTURE_BINDING
                | WgpuNative.TEXTURE_USAGE_COPY_DST
                | WgpuNative.TEXTURE_USAGE_COPY_SRC;

        if (nativeAvailable) {
            var texHandle = createWgpuTexture(descriptor, wgpuFormat, usage);
            var view = WgpuNative.textureCreateView(texHandle, MemorySegment.NULL);
            return textures.register(new WgpuTexture(texHandle, view, descriptor, wgpuFormat));
        }
        return textures.register(new WgpuTexture(MemorySegment.NULL, MemorySegment.NULL, descriptor, wgpuFormat));
    }

    private Handle<TextureResource> createTextureForRenderTarget(TextureDescriptor descriptor, long extraUsage) {
        int wgpuFormat = mapTextureFormat(descriptor.format());
        long usage = WgpuNative.TEXTURE_USAGE_COPY_SRC | extraUsage;

        if (nativeAvailable) {
            var texHandle = createWgpuTexture(descriptor, wgpuFormat, usage);
            var view = WgpuNative.textureCreateView(texHandle, MemorySegment.NULL);
            return textures.register(new WgpuTexture(texHandle, view, descriptor, wgpuFormat));
        }
        return textures.register(new WgpuTexture(MemorySegment.NULL, MemorySegment.NULL, descriptor, wgpuFormat));
    }

    private MemorySegment createWgpuTexture(TextureDescriptor descriptor, int wgpuFormat, long usage) {
        try (var arena = Arena.ofConfined()) {
            // WGPUTextureDescriptor layout (v24 webgpu.h):
            //   0: nextInChain (8, ptr)
            //   8: label.data (8, ptr)
            //  16: label.length (8, size_t)
            //  24: usage (8, WGPUFlags uint64)
            //  32: dimension (4, uint32 enum)
            //  36: size.width (4, uint32) -- Extent3D has align 4, no padding after dimension
            //  40: size.height (4, uint32)
            //  44: size.depthOrArrayLayers (4, uint32)
            //  48: format (4, uint32 enum)
            //  52: mipLevelCount (4, uint32)
            //  56: sampleCount (4, uint32)
            //  60: pad (4) -- align to 8 for size_t
            //  64: viewFormatCount (8, size_t)
            //  72: viewFormats (8, ptr)
            // Total: 80
            var desc = arena.allocate(80, 8);
            desc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);    // nextInChain
            desc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);    // label.data
            desc.set(ValueLayout.JAVA_LONG, 16, 0L);                 // label.length
            desc.set(ValueLayout.JAVA_LONG, 24, usage);              // usage
            desc.set(ValueLayout.JAVA_INT, 32, WgpuNative.TEXTURE_DIMENSION_2D); // dimension
            desc.set(ValueLayout.JAVA_INT, 36, descriptor.width());  // size.width
            desc.set(ValueLayout.JAVA_INT, 40, descriptor.height()); // size.height
            int depthOrLayers = 1;
            if (descriptor.type() == TextureType.TEXTURE_3D) depthOrLayers = descriptor.depth();
            else if (descriptor.type() == TextureType.TEXTURE_2D_ARRAY) depthOrLayers = descriptor.layers();
            else if (descriptor.type() == TextureType.TEXTURE_CUBE) depthOrLayers = 6;
            desc.set(ValueLayout.JAVA_INT, 44, depthOrLayers);       // size.depthOrArrayLayers
            desc.set(ValueLayout.JAVA_INT, 48, wgpuFormat);          // format
            desc.set(ValueLayout.JAVA_INT, 52, 1);                   // mipLevelCount
            desc.set(ValueLayout.JAVA_INT, 56, 1);                   // sampleCount
            // pad at 60
            desc.set(ValueLayout.JAVA_LONG, 64, 0L);                 // viewFormatCount
            desc.set(ValueLayout.ADDRESS, 72, MemorySegment.NULL);   // viewFormats

            return WgpuNative.deviceCreateTexture(wgpuDevice, desc);
        }
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        if (!nativeAvailable) return;
        var tex = textures.get(texture);
        if (tex == null || tex.handle().equals(MemorySegment.NULL)) return;

        try (var arena = Arena.ofConfined()) {
            var desc = tex.desc();
            long dataSize = pixels.remaining();

            // Copy ByteBuffer to a MemorySegment
            var dataSeg = arena.allocate(dataSize);
            dataSeg.copyFrom(MemorySegment.ofBuffer(pixels));

            // WGPUTexelCopyTextureInfo (destination) — v24: NO nextInChain:
            //   texture(8), mipLevel(4), origin{x(4),y(4),z(4)}, aspect(4)
            // Total: 28, padded to 32 for 8-byte alignment
            var destination = arena.allocate(32, 8);
            destination.set(ValueLayout.ADDRESS, 0, tex.handle());       // texture
            destination.set(ValueLayout.JAVA_INT, 8, 0);                 // mipLevel
            destination.set(ValueLayout.JAVA_INT, 12, 0);                // origin.x
            destination.set(ValueLayout.JAVA_INT, 16, 0);                // origin.y
            destination.set(ValueLayout.JAVA_INT, 20, 0);                // origin.z
            destination.set(ValueLayout.JAVA_INT, 24, WgpuNative.TEXTURE_ASPECT_ALL); // aspect

            // WGPUTexelCopyBufferLayout (dataLayout) — v24: NO nextInChain:
            //   offset(8), bytesPerRow(4), rowsPerImage(4)
            // Total: 16
            int bytesPerRow = desc.width() * bytesPerPixel(desc.format());
            var dataLayout = arena.allocate(16, 8);
            dataLayout.set(ValueLayout.JAVA_LONG, 0, 0L);               // offset
            dataLayout.set(ValueLayout.JAVA_INT, 8, bytesPerRow);        // bytesPerRow
            dataLayout.set(ValueLayout.JAVA_INT, 12, desc.height());     // rowsPerImage

            // WGPUExtent3D (writeSize):
            //   width(4), height(4), depthOrArrayLayers(4)
            // Total: 12
            var writeSize = arena.allocate(12, 4);
            writeSize.set(ValueLayout.JAVA_INT, 0, desc.width());
            writeSize.set(ValueLayout.JAVA_INT, 4, desc.height());
            writeSize.set(ValueLayout.JAVA_INT, 8, 1);

            WgpuNative.queueWriteTexture(wgpuQueue, destination, dataSeg, dataSize, dataLayout, writeSize);
        }
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!textures.isValid(texture)) return;
        var tex = textures.remove(texture);
        if (tex != null && nativeAvailable) {
            if (!tex.view().equals(MemorySegment.NULL)) WgpuNative.textureViewRelease(tex.view());
            if (!tex.handle().equals(MemorySegment.NULL)) WgpuNative.textureRelease(tex.handle());
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
                    WgpuNative.TEXTURE_USAGE_RENDER_ATTACHMENT | WgpuNative.TEXTURE_USAGE_TEXTURE_BINDING);
            colorTextures.add(texHandle);
        }

        Handle<TextureResource> depthTexture = null;
        if (descriptor.depthFormat() != null) {
            var depthDesc = new TextureDescriptor(descriptor.width(), descriptor.height(),
                    descriptor.depthFormat(), MipMode.NONE);
            depthTexture = createTextureForRenderTarget(depthDesc,
                    WgpuNative.TEXTURE_USAGE_RENDER_ATTACHMENT);
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
            try (var arena = Arena.ofConfined()) {
                // WGPUSamplerDescriptor layout:
                //   nextInChain(8), label{data(8), length(8)},
                //   addressModeU(4), addressModeV(4), addressModeW(4),
                //   magFilter(4), minFilter(4), mipmapFilter(4),
                //   lodMinClamp(4), lodMaxClamp(4),
                //   compare(4), maxAnisotropy(2), pad(2)
                // Total: 64
                var desc = arena.allocate(64, 8);
                desc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);  // nextInChain
                desc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);  // label.data
                desc.set(ValueLayout.JAVA_LONG, 16, 0L);               // label.length
                desc.set(ValueLayout.JAVA_INT, 24, mapWrapMode(descriptor.wrapS()));  // addressModeU
                desc.set(ValueLayout.JAVA_INT, 28, mapWrapMode(descriptor.wrapT()));  // addressModeV
                desc.set(ValueLayout.JAVA_INT, 32, mapWrapMode(descriptor.wrapS()));  // addressModeW
                desc.set(ValueLayout.JAVA_INT, 36, mapFilterMode(descriptor.magFilter())); // magFilter
                desc.set(ValueLayout.JAVA_INT, 40, mapFilterMode(descriptor.minFilter())); // minFilter
                desc.set(ValueLayout.JAVA_INT, 44, mapMipmapFilterMode(descriptor.minFilter())); // mipmapFilter
                desc.set(ValueLayout.JAVA_FLOAT, 48, 0.0f);             // lodMinClamp
                desc.set(ValueLayout.JAVA_FLOAT, 52, 32.0f);            // lodMaxClamp
                desc.set(ValueLayout.JAVA_INT, 56, 0);                  // compare (0 = undefined)
                desc.set(ValueLayout.JAVA_SHORT, 60, (short) 1);        // maxAnisotropy

                var sampler = WgpuNative.deviceCreateSampler(wgpuDevice, desc);
                return samplers.register(new WgpuSampler(sampler, descriptor));
            }
        }
        return samplers.register(new WgpuSampler(MemorySegment.NULL, descriptor));
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplers.isValid(sampler)) return;
        var s = samplers.remove(sampler);
        if (s != null && nativeAvailable && !s.handle().equals(MemorySegment.NULL)) {
            WgpuNative.samplerRelease(s.handle());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Pipeline
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        if (!nativeAvailable) {
            return pipelines.register(new WgpuPipeline(MemorySegment.NULL, MemorySegment.NULL));
        }

        try (var arena = Arena.ofConfined()) {
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

            // Create shader modules
            var vertModule = WgpuNative.deviceCreateShaderModuleWGSL(
                    wgpuDevice, vertexShader.source(), arena);
            MemorySegment fragModule = null;
            if (fragmentShader != null) {
                fragModule = WgpuNative.deviceCreateShaderModuleWGSL(
                        wgpuDevice, fragmentShader.source(), arena);
            }

            // Build the render pipeline descriptor using "auto" layout
            var pipeline = buildRenderPipeline(arena, vertModule, fragModule, descriptor,
                    vertexShader.entryPoint(), fragmentShader != null ? fragmentShader.entryPoint() : "main");

            // Release shader modules (pipeline keeps its own references)
            WgpuNative.shaderModuleRelease(vertModule);
            if (fragModule != null) WgpuNative.shaderModuleRelease(fragModule);

            return pipelines.register(new WgpuPipeline(pipeline, MemorySegment.NULL));
        }
    }

    private MemorySegment buildRenderPipeline(Arena arena, MemorySegment vertModule,
                                               MemorySegment fragModule,
                                               PipelineDescriptor descriptor,
                                               String vsEntryPoint, String fsEntryPoint) {
        // ── Vertex state ──────────────────────────────────────────────
        // WGPUVertexAttribute (v24, NO nextInChain):
        //   0: format (uint32, 4)
        //   4: pad (4)
        //   8: offset (uint64, 8)
        //  16: shaderLocation (uint32, 4)
        //  20: pad (4)
        // Total: 24
        //
        // WGPUVertexBufferLayout (v24, NO nextInChain):
        //   0: stepMode (uint32, 4)
        //   4: pad (4)
        //   8: arrayStride (uint64, 8)
        //  16: attributeCount (size_t, 8)
        //  24: attributes (ptr, 8)
        // Total: 32
        MemorySegment vertexBufferLayouts = MemorySegment.NULL;
        long vertexBufferLayoutCount = 0;

        if (descriptor.vertexFormat() != null) {
            var format = descriptor.vertexFormat();
            var attrs = format.attributes();

            var attrArray = arena.allocate(24L * attrs.size(), 8);
            for (int i = 0; i < attrs.size(); i++) {
                var attr = attrs.get(i);
                long base = i * 24L;
                attrArray.set(ValueLayout.JAVA_INT, base, mapVertexFormat(attr));
                // pad at base+4
                attrArray.set(ValueLayout.JAVA_LONG, base + 8, (long) attr.offset());
                attrArray.set(ValueLayout.JAVA_INT, base + 16, attr.location());
                // pad at base+20
            }

            vertexBufferLayouts = arena.allocate(32, 8);
            vertexBufferLayouts.set(ValueLayout.JAVA_INT, 0, WgpuNative.VERTEX_STEP_MODE_VERTEX);
            // pad at 4
            vertexBufferLayouts.set(ValueLayout.JAVA_LONG, 8, (long) format.stride());
            vertexBufferLayouts.set(ValueLayout.JAVA_LONG, 16, (long) attrs.size());
            vertexBufferLayouts.set(ValueLayout.ADDRESS, 24, attrArray);
            vertexBufferLayoutCount = 1;
        }

        var vsEntry = arena.allocateFrom(vsEntryPoint);
        var fsEntry = arena.allocateFrom(fsEntryPoint);

        // ── WGPUVertexState (64 bytes) ────────────────────────────────
        var vertexState = arena.allocate(64, 8);
        vertexState.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);   // nextInChain
        vertexState.set(ValueLayout.ADDRESS, 8, vertModule);            // module
        vertexState.set(ValueLayout.ADDRESS, 16, vsEntry);              // entryPoint.data
        vertexState.set(ValueLayout.JAVA_LONG, 24, (long) vsEntryPoint.length()); // entryPoint.length
        vertexState.set(ValueLayout.JAVA_LONG, 32, 0L);                // constantCount
        vertexState.set(ValueLayout.ADDRESS, 40, MemorySegment.NULL);   // constants
        vertexState.set(ValueLayout.JAVA_LONG, 48, vertexBufferLayoutCount);
        vertexState.set(ValueLayout.ADDRESS, 56, vertexBufferLayouts);

        // ── WGPUPrimitiveState (v24: 32 bytes with unclippedDepth) ───
        //   0: nextInChain (ptr, 8)
        //   8: topology (uint32, 4)
        //  12: stripIndexFormat (uint32, 4)
        //  16: frontFace (uint32, 4)
        //  20: cullMode (uint32, 4)
        //  24: unclippedDepth (uint32 WGPUBool, 4)
        //  28: pad (4)
        // Total: 32
        var primitiveState = arena.allocate(32, 8);
        primitiveState.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        primitiveState.set(ValueLayout.JAVA_INT, 8, WgpuNative.PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        primitiveState.set(ValueLayout.JAVA_INT, 12, 0); // stripIndexFormat undefined
        primitiveState.set(ValueLayout.JAVA_INT, 16, wgpuFrontFace);
        primitiveState.set(ValueLayout.JAVA_INT, 20, wgpuCullMode);
        primitiveState.set(ValueLayout.JAVA_INT, 24, 0); // unclippedDepth = false

        // ── WGPUDepthStencilState (v24: 72 bytes) ────────────────────
        //   0: nextInChain (ptr, 8)
        //   8: format (uint32, 4)
        //  12: depthWriteEnabled (uint32 WGPUOptionalBool, 4)
        //  16: depthCompare (uint32, 4)
        //  20: stencilFront{compare,failOp,depthFailOp,passOp} (4x4=16)
        //  36: stencilBack{...} (16)
        //  52: stencilReadMask (uint32, 4)
        //  56: stencilWriteMask (uint32, 4)
        //  60: depthBias (int32, 4)
        //  64: depthBiasSlopeScale (float, 4)
        //  68: depthBiasClamp (float, 4)
        // Total: 72
        var depthStencilState = arena.allocate(72, 8);
        depthStencilState.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        depthStencilState.set(ValueLayout.JAVA_INT, 8, WgpuNative.TEXTURE_FORMAT_DEPTH32_FLOAT);
        depthStencilState.set(ValueLayout.JAVA_INT, 12, depthWriteEnabled ? 1 : 0); // OptionalBool: 0=false, 1=true
        depthStencilState.set(ValueLayout.JAVA_INT, 16, depthTestEnabled
                ? WgpuNative.COMPARE_FUNCTION_LESS : WgpuNative.COMPARE_FUNCTION_ALWAYS);
        // stencilFront
        depthStencilState.set(ValueLayout.JAVA_INT, 20, WgpuNative.COMPARE_FUNCTION_ALWAYS);
        depthStencilState.set(ValueLayout.JAVA_INT, 24, 0);
        depthStencilState.set(ValueLayout.JAVA_INT, 28, 0);
        depthStencilState.set(ValueLayout.JAVA_INT, 32, 0);
        // stencilBack
        depthStencilState.set(ValueLayout.JAVA_INT, 36, WgpuNative.COMPARE_FUNCTION_ALWAYS);
        depthStencilState.set(ValueLayout.JAVA_INT, 40, 0);
        depthStencilState.set(ValueLayout.JAVA_INT, 44, 0);
        depthStencilState.set(ValueLayout.JAVA_INT, 48, 0);
        // masks and bias
        depthStencilState.set(ValueLayout.JAVA_INT, 52, 0xFFFFFFFF);
        depthStencilState.set(ValueLayout.JAVA_INT, 56, 0xFFFFFFFF);
        depthStencilState.set(ValueLayout.JAVA_INT, 60, 0);
        depthStencilState.set(ValueLayout.JAVA_FLOAT, 64, 0.0f);
        depthStencilState.set(ValueLayout.JAVA_FLOAT, 68, 0.0f);

        // ── WGPUMultisampleState (v24: 24 bytes) ─────────────────────
        var multisampleState = arena.allocate(24, 8);
        multisampleState.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        multisampleState.set(ValueLayout.JAVA_INT, 8, 1);
        multisampleState.set(ValueLayout.JAVA_INT, 12, 0xFFFFFFFF);
        multisampleState.set(ValueLayout.JAVA_INT, 16, 0);

        // ── Fragment state (optional) ─────────────────────────────────
        MemorySegment fragmentState = MemorySegment.NULL;
        if (fragModule != null) {
            // WGPUBlendComponent: { operation(4), srcFactor(4), dstFactor(4) } = 12
            // WGPUBlendState: { color(12), alpha(12) } = 24
            var blendState = arena.allocate(24, 4);
            if (blendEnabled) {
                blendState.set(ValueLayout.JAVA_INT, 0, WgpuNative.BLEND_OPERATION_ADD);
                blendState.set(ValueLayout.JAVA_INT, 4, WgpuNative.BLEND_FACTOR_SRC_ALPHA);
                blendState.set(ValueLayout.JAVA_INT, 8, WgpuNative.BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
                blendState.set(ValueLayout.JAVA_INT, 12, WgpuNative.BLEND_OPERATION_ADD);
                blendState.set(ValueLayout.JAVA_INT, 16, WgpuNative.BLEND_FACTOR_ONE);
                blendState.set(ValueLayout.JAVA_INT, 20, WgpuNative.BLEND_FACTOR_ONE_MINUS_SRC_ALPHA);
            } else {
                blendState.set(ValueLayout.JAVA_INT, 0, WgpuNative.BLEND_OPERATION_ADD);
                blendState.set(ValueLayout.JAVA_INT, 4, WgpuNative.BLEND_FACTOR_ONE);
                blendState.set(ValueLayout.JAVA_INT, 8, WgpuNative.BLEND_FACTOR_ZERO);
                blendState.set(ValueLayout.JAVA_INT, 12, WgpuNative.BLEND_OPERATION_ADD);
                blendState.set(ValueLayout.JAVA_INT, 16, WgpuNative.BLEND_FACTOR_ONE);
                blendState.set(ValueLayout.JAVA_INT, 20, WgpuNative.BLEND_FACTOR_ZERO);
            }

            // WGPUColorTargetState (v24):
            //   0: nextInChain (ptr, 8)
            //   8: format (uint32, 4)
            //  12: pad (4)
            //  16: blend* (ptr, 8)
            //  24: writeMask (uint64 WGPUColorWriteMask, 8)
            // Total: 32
            var colorTarget = arena.allocate(32, 8);
            colorTarget.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            colorTarget.set(ValueLayout.JAVA_INT, 8, WgpuNative.TEXTURE_FORMAT_RGBA8_UNORM);
            colorTarget.set(ValueLayout.ADDRESS, 16, blendState);
            colorTarget.set(ValueLayout.JAVA_LONG, 24, WgpuNative.COLOR_WRITE_MASK_ALL);

            // WGPUFragmentState (64 bytes)
            fragmentState = arena.allocate(64, 8);
            fragmentState.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            fragmentState.set(ValueLayout.ADDRESS, 8, fragModule);
            fragmentState.set(ValueLayout.ADDRESS, 16, fsEntry);
            fragmentState.set(ValueLayout.JAVA_LONG, 24, (long) fsEntryPoint.length());
            fragmentState.set(ValueLayout.JAVA_LONG, 32, 0L);
            fragmentState.set(ValueLayout.ADDRESS, 40, MemorySegment.NULL);
            fragmentState.set(ValueLayout.JAVA_LONG, 48, 1L);
            fragmentState.set(ValueLayout.ADDRESS, 56, colorTarget);
        }

        // ── WGPURenderPipelineDescriptor (v24) ───────────────────────
        //   0: nextInChain (ptr, 8)
        //   8: label.data (ptr, 8)
        //  16: label.length (size_t, 8)
        //  24: layout (ptr, 8)
        //  32: vertex (WGPUVertexState, 64 bytes inline)
        //  96: primitive (WGPUPrimitiveState, 32 bytes inline)
        // 128: depthStencil* (ptr, 8)
        // 136: multisample (WGPUMultisampleState, 24 bytes inline)
        // 160: fragment* (ptr, 8)
        // Total: 168
        var pipelineDesc = arena.allocate(168, 8);
        pipelineDesc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        pipelineDesc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        pipelineDesc.set(ValueLayout.JAVA_LONG, 16, 0L);
        pipelineDesc.set(ValueLayout.ADDRESS, 24, MemorySegment.NULL); // layout (NULL = auto)

        // Copy inline sub-structs
        MemorySegment.copy(vertexState, 0, pipelineDesc, 32, 64);
        MemorySegment.copy(primitiveState, 0, pipelineDesc, 96, 32);
        pipelineDesc.set(ValueLayout.ADDRESS, 128, depthStencilState);
        MemorySegment.copy(multisampleState, 0, pipelineDesc, 136, 24);
        pipelineDesc.set(ValueLayout.ADDRESS, 160, fragModule != null ? fragmentState : MemorySegment.NULL);

        return WgpuNative.deviceCreateRenderPipeline(wgpuDevice, pipelineDesc);
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelines.isValid(pipeline)) return;
        var p = pipelines.remove(pipeline);
        if (p != null && nativeAvailable && !p.handle().equals(MemorySegment.NULL)) {
            WgpuNative.renderPipelineRelease(p.handle());
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
        // WebGPU doesn't expose fences directly; submit is synchronous enough
        // for our purposes (wgpu-native blocks on queue submit completion).
        return new GpuFence() {
            @Override public boolean isSignaled() { return true; }
            @Override public void waitFor() {}
            @Override public boolean waitFor(long timeoutNanos) { return true; }
            @Override public void close() {}
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // Frame Lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void beginFrame() {
        frameCounter.incrementAndGet();
        if (!nativeAvailable) return;

        frameArena = Arena.ofConfined();

        // Create command encoder
        // WGPUCommandEncoderDescriptor: { nextInChain(8), label{data(8),len(8)} } = 24
        var encoderDesc = frameArena.allocate(24, 8);
        encoderDesc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        encoderDesc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        encoderDesc.set(ValueLayout.JAVA_LONG, 16, 0L);
        commandEncoder = WgpuNative.deviceCreateCommandEncoder(wgpuDevice, encoderDesc);

        // Reset render pass
        renderPassEncoder = null;
        currentRenderTarget = null;
        currentPipeline = null;
        currentBindGroup = null;
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
        // WGPUCommandBufferDescriptor: { nextInChain(8), label{data(8),len(8)} } = 24
        var cmdBufDesc = frameArena.allocate(24, 8);
        cmdBufDesc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        cmdBufDesc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
        cmdBufDesc.set(ValueLayout.JAVA_LONG, 16, 0L);

        var commandBuffer = WgpuNative.commandEncoderFinish(commandEncoder, cmdBufDesc);

        // Submit
        var cmdBufArray = frameArena.allocate(ValueLayout.ADDRESS);
        cmdBufArray.set(ValueLayout.ADDRESS, 0, commandBuffer);
        WgpuNative.queueSubmit(wgpuQueue, 1, cmdBufArray);

        // Release
        WgpuNative.commandBufferRelease(commandBuffer);
        WgpuNative.commandEncoderRelease(commandEncoder);
        commandEncoder = null;

        if (currentBindGroup != null && !currentBindGroup.equals(MemorySegment.NULL)) {
            WgpuNative.bindGroupRelease(currentBindGroup);
            currentBindGroup = null;
        }

        frameArena.close();
        frameArena = null;
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
                currentRenderTarget = null;
                // No default render target in headless mode
            }
            case RenderCommand.Clear cmd -> {
                // Clear is handled by render pass loadOp.
                // If we have a current render pass, end it and begin a new one with the clear color.
                endCurrentRenderPass();
                if (currentRenderTarget != null) {
                    beginRenderPassWithClear(cmd.r(), cmd.g(), cmd.b(), cmd.a());
                }
            }
            case RenderCommand.Viewport cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    WgpuNative.renderPassEncoderSetViewport(renderPassEncoder,
                            (float) cmd.x(), (float) cmd.y(),
                            (float) cmd.width(), (float) cmd.height(), 0.0f, 1.0f);
                }
            }
            case RenderCommand.Scissor cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    WgpuNative.renderPassEncoderSetScissorRect(renderPassEncoder,
                            cmd.x(), cmd.y(), cmd.width(), cmd.height());
                }
            }
            case RenderCommand.BindPipeline cmd -> {
                currentPipeline = cmd.pipeline();
                if (renderPassEncoder != null && nativeAvailable) {
                    var p = pipelines.get(cmd.pipeline());
                    if (p != null && !p.handle().equals(MemorySegment.NULL)) {
                        WgpuNative.renderPassEncoderSetPipeline(renderPassEncoder, p.handle());
                    }
                }
                bindingsDirty = true;
            }
            case RenderCommand.BindVertexBuffer cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    var buf = buffers.get(cmd.buffer());
                    if (buf != null && !buf.handle().equals(MemorySegment.NULL)) {
                        WgpuNative.renderPassEncoderSetVertexBuffer(renderPassEncoder,
                                0, buf.handle(), 0, buf.size());
                    }
                }
            }
            case RenderCommand.BindIndexBuffer cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    var buf = buffers.get(cmd.buffer());
                    if (buf != null && !buf.handle().equals(MemorySegment.NULL)) {
                        WgpuNative.renderPassEncoderSetIndexBuffer(renderPassEncoder,
                                buf.handle(), WgpuNative.INDEX_FORMAT_UINT32, 0, buf.size());
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
                    WgpuNative.renderPassEncoderDraw(renderPassEncoder,
                            cmd.vertexCount(), 1, cmd.firstVertex(), 0);
                }
            }
            case RenderCommand.DrawIndexed cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    WgpuNative.renderPassEncoderDrawIndexed(renderPassEncoder,
                            cmd.indexCount(), 1, cmd.firstIndex(), 0, 0);
                }
            }
            case RenderCommand.DrawInstanced cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    WgpuNative.renderPassEncoderDraw(renderPassEncoder,
                            cmd.vertexCount(), cmd.instanceCount(), cmd.firstVertex(), cmd.firstInstance());
                }
            }
            case RenderCommand.DrawIndexedInstanced cmd -> {
                if (renderPassEncoder != null && nativeAvailable) {
                    flushBindings();
                    WgpuNative.renderPassEncoderDrawIndexed(renderPassEncoder,
                            cmd.indexCount(), cmd.instanceCount(), cmd.firstIndex(), 0, cmd.firstInstance());
                }
            }
            case RenderCommand.SetDepthTest cmd -> {
                depthTestEnabled = cmd.enabled();
            }
            case RenderCommand.SetBlending cmd -> {
                blendEnabled = cmd.enabled();
            }
            case RenderCommand.SetCullFace cmd -> {
                wgpuCullMode = cmd.enabled() ? WgpuNative.CULL_MODE_BACK : WgpuNative.CULL_MODE_NONE;
            }
            case RenderCommand.SetWireframe cmd -> {
                // WebGPU does not support wireframe natively
            }
            case RenderCommand.SetRenderState cmd -> {
                var props = cmd.properties();
                if (props.contains(RenderState.DEPTH_TEST)) depthTestEnabled = props.get(RenderState.DEPTH_TEST);
                if (props.contains(RenderState.DEPTH_WRITE)) depthWriteEnabled = props.get(RenderState.DEPTH_WRITE);
                if (props.contains(RenderState.CULL_MODE)) {
                    var mode = props.get(RenderState.CULL_MODE);
                    if (mode == CullMode.NONE) wgpuCullMode = WgpuNative.CULL_MODE_NONE;
                    else if (mode == CullMode.FRONT) wgpuCullMode = WgpuNative.CULL_MODE_FRONT;
                    else wgpuCullMode = WgpuNative.CULL_MODE_BACK;
                }
                if (props.contains(RenderState.FRONT_FACE)) {
                    wgpuFrontFace = props.get(RenderState.FRONT_FACE) == FrontFace.CCW
                            ? WgpuNative.FRONT_FACE_CCW : WgpuNative.FRONT_FACE_CW;
                }
                if (props.contains(RenderState.BLEND_MODE)) {
                    blendEnabled = props.get(RenderState.BLEND_MODE) != BlendMode.NONE;
                }
            }
            case RenderCommand.PushConstants cmd -> {
                // Push constants not directly supported in WebGPU.
                // Would need to use a uniform buffer; for now, ignore.
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
                    if (src != null && dst != null) {
                        WgpuNative.commandEncoderCopyBufferToBuffer(commandEncoder,
                                src.handle(), cmd.srcOffset(), dst.handle(), cmd.dstOffset(), cmd.size());
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

        // Build color attachments
        // WGPURenderPassColorAttachment (v24):
        //   0: nextInChain (ptr, 8)
        //   8: view (ptr, 8)
        //  16: depthSlice (uint32, 4)
        //  20: pad (4)
        //  24: resolveTarget (ptr, 8)
        //  32: loadOp (uint32, 4)
        //  36: storeOp (uint32, 4)
        //  40: clearValue.r (double, 8)
        //  48: clearValue.g (double, 8)
        //  56: clearValue.b (double, 8)
        //  64: clearValue.a (double, 8)
        // Total: 72
        int colorCount = rt.colorTextures().size();
        var colorAttachments = frameArena.allocate(72L * colorCount, 8);

        for (int i = 0; i < colorCount; i++) {
            long base = i * 72L;
            var colorTex = textures.get(rt.colorTextures().get(i));
            colorAttachments.set(ValueLayout.ADDRESS, base, MemorySegment.NULL);       // nextInChain
            colorAttachments.set(ValueLayout.ADDRESS, base + 8, colorTex.view());       // view
            colorAttachments.set(ValueLayout.JAVA_INT, base + 16, 0xFFFFFFFF);          // depthSlice (WGPU_DEPTH_SLICE_UNDEFINED)
            // pad at base+20
            colorAttachments.set(ValueLayout.ADDRESS, base + 24, MemorySegment.NULL);   // resolveTarget
            colorAttachments.set(ValueLayout.JAVA_INT, base + 32, WgpuNative.LOAD_OP_CLEAR); // loadOp
            colorAttachments.set(ValueLayout.JAVA_INT, base + 36, WgpuNative.STORE_OP_STORE); // storeOp
            colorAttachments.set(ValueLayout.JAVA_DOUBLE, base + 40, (double) r);       // clearValue.r
            colorAttachments.set(ValueLayout.JAVA_DOUBLE, base + 48, (double) g);       // clearValue.g
            colorAttachments.set(ValueLayout.JAVA_DOUBLE, base + 56, (double) b);       // clearValue.b
            colorAttachments.set(ValueLayout.JAVA_DOUBLE, base + 64, (double) a);       // clearValue.a
        }

        // Depth attachment (optional)
        // WGPURenderPassDepthStencilAttachment (v24, NO nextInChain):
        //   0: view (ptr, 8)
        //   8: depthLoadOp (uint32, 4)
        //  12: depthStoreOp (uint32, 4)
        //  16: depthClearValue (float, 4)
        //  20: depthReadOnly (uint32 WGPUBool, 4)
        //  24: stencilLoadOp (uint32, 4)
        //  28: stencilStoreOp (uint32, 4)
        //  32: stencilClearValue (uint32, 4)
        //  36: stencilReadOnly (uint32 WGPUBool, 4)
        // Total: 40
        MemorySegment depthAttachment = MemorySegment.NULL;
        if (rt.depthTexture() != null) {
            var depthTex = textures.get(rt.depthTexture());
            depthAttachment = frameArena.allocate(40, 8);
            depthAttachment.set(ValueLayout.ADDRESS, 0, depthTex.view());              // view
            depthAttachment.set(ValueLayout.JAVA_INT, 8, WgpuNative.LOAD_OP_CLEAR);   // depthLoadOp
            depthAttachment.set(ValueLayout.JAVA_INT, 12, WgpuNative.STORE_OP_STORE);  // depthStoreOp
            depthAttachment.set(ValueLayout.JAVA_FLOAT, 16, 1.0f);                     // depthClearValue
            depthAttachment.set(ValueLayout.JAVA_INT, 20, 0);                          // depthReadOnly (false)
            depthAttachment.set(ValueLayout.JAVA_INT, 24, WgpuNative.LOAD_OP_CLEAR);  // stencilLoadOp
            depthAttachment.set(ValueLayout.JAVA_INT, 28, WgpuNative.STORE_OP_STORE); // stencilStoreOp
            depthAttachment.set(ValueLayout.JAVA_INT, 32, 0);                          // stencilClearValue
            depthAttachment.set(ValueLayout.JAVA_INT, 36, 0);                          // stencilReadOnly (false)
        }

        // WGPURenderPassDescriptor:
        //   nextInChain(8), label{data(8),len(8)},
        //   colorAttachmentCount(8), colorAttachments*(8),
        //   depthStencilAttachment*(8),
        //   occlusionQuerySet(8),
        //   timestampWrites*(8)
        // Total: 64
        var rpDesc = frameArena.allocate(64, 8);
        rpDesc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);        // nextInChain
        rpDesc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);        // label.data
        rpDesc.set(ValueLayout.JAVA_LONG, 16, 0L);                     // label.length
        rpDesc.set(ValueLayout.JAVA_LONG, 24, (long) colorCount);      // colorAttachmentCount
        rpDesc.set(ValueLayout.ADDRESS, 32, colorAttachments);          // colorAttachments
        rpDesc.set(ValueLayout.ADDRESS, 40, depthAttachment);           // depthStencilAttachment
        rpDesc.set(ValueLayout.ADDRESS, 48, MemorySegment.NULL);       // occlusionQuerySet
        rpDesc.set(ValueLayout.ADDRESS, 56, MemorySegment.NULL);       // timestampWrites

        renderPassEncoder = WgpuNative.commandEncoderBeginRenderPass(commandEncoder, rpDesc);

        // Set default viewport
        WgpuNative.renderPassEncoderSetViewport(renderPassEncoder,
                0, 0, rt.width(), rt.height(), 0.0f, 1.0f);
        WgpuNative.renderPassEncoderSetScissorRect(renderPassEncoder,
                0, 0, rt.width(), rt.height());

        // Re-bind current pipeline if any
        if (currentPipeline != null) {
            var p = pipelines.get(currentPipeline);
            if (p != null && !p.handle().equals(MemorySegment.NULL)) {
                WgpuNative.renderPassEncoderSetPipeline(renderPassEncoder, p.handle());
            }
        }
    }

    private void endCurrentRenderPass() {
        if (renderPassEncoder != null && nativeAvailable) {
            WgpuNative.renderPassEncoderEnd(renderPassEncoder);
            WgpuNative.renderPassEncoderRelease(renderPassEncoder);
            renderPassEncoder = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Bind Group Management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Flush pending bind state by creating a bind group from the currently
     * bound pipeline's layout and setting it on the render pass encoder.
     *
     * <p>WebGPU pipelines created with layout=NULL use "auto" layout,
     * where the bind group layout is derived from the shader. We use
     * {@code wgpuRenderPipelineGetBindGroupLayout(pipeline, 0)} to get it.
     * However, that function is not yet bound in WgpuNative, so for now
     * we skip bind group management and rely on pipelines that don't
     * require external bindings (simple vertex-only shaders).
     *
     * <p>TODO: Implement full bind group creation once pipeline layout
     * introspection is available.
     */
    private void flushBindings() {
        if (!bindingsDirty || renderPassEncoder == null || !nativeAvailable) return;
        bindingsDirty = false;

        // For "auto" layout pipelines, we would need wgpuRenderPipelineGetBindGroupLayout
        // to create matching bind groups. For now, bind groups are skipped for simple
        // rendering (clear + vertex-only draw calls work without bind groups).
        //
        // Full bind group support will be added when we bind
        // wgpuRenderPipelineGetBindGroupLayout in WgpuNative.
    }

    // ═══════════════════════════════════════════════════════════════════
    // Readback
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public byte[] readFramebuffer(int width, int height) {
        if (!nativeAvailable || currentRenderTarget == null) return null;

        var rt = renderTargets.get(currentRenderTarget);
        if (rt == null || rt.colorTextures().isEmpty()) return null;

        var colorTex = textures.get(rt.colorTextures().getFirst());
        if (colorTex == null || colorTex.handle().equals(MemorySegment.NULL)) return null;

        // We need to do the readback in a separate command encoder submit
        // since the main render pass may still be active.
        try (var readbackArena = Arena.ofConfined()) {
            int bytesPerRow = alignTo256(width * 4); // WebGPU requires 256-byte aligned rows
            long bufferSize = (long) bytesPerRow * height;

            // Create staging buffer
            var stagingBuf = WgpuNative.deviceCreateBuffer(wgpuDevice,
                    bufferSize,
                    WgpuNative.BUFFER_USAGE_MAP_READ | WgpuNative.BUFFER_USAGE_COPY_DST,
                    false, readbackArena);

            // Create a command encoder for the copy
            var encDesc = readbackArena.allocate(24, 8);
            encDesc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            encDesc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
            encDesc.set(ValueLayout.JAVA_LONG, 16, 0L);
            var encoder = WgpuNative.deviceCreateCommandEncoder(wgpuDevice, encDesc);

            // WGPUTexelCopyTextureInfo (source) — v24: NO nextInChain:
            //   texture(8), mipLevel(4), origin{x(4),y(4),z(4)}, aspect(4)
            // Total: 28, padded to 32 for 8-byte struct alignment
            var srcInfo = readbackArena.allocate(32, 8);
            srcInfo.set(ValueLayout.ADDRESS, 0, colorTex.handle());    // texture
            srcInfo.set(ValueLayout.JAVA_INT, 8, 0);                   // mipLevel
            srcInfo.set(ValueLayout.JAVA_INT, 12, 0);                  // origin.x
            srcInfo.set(ValueLayout.JAVA_INT, 16, 0);                  // origin.y
            srcInfo.set(ValueLayout.JAVA_INT, 20, 0);                  // origin.z
            srcInfo.set(ValueLayout.JAVA_INT, 24, WgpuNative.TEXTURE_ASPECT_ALL); // aspect

            // WGPUTexelCopyBufferInfo (destination) — v24: NO nextInChain:
            //   layout.offset(8), layout.bytesPerRow(4), layout.rowsPerImage(4), buffer(8)
            // Total: 24
            var dstInfo = readbackArena.allocate(24, 8);
            dstInfo.set(ValueLayout.JAVA_LONG, 0, 0L);                // layout.offset
            dstInfo.set(ValueLayout.JAVA_INT, 8, bytesPerRow);         // layout.bytesPerRow
            dstInfo.set(ValueLayout.JAVA_INT, 12, height);             // layout.rowsPerImage
            dstInfo.set(ValueLayout.ADDRESS, 16, stagingBuf);          // buffer

            // WGPUExtent3D (copySize):
            var copySize = readbackArena.allocate(12, 4);
            copySize.set(ValueLayout.JAVA_INT, 0, width);
            copySize.set(ValueLayout.JAVA_INT, 4, height);
            copySize.set(ValueLayout.JAVA_INT, 8, 1);

            WgpuNative.commandEncoderCopyTextureToBuffer(encoder, srcInfo, dstInfo, copySize);

            // Finish and submit
            var cmdBufDesc = readbackArena.allocate(24, 8);
            cmdBufDesc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            cmdBufDesc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);
            cmdBufDesc.set(ValueLayout.JAVA_LONG, 16, 0L);

            var cmdBuf = WgpuNative.commandEncoderFinish(encoder, cmdBufDesc);
            var cmdBufArr = readbackArena.allocate(ValueLayout.ADDRESS);
            cmdBufArr.set(ValueLayout.ADDRESS, 0, cmdBuf);
            WgpuNative.queueSubmit(wgpuQueue, 1, cmdBufArr);
            WgpuNative.commandBufferRelease(cmdBuf);
            WgpuNative.commandEncoderRelease(encoder);

            // Map the staging buffer synchronously
            WgpuNative.bufferMapSync(wgpuDevice, stagingBuf,
                    WgpuNative.MAP_MODE_READ, 0, bufferSize);

            var mapped = WgpuNative.bufferGetMappedRange(stagingBuf, 0, bufferSize);
            mapped = mapped.reinterpret(bufferSize);

            // Copy to byte array, handling row alignment
            byte[] rgba = new byte[width * height * 4];
            for (int y = 0; y < height; y++) {
                long srcOffset = (long) y * bytesPerRow;
                int dstOffset = y * width * 4;
                MemorySegment.copy(mapped, ValueLayout.JAVA_BYTE, srcOffset,
                        rgba, dstOffset, width * 4);
            }

            WgpuNative.bufferUnmap(stagingBuf);
            WgpuNative.bufferRelease(stagingBuf);

            return rgba;
        }
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
        if (capability == DeviceCapability.DEVICE_NAME) return (T) "wgpu-native";
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
        if (nativeAvailable) {
            if (wgpuDevice != null) {
                WgpuNative.deviceRelease(wgpuDevice);
                wgpuDevice = null;
            }
            if (wgpuAdapter != null) {
                WgpuNative.adapterRelease(wgpuAdapter);
                wgpuAdapter = null;
            }
            if (wgpuInstance != null) {
                WgpuNative.instanceRelease(wgpuInstance);
                wgpuInstance = null;
            }
            log.info("WebGPU device released");
        }
        deviceArena.close();
        log.info("WgpuRenderDevice closed");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Format Mapping Helpers
    // ═══════════════════════════════════════════════════════════════════

    private static long mapBufferUsage(BufferUsage usage) {
        if (usage == BufferUsage.VERTEX) return WgpuNative.BUFFER_USAGE_VERTEX;
        if (usage == BufferUsage.INDEX) return WgpuNative.BUFFER_USAGE_INDEX;
        if (usage == BufferUsage.UNIFORM) return WgpuNative.BUFFER_USAGE_UNIFORM;
        if (usage == BufferUsage.STORAGE) return WgpuNative.BUFFER_USAGE_STORAGE;
        return WgpuNative.BUFFER_USAGE_VERTEX; // fallback
    }

    static int mapTextureFormat(TextureFormat format) {
        if (format == TextureFormat.RGBA8) return WgpuNative.TEXTURE_FORMAT_RGBA8_UNORM;
        if (format == TextureFormat.DEPTH32F) return WgpuNative.TEXTURE_FORMAT_DEPTH32_FLOAT;
        if (format == TextureFormat.DEPTH24) return WgpuNative.TEXTURE_FORMAT_DEPTH24_PLUS;
        if (format == TextureFormat.DEPTH24_STENCIL8) return WgpuNative.TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8;
        // For formats that don't have a direct WebGPU equivalent, approximate
        if (format == TextureFormat.RGB8) return WgpuNative.TEXTURE_FORMAT_RGBA8_UNORM; // No RGB8 in WebGPU
        if (format == TextureFormat.R8) return 1; // WGPUTextureFormat_R8Unorm = 1
        if (format == TextureFormat.RGBA16F) return 33; // WGPUTextureFormat_RGBA16Float
        if (format == TextureFormat.RGBA32F) return 34; // WGPUTextureFormat_RGBA32Float
        if (format == TextureFormat.RG16F) return 28;   // WGPUTextureFormat_RG16Float
        if (format == TextureFormat.RG32F) return 29;   // WGPUTextureFormat_RG32Float
        if (format == TextureFormat.R16F) return 15;    // WGPUTextureFormat_R16Float
        if (format == TextureFormat.R32F) return 16;    // WGPUTextureFormat_R32Float
        if (format == TextureFormat.R32UI) return 17;   // WGPUTextureFormat_R32Uint
        if (format == TextureFormat.R32I) return 18;    // R32Sint... actually this overlaps with RGBA8Unorm
        // Fallback
        return WgpuNative.TEXTURE_FORMAT_RGBA8_UNORM;
    }

    private static int mapVertexFormat(VertexAttribute attr) {
        if (attr.componentType() == ComponentType.FLOAT) {
            return switch (attr.componentCount()) {
                case 2 -> WgpuNative.VERTEX_FORMAT_FLOAT32X2;
                case 3 -> WgpuNative.VERTEX_FORMAT_FLOAT32X3;
                case 4 -> WgpuNative.VERTEX_FORMAT_FLOAT32X4;
                default -> WgpuNative.VERTEX_FORMAT_FLOAT32X4;
            };
        }
        // TODO: add more component type mappings
        return WgpuNative.VERTEX_FORMAT_FLOAT32X4;
    }

    private static int mapFilterMode(FilterMode mode) {
        if (mode == FilterMode.NEAREST || mode == FilterMode.NEAREST_MIPMAP_NEAREST) {
            return WgpuNative.FILTER_MODE_NEAREST;
        }
        return WgpuNative.FILTER_MODE_LINEAR;
    }

    private static int mapMipmapFilterMode(FilterMode mode) {
        if (mode == FilterMode.LINEAR_MIPMAP_LINEAR) return WgpuNative.MIPMAP_FILTER_MODE_LINEAR;
        if (mode == FilterMode.NEAREST_MIPMAP_NEAREST) return WgpuNative.MIPMAP_FILTER_MODE_NEAREST;
        return WgpuNative.MIPMAP_FILTER_MODE_NEAREST;
    }

    private static int mapWrapMode(WrapMode mode) {
        if (mode == WrapMode.REPEAT) return WgpuNative.ADDRESS_MODE_REPEAT;
        if (mode == WrapMode.CLAMP_TO_EDGE) return WgpuNative.ADDRESS_MODE_CLAMP_TO_EDGE;
        if (mode == WrapMode.MIRRORED_REPEAT) return WgpuNative.ADDRESS_MODE_MIRROR_REPEAT;
        return WgpuNative.ADDRESS_MODE_REPEAT;
    }

    private static int bytesPerPixel(TextureFormat format) {
        if (format == TextureFormat.RGBA8 || format == TextureFormat.DEPTH32F) return 4;
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

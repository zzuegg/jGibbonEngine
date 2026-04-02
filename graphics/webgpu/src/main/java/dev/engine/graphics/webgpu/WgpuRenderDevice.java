package dev.engine.graphics.webgpu;

import dev.engine.bindings.wgpu.WgpuNative;
import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderCapability;
import dev.engine.graphics.RenderContext;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.RenderTargetResource;
import dev.engine.graphics.SamplerResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.vertex.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebGPU render device backed by wgpu-native via FFM bindings.
 *
 * <p>When wgpu-native is not installed, the device still constructs
 * successfully but operates without a native instance. All handle
 * management works through {@link HandlePool} regardless.
 */
public class WgpuRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(WgpuRenderDevice.class);

    private final HandlePool<BufferResource> bufferPool = new HandlePool<>();
    private final Map<Integer, Long> bufferSizes = new HashMap<>();
    private final HandlePool<TextureResource> texturePool = new HandlePool<>();
    private final HandlePool<RenderTargetResource> renderTargetPool = new HandlePool<>();
    private final Map<Integer, List<Handle<TextureResource>>> renderTargetColorTextures = new HashMap<>();
    private final HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();
    private final HandlePool<SamplerResource> samplerPool = new HandlePool<>();
    private final HandlePool<PipelineResource> pipelinePool = new HandlePool<>();
    private final AtomicLong frameCounter = new AtomicLong(0);

    private final boolean nativeAvailable;
    private MemorySegment wgpuInstance;

    public WgpuRenderDevice() {
        nativeAvailable = WgpuNative.isAvailable();
        if (nativeAvailable) {
            wgpuInstance = WgpuNative.createInstance();
            log.info("WebGPU device created with wgpu-native instance");
        } else {
            wgpuInstance = null;
            log.warn("wgpu-native is not installed; WebGPU device running without native backend");
        }
    }

    // --- Buffer ---

    @Override
    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        var handle = bufferPool.allocate();
        bufferSizes.put(handle.index(), descriptor.size());
        return handle;
    }

    @Override
    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!bufferPool.isValid(buffer)) return;
        bufferSizes.remove(buffer.index());
        bufferPool.release(buffer);
    }

    @Override
    public boolean isValidBuffer(Handle<BufferResource> buffer) {
        return bufferPool.isValid(buffer);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        long size = bufferSizes.getOrDefault(buffer.index(), 0L);
        return writeBuffer(buffer, 0, size);
    }

    @Override
    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        var arena = Arena.ofConfined();
        var segment = arena.allocate(length);
        return new BufferWriter() {
            @Override
            public MemorySegment segment() {
                return segment;
            }

            @Override
            public void close() {
                // TODO: upload segment data to wgpu buffer when native backend is wired
                arena.close();
            }
        };
    }

    // --- Texture ---

    @Override
    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        return texturePool.allocate();
    }

    @Override
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        // TODO: upload pixel data via wgpu queue
    }

    @Override
    public void destroyTexture(Handle<TextureResource> texture) {
        if (!texturePool.isValid(texture)) return;
        texturePool.release(texture);
    }

    @Override
    public boolean isValidTexture(Handle<TextureResource> texture) {
        return texturePool.isValid(texture);
    }

    // --- Render Target ---

    @Override
    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        var colorTextures = new ArrayList<Handle<TextureResource>>();
        for (int i = 0; i < descriptor.colorAttachments().size(); i++) {
            colorTextures.add(texturePool.allocate());
        }
        var handle = renderTargetPool.allocate();
        renderTargetColorTextures.put(handle.index(), colorTextures);
        return handle;
    }

    @Override
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> renderTarget, int index) {
        return renderTargetColorTextures.get(renderTarget.index()).get(index);
    }

    @Override
    public void destroyRenderTarget(Handle<RenderTargetResource> renderTarget) {
        if (!renderTargetPool.isValid(renderTarget)) return;
        var textures = renderTargetColorTextures.remove(renderTarget.index());
        if (textures != null) {
            for (var tex : textures) {
                destroyTexture(tex);
            }
        }
        renderTargetPool.release(renderTarget);
    }

    // --- Vertex Input ---

    @Override
    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        return vertexInputPool.allocate();
    }

    @Override
    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        if (!vertexInputPool.isValid(vertexInput)) return;
        vertexInputPool.release(vertexInput);
    }

    // --- Sampler ---

    @Override
    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        return samplerPool.allocate();
    }

    @Override
    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!samplerPool.isValid(sampler)) return;
        samplerPool.release(sampler);
    }

    // --- Pipeline ---

    @Override
    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        return pipelinePool.allocate();
    }

    @Override
    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipelinePool.isValid(pipeline)) return;
        pipelinePool.release(pipeline);
    }

    @Override
    public boolean isValidPipeline(Handle<PipelineResource> pipeline) {
        return pipelinePool.isValid(pipeline);
    }

    // --- Frame ---

    @Override
    public RenderContext beginFrame() {
        long frame = frameCounter.incrementAndGet();
        return new NoOpRenderContext(frame);
    }

    @Override
    public void endFrame(RenderContext context) {
        // TODO: present frame via wgpu surface
    }

    // --- Capabilities ---

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(RenderCapability<T> capability) {
        if (capability == RenderCapability.MAX_TEXTURE_SIZE) {
            return (T) Integer.valueOf(4096);
        }
        if (capability == RenderCapability.MAX_FRAMEBUFFER_WIDTH) {
            return (T) Integer.valueOf(4096);
        }
        if (capability == RenderCapability.MAX_FRAMEBUFFER_HEIGHT) {
            return (T) Integer.valueOf(4096);
        }
        return null;
    }

    // --- Lifecycle ---

    @Override
    public void close() {
        if (wgpuInstance != null) {
            WgpuNative.instanceRelease(wgpuInstance);
            wgpuInstance = null;
            log.info("WebGPU instance released");
        }
        log.info("WgpuRenderDevice closed");
    }

    // ------------------------------------------------------------------
    // Inner no-op RenderContext returned by beginFrame()
    // ------------------------------------------------------------------

    private record NoOpRenderContext(long frame) implements RenderContext {

        @Override
        public long frameNumber() {
            return frame;
        }

        @Override
        public void bindPipeline(Handle<PipelineResource> pipeline) {}

        @Override
        public void bindVertexBuffer(Handle<BufferResource> buffer, Handle<VertexInputResource> vertexInput) {}

        @Override
        public void bindIndexBuffer(Handle<BufferResource> buffer) {}

        @Override
        public void bindUniformBuffer(int binding, Handle<BufferResource> buffer) {}

        @Override
        public void bindTexture(int unit, Handle<TextureResource> texture) {}

        @Override
        public void bindSampler(int unit, Handle<SamplerResource> sampler) {}

        @Override
        public void draw(int vertexCount, int firstVertex) {}

        @Override
        public void drawIndexed(int indexCount, int firstIndex) {}

        @Override
        public void bindRenderTarget(Handle<RenderTargetResource> renderTarget) {}

        @Override
        public void bindDefaultRenderTarget() {}

        @Override
        public void setDepthTest(boolean enabled) {}

        @Override
        public void setBlending(boolean enabled) {}

        @Override
        public void setCullFace(boolean enabled) {}

        @Override
        public void setWireframe(boolean enabled) {}

        @Override
        public void clear(float r, float g, float b, float a) {}

        @Override
        public void viewport(int x, int y, int width, int height) {}

        @Override
        public void scissor(int x, int y, int width, int height) {}
    }
}

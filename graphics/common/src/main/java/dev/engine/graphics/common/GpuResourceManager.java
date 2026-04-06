package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.core.mesh.VertexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified GPU resource lifecycle layer.
 *
 * <p>All GPU resource creation and destruction goes through this manager.
 * Provides:
 * <ul>
 *   <li>Unified tracking of all live resources</li>
 *   <li>Deferred destruction — resources queued mid-frame are destroyed at frame end</li>
 *   <li>Leak detection on shutdown</li>
 *   <li>Resource statistics</li>
 * </ul>
 *
 * <p>Domain managers ({@link MeshManager}, {@link TextureManager}, etc.)
 * use this instead of {@link RenderDevice} directly.
 */
public class GpuResourceManager {

    private static final Logger log = LoggerFactory.getLogger(GpuResourceManager.class);

    private final RenderDevice device;

    // Live resource counts
    private final AtomicInteger liveBuffers = new AtomicInteger();
    private final AtomicInteger liveTextures = new AtomicInteger();
    private final AtomicInteger liveRenderTargets = new AtomicInteger();
    private final AtomicInteger livePipelines = new AtomicInteger();
    private final AtomicInteger liveSamplers = new AtomicInteger();
    private final AtomicInteger liveVertexInputs = new AtomicInteger();

    // Deferred deletion queues — synchronized for thread safety, ArrayDeque for TeaVM compatibility
    private java.util.Queue<Runnable> deletionQueue = new java.util.ArrayDeque<>();
    private java.util.Queue<Runnable> pendingQueue = new java.util.ArrayDeque<>();

    public GpuResourceManager(RenderDevice device) {
        this.device = device;
    }

    // --- Buffers ---

    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        var handle = device.createBuffer(descriptor);
        liveBuffers.incrementAndGet();
        return handle;
    }

    public Handle<BufferResource> createBuffer(long size, BufferUsage usage, AccessPattern access) {
        return createBuffer(new BufferDescriptor(size, usage, access));
    }

    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        return device.writeBuffer(buffer);
    }

    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        return device.writeBuffer(buffer, offset, length);
    }

    public synchronized void destroyBuffer(Handle<BufferResource> buffer) {
        deletionQueue.add(() -> {
            device.destroyBuffer(buffer);
            liveBuffers.decrementAndGet();
        });
    }

    // --- Textures ---

    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        var handle = device.createTexture(descriptor);
        liveTextures.incrementAndGet();
        return handle;
    }

    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        device.uploadTexture(texture, pixels);
    }

    public synchronized void destroyTexture(Handle<TextureResource> texture) {
        deletionQueue.add(() -> {
            device.destroyTexture(texture);
            liveTextures.decrementAndGet();
        });
    }

    // --- Render targets ---

    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        var handle = device.createRenderTarget(descriptor);
        liveRenderTargets.incrementAndGet();
        return handle;
    }

    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> rt, int index) {
        return device.getRenderTargetColorTexture(rt, index);
    }

    public synchronized void destroyRenderTarget(Handle<RenderTargetResource> rt) {
        deletionQueue.add(() -> {
            device.destroyRenderTarget(rt);
            liveRenderTargets.decrementAndGet();
        });
    }

    // --- Pipelines ---

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        var handle = device.createPipeline(descriptor);
        livePipelines.incrementAndGet();
        return handle;
    }

    public synchronized void destroyPipeline(Handle<PipelineResource> pipeline) {
        deletionQueue.add(() -> {
            device.destroyPipeline(pipeline);
            livePipelines.decrementAndGet();
        });
    }

    // --- Samplers ---

    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        var handle = device.createSampler(descriptor);
        liveSamplers.incrementAndGet();
        return handle;
    }

    public synchronized void destroySampler(Handle<SamplerResource> sampler) {
        deletionQueue.add(() -> {
            device.destroySampler(sampler);
            liveSamplers.decrementAndGet();
        });
    }

    // --- Vertex inputs ---

    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        var handle = device.createVertexInput(format);
        liveVertexInputs.incrementAndGet();
        return handle;
    }

    public synchronized void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        deletionQueue.add(() -> {
            device.destroyVertexInput(vertexInput);
            liveVertexInputs.decrementAndGet();
        });
    }

    // --- Frame lifecycle ---

    /**
     * Processes the deferred deletion queue.
     * Call once per frame after GPU submission is complete (after endFrame).
     */
    public synchronized void processDeferred() {
        // Swap queues — pending from last frame is now safe to delete
        var toDelete = pendingQueue;
        pendingQueue = deletionQueue;
        deletionQueue = new java.util.ArrayDeque<>();

        Runnable action;
        while ((action = toDelete.poll()) != null) {
            action.run();
        }
    }

    // --- Stats ---

    public int liveBufferCount() { return liveBuffers.get(); }
    public int liveTextureCount() { return liveTextures.get(); }
    public int liveRenderTargetCount() { return liveRenderTargets.get(); }
    public int livePipelineCount() { return livePipelines.get(); }
    public int liveSamplerCount() { return liveSamplers.get(); }
    public int liveVertexInputCount() { return liveVertexInputs.get(); }

    public int totalLiveResources() {
        return liveBuffers.get() + liveTextures.get() + liveRenderTargets.get()
             + livePipelines.get() + liveSamplers.get() + liveVertexInputs.get();
    }

    // --- Shutdown ---

    /**
     * Reports any leaked resources and destroys everything immediately.
     * Call during engine shutdown.
     */
    public synchronized void shutdown() {
        // Flush both queues immediately
        for (var action : deletionQueue) action.run();
        deletionQueue.clear();
        for (var action : pendingQueue) action.run();
        pendingQueue.clear();

        int leaks = totalLiveResources();
        if (leaks > 0) {
            log.warn("GPU resource leaks detected at shutdown: {} buffers, {} textures, {} render targets, {} pipelines, {} samplers, {} vertex inputs",
                    liveBuffers.get(), liveTextures.get(), liveRenderTargets.get(),
                    livePipelines.get(), liveSamplers.get(), liveVertexInputs.get());
        }
    }

    /** Direct device access — escape hatch for operations not covered by this manager. */
    public RenderDevice device() { return device; }
}

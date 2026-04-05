package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.profiler.ResourceStats;
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

    /** Well-known resource type keys used by this manager. */
    public static final String BUFFER        = "buffer";
    public static final String TEXTURE       = "texture";
    public static final String RENDER_TARGET = "render_target";
    public static final String PIPELINE      = "pipeline";
    public static final String SAMPLER       = "sampler";
    public static final String VERTEX_INPUT  = "vertex_input";

    private final RenderDevice device;
    private final ResourceStats resourceStats;

    // Deferred deletion queues (thread-safe for concurrent destroy calls)
    private volatile java.util.Queue<Runnable> deletionQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile java.util.Queue<Runnable> pendingQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public GpuResourceManager(RenderDevice device) {
        this(device, new ResourceStats());
    }

    public GpuResourceManager(RenderDevice device, ResourceStats resourceStats) {
        this.device = device;
        this.resourceStats = resourceStats;
        // Pre-register well-known types
        resourceStats.register(BUFFER);
        resourceStats.register(TEXTURE);
        resourceStats.register(RENDER_TARGET);
        resourceStats.register(PIPELINE);
        resourceStats.register(SAMPLER);
        resourceStats.register(VERTEX_INPUT);
    }

    // --- Buffers ---

    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        var handle = device.createBuffer(descriptor);
        resourceStats.recordCreate(BUFFER);
        return handle;
    }

    public Handle<BufferResource> createBuffer(long size, BufferUsage usage, AccessPattern access) {
        return createBuffer(new BufferDescriptor(size, usage, access));
    }

    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        resourceStats.recordUpdate(BUFFER);
        return device.writeBuffer(buffer);
    }

    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        resourceStats.recordUpdate(BUFFER);
        return device.writeBuffer(buffer, offset, length);
    }

    public void destroyBuffer(Handle<BufferResource> buffer) {
        deletionQueue.add(() -> {
            device.destroyBuffer(buffer);
            resourceStats.recordDestroy(BUFFER);
        });
    }

    // --- Textures ---

    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        var handle = device.createTexture(descriptor);
        resourceStats.recordCreate(TEXTURE);
        return handle;
    }

    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        resourceStats.recordUpdate(TEXTURE);
        device.uploadTexture(texture, pixels);
    }

    public void destroyTexture(Handle<TextureResource> texture) {
        deletionQueue.add(() -> {
            device.destroyTexture(texture);
            resourceStats.recordDestroy(TEXTURE);
        });
    }

    // --- Render targets ---

    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        var handle = device.createRenderTarget(descriptor);
        resourceStats.recordCreate(RENDER_TARGET);
        return handle;
    }

    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> rt, int index) {
        resourceStats.recordUse(RENDER_TARGET);
        return device.getRenderTargetColorTexture(rt, index);
    }

    public void destroyRenderTarget(Handle<RenderTargetResource> rt) {
        deletionQueue.add(() -> {
            device.destroyRenderTarget(rt);
            resourceStats.recordDestroy(RENDER_TARGET);
        });
    }

    // --- Pipelines ---

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        var handle = device.createPipeline(descriptor);
        resourceStats.recordCreate(PIPELINE);
        return handle;
    }

    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        deletionQueue.add(() -> {
            device.destroyPipeline(pipeline);
            resourceStats.recordDestroy(PIPELINE);
        });
    }

    // --- Samplers ---

    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        var handle = device.createSampler(descriptor);
        resourceStats.recordCreate(SAMPLER);
        return handle;
    }

    public void destroySampler(Handle<SamplerResource> sampler) {
        deletionQueue.add(() -> {
            device.destroySampler(sampler);
            resourceStats.recordDestroy(SAMPLER);
        });
    }

    // --- Vertex inputs ---

    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        var handle = device.createVertexInput(format);
        resourceStats.recordCreate(VERTEX_INPUT);
        return handle;
    }

    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        deletionQueue.add(() -> {
            device.destroyVertexInput(vertexInput);
            resourceStats.recordDestroy(VERTEX_INPUT);
        });
    }

    // --- Frame lifecycle ---

    /**
     * Processes the deferred deletion queue.
     * Call once per frame after GPU submission is complete (after endFrame).
     */
    public void processDeferred() {
        // Swap queues — pending from last frame is now safe to delete
        var toDelete = pendingQueue;
        pendingQueue = deletionQueue;
        deletionQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

        Runnable action;
        while ((action = toDelete.poll()) != null) {
            action.run();
        }
    }

    // --- Stats ---

    /** Returns the ResourceStats instance tracking all resource lifecycle operations. */
    public ResourceStats resourceStats() { return resourceStats; }

    public int liveBufferCount() { return resourceStats.liveCount(BUFFER); }
    public int liveTextureCount() { return resourceStats.liveCount(TEXTURE); }
    public int liveRenderTargetCount() { return resourceStats.liveCount(RENDER_TARGET); }
    public int livePipelineCount() { return resourceStats.liveCount(PIPELINE); }
    public int liveSamplerCount() { return resourceStats.liveCount(SAMPLER); }
    public int liveVertexInputCount() { return resourceStats.liveCount(VERTEX_INPUT); }

    public int totalLiveResources() { return resourceStats.totalLiveCount(); }

    // --- Shutdown ---

    /**
     * Reports any leaked resources and destroys everything immediately.
     * Call during engine shutdown.
     */
    public void shutdown() {
        // Flush both queues immediately
        for (var action : deletionQueue) action.run();
        deletionQueue.clear();
        for (var action : pendingQueue) action.run();
        pendingQueue.clear();

        int leaks = totalLiveResources();
        if (leaks > 0) {
            var sb = new StringBuilder("GPU resource leaks detected at shutdown:");
            for (var type : resourceStats.resourceTypes()) {
                int count = resourceStats.liveCount(type);
                if (count > 0) sb.append(" ").append(count).append(" ").append(type).append(",");
            }
            log.warn(sb.toString());
        }
    }

    /** Direct device access — escape hatch for operations not covered by this manager. */
    public RenderDevice device() { return device; }
}

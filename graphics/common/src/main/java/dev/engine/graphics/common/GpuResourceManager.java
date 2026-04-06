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
 *   <li>Cleaner-based safety net — unreachable handles are automatically cleaned by GC</li>
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
    public static final String BUFFER_VERTEX = "buffer/vertex";
    public static final String BUFFER_INDEX  = "buffer/index";
    public static final String BUFFER_UNIFORM = "buffer/uniform";
    public static final String BUFFER_STORAGE = "buffer/storage";
    public static final String TEXTURE       = "texture";
    public static final String RENDER_TARGET = "render_target";
    public static final String PIPELINE      = "pipeline";
    public static final String SAMPLER       = "sampler";
    public static final String VERTEX_INPUT  = "vertex_input";

    private final RenderDevice device;
    private final ResourceStats resourceStats;

    // WeakHashMap: buffer handles must not be kept strongly reachable by this map,
    // otherwise the Cleaner can never collect abandoned handles.
    // Synchronized externally since WeakHashMap is not thread-safe.
    private final java.util.Map<Handle<BufferResource>, String> bufferTypes =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    // Set to true after shutdown — Cleaner actions check this to avoid
    // queueing GPU operations after the device is released.
    private volatile boolean shuttingDown = false;

    // Ring of deferred deletion queues. Resources queued in frame N are deleted
    // after deferralDepth frames, ensuring in-flight GPU frames don't reference freed resources.
    // deferralDepth = framesInFlight + 1 (e.g. 3 queues for 2 frames-in-flight).
    private final java.util.Queue<Runnable>[] deferralRing;
    private int deferralHead = 0;
    // Active queue for new deletions (thread-safe for Cleaner thread)
    private volatile java.util.Queue<Runnable> deletionQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public GpuResourceManager(RenderDevice device) {
        this(device, new ResourceStats());
    }

    @SuppressWarnings("unchecked")
    public GpuResourceManager(RenderDevice device, ResourceStats resourceStats) {
        this.device = device;
        this.resourceStats = resourceStats;

        // Query frames-in-flight from the device (default 1 for immediate-mode backends)
        var fif = device.queryCapability(DeviceCapability.FRAMES_IN_FLIGHT);
        int framesInFlight = (fif != null && fif > 0) ? fif : 1;
        // Need framesInFlight + 1 deferral stages: current frame + N in-flight
        this.deferralRing = new java.util.concurrent.ConcurrentLinkedQueue[framesInFlight + 1];
        for (int i = 0; i < deferralRing.length; i++) {
            deferralRing[i] = new java.util.concurrent.ConcurrentLinkedQueue<>();
        }
        log.debug("GpuResourceManager: framesInFlight={}, deferralDepth={}", framesInFlight, deferralRing.length);

        // Pre-register well-known types
        resourceStats.register(BUFFER);
        resourceStats.register(BUFFER_VERTEX);
        resourceStats.register(BUFFER_INDEX);
        resourceStats.register(BUFFER_UNIFORM);
        resourceStats.register(BUFFER_STORAGE);
        resourceStats.register(TEXTURE);
        resourceStats.register(RENDER_TARGET);
        resourceStats.register(PIPELINE);
        resourceStats.register(SAMPLER);
        resourceStats.register(VERTEX_INPUT);
    }

    // --- Buffers ---

    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) {
        var handle = device.createBuffer(descriptor);
        var subType = bufferSubType(descriptor.usage());
        bufferTypes.put(handle, subType);
        resourceStats.recordCreate(BUFFER);
        resourceStats.recordCreate(subType);

        // Capture primitives for Cleaner (must not capture 'handle' — that prevents GC)
        var idx = handle.index();
        var gen = handle.generation();
        handle.registerCleanup(() -> {
            log.warn("GPU buffer leak: handle[{},{}] type={} was not explicitly destroyed — cleaned by GC", idx, gen, subType);
            if (shuttingDown) return;
            deferBufferCleanup(idx, gen, subType);
        });

        return handle;
    }

    /** Called by Cleaner — queues deferred GPU buffer destruction. */
    private void deferBufferCleanup(int index, int generation, String subType) {
        var cleanupHandle = new Handle<BufferResource>(index, generation);
        bufferTypes.remove(cleanupHandle);
        deletionQueue.add(() -> {
            device.destroyBuffer(cleanupHandle);
            resourceStats.recordDestroy(BUFFER);
            resourceStats.recordDestroy(subType);
        });
    }

    public Handle<BufferResource> createBuffer(long size, BufferUsage usage, AccessPattern access) {
        return createBuffer(new BufferDescriptor(size, usage, access));
    }

    public BufferWriter writeBuffer(Handle<BufferResource> buffer) {
        resourceStats.recordUpdate(BUFFER);
        resourceStats.recordUpdate(bufferTypes.getOrDefault(buffer, BUFFER));
        return device.writeBuffer(buffer);
    }

    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) {
        resourceStats.recordUpdate(BUFFER);
        resourceStats.recordUpdate(bufferTypes.getOrDefault(buffer, BUFFER));
        return device.writeBuffer(buffer, offset, length);
    }

    public void destroyBuffer(Handle<BufferResource> buffer) {
        if (!buffer.markClosed()) return; // already destroyed or cleaned
        var subType = bufferTypes.remove(buffer);
        deletionQueue.add(() -> {
            device.destroyBuffer(buffer);
            resourceStats.recordDestroy(BUFFER);
            if (subType != null) resourceStats.recordDestroy(subType);
        });
    }

    // --- Textures ---

    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) {
        var handle = device.createTexture(descriptor);
        resourceStats.recordCreate(TEXTURE);

        var idx = handle.index();
        var gen = handle.generation();
        handle.registerCleanup(() -> {
            log.warn("GPU texture leak: handle[{},{}] was not explicitly destroyed — cleaned by GC", idx, gen);
            if (shuttingDown) return;
            deletionQueue.add(() -> {
                device.destroyTexture(new Handle<>(idx, gen));
                resourceStats.recordDestroy(TEXTURE);
            });
        });

        return handle;
    }

    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) {
        resourceStats.recordUpdate(TEXTURE);
        device.uploadTexture(texture, pixels);
    }

    public void destroyTexture(Handle<TextureResource> texture) {
        if (!texture.markClosed()) return;
        deletionQueue.add(() -> {
            device.destroyTexture(texture);
            resourceStats.recordDestroy(TEXTURE);
        });
    }

    // --- Render targets ---

    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) {
        var handle = device.createRenderTarget(descriptor);
        resourceStats.recordCreate(RENDER_TARGET);

        var idx = handle.index();
        var gen = handle.generation();
        handle.registerCleanup(() -> {
            log.warn("GPU render target leak: handle[{},{}] was not explicitly destroyed — cleaned by GC", idx, gen);
            if (shuttingDown) return;
            deletionQueue.add(() -> {
                device.destroyRenderTarget(new Handle<>(idx, gen));
                resourceStats.recordDestroy(RENDER_TARGET);
            });
        });

        return handle;
    }

    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> rt, int index) {
        return device.getRenderTargetColorTexture(rt, index);
    }

    public void destroyRenderTarget(Handle<RenderTargetResource> rt) {
        if (!rt.markClosed()) return;
        deletionQueue.add(() -> {
            device.destroyRenderTarget(rt);
            resourceStats.recordDestroy(RENDER_TARGET);
        });
    }

    // --- Pipelines ---

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        var handle = device.createPipeline(descriptor);
        resourceStats.recordCreate(PIPELINE);

        var idx = handle.index();
        var gen = handle.generation();
        handle.registerCleanup(() -> {
            log.warn("GPU pipeline leak: handle[{},{}] was not explicitly destroyed — cleaned by GC", idx, gen);
            if (shuttingDown) return;
            deletionQueue.add(() -> {
                device.destroyPipeline(new Handle<>(idx, gen));
                resourceStats.recordDestroy(PIPELINE);
            });
        });

        return handle;
    }

    public void destroyPipeline(Handle<PipelineResource> pipeline) {
        if (!pipeline.markClosed()) return;
        deletionQueue.add(() -> {
            device.destroyPipeline(pipeline);
            resourceStats.recordDestroy(PIPELINE);
        });
    }

    // --- Samplers ---

    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) {
        var handle = device.createSampler(descriptor);
        resourceStats.recordCreate(SAMPLER);

        var idx = handle.index();
        var gen = handle.generation();
        handle.registerCleanup(() -> {
            log.warn("GPU sampler leak: handle[{},{}] was not explicitly destroyed — cleaned by GC", idx, gen);
            if (shuttingDown) return;
            deletionQueue.add(() -> {
                device.destroySampler(new Handle<>(idx, gen));
                resourceStats.recordDestroy(SAMPLER);
            });
        });

        return handle;
    }

    public void destroySampler(Handle<SamplerResource> sampler) {
        if (!sampler.markClosed()) return;
        deletionQueue.add(() -> {
            device.destroySampler(sampler);
            resourceStats.recordDestroy(SAMPLER);
        });
    }

    // --- Vertex inputs ---

    public Handle<VertexInputResource> createVertexInput(VertexFormat format) {
        var handle = device.createVertexInput(format);
        resourceStats.recordCreate(VERTEX_INPUT);

        var idx = handle.index();
        var gen = handle.generation();
        handle.registerCleanup(() -> {
            log.warn("GPU vertex input leak: handle[{},{}] was not explicitly destroyed — cleaned by GC", idx, gen);
            if (shuttingDown) return;
            deletionQueue.add(() -> {
                device.destroyVertexInput(new Handle<>(idx, gen));
                resourceStats.recordDestroy(VERTEX_INPUT);
            });
        });

        return handle;
    }

    public void destroyVertexInput(Handle<VertexInputResource> vertexInput) {
        if (!vertexInput.markClosed()) return;
        deletionQueue.add(() -> {
            device.destroyVertexInput(vertexInput);
            resourceStats.recordDestroy(VERTEX_INPUT);
        });
    }

    // --- Frame lifecycle ---

    /**
     * Processes the deferred deletion queue.
     * Call once per frame after GPU submission is complete (after endFrame).
     *
     * <p>Resources are deferred through a ring of queues sized to match
     * the device's frames-in-flight count. A resource queued in frame N
     * is deleted after {@code framesInFlight + 1} calls to processDeferred(),
     * ensuring all in-flight GPU frames have finished using it.
     */
    public void processDeferred() {
        // Move current deletionQueue into the ring at the head position
        var incoming = deletionQueue;
        deletionQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();

        // The oldest slot in the ring is safe to process — enough frames have passed
        var toDelete = deferralRing[deferralHead];

        // Store incoming deletions into this slot (will be processed after a full ring rotation)
        deferralRing[deferralHead] = incoming;

        // Advance head
        deferralHead = (deferralHead + 1) % deferralRing.length;

        // Process the old queue
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
        shuttingDown = true;
        // Flush the active deletion queue
        Runnable action;
        while ((action = deletionQueue.poll()) != null) action.run();
        // Flush all ring slots
        for (var queue : deferralRing) {
            while ((action = queue.poll()) != null) action.run();
        }

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

    /** Returns the resource stats sub-type key for a buffer handle, or {@link #BUFFER} if unknown. */
    public String bufferSubType(Handle<BufferResource> buffer) {
        return bufferTypes.getOrDefault(buffer, BUFFER);
    }

    /** Direct device access — escape hatch for operations not covered by this manager. */
    public RenderDevice device() { return device; }

    private static String bufferSubType(BufferUsage usage) {
        return switch (usage.name()) {
            case "VERTEX"  -> BUFFER_VERTEX;
            case "INDEX"   -> BUFFER_INDEX;
            case "UNIFORM" -> BUFFER_UNIFORM;
            case "STORAGE" -> BUFFER_STORAGE;
            default        -> BUFFER;
        };
    }
}

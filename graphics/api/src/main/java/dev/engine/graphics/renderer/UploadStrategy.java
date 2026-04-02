package dev.engine.graphics.renderer;

import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.command.CommandRecorder;

import java.util.List;

/**
 * Strategy for uploading scene data to GPU and issuing draw commands.
 *
 * <p>Implementations control how transforms, materials, and draw calls
 * are organized. The default per-object UBO approach works everywhere
 * but isn't optimal. Batched SSBO + multidraw indirect is faster for
 * large scenes but requires more GPU features.
 *
 * <p>Users can implement custom strategies for their specific needs.
 */
public interface UploadStrategy {

    /**
     * Prepares GPU resources for a frame's draw commands.
     * Called once per frame before any draw calls.
     */
    void prepare(RenderDevice device, List<DrawCommand> batch);

    /**
     * Records draw commands for a single draw command entry.
     * Called once per entity in the batch.
     */
    void record(RenderDevice device, CommandRecorder recorder, DrawCommand command, int index);

    /**
     * Cleans up after a frame. Called once per frame after all draw calls.
     */
    void finish(RenderDevice device);
}

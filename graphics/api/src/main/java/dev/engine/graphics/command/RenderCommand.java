package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.*;
import dev.engine.graphics.renderstate.BarrierScope;

import java.nio.ByteBuffer;

/**
 * All possible GPU commands as pure data records.
 * Recorded by {@link CommandRecorder}, executed by backend-specific CommandExecutors.
 * No native API calls — just data describing what to do.
 */
public sealed interface RenderCommand {

    // --- Resource binding ---
    record BindPipeline(Handle<PipelineResource> pipeline) implements RenderCommand {}
    record BindVertexBuffer(Handle<BufferResource> buffer, Handle<VertexInputResource> vertexInput) implements RenderCommand {}
    record BindIndexBuffer(Handle<BufferResource> buffer) implements RenderCommand {}
    record BindUniformBuffer(int binding, Handle<BufferResource> buffer) implements RenderCommand {}
    record BindTexture(int unit, Handle<TextureResource> texture) implements RenderCommand {}
    record BindSampler(int unit, Handle<SamplerResource> sampler) implements RenderCommand {}
    record BindStorageBuffer(int binding, Handle<BufferResource> buffer) implements RenderCommand {}

    // --- Draw commands ---
    record Draw(int vertexCount, int firstVertex) implements RenderCommand {}
    record DrawIndexed(int indexCount, int firstIndex) implements RenderCommand {}

    // --- Render targets ---
    record BindRenderTarget(Handle<RenderTargetResource> renderTarget) implements RenderCommand {}
    record BindDefaultRenderTarget() implements RenderCommand {}

    // --- State ---
    record SetDepthTest(boolean enabled) implements RenderCommand {}
    record SetBlending(boolean enabled) implements RenderCommand {}
    record SetCullFace(boolean enabled) implements RenderCommand {}
    record SetWireframe(boolean enabled) implements RenderCommand {}

    // --- Render state (property-based) ---
    record SetRenderState(PropertyMap properties) implements RenderCommand {}

    // --- Push constants ---
    record PushConstants(ByteBuffer data) implements RenderCommand {}

    // --- Compute ---
    record BindComputePipeline(Handle<PipelineResource> pipeline) implements RenderCommand {}
    record Dispatch(int groupsX, int groupsY, int groupsZ) implements RenderCommand {}

    // --- Synchronization ---
    record MemoryBarrier(BarrierScope scope) implements RenderCommand {}

    // --- Framebuffer ops ---
    record Clear(float r, float g, float b, float a) implements RenderCommand {}
    record Viewport(int x, int y, int width, int height) implements RenderCommand {}
    record Scissor(int x, int y, int width, int height) implements RenderCommand {}
}

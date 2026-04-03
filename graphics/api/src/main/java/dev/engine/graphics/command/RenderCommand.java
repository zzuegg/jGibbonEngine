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

    // --- Instanced draw commands ---
    record DrawInstanced(int vertexCount, int firstVertex, int instanceCount, int firstInstance) implements RenderCommand {}
    record DrawIndexedInstanced(int indexCount, int firstIndex, int instanceCount, int firstInstance) implements RenderCommand {}

    // --- Indirect draw commands ---
    record DrawIndirect(Handle<BufferResource> buffer, long offset, int drawCount, int stride) implements RenderCommand {}
    record DrawIndexedIndirect(Handle<BufferResource> buffer, long offset, int drawCount, int stride) implements RenderCommand {}

    // --- Render targets ---
    record BindRenderTarget(Handle<RenderTargetResource> renderTarget) implements RenderCommand {}
    record BindDefaultRenderTarget() implements RenderCommand {}

    // --- State (deprecated — use SetRenderState with PropertyMap instead) ---
    @Deprecated record SetDepthTest(boolean enabled) implements RenderCommand {}
    @Deprecated record SetBlending(boolean enabled) implements RenderCommand {}
    @Deprecated record SetCullFace(boolean enabled) implements RenderCommand {}
    @Deprecated record SetWireframe(boolean enabled) implements RenderCommand {}

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

    // --- Transfer commands ---
    record CopyBuffer(Handle<BufferResource> src, Handle<BufferResource> dst, long srcOffset, long dstOffset, long size) implements RenderCommand {}
    record CopyTexture(Handle<TextureResource> src, Handle<TextureResource> dst,
        int srcX, int srcY, int dstX, int dstY, int width, int height, int srcMipLevel, int dstMipLevel) implements RenderCommand {}
    record BlitTexture(Handle<TextureResource> src, Handle<TextureResource> dst,
        int srcX0, int srcY0, int srcX1, int srcY1,
        int dstX0, int dstY0, int dstX1, int dstY1, boolean linearFilter) implements RenderCommand {}
}

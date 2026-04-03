package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.*;
import dev.engine.graphics.renderstate.BarrierScope;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Records render commands into a {@link CommandList}.
 * This is the shared implementation — all backends use the same recorder.
 * No native API calls happen here.
 */
public class CommandRecorder {

    private final List<RenderCommand> commands = new ArrayList<>();

    // --- Resource binding ---
    public void bindPipeline(Handle<PipelineResource> pipeline) { commands.add(new RenderCommand.BindPipeline(pipeline)); }
    public void bindVertexBuffer(Handle<BufferResource> buffer, Handle<VertexInputResource> vertexInput) { commands.add(new RenderCommand.BindVertexBuffer(buffer, vertexInput)); }
    public void bindIndexBuffer(Handle<BufferResource> buffer) { commands.add(new RenderCommand.BindIndexBuffer(buffer)); }
    public void bindUniformBuffer(int binding, Handle<BufferResource> buffer) { commands.add(new RenderCommand.BindUniformBuffer(binding, buffer)); }
    public void bindTexture(int unit, Handle<TextureResource> texture) { commands.add(new RenderCommand.BindTexture(unit, texture)); }
    public void bindSampler(int unit, Handle<SamplerResource> sampler) { commands.add(new RenderCommand.BindSampler(unit, sampler)); }
    public void bindStorageBuffer(int binding, Handle<BufferResource> buffer) { commands.add(new RenderCommand.BindStorageBuffer(binding, buffer)); }

    // --- Image binding (for compute shader imageLoad/imageStore) ---
    public void bindImage(int unit, Handle<TextureResource> texture, int mipLevel, boolean read, boolean write) {
        commands.add(new RenderCommand.BindImage(unit, texture, mipLevel, read, write));
    }
    /** Convenience: bind for read-write at mip 0. */
    public void bindImage(int unit, Handle<TextureResource> texture) {
        bindImage(unit, texture, 0, true, true);
    }

    // --- Draw commands ---
    public void draw(DrawCall call) { commands.addAll(call.toCommands()); }
    public void draw(int vertexCount, int firstVertex) { commands.add(new RenderCommand.Draw(vertexCount, firstVertex)); }
    public void drawIndexed(int indexCount, int firstIndex) { commands.add(new RenderCommand.DrawIndexed(indexCount, firstIndex)); }
    public void drawInstanced(int vertexCount, int firstVertex, int instanceCount, int firstInstance) { commands.add(new RenderCommand.DrawInstanced(vertexCount, firstVertex, instanceCount, firstInstance)); }
    public void drawIndexedInstanced(int indexCount, int firstIndex, int instanceCount, int firstInstance) { commands.add(new RenderCommand.DrawIndexedInstanced(indexCount, firstIndex, instanceCount, firstInstance)); }
    public void drawIndirect(Handle<BufferResource> buffer, long offset, int drawCount, int stride) { commands.add(new RenderCommand.DrawIndirect(buffer, offset, drawCount, stride)); }
    public void drawIndexedIndirect(Handle<BufferResource> buffer, long offset, int drawCount, int stride) { commands.add(new RenderCommand.DrawIndexedIndirect(buffer, offset, drawCount, stride)); }

    // --- Render targets ---
    public void bindRenderTarget(Handle<RenderTargetResource> renderTarget) { commands.add(new RenderCommand.BindRenderTarget(renderTarget)); }
    public void bindDefaultRenderTarget() { commands.add(new RenderCommand.BindDefaultRenderTarget()); }

    // --- State (deprecated — use setRenderState with PropertyMap instead) ---
    @Deprecated public void setDepthTest(boolean enabled) { commands.add(new RenderCommand.SetDepthTest(enabled)); }
    @Deprecated public void setBlending(boolean enabled) { commands.add(new RenderCommand.SetBlending(enabled)); }
    @Deprecated public void setCullFace(boolean enabled) { commands.add(new RenderCommand.SetCullFace(enabled)); }
    @Deprecated public void setWireframe(boolean enabled) { commands.add(new RenderCommand.SetWireframe(enabled)); }

    // --- Render state (property-based) ---
    public void setRenderState(PropertyMap properties) { commands.add(new RenderCommand.SetRenderState(properties)); }

    // --- Push constants ---
    public void pushConstants(ByteBuffer data) { commands.add(new RenderCommand.PushConstants(data)); }

    // --- Compute ---
    public void bindComputePipeline(Handle<PipelineResource> pipeline) { commands.add(new RenderCommand.BindComputePipeline(pipeline)); }
    public void dispatch(int groupsX, int groupsY, int groupsZ) { commands.add(new RenderCommand.Dispatch(groupsX, groupsY, groupsZ)); }

    // --- Synchronization ---
    public void memoryBarrier(BarrierScope scope) { commands.add(new RenderCommand.MemoryBarrier(scope)); }

    // --- Framebuffer ops ---
    public void clear(float r, float g, float b, float a) { commands.add(new RenderCommand.Clear(r, g, b, a)); }
    public void viewport(int x, int y, int width, int height) { commands.add(new RenderCommand.Viewport(x, y, width, height)); }
    public void scissor(int x, int y, int width, int height) { commands.add(new RenderCommand.Scissor(x, y, width, height)); }

    // --- Transfer commands ---
    public void copyBuffer(Handle<BufferResource> src, Handle<BufferResource> dst, long srcOffset, long dstOffset, long size) {
        commands.add(new RenderCommand.CopyBuffer(src, dst, srcOffset, dstOffset, size));
    }
    public void copyTexture(Handle<TextureResource> src, Handle<TextureResource> dst,
            int srcX, int srcY, int dstX, int dstY, int width, int height) {
        commands.add(new RenderCommand.CopyTexture(src, dst, srcX, srcY, dstX, dstY, width, height, 0, 0));
    }
    public void copyTexture(Handle<TextureResource> src, Handle<TextureResource> dst,
            int srcX, int srcY, int dstX, int dstY, int width, int height, int srcMipLevel, int dstMipLevel) {
        commands.add(new RenderCommand.CopyTexture(src, dst, srcX, srcY, dstX, dstY, width, height, srcMipLevel, dstMipLevel));
    }
    public void blitTexture(Handle<TextureResource> src, Handle<TextureResource> dst,
            int srcX0, int srcY0, int srcX1, int srcY1,
            int dstX0, int dstY0, int dstX1, int dstY1, boolean linearFilter) {
        commands.add(new RenderCommand.BlitTexture(src, dst, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, linearFilter));
    }

    /** Finishes recording and returns an immutable CommandList. */
    public CommandList finish() {
        return new CommandList(new ArrayList<>(commands));
    }
}

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

    // --- Draw commands ---
    public void draw(DrawCall call) { commands.addAll(call.toCommands()); }
    public void draw(int vertexCount, int firstVertex) { commands.add(new RenderCommand.Draw(vertexCount, firstVertex)); }
    public void drawIndexed(int indexCount, int firstIndex) { commands.add(new RenderCommand.DrawIndexed(indexCount, firstIndex)); }

    // --- Render targets ---
    public void bindRenderTarget(Handle<RenderTargetResource> renderTarget) { commands.add(new RenderCommand.BindRenderTarget(renderTarget)); }
    public void bindDefaultRenderTarget() { commands.add(new RenderCommand.BindDefaultRenderTarget()); }

    // --- State ---
    public void setDepthTest(boolean enabled) { commands.add(new RenderCommand.SetDepthTest(enabled)); }
    public void setBlending(boolean enabled) { commands.add(new RenderCommand.SetBlending(enabled)); }
    public void setCullFace(boolean enabled) { commands.add(new RenderCommand.SetCullFace(enabled)); }
    public void setWireframe(boolean enabled) { commands.add(new RenderCommand.SetWireframe(enabled)); }

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

    /** Finishes recording and returns an immutable CommandList. */
    public CommandList finish() {
        return new CommandList(new ArrayList<>(commands));
    }
}

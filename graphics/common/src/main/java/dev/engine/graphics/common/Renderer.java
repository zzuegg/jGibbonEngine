package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.command.CommandList;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.target.RenderTargetDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.vertex.VertexFormat;
import dev.engine.graphics.window.WindowHandle;

import java.nio.ByteBuffer;

/**
 * The single public entry point for all rendering operations.
 * Users create a Renderer, use it to manage resources and record frames.
 * Backend details are completely hidden.
 */
public class Renderer implements AutoCloseable {

    private final RenderDevice device;

    public Renderer(RenderDevice device) {
        this.device = device;
    }

    // --- Resource creation (delegates to device) ---

    public Handle<BufferResource> createBuffer(BufferDescriptor descriptor) { return device.createBuffer(descriptor); }
    public void destroyBuffer(Handle<BufferResource> buffer) { device.destroyBuffer(buffer); }
    public boolean isValidBuffer(Handle<BufferResource> buffer) { return device.isValidBuffer(buffer); }
    public BufferWriter writeBuffer(Handle<BufferResource> buffer) { return device.writeBuffer(buffer); }
    public BufferWriter writeBuffer(Handle<BufferResource> buffer, long offset, long length) { return device.writeBuffer(buffer, offset, length); }

    public Handle<TextureResource> createTexture(TextureDescriptor descriptor) { return device.createTexture(descriptor); }
    public void uploadTexture(Handle<TextureResource> texture, ByteBuffer pixels) { device.uploadTexture(texture, pixels); }
    public void destroyTexture(Handle<TextureResource> texture) { device.destroyTexture(texture); }

    public Handle<RenderTargetResource> createRenderTarget(RenderTargetDescriptor descriptor) { return device.createRenderTarget(descriptor); }
    public Handle<TextureResource> getRenderTargetColorTexture(Handle<RenderTargetResource> rt, int index) { return device.getRenderTargetColorTexture(rt, index); }
    public void destroyRenderTarget(Handle<RenderTargetResource> rt) { device.destroyRenderTarget(rt); }

    public Handle<VertexInputResource> createVertexInput(VertexFormat format) { return device.createVertexInput(format); }
    public void destroyVertexInput(Handle<VertexInputResource> vi) { device.destroyVertexInput(vi); }

    public Handle<SamplerResource> createSampler(SamplerDescriptor descriptor) { return device.createSampler(descriptor); }
    public void destroySampler(Handle<SamplerResource> sampler) { device.destroySampler(sampler); }

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) { return device.createPipeline(descriptor); }
    public void destroyPipeline(Handle<PipelineResource> pipeline) { device.destroyPipeline(pipeline); }

    // --- Frame lifecycle ---

    public CommandRecorder beginFrame() {
        device.beginFrame();
        return new CommandRecorder();
    }

    public void endFrame(CommandRecorder recorder) {
        device.submit(recorder.finish());
        device.endFrame();
    }

    // --- Capabilities ---

    public <T> T queryCapability(DeviceCapability<T> capability) {
        return device.queryCapability(capability);
    }

    public boolean supports(DeviceCapability<Boolean> feature) {
        Boolean result = device.queryCapability(feature);
        return result != null && result;
    }

    public String deviceName() {
        return queryCapability(DeviceCapability.DEVICE_NAME);
    }

    public String backendName() {
        return queryCapability(DeviceCapability.BACKEND_NAME);
    }

    @Override
    public void close() {
        device.close();
    }
}

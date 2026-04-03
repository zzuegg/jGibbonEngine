package dev.engine.providers.teavm.webgpu;

import dev.engine.graphics.webgpu.WgpuBindings;

import java.nio.ByteBuffer;

/**
 * Browser WebGPU implementation of {@link WgpuBindings} using TeaVM JSO.
 *
 * <p>Delegates to the browser's native {@code navigator.gpu} API via
 * {@code @JSBody} calls and TeaVM's {@code @Async} mechanism for
 * Promise-based WebGPU methods.
 *
 * <p><b>Status:</b> Stub — method signatures are implemented but bodies
 * throw {@link UnsupportedOperationException} until the JSO bridging is
 * wired up incrementally.
 */
public class TeaVmWgpuBindings implements WgpuBindings {

    // ===== Lifecycle =====

    @Override
    public boolean initialize() {
        // In the browser, WebGPU is always "loaded" if available
        return isAvailable();
    }

    @Override
    public boolean isAvailable() {
        return hasWebGPU();
    }

    private static boolean hasWebGPU() {
        // Will be replaced with @JSBody once wired up:
        // @JSBody(script = "return !!navigator.gpu;")
        // For now, assume available when running in TeaVM context
        return true;
    }

    // ===== Instance =====

    @Override
    public long createInstance() {
        // Browser WebGPU has no explicit instance; use a sentinel value
        return 1;
    }

    @Override
    public void instanceProcessEvents(long instance) {
        // No-op in browser — the event loop is the browser's own
    }

    @Override
    public void instanceRelease(long instance) {
        // No-op — no explicit instance in browser WebGPU
    }

    // ===== Adapter =====

    @Override
    public long instanceRequestAdapter(long instance) {
        throw todo("instanceRequestAdapter");
    }

    @Override
    public void adapterRelease(long adapter) {
        throw todo("adapterRelease");
    }

    // ===== Device =====

    @Override
    public long adapterRequestDevice(long instance, long adapter) {
        throw todo("adapterRequestDevice");
    }

    @Override
    public long deviceGetQueue(long device) {
        throw todo("deviceGetQueue");
    }

    @Override
    public void deviceRelease(long device) {
        throw todo("deviceRelease");
    }

    // ===== Buffer =====

    @Override
    public long deviceCreateBuffer(long device, long size, int usage) {
        throw todo("deviceCreateBuffer");
    }

    @Override
    public void bufferRelease(long buffer) {
        throw todo("bufferRelease");
    }

    @Override
    public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        throw todo("queueWriteBuffer");
    }

    @Override
    public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        throw todo("bufferMapReadSync");
    }

    @Override
    public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        throw todo("bufferGetConstMappedRange");
    }

    @Override
    public void bufferUnmap(long buffer) {
        throw todo("bufferUnmap");
    }

    // ===== Texture =====

    @Override
    public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                    int format, int dimension, int usage) {
        throw todo("deviceCreateTexture");
    }

    @Override
    public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        throw todo("textureCreateView");
    }

    @Override
    public void textureRelease(long texture) {
        throw todo("textureRelease");
    }

    @Override
    public void textureViewRelease(long textureView) {
        throw todo("textureViewRelease");
    }

    @Override
    public void queueWriteTexture(long queue, long texture, int width, int height,
                                  int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        throw todo("queueWriteTexture");
    }

    // ===== Sampler =====

    @Override
    public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                    int magFilter, int minFilter, int mipmapFilter) {
        throw todo("deviceCreateSampler");
    }

    @Override
    public void samplerRelease(long sampler) {
        throw todo("samplerRelease");
    }

    // ===== Shader Module =====

    @Override
    public long deviceCreateShaderModule(long device, String wgsl) {
        throw todo("deviceCreateShaderModule");
    }

    @Override
    public boolean shaderModuleIsValid(long shaderModule) {
        throw todo("shaderModuleIsValid");
    }

    @Override
    public void shaderModuleRelease(long shaderModule) {
        throw todo("shaderModuleRelease");
    }

    // ===== Bind Group Layout =====

    @Override
    public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        throw todo("deviceCreateBindGroupLayout");
    }

    @Override
    public void bindGroupLayoutRelease(long bindGroupLayout) {
        throw todo("bindGroupLayoutRelease");
    }

    // ===== Pipeline Layout =====

    @Override
    public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        throw todo("deviceCreatePipelineLayout");
    }

    @Override
    public void pipelineLayoutRelease(long pipelineLayout) {
        throw todo("pipelineLayoutRelease");
    }

    // ===== Render Pipeline =====

    @Override
    public long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc) {
        throw todo("deviceCreateRenderPipeline");
    }

    @Override
    public void renderPipelineRelease(long renderPipeline) {
        throw todo("renderPipelineRelease");
    }

    // ===== Bind Group =====

    @Override
    public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        throw todo("deviceCreateBindGroup");
    }

    @Override
    public void bindGroupRelease(long bindGroup) {
        throw todo("bindGroupRelease");
    }

    // ===== Command Encoder =====

    @Override
    public long deviceCreateCommandEncoder(long device) {
        throw todo("deviceCreateCommandEncoder");
    }

    @Override
    public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        throw todo("commandEncoderBeginRenderPass");
    }

    @Override
    public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                  long dst, int dstOffset, int size) {
        throw todo("commandEncoderCopyBufferToBuffer");
    }

    @Override
    public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                   int width, int height,
                                                   int bytesPerRow, int rowsPerImage) {
        throw todo("commandEncoderCopyTextureToBuffer");
    }

    @Override
    public long commandEncoderFinish(long encoder) {
        throw todo("commandEncoderFinish");
    }

    @Override
    public void commandEncoderRelease(long encoder) {
        throw todo("commandEncoderRelease");
    }

    // ===== Command Buffer =====

    @Override
    public void commandBufferRelease(long commandBuffer) {
        throw todo("commandBufferRelease");
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queue, long commandBuffer) {
        throw todo("queueSubmit");
    }

    // ===== Render Pass Encoder =====

    @Override
    public void renderPassEnd(long renderPass) {
        throw todo("renderPassEnd");
    }

    @Override
    public void renderPassRelease(long renderPass) {
        throw todo("renderPassRelease");
    }

    @Override
    public void renderPassSetPipeline(long renderPass, long pipeline) {
        throw todo("renderPassSetPipeline");
    }

    @Override
    public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size) {
        throw todo("renderPassSetVertexBuffer");
    }

    @Override
    public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat, int offset, int size) {
        throw todo("renderPassSetIndexBuffer");
    }

    @Override
    public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        throw todo("renderPassSetBindGroup");
    }

    @Override
    public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                      float minDepth, float maxDepth) {
        throw todo("renderPassSetViewport");
    }

    @Override
    public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        throw todo("renderPassSetScissorRect");
    }

    @Override
    public void renderPassSetStencilReference(long renderPass, int ref) {
        throw todo("renderPassSetStencilReference");
    }

    @Override
    public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                               int firstVertex, int firstInstance) {
        throw todo("renderPassDraw");
    }

    @Override
    public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                      int firstIndex, int baseVertex, int firstInstance) {
        throw todo("renderPassDrawIndexed");
    }

    // ===== Helpers =====

    private static UnsupportedOperationException todo(String method) {
        return new UnsupportedOperationException(
                "TeaVmWgpuBindings." + method + " not yet implemented");
    }
}

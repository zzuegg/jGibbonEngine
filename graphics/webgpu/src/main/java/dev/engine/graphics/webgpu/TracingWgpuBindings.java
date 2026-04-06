package dev.engine.graphics.webgpu;

import dev.engine.graphics.window.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Logging decorator for {@link WgpuBindings}. Wraps any implementation and
 * traces every WebGPU API call at TRACE level, making it easy to compare
 * the engine's call sequence against working examples.
 *
 * <p>Usage:
 * <pre>{@code
 * var bindings = new TracingWgpuBindings(new JWebGpuBindings());
 * var device = new WgpuRenderDevice(window, bindings);
 * }</pre>
 */
public class TracingWgpuBindings implements WgpuBindings {

    private static final Logger log = LoggerFactory.getLogger(TracingWgpuBindings.class);

    private final WgpuBindings delegate;

    public TracingWgpuBindings(WgpuBindings delegate) {
        this.delegate = delegate;
    }

    // ===== Lifecycle =====

    @Override public boolean initialize() {
        log.trace("wgpu: initialize()");
        var r = delegate.initialize();
        log.trace("wgpu: initialize() -> {}", r);
        return r;
    }

    @Override public boolean isAvailable() {
        return delegate.isAvailable();
    }

    // ===== Surface =====

    @Override public long configureSurface(long instance, long device, WindowHandle window) {
        log.trace("wgpu: configureSurface(instance=0x{}, device=0x{}, window={})",
                Long.toHexString(instance), Long.toHexString(device), window);
        var r = delegate.configureSurface(instance, device, window);
        log.trace("wgpu: configureSurface() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public long getSurfaceTextureView(long surface) {
        var r = delegate.getSurfaceTextureView(surface);
        log.trace("wgpu: getSurfaceTextureView(0x{}) -> 0x{}", Long.toHexString(surface), Long.toHexString(r));
        return r;
    }

    @Override public void releaseSurfaceTextureView(long textureView) {
        log.trace("wgpu: releaseSurfaceTextureView(0x{})", Long.toHexString(textureView));
        delegate.releaseSurfaceTextureView(textureView);
    }

    @Override public void surfacePresent(long surface) {
        log.trace("wgpu: surfacePresent(0x{})", Long.toHexString(surface));
        delegate.surfacePresent(surface);
    }

    @Override public int surfaceFormat() { return delegate.surfaceFormat(); }
    @Override public boolean hasSurface() { return delegate.hasSurface(); }

    // ===== Instance =====

    @Override public long createInstance() {
        var r = delegate.createInstance();
        log.trace("wgpu: createInstance() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public void instanceProcessEvents(long instance) {
        delegate.instanceProcessEvents(instance);
    }

    @Override public void instanceRelease(long instance) {
        log.trace("wgpu: instanceRelease(0x{})", Long.toHexString(instance));
        delegate.instanceRelease(instance);
    }

    // ===== Adapter =====

    @Override public long instanceRequestAdapter(long instance) {
        log.trace("wgpu: instanceRequestAdapter(0x{})", Long.toHexString(instance));
        var r = delegate.instanceRequestAdapter(instance);
        log.trace("wgpu: instanceRequestAdapter() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public void adapterRelease(long adapter) {
        log.trace("wgpu: adapterRelease(0x{})", Long.toHexString(adapter));
        delegate.adapterRelease(adapter);
    }

    // ===== Device =====

    @Override public long adapterRequestDevice(long instance, long adapter) {
        log.trace("wgpu: adapterRequestDevice(instance=0x{}, adapter=0x{})",
                Long.toHexString(instance), Long.toHexString(adapter));
        var r = delegate.adapterRequestDevice(instance, adapter);
        log.trace("wgpu: adapterRequestDevice() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public long deviceGetQueue(long device) {
        var r = delegate.deviceGetQueue(device);
        log.trace("wgpu: deviceGetQueue(0x{}) -> 0x{}", Long.toHexString(device), Long.toHexString(r));
        return r;
    }

    @Override public void deviceRelease(long device) {
        log.trace("wgpu: deviceRelease(0x{})", Long.toHexString(device));
        delegate.deviceRelease(device);
    }

    @Override public DeviceLimits deviceGetLimits(long device) {
        var r = delegate.deviceGetLimits(device);
        log.trace("wgpu: deviceGetLimits(0x{}) -> {}", Long.toHexString(device), r);
        return r;
    }

    // ===== Buffer =====

    @Override public long deviceCreateBuffer(long device, long size, int usage) {
        var r = delegate.deviceCreateBuffer(device, size, usage);
        log.trace("wgpu: deviceCreateBuffer(size={}, usage=0x{}) -> 0x{}",
                size, Integer.toHexString(usage), Long.toHexString(r));
        return r;
    }

    @Override public void bufferRelease(long buffer) {
        log.trace("wgpu: bufferRelease(0x{})", Long.toHexString(buffer));
        delegate.bufferRelease(buffer);
    }

    @Override public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        log.trace("wgpu: queueWriteBuffer(buffer=0x{}, offset={}, size={})",
                Long.toHexString(buffer), offset, size);
        delegate.queueWriteBuffer(queue, buffer, offset, data, size);
    }

    @Override public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        log.trace("wgpu: bufferMapReadSync(buffer=0x{}, size={}, maxPolls={})",
                Long.toHexString(buffer), size, maxPolls);
        delegate.bufferMapReadSync(instance, buffer, size, maxPolls);
    }

    @Override public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        log.trace("wgpu: bufferGetConstMappedRange(buffer=0x{}, offset={}, size={})",
                Long.toHexString(buffer), offset, size);
        delegate.bufferGetConstMappedRange(buffer, offset, size, dest);
    }

    @Override public void bufferUnmap(long buffer) {
        log.trace("wgpu: bufferUnmap(0x{})", Long.toHexString(buffer));
        delegate.bufferUnmap(buffer);
    }

    // ===== Texture =====

    @Override public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                              int format, int dimension, int usage) {
        var r = delegate.deviceCreateTexture(device, width, height, depthOrLayers, format, dimension, usage);
        log.trace("wgpu: deviceCreateTexture({}x{}x{}, format=0x{}, dim={}, usage=0x{}) -> 0x{}",
                width, height, depthOrLayers, Integer.toHexString(format), dimension,
                Integer.toHexString(usage), Long.toHexString(r));
        return r;
    }

    @Override public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        var r = delegate.textureCreateView(texture, format, viewDimension, arrayLayerCount);
        log.trace("wgpu: textureCreateView(texture=0x{}, format=0x{}, viewDim={}, layers={}) -> 0x{}",
                Long.toHexString(texture), Integer.toHexString(format), viewDimension, arrayLayerCount,
                Long.toHexString(r));
        return r;
    }

    @Override public void textureRelease(long texture) {
        log.trace("wgpu: textureRelease(0x{})", Long.toHexString(texture));
        delegate.textureRelease(texture);
    }

    @Override public void textureViewRelease(long textureView) {
        log.trace("wgpu: textureViewRelease(0x{})", Long.toHexString(textureView));
        delegate.textureViewRelease(textureView);
    }

    @Override public void queueWriteTexture(long queue, long texture, int width, int height,
                                            int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        log.trace("wgpu: queueWriteTexture(texture=0x{}, {}x{}x{}, bytesPerRow={})",
                Long.toHexString(texture), width, height, depthOrLayers, bytesPerRow);
        delegate.queueWriteTexture(queue, texture, width, height, depthOrLayers, bytesPerRow, data);
    }

    // ===== Sampler =====

    @Override public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                              int magFilter, int minFilter, int mipmapFilter,
                                              float lodMinClamp, float lodMaxClamp,
                                              int compare, float maxAnisotropy) {
        var r = delegate.deviceCreateSampler(device, addressU, addressV, addressW,
                magFilter, minFilter, mipmapFilter, lodMinClamp, lodMaxClamp, compare, maxAnisotropy);
        log.trace("wgpu: deviceCreateSampler(addr={}/{}/{}, filter={}/{}/{}, lod={}-{}, compare={}, aniso={}) -> 0x{}",
                addressU, addressV, addressW, magFilter, minFilter, mipmapFilter,
                lodMinClamp, lodMaxClamp, compare, maxAnisotropy, Long.toHexString(r));
        return r;
    }

    @Override public void samplerRelease(long sampler) {
        log.trace("wgpu: samplerRelease(0x{})", Long.toHexString(sampler));
        delegate.samplerRelease(sampler);
    }

    // ===== Shader Module =====

    @Override public long deviceCreateShaderModule(long device, String wgsl) {
        var r = delegate.deviceCreateShaderModule(device, wgsl);
        int lines = wgsl != null ? wgsl.split("\n").length : 0;
        log.trace("wgpu: deviceCreateShaderModule({} lines) -> 0x{}", lines, Long.toHexString(r));
        if (log.isTraceEnabled()) {
            log.trace("wgpu: --- WGSL source ---\n{}\n--- end WGSL ---", wgsl);
        }
        return r;
    }

    @Override public boolean shaderModuleIsValid(long shaderModule) {
        return delegate.shaderModuleIsValid(shaderModule);
    }

    @Override public void shaderModuleRelease(long shaderModule) {
        log.trace("wgpu: shaderModuleRelease(0x{})", Long.toHexString(shaderModule));
        delegate.shaderModuleRelease(shaderModule);
    }

    // ===== Bind Group Layout =====

    @Override public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        var r = delegate.deviceCreateBindGroupLayout(device, entries);
        log.trace("wgpu: deviceCreateBindGroupLayout({} entries) -> 0x{}", entries.length, Long.toHexString(r));
        for (var e : entries) {
            log.trace("wgpu:   binding={}, visibility=0x{}, type={}", e.binding(),
                    Integer.toHexString(e.visibility()), e.type());
        }
        return r;
    }

    @Override public void bindGroupLayoutRelease(long layout) {
        log.trace("wgpu: bindGroupLayoutRelease(0x{})", Long.toHexString(layout));
        delegate.bindGroupLayoutRelease(layout);
    }

    // ===== Pipeline Layout =====

    @Override public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        var r = delegate.deviceCreatePipelineLayout(device, bindGroupLayouts);
        log.trace("wgpu: deviceCreatePipelineLayout({} layouts) -> 0x{}", bindGroupLayouts.length, Long.toHexString(r));
        return r;
    }

    @Override public void pipelineLayoutRelease(long layout) {
        log.trace("wgpu: pipelineLayoutRelease(0x{})", Long.toHexString(layout));
        delegate.pipelineLayoutRelease(layout);
    }

    // ===== Render Pipeline =====

    @Override public long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc) {
        log.trace("wgpu: deviceCreateRenderPipeline(");
        log.trace("wgpu:   vertexModule=0x{}, vertexEntry='{}'",
                Long.toHexString(desc.vertexModule()), desc.vertexEntryPoint());
        log.trace("wgpu:   fragmentModule=0x{}, fragmentEntry='{}'",
                Long.toHexString(desc.fragmentModule()), desc.fragmentEntryPoint());
        if (desc.vertexBufferLayout() != null) {
            var vbl = desc.vertexBufferLayout();
            log.trace("wgpu:   vertexBuffer: stride={}, stepMode={}, {} attributes",
                    vbl.stride(), vbl.stepMode(), vbl.attributes() != null ? vbl.attributes().length : 0);
            if (vbl.attributes() != null) {
                for (var a : vbl.attributes()) {
                    log.trace("wgpu:     attr: loc={}, format=0x{}, offset={}",
                            a.shaderLocation(), Integer.toHexString(a.format()), a.offset());
                }
            }
        }
        log.trace("wgpu:   topology={}, frontFace={}, cullMode={}", desc.topology(), desc.frontFace(), desc.cullMode());
        log.trace("wgpu:   depthFormat=0x{}, depthWrite={}, depthCompare={}",
                Integer.toHexString(desc.depthStencilFormat()), desc.depthWriteEnabled(), desc.depthCompare());
        log.trace("wgpu:   colorFormat=0x{}, blend: color={}/{}/{}, alpha={}/{}/{}",
                Integer.toHexString(desc.colorTargetFormat()),
                desc.blendColorSrcFactor(), desc.blendColorDstFactor(), desc.blendColorOperation(),
                desc.blendAlphaSrcFactor(), desc.blendAlphaDstFactor(), desc.blendAlphaOperation());
        log.trace("wgpu: )");

        var r = delegate.deviceCreateRenderPipeline(device, desc);
        log.trace("wgpu: deviceCreateRenderPipeline() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public void renderPipelineRelease(long pipeline) {
        log.trace("wgpu: renderPipelineRelease(0x{})", Long.toHexString(pipeline));
        delegate.renderPipelineRelease(pipeline);
    }

    // ===== Bind Group =====

    @Override public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        var r = delegate.deviceCreateBindGroup(device, layout, entries);
        log.trace("wgpu: deviceCreateBindGroup(layout=0x{}, {} entries) -> 0x{}",
                Long.toHexString(layout), entries.length, Long.toHexString(r));
        for (var e : entries) {
            log.trace("wgpu:   binding={}, type={}, handle=0x{}, offset={}, size={}",
                    e.binding(), e.resourceType(), Long.toHexString(e.handle()), e.offset(), e.size());
        }
        return r;
    }

    @Override public void bindGroupRelease(long bindGroup) {
        log.trace("wgpu: bindGroupRelease(0x{})", Long.toHexString(bindGroup));
        delegate.bindGroupRelease(bindGroup);
    }

    // ===== Command Encoder =====

    @Override public long deviceCreateCommandEncoder(long device) {
        var r = delegate.deviceCreateCommandEncoder(device);
        log.trace("wgpu: deviceCreateCommandEncoder() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        log.trace("wgpu: commandEncoderBeginRenderPass(encoder=0x{}, {} colorAttachments, depthStencil={})",
                Long.toHexString(encoder),
                desc.colorAttachments() != null ? desc.colorAttachments().length : 0,
                desc.depthStencil() != null);
        if (desc.colorAttachments() != null) {
            for (int i = 0; i < desc.colorAttachments().length; i++) {
                var ca = desc.colorAttachments()[i];
                log.trace("wgpu:   color[{}]: view=0x{}, clear=({},{},{},{})",
                        i, Long.toHexString(ca.textureView()), ca.clearR(), ca.clearG(), ca.clearB(), ca.clearA());
            }
        }
        if (desc.depthStencil() != null) {
            log.trace("wgpu:   depth: view=0x{}, clear={}, stencil={}",
                    Long.toHexString(desc.depthStencil().textureView()),
                    desc.depthStencil().depthClearValue(), desc.depthStencil().stencilClearValue());
        }
        var r = delegate.commandEncoderBeginRenderPass(encoder, desc);
        log.trace("wgpu: commandEncoderBeginRenderPass() -> 0x{}", Long.toHexString(r));
        return r;
    }

    @Override public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                           long dst, int dstOffset, int size) {
        log.trace("wgpu: commandEncoderCopyBufferToBuffer(src=0x{}, srcOff={}, dst=0x{}, dstOff={}, size={})",
                Long.toHexString(src), srcOffset, Long.toHexString(dst), dstOffset, size);
        delegate.commandEncoderCopyBufferToBuffer(encoder, src, srcOffset, dst, dstOffset, size);
    }

    @Override public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                            int width, int height,
                                                            int bytesPerRow, int rowsPerImage) {
        log.trace("wgpu: commandEncoderCopyTextureToBuffer(tex=0x{}, buf=0x{}, {}x{}, bpr={}, rows={})",
                Long.toHexString(texture), Long.toHexString(buffer), width, height, bytesPerRow, rowsPerImage);
        delegate.commandEncoderCopyTextureToBuffer(encoder, texture, buffer, width, height, bytesPerRow, rowsPerImage);
    }

    @Override public long commandEncoderFinish(long encoder) {
        var r = delegate.commandEncoderFinish(encoder);
        log.trace("wgpu: commandEncoderFinish(0x{}) -> 0x{}", Long.toHexString(encoder), Long.toHexString(r));
        return r;
    }

    @Override public void commandEncoderRelease(long encoder) {
        log.trace("wgpu: commandEncoderRelease(0x{})", Long.toHexString(encoder));
        delegate.commandEncoderRelease(encoder);
    }

    // ===== Command Buffer =====

    @Override public void commandBufferRelease(long commandBuffer) {
        log.trace("wgpu: commandBufferRelease(0x{})", Long.toHexString(commandBuffer));
        delegate.commandBufferRelease(commandBuffer);
    }

    // ===== Queue =====

    @Override public void queueSubmit(long queue, long commandBuffer) {
        log.trace("wgpu: queueSubmit(queue=0x{}, cmdBuf=0x{})",
                Long.toHexString(queue), Long.toHexString(commandBuffer));
        delegate.queueSubmit(queue, commandBuffer);
    }

    // ===== Render Pass Encoder =====

    @Override public void renderPassEnd(long renderPass) {
        log.trace("wgpu: renderPassEnd(0x{})", Long.toHexString(renderPass));
        delegate.renderPassEnd(renderPass);
    }

    @Override public void renderPassRelease(long renderPass) {
        log.trace("wgpu: renderPassRelease(0x{})", Long.toHexString(renderPass));
        delegate.renderPassRelease(renderPass);
    }

    @Override public void renderPassSetPipeline(long renderPass, long pipeline) {
        log.trace("wgpu: renderPassSetPipeline(pass=0x{}, pipeline=0x{})",
                Long.toHexString(renderPass), Long.toHexString(pipeline));
        delegate.renderPassSetPipeline(renderPass, pipeline);
    }

    @Override public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size) {
        log.trace("wgpu: renderPassSetVertexBuffer(slot={}, buffer=0x{}, offset={}, size={})",
                slot, Long.toHexString(buffer), offset, size);
        delegate.renderPassSetVertexBuffer(renderPass, slot, buffer, offset, size);
    }

    @Override public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat, int offset, int size) {
        log.trace("wgpu: renderPassSetIndexBuffer(buffer=0x{}, format={}, offset={}, size={})",
                Long.toHexString(buffer), indexFormat, offset, size);
        delegate.renderPassSetIndexBuffer(renderPass, buffer, indexFormat, offset, size);
    }

    @Override public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        log.trace("wgpu: renderPassSetBindGroup(group={}, bindGroup=0x{})",
                groupIndex, Long.toHexString(bindGroup));
        delegate.renderPassSetBindGroup(renderPass, groupIndex, bindGroup);
    }

    @Override public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                                float minDepth, float maxDepth) {
        log.trace("wgpu: renderPassSetViewport({}, {}, {}, {}, depth={}-{})", x, y, w, h, minDepth, maxDepth);
        delegate.renderPassSetViewport(renderPass, x, y, w, h, minDepth, maxDepth);
    }

    @Override public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        log.trace("wgpu: renderPassSetScissorRect({}, {}, {}, {})", x, y, width, height);
        delegate.renderPassSetScissorRect(renderPass, x, y, width, height);
    }

    @Override public void renderPassSetStencilReference(long renderPass, int ref) {
        log.trace("wgpu: renderPassSetStencilReference({})", ref);
        delegate.renderPassSetStencilReference(renderPass, ref);
    }

    @Override public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                                         int firstVertex, int firstInstance) {
        log.trace("wgpu: renderPassDraw(verts={}, instances={}, firstVert={}, firstInst={})",
                vertexCount, instanceCount, firstVertex, firstInstance);
        delegate.renderPassDraw(renderPass, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                                int firstIndex, int baseVertex, int firstInstance) {
        log.trace("wgpu: renderPassDrawIndexed(indices={}, instances={}, firstIdx={}, baseVert={}, firstInst={})",
                indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
        delegate.renderPassDrawIndexed(renderPass, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }
}

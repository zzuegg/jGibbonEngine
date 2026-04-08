package dev.engine.providers.graal.webgpu;

import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.graphics.window.WindowHandle;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * GraalJS-based implementation of {@link WgpuBindings}.
 *
 * <p>Calls the browser WebGPU API through GraalVM polyglot interop. The JS
 * handle registry pattern is identical to TeaVmWgpuBindings — all WebGPU
 * objects live in {@code globalThis._wgpu} keyed by integer IDs.
 *
 * <p>Requires a GraalJS {@link Context} that has access to {@code navigator.gpu}
 * (i.e., running in a browser-like environment or with a WebGPU polyfill).
 */
public class GraalWgpuBindings implements WgpuBindings {

    private static final Logger log = LoggerFactory.getLogger(GraalWgpuBindings.class);

    private final Context context;
    private final Value bridge;

    /**
     * @param context shared GraalJS context with WebGPU access
     */
    public GraalWgpuBindings(Context context) {
        this.context = context;
        this.bridge = context.eval("js", BRIDGE_JS);
    }

    // ===== Lifecycle =====

    @Override
    public boolean initialize() {
        return bridge.getMember("initialize").execute().asBoolean();
    }

    @Override
    public boolean isAvailable() {
        return bridge.getMember("isAvailable").execute().asBoolean();
    }

    // ===== Surface / Presentation =====

    @Override
    public long configureSurface(long instance, long device, WindowHandle window) {
        return bridge.getMember("configureSurface").execute((int) device).asInt();
    }

    @Override
    public long getSurfaceTextureView(long surface) {
        return bridge.getMember("getSurfaceTextureView").execute((int) surface).asInt();
    }

    @Override
    public void releaseSurfaceTextureView(long textureView) {
        bridge.getMember("release").executeVoid((int) textureView);
    }

    @Override
    public boolean hasSurface() { return true; }

    @Override
    public int surfaceFormat() {
        String fmt = bridge.getMember("getPreferredCanvasFormat").execute().asString();
        if ("rgba8unorm".equals(fmt)) return TEXTURE_FORMAT_RGBA8_UNORM;
        return TEXTURE_FORMAT_BGRA8_UNORM;
    }

    // ===== Instance =====

    @Override
    public long createInstance() { return 1; }

    @Override
    public void instanceProcessEvents(long instance) {}

    @Override
    public void instanceRelease(long instance) {}

    // ===== Adapter =====

    @Override
    public long instanceRequestAdapter(long instance) {
        int id = bridge.getMember("getStoredAdapter").execute().asInt();
        if (id <= 0) throw new IllegalStateException(
                "Adapter not initialized. Call GraalWgpuInit.initAsync() first.");
        return id;
    }

    @Override
    public void adapterRelease(long adapter) {}

    // ===== Device =====

    @Override
    public long adapterRequestDevice(long instance, long adapter) {
        int id = bridge.getMember("getStoredDevice").execute().asInt();
        if (id <= 0) throw new IllegalStateException(
                "Device not initialized. Call GraalWgpuInit.initAsync() first.");
        return id;
    }

    @Override
    public long deviceGetQueue(long device) {
        return bridge.getMember("deviceGetQueue").execute((int) device).asInt();
    }

    @Override
    public void deviceRelease(long device) {}

    // ===== Buffer =====

    @Override
    public long deviceCreateBuffer(long device, long size, int usage) {
        return bridge.getMember("createBuffer").execute((int) device, (int) size, usage).asInt();
    }

    @Override
    public void bufferRelease(long buffer) {
        bridge.getMember("bufferRelease").executeVoid((int) buffer);
    }

    @Override
    public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        byte[] bytes = new byte[size];
        data.get(bytes);
        bridge.getMember("queueWriteBuffer").executeVoid((int) queue, (int) buffer, offset, bytes);
    }

    @Override
    public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        bridge.getMember("bufferMapReadSync").executeVoid((int) buffer, size);
    }

    @Override
    public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        Value data = bridge.getMember("bufferGetMappedRange").execute((int) buffer, offset, size);
        for (int i = 0; i < size && i < data.getArraySize(); i++) {
            dest.put((byte) (data.getArrayElement(i).asInt() & 0xFF));
        }
    }

    @Override
    public void bufferUnmap(long buffer) {
        bridge.getMember("bufferUnmap").executeVoid((int) buffer);
    }

    // ===== Texture =====

    @Override
    public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                    int format, int dimension, int usage) {
        String fmtStr = wgpuTextureFormatString(format);
        String dimStr = dimension == TEXTURE_DIMENSION_3D ? "3d" : "2d";
        return bridge.getMember("createTexture")
                .execute((int) device, width, height, depthOrLayers, fmtStr, dimStr, usage).asInt();
    }

    @Override
    public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        return bridge.getMember("textureCreateView").execute((int) texture).asInt();
    }

    @Override
    public void textureRelease(long texture) {
        bridge.getMember("textureRelease").executeVoid((int) texture);
    }

    @Override
    public void textureViewRelease(long textureView) {
        bridge.getMember("release").executeVoid((int) textureView);
    }

    @Override
    public void queueWriteTexture(long queue, long texture, int width, int height,
                                  int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        bridge.getMember("queueWriteTexture")
                .executeVoid((int) queue, (int) texture, width, height, depthOrLayers, bytesPerRow, bytes);
    }

    // ===== Sampler =====

    @Override
    public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                    int magFilter, int minFilter, int mipmapFilter,
                                    float lodMinClamp, float lodMaxClamp,
                                    int compare, float maxAnisotropy) {
        return bridge.getMember("createSampler").execute(
                (int) device,
                addressModeStr(addressU), addressModeStr(addressV), addressModeStr(addressW),
                filterStr(magFilter), filterStr(minFilter), filterStr(mipmapFilter),
                lodMinClamp, lodMaxClamp,
                compare != 0 ? compareString(compare) : null,
                (int) maxAnisotropy
        ).asInt();
    }

    @Override
    public void samplerRelease(long sampler) {
        bridge.getMember("release").executeVoid((int) sampler);
    }

    // ===== Shader Module =====

    @Override
    public long deviceCreateShaderModule(long device, String wgsl) {
        return bridge.getMember("createShaderModule").execute((int) device, wgsl).asInt();
    }

    @Override
    public boolean shaderModuleIsValid(long shaderModule) { return shaderModule > 0; }

    @Override
    public void shaderModuleRelease(long shaderModule) {
        bridge.getMember("release").executeVoid((int) shaderModule);
    }

    // ===== Bind Group Layout =====

    @Override
    public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        return bridge.getMember("createBindGroupLayout")
                .execute((int) device, encodeBindGroupLayoutEntries(entries)).asInt();
    }

    @Override
    public void bindGroupLayoutRelease(long bindGroupLayout) {
        bridge.getMember("release").executeVoid((int) bindGroupLayout);
    }

    // ===== Pipeline Layout =====

    @Override
    public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        int[] ids = new int[bindGroupLayouts.length];
        for (int i = 0; i < bindGroupLayouts.length; i++) ids[i] = (int) bindGroupLayouts[i];
        return bridge.getMember("createPipelineLayout").execute((int) device, ids).asInt();
    }

    @Override
    public void pipelineLayoutRelease(long pipelineLayout) {
        bridge.getMember("release").executeVoid((int) pipelineLayout);
    }

    // ===== Render Pipeline =====

    @Override
    public long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc) {
        var vbl = desc.vertexBufferLayout();
        String attrsJson = "[]";
        int stride = 0;
        if (vbl != null) {
            stride = vbl.stride();
            var sb = new StringBuilder("[");
            for (int i = 0; i < vbl.attributes().length; i++) {
                if (i > 0) sb.append(",");
                var a = vbl.attributes()[i];
                sb.append("{\"format\":\"").append(vertexFormatString(a.format()))
                  .append("\",\"offset\":").append(a.offset())
                  .append(",\"shaderLocation\":").append(a.shaderLocation()).append("}");
            }
            sb.append("]");
            attrsJson = sb.toString();
        }

        StencilFaceState sf = desc.stencilFront();
        StencilFaceState sb2 = desc.stencilBack();

        return bridge.getMember("createRenderPipeline").execute(
                (int) device, (int) desc.pipelineLayout(),
                (int) desc.vertexModule(), desc.vertexEntryPoint(),
                (int) desc.fragmentModule(), desc.fragmentEntryPoint(),
                stride, attrsJson,
                topologyString(desc.topology()),
                cullModeString(desc.cullMode()),
                frontFaceString(desc.frontFace()),
                wgpuTextureFormatString(desc.colorTargetFormat()),
                desc.depthStencilFormat() > 0 ? wgpuTextureFormatString(desc.depthStencilFormat()) : "",
                desc.depthWriteEnabled() == OPTIONAL_BOOL_TRUE,
                compareString(desc.depthCompare()),
                compareString(sf.compare()), stencilOpString(sf.failOp()),
                stencilOpString(sf.depthFailOp()), stencilOpString(sf.passOp()),
                compareString(sb2.compare()), stencilOpString(sb2.failOp()),
                stencilOpString(sb2.depthFailOp()), stencilOpString(sb2.passOp()),
                desc.stencilReadMask(), desc.stencilWriteMask(),
                blendFactorString(desc.blendColorSrcFactor()),
                blendFactorString(desc.blendColorDstFactor()),
                blendOpString(desc.blendColorOperation()),
                blendFactorString(desc.blendAlphaSrcFactor()),
                blendFactorString(desc.blendAlphaDstFactor()),
                blendOpString(desc.blendAlphaOperation())
        ).asInt();
    }

    @Override
    public void renderPipelineRelease(long renderPipeline) {
        bridge.getMember("release").executeVoid((int) renderPipeline);
    }

    // ===== Bind Group =====

    @Override
    public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        return bridge.getMember("createBindGroup")
                .execute((int) device, (int) layout, encodeBindGroupEntries(entries)).asInt();
    }

    @Override
    public void bindGroupRelease(long bindGroup) {
        bridge.getMember("release").executeVoid((int) bindGroup);
    }

    // ===== Command Encoder =====

    @Override
    public long deviceCreateCommandEncoder(long device) {
        return bridge.getMember("createCommandEncoder").execute((int) device).asInt();
    }

    @Override
    public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        var ca = desc.colorAttachments();
        if (ca == null || ca.length == 0) throw new IllegalArgumentException("Need color attachment");
        var c = ca[0];
        int depthView = desc.depthStencil() != null ? (int) desc.depthStencil().textureView() : 0;
        float depthClear = desc.depthStencil() != null ? desc.depthStencil().depthClearValue() : 1.0f;

        return bridge.getMember("beginRenderPass").execute(
                (int) encoder, (int) c.textureView(),
                c.clearR(), c.clearG(), c.clearB(), c.clearA(),
                depthView, depthClear
        ).asInt();
    }

    @Override
    public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                  long dst, int dstOffset, int size) {
        bridge.getMember("copyBufferToBuffer").executeVoid(
                (int) encoder, (int) src, srcOffset, (int) dst, dstOffset, size);
    }

    @Override
    public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                   int width, int height,
                                                   int bytesPerRow, int rowsPerImage) {
        bridge.getMember("copyTextureToBuffer").executeVoid(
                (int) encoder, (int) texture, (int) buffer, width, height, bytesPerRow, rowsPerImage);
    }

    @Override
    public long commandEncoderFinish(long encoder) {
        return bridge.getMember("encoderFinish").execute((int) encoder).asInt();
    }

    @Override
    public void commandEncoderRelease(long encoder) {
        bridge.getMember("release").executeVoid((int) encoder);
    }

    @Override
    public void commandBufferRelease(long commandBuffer) {
        bridge.getMember("release").executeVoid((int) commandBuffer);
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queue, long commandBuffer) {
        bridge.getMember("queueSubmit").executeVoid((int) queue, (int) commandBuffer);
    }

    // ===== Render Pass Encoder =====

    @Override
    public void renderPassEnd(long renderPass) {
        bridge.getMember("renderPassEnd").executeVoid((int) renderPass);
    }

    @Override
    public void renderPassRelease(long renderPass) {
        bridge.getMember("release").executeVoid((int) renderPass);
    }

    @Override
    public void renderPassSetPipeline(long renderPass, long pipeline) {
        bridge.getMember("renderPassSetPipeline").executeVoid((int) renderPass, (int) pipeline);
    }

    @Override
    public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size) {
        bridge.getMember("renderPassSetVertexBuffer").executeVoid(
                (int) renderPass, slot, (int) buffer, offset, size);
    }

    @Override
    public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat,
                                          int offset, int size) {
        String fmt = indexFormat == INDEX_FORMAT_UINT32 ? "uint32" : "uint16";
        bridge.getMember("renderPassSetIndexBuffer").executeVoid(
                (int) renderPass, (int) buffer, fmt, offset, size);
    }

    @Override
    public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        bridge.getMember("renderPassSetBindGroup").executeVoid(
                (int) renderPass, groupIndex, (int) bindGroup);
    }

    @Override
    public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                      float minDepth, float maxDepth) {
        bridge.getMember("renderPassSetViewport").executeVoid(
                (int) renderPass, x, y, w, h, minDepth, maxDepth);
    }

    @Override
    public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        bridge.getMember("renderPassSetScissorRect").executeVoid((int) renderPass, x, y, width, height);
    }

    @Override
    public void renderPassSetStencilReference(long renderPass, int ref) {
        bridge.getMember("renderPassSetStencilReference").executeVoid((int) renderPass, ref);
    }

    @Override
    public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                               int firstVertex, int firstInstance) {
        bridge.getMember("renderPassDraw").executeVoid(
                (int) renderPass, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                      int firstIndex, int baseVertex, int firstInstance) {
        bridge.getMember("renderPassDrawIndexed").executeVoid(
                (int) renderPass, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    // ===== JSON encoding helpers (shared with TeaVm) =====

    private static String encodeBindGroupLayoutEntries(BindGroupLayoutEntry[] entries) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) sb.append(",");
            var e = entries[i];
            sb.append("{\"binding\":").append(e.binding())
              .append(",\"visibility\":").append(e.visibility());
            switch (e.type()) {
                case UNIFORM_BUFFER -> sb.append(",\"buffer\":{\"type\":\"uniform\"}");
                case READ_ONLY_STORAGE_BUFFER -> sb.append(",\"buffer\":{\"type\":\"read-only-storage\"}");
                case SAMPLED_TEXTURE -> sb.append(",\"texture\":{}");
                case FILTERING_SAMPLER -> sb.append(",\"sampler\":{}");
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String encodeBindGroupEntries(BindGroupEntry[] entries) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) sb.append(",");
            var e = entries[i];
            String type = switch (e.resourceType()) {
                case BUFFER -> "buffer";
                case TEXTURE_VIEW -> "textureView";
                case SAMPLER -> "sampler";
            };
            sb.append("{\"binding\":").append(e.binding())
              .append(",\"type\":\"").append(type).append("\"")
              .append(",\"handle\":").append(e.handle())
              .append(",\"offset\":").append(e.offset())
              .append(",\"size\":").append(e.size()).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ===== String conversion helpers (same as TeaVmWgpuBindings) =====

    private static String wgpuTextureFormatString(int format) {
        return switch (format) {
            case TEXTURE_FORMAT_R8_UNORM -> "r8unorm";
            case TEXTURE_FORMAT_RGBA8_UNORM -> "rgba8unorm";
            case TEXTURE_FORMAT_BGRA8_UNORM -> "bgra8unorm";
            case TEXTURE_FORMAT_R16_FLOAT -> "r16float";
            case TEXTURE_FORMAT_RG16_FLOAT -> "rg16float";
            case TEXTURE_FORMAT_RGBA16_FLOAT -> "rgba16float";
            case TEXTURE_FORMAT_R32_FLOAT -> "r32float";
            case TEXTURE_FORMAT_RG32_FLOAT -> "rg32float";
            case TEXTURE_FORMAT_RGBA32_FLOAT -> "rgba32float";
            case TEXTURE_FORMAT_R32_UINT -> "r32uint";
            case TEXTURE_FORMAT_R32_SINT -> "r32sint";
            case TEXTURE_FORMAT_DEPTH24_PLUS -> "depth24plus";
            case TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8 -> "depth24plus-stencil8";
            case TEXTURE_FORMAT_DEPTH32_FLOAT -> "depth32float";
            default -> "bgra8unorm";
        };
    }

    private static String vertexFormatString(int f) {
        return switch (f) {
            case VERTEX_FORMAT_UNORM8X4 -> "unorm8x4";
            case VERTEX_FORMAT_FLOAT32 -> "float32";
            case VERTEX_FORMAT_FLOAT32X2 -> "float32x2";
            case VERTEX_FORMAT_FLOAT32X3 -> "float32x3";
            case VERTEX_FORMAT_FLOAT32X4 -> "float32x4";
            default -> "float32x4";
        };
    }

    private static String topologyString(int t) {
        return t == PRIMITIVE_TOPOLOGY_TRIANGLE_LIST ? "triangle-list" : "triangle-list";
    }

    private static String cullModeString(int c) {
        return switch (c) { case CULL_MODE_FRONT -> "front"; case CULL_MODE_BACK -> "back"; default -> "none"; };
    }

    private static String frontFaceString(int f) {
        return f == FRONT_FACE_CW ? "cw" : "ccw";
    }

    private static String compareString(int c) {
        return switch (c) {
            case COMPARE_NEVER -> "never"; case COMPARE_LESS -> "less"; case COMPARE_EQUAL -> "equal";
            case COMPARE_LESS_EQUAL -> "less-equal"; case COMPARE_GREATER -> "greater";
            case COMPARE_NOT_EQUAL -> "not-equal"; case COMPARE_GREATER_EQUAL -> "greater-equal";
            case COMPARE_ALWAYS -> "always"; default -> "always";
        };
    }

    private static String stencilOpString(int o) {
        return switch (o) {
            case STENCIL_OP_KEEP -> "keep"; case STENCIL_OP_ZERO -> "zero";
            case STENCIL_OP_REPLACE -> "replace"; case STENCIL_OP_INVERT -> "invert";
            case STENCIL_OP_INCREMENT_CLAMP -> "increment-clamp"; case STENCIL_OP_DECREMENT_CLAMP -> "decrement-clamp";
            case STENCIL_OP_INCREMENT_WRAP -> "increment-wrap"; case STENCIL_OP_DECREMENT_WRAP -> "decrement-wrap";
            default -> "keep";
        };
    }

    private static String blendFactorString(int f) {
        return switch (f) {
            case BLEND_FACTOR_ZERO -> "zero"; case BLEND_FACTOR_ONE -> "one";
            case BLEND_FACTOR_SRC -> "src"; case BLEND_FACTOR_ONE_MINUS_SRC -> "one-minus-src";
            case BLEND_FACTOR_SRC_ALPHA -> "src-alpha"; case BLEND_FACTOR_ONE_MINUS_SRC_ALPHA -> "one-minus-src-alpha";
            case BLEND_FACTOR_DST -> "dst"; case BLEND_FACTOR_ONE_MINUS_DST -> "one-minus-dst";
            case BLEND_FACTOR_DST_ALPHA -> "dst-alpha"; case BLEND_FACTOR_ONE_MINUS_DST_ALPHA -> "one-minus-dst-alpha";
            default -> "zero";
        };
    }

    private static String blendOpString(int o) {
        return switch (o) {
            case BLEND_OP_ADD -> "add"; case BLEND_OP_SUBTRACT -> "subtract";
            case BLEND_OP_REVERSE_SUBTRACT -> "reverse-subtract";
            case BLEND_OP_MIN -> "min"; case BLEND_OP_MAX -> "max"; default -> "add";
        };
    }

    private static String addressModeStr(int m) {
        return switch (m) {
            case ADDRESS_MODE_REPEAT -> "repeat"; case ADDRESS_MODE_MIRROR_REPEAT -> "mirror-repeat";
            default -> "clamp-to-edge";
        };
    }

    private static String filterStr(int m) {
        return m == FILTER_MODE_LINEAR ? "linear" : "nearest";
    }

    // =====================================================================
    // JS bridge — all WebGPU operations as a single object.
    // Same logic as TeaVmWgpuBindings @JSBody methods, bundled for polyglot.
    // =====================================================================

    private static final String BRIDGE_JS = """
            (function() {
                var W = globalThis._wgpu || (globalThis._wgpu = {});
                var nextId = globalThis._wgpuNextId || 1;
                function reg(obj) { var id = nextId++; W[id] = obj; globalThis._wgpuNextId = nextId; return id; }
                function get(id) { return W[id]; }
                function rel(id) { delete W[id]; }

                return {
                    initialize: function() {
                        if (!globalThis._wgpu) { globalThis._wgpu = {}; globalThis._wgpuNextId = 1; }
                        return !!navigator.gpu;
                    },
                    isAvailable: function() { return !!navigator.gpu; },
                    getStoredAdapter: function() { return globalThis._wgpuAdapter || 0; },
                    getStoredDevice: function() { return globalThis._wgpuDevice || 0; },
                    release: function(id) { rel(id); },

                    // Surface
                    configureSurface: function(deviceId) {
                        var canvas = document.getElementById('canvas');
                        var ctx = canvas.getContext('webgpu');
                        var device = get(deviceId);
                        var format = navigator.gpu.getPreferredCanvasFormat();
                        ctx.configure({ device: device, format: format });
                        globalThis._wgpuCanvasFormat = format;
                        return reg(ctx);
                    },
                    getSurfaceTextureView: function(ctxId) {
                        var ctx = get(ctxId);
                        return reg(ctx.getCurrentTexture().createView());
                    },
                    getPreferredCanvasFormat: function() {
                        return globalThis._wgpuCanvasFormat || 'bgra8unorm';
                    },

                    // Device
                    deviceGetQueue: function(deviceId) { return reg(get(deviceId).queue); },

                    // Buffer
                    createBuffer: function(deviceId, size, usage) {
                        return reg(get(deviceId).createBuffer({ size: size, usage: usage }));
                    },
                    bufferRelease: function(id) { var b = get(id); if (b) { b.destroy(); rel(id); } },
                    queueWriteBuffer: function(queueId, bufferId, offset, bytes) {
                        get(queueId).writeBuffer(get(bufferId), offset, new Uint8Array(bytes));
                    },
                    bufferMapReadSync: function(bufferId, size) {
                        // Synchronous polling — works in GraalJS single-threaded context
                        throw new Error('bufferMapReadSync requires async support');
                    },
                    bufferGetMappedRange: function(bufferId, offset, size) {
                        return Array.from(new Uint8Array(get(bufferId).getMappedRange(offset, size)));
                    },
                    bufferUnmap: function(bufferId) { get(bufferId).unmap(); },

                    // Texture
                    createTexture: function(deviceId, w, h, depth, format, dim, usage) {
                        return reg(get(deviceId).createTexture({
                            size: { width: w, height: h, depthOrArrayLayers: depth },
                            format: format, dimension: dim, usage: usage
                        }));
                    },
                    textureCreateView: function(texId) { return reg(get(texId).createView()); },
                    textureRelease: function(id) { var t = get(id); if (t) { t.destroy(); rel(id); } },
                    queueWriteTexture: function(queueId, textureId, w, h, depth, bytesPerRow, bytes) {
                        get(queueId).writeTexture(
                            { texture: get(textureId) }, new Uint8Array(bytes),
                            { bytesPerRow: bytesPerRow, rowsPerImage: h },
                            { width: w, height: h, depthOrArrayLayers: depth }
                        );
                    },

                    // Sampler
                    createSampler: function(deviceId, addrU, addrV, addrW, mag, min, mip,
                                            lodMin, lodMax, compare, maxAniso) {
                        var desc = {
                            addressModeU: addrU, addressModeV: addrV, addressModeW: addrW,
                            magFilter: mag, minFilter: min, mipmapFilter: mip,
                            lodMinClamp: lodMin, lodMaxClamp: lodMax, maxAnisotropy: maxAniso
                        };
                        if (compare) desc.compare = compare;
                        return reg(get(deviceId).createSampler(desc));
                    },

                    // Shader Module
                    createShaderModule: function(deviceId, wgsl) {
                        return reg(get(deviceId).createShaderModule({ code: wgsl }));
                    },

                    // Bind Group Layout
                    createBindGroupLayout: function(deviceId, entriesJson) {
                        return reg(get(deviceId).createBindGroupLayout({ entries: JSON.parse(entriesJson) }));
                    },

                    // Pipeline Layout
                    createPipelineLayout: function(deviceId, layoutIds) {
                        var layouts = [];
                        for (var i = 0; i < layoutIds.length; i++) layouts.push(get(layoutIds[i]));
                        return reg(get(deviceId).createPipelineLayout({ bindGroupLayouts: layouts }));
                    },

                    // Render Pipeline
                    createRenderPipeline: function(deviceId, layoutId, vsModId, vsEntry,
                            fsModId, fsEntry, stride, attrsJson, topology, cullMode, frontFace,
                            colorFormat, depthFormat, depthWrite, depthCompare,
                            sfCompare, sfFail, sfDepthFail, sfPass,
                            sbCompare, sbFail, sbDepthFail, sbPass,
                            stencilReadMask, stencilWriteMask,
                            blendCSrc, blendCDst, blendCOp, blendASrc, blendADst, blendAOp) {
                        var desc = {
                            layout: layoutId > 0 ? get(layoutId) : 'auto',
                            vertex: {
                                module: get(vsModId), entryPoint: vsEntry,
                                buffers: stride > 0 ? [{ arrayStride: stride, stepMode: 'vertex',
                                    attributes: JSON.parse(attrsJson) }] : []
                            },
                            primitive: { topology: topology, frontFace: frontFace, cullMode: cullMode }
                        };
                        if (fsModId > 0) {
                            desc.fragment = {
                                module: get(fsModId), entryPoint: fsEntry,
                                targets: [{ format: colorFormat, blend: {
                                    color: { srcFactor: blendCSrc, dstFactor: blendCDst, operation: blendCOp },
                                    alpha: { srcFactor: blendASrc, dstFactor: blendADst, operation: blendAOp }
                                }}]
                            };
                        }
                        if (depthFormat && depthFormat.length > 0) {
                            desc.depthStencil = {
                                format: depthFormat, depthWriteEnabled: depthWrite, depthCompare: depthCompare,
                                stencilFront: { compare: sfCompare, failOp: sfFail, depthFailOp: sfDepthFail, passOp: sfPass },
                                stencilBack: { compare: sbCompare, failOp: sbFail, depthFailOp: sbDepthFail, passOp: sbPass },
                                stencilReadMask: stencilReadMask, stencilWriteMask: stencilWriteMask
                            };
                        }
                        return reg(get(deviceId).createRenderPipeline(desc));
                    },

                    // Bind Group
                    createBindGroup: function(deviceId, layoutId, entriesJson) {
                        var raw = JSON.parse(entriesJson);
                        var entries = [];
                        for (var i = 0; i < raw.length; i++) {
                            var e = raw[i];
                            var entry = { binding: e.binding };
                            if (e.type === 'buffer') entry.resource = { buffer: get(e.handle), offset: e.offset, size: e.size };
                            else if (e.type === 'textureView') entry.resource = get(e.handle);
                            else if (e.type === 'sampler') entry.resource = get(e.handle);
                            entries.push(entry);
                        }
                        return reg(get(deviceId).createBindGroup({ layout: get(layoutId), entries: entries }));
                    },

                    // Command Encoder
                    createCommandEncoder: function(deviceId) {
                        return reg(get(deviceId).createCommandEncoder());
                    },
                    beginRenderPass: function(encId, colorViewId, r, g, b, a, depthViewId, depthClear) {
                        var desc = { colorAttachments: [{
                            view: get(colorViewId), clearValue: { r:r, g:g, b:b, a:a },
                            loadOp: 'clear', storeOp: 'store'
                        }]};
                        if (depthViewId > 0) {
                            desc.depthStencilAttachment = {
                                view: get(depthViewId), depthClearValue: depthClear,
                                depthLoadOp: 'clear', depthStoreOp: 'store',
                                stencilClearValue: 0, stencilLoadOp: 'clear', stencilStoreOp: 'store'
                            };
                        }
                        return reg(get(encId).beginRenderPass(desc));
                    },
                    copyBufferToBuffer: function(encId, srcId, srcOff, dstId, dstOff, size) {
                        get(encId).copyBufferToBuffer(get(srcId), srcOff, get(dstId), dstOff, size);
                    },
                    copyTextureToBuffer: function(encId, texId, bufId, w, h, bytesPerRow, rowsPerImage) {
                        get(encId).copyTextureToBuffer(
                            { texture: get(texId) },
                            { buffer: get(bufId), bytesPerRow: bytesPerRow, rowsPerImage: rowsPerImage },
                            { width: w, height: h });
                    },
                    encoderFinish: function(encId) {
                        var cmdBuf = get(encId).finish(); rel(encId); return reg(cmdBuf);
                    },

                    // Queue
                    queueSubmit: function(queueId, cmdBufId) {
                        get(queueId).submit([get(cmdBufId)]); rel(cmdBufId);
                    },

                    // Render Pass
                    renderPassEnd: function(id) { get(id).end(); },
                    renderPassSetPipeline: function(id, pId) { get(id).setPipeline(get(pId)); },
                    renderPassSetVertexBuffer: function(id, slot, bId, off, sz) { get(id).setVertexBuffer(slot, get(bId), off, sz); },
                    renderPassSetIndexBuffer: function(id, bId, fmt, off, sz) { get(id).setIndexBuffer(get(bId), fmt, off, sz); },
                    renderPassSetBindGroup: function(id, idx, bgId) { get(id).setBindGroup(idx, get(bgId)); },
                    renderPassSetViewport: function(id, x, y, w, h, minD, maxD) { get(id).setViewport(x, y, w, h, minD, maxD); },
                    renderPassSetScissorRect: function(id, x, y, w, h) { get(id).setScissorRect(x, y, w, h); },
                    renderPassSetStencilReference: function(id, ref) { get(id).setStencilReference(ref); },
                    renderPassDraw: function(id, vc, ic, fv, fi) { get(id).draw(vc, ic, fv, fi); },
                    renderPassDrawIndexed: function(id, ic, instC, fi, bv, fInst) { get(id).drawIndexed(ic, instC, fi, bv, fInst); }
                };
            })()
            """;
}

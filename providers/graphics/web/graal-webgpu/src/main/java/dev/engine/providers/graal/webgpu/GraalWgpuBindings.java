package dev.engine.providers.graal.webgpu;

import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.graphics.window.WindowHandle;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * GraalVM Web Image implementation of {@link WgpuBindings}.
 *
 * <p>Uses a JS bridge pattern to work around GraalVM Web Image's inability to
 * properly coerce Java primitive types (int, boolean, double) to/from JavaScript
 * values. Java {@code int} parameters become WASM GC i32 structs, not JS numbers.
 *
 * <p>The bridge is a single JavaScript object ({@code globalThis._bridge}) that
 * contains all WebGPU functions. Java calls dispatch through typed methods:
 * <ul>
 *   <li>{@code callInt(cmd, args)} — returns JSNumber (for handle IDs)</li>
 *   <li>{@code callVoid(cmd, args)} — void (fire-and-forget)</li>
 *   <li>{@code callString(cmd, args)} — returns JSString</li>
 *   <li>{@code callBool(cmd, args)} — returns JSBoolean</li>
 *   <li>{@code callBytes(cmd, args)} — returns byte[]</li>
 * </ul>
 *
 * <p>All WebGPU objects live in {@code globalThis._wgpu} keyed by integer IDs —
 * the same handle registry pattern as TeaVmWgpuBindings.
 */
public class GraalWgpuBindings implements WgpuBindings {

    private static final Logger log = LoggerFactory.getLogger(GraalWgpuBindings.class);

    private static boolean bridgeInitialized = false;

    private static void ensureBridge() {
        if (!bridgeInitialized) {
            initBridge();
            bridgeInitialized = true;
        }
    }

    // =====================================================================
    // @JS bridge — single init, typed dispatch
    // =====================================================================

    @JS(value = """
        if (!globalThis._wgpu) { globalThis._wgpu = {}; globalThis._wgpuNextId = 1; }
        var W = globalThis._wgpu;
        var reg = function(obj) { var id = globalThis._wgpuNextId++; W[id] = obj; return id; };
        var g = function(id) { return W[id]; };
        var rel = function(id) { delete W[id]; };

        var B = {};
        globalThis._bridge = B;

        // --- lifecycle ---
        B.isAvailable = function(a) { return !!navigator.gpu; };

        // --- surface ---
        B.configureSurface = function(a) {
            var canvas = document.getElementById('canvas');
            var ctx = canvas.getContext('webgpu');
            var device = g(a.deviceId);
            var format = navigator.gpu.getPreferredCanvasFormat();
            ctx.configure({ device: device, format: format, usage: GPUTextureUsage.RENDER_ATTACHMENT | GPUTextureUsage.COPY_SRC });
            globalThis._wgpuCanvasFormat = format;
            return reg(ctx);
        };
        B.getSurfaceTextureView = function(a) {
            var ctx = g(a.ctxId);
            var view = ctx.getCurrentTexture().createView();
            return reg(view);
        };
        B.getPreferredCanvasFormat = function(a) {
            return globalThis._wgpuCanvasFormat || 'bgra8unorm';
        };

        // --- adapter/device ---
        B.getStoredAdapter = function(a) { return globalThis._wgpuAdapter || 0; };
        B.getStoredDevice = function(a) { return globalThis._wgpuDevice || 0; };
        B.deviceGetQueue = function(a) {
            return reg(g(a.deviceId).queue);
        };

        // --- buffer ---
        B.createBuffer = function(a) {
            var buf = g(a.deviceId).createBuffer({ size: a.size, usage: a.usage });
            return reg(buf);
        };
        B.bufferRelease = function(a) {
            var b = g(a.id); if (b) { b.destroy(); rel(a.id); }
        };
        B.queueWriteBuffer = function(a) {
            g(a.queueId).writeBuffer(g(a.bufferId), a.offset, new Uint8Array(a.bytes));
        };
        B.startBufferMap = function(a) {
            globalThis._wgpuMapDone = false;
            g(a.bufferId).mapAsync(GPUMapMode.READ, 0, a.size).then(function() {
                globalThis._wgpuMapDone = true;
            });
        };
        B.isMapDone = function(a) { return globalThis._wgpuMapDone === true; };
        B.bufferGetMappedRange = function(a) {
            return Array.from(new Uint8Array(g(a.bufferId).getMappedRange(a.offset, a.size)));
        };
        B.bufferUnmap = function(a) { g(a.bufferId).unmap(); };

        // --- texture ---
        B.createTexture = function(a) {
            var tex = g(a.deviceId).createTexture({
                size: { width: a.w, height: a.h, depthOrArrayLayers: a.depth },
                format: a.format, dimension: a.dim, usage: a.usage
            });
            return reg(tex);
        };
        B.textureCreateView = function(a) {
            return reg(g(a.texId).createView());
        };
        B.textureRelease = function(a) {
            var t = g(a.id); if (t) { t.destroy(); rel(a.id); }
        };
        B.queueWriteTexture = function(a) {
            g(a.queueId).writeTexture(
                { texture: g(a.textureId) }, new Uint8Array(a.bytes),
                { bytesPerRow: a.bytesPerRow, rowsPerImage: a.h },
                { width: a.w, height: a.h, depthOrArrayLayers: a.depth }
            );
        };

        // --- sampler ---
        B.createSampler = function(a) {
            var desc = {
                addressModeU: a.addrU, addressModeV: a.addrV, addressModeW: a.addrW,
                magFilter: a.mag, minFilter: a.min, mipmapFilter: a.mip,
                lodMinClamp: a.lodMin, lodMaxClamp: a.lodMax, maxAnisotropy: a.maxAniso
            };
            if (a.compare && a.compare.length > 0) desc.compare = a.compare;
            return reg(g(a.deviceId).createSampler(desc));
        };

        // --- shader module ---
        B.createShaderModule = function(a) {
            return reg(g(a.deviceId).createShaderModule({ code: a.wgsl }));
        };

        // --- bind group layout ---
        B.createBindGroupLayout = function(a) {
            var entries = JSON.parse(a.entriesJson);
            return reg(g(a.deviceId).createBindGroupLayout({ entries: entries }));
        };

        // --- pipeline layout ---
        B.createPipelineLayout = function(a) {
            var ids = JSON.parse(a.layoutIdsJson);
            var layouts = [];
            for (var i = 0; i < ids.length; i++) layouts.push(g(ids[i]));
            return reg(g(a.deviceId).createPipelineLayout({ bindGroupLayouts: layouts }));
        };

        // --- render pipeline ---
        B.createRenderPipeline = function(a) {
            var desc = {
                layout: a.layoutId > 0 ? g(a.layoutId) : 'auto',
                vertex: {
                    module: g(a.vsModId), entryPoint: a.vsEntry,
                    buffers: a.stride > 0 ? [{ arrayStride: a.stride, stepMode: 'vertex',
                        attributes: JSON.parse(a.attrsJson) }] : []
                },
                primitive: { topology: a.topology, frontFace: a.frontFace, cullMode: a.cullMode }
            };
            if (a.fsModId > 0) {
                desc.fragment = {
                    module: g(a.fsModId), entryPoint: a.fsEntry,
                    targets: [{ format: a.colorFormat, blend: {
                        color: { srcFactor: a.blendCSrc, dstFactor: a.blendCDst, operation: a.blendCOp },
                        alpha: { srcFactor: a.blendASrc, dstFactor: a.blendADst, operation: a.blendAOp }
                    }}]
                };
            }
            if (a.depthFormat && a.depthFormat.length > 0) {
                desc.depthStencil = {
                    format: a.depthFormat, depthWriteEnabled: a.depthWrite, depthCompare: a.depthCompare,
                    stencilFront: { compare: a.sfCompare, failOp: a.sfFail, depthFailOp: a.sfDepthFail, passOp: a.sfPass },
                    stencilBack: { compare: a.sbCompare, failOp: a.sbFail, depthFailOp: a.sbDepthFail, passOp: a.sbPass },
                    stencilReadMask: a.stencilReadMask, stencilWriteMask: a.stencilWriteMask
                };
            }
            return reg(g(a.deviceId).createRenderPipeline(desc));
        };

        // --- bind group ---
        B.createBindGroup = function(a) {
            var raw = JSON.parse(a.entriesJson);
            var entries = [];
            for (var i = 0; i < raw.length; i++) {
                var e = raw[i];
                var entry = { binding: e.binding };
                if (e.type === 'buffer') entry.resource = { buffer: g(e.handle), offset: e.offset, size: e.size };
                else if (e.type === 'textureView') entry.resource = g(e.handle);
                else if (e.type === 'sampler') entry.resource = g(e.handle);
                entries.push(entry);
            }
            return reg(g(a.deviceId).createBindGroup({ layout: g(a.layoutId), entries: entries }));
        };

        // --- command encoder ---
        B.createCommandEncoder = function(a) {
            return reg(g(a.deviceId).createCommandEncoder());
        };
        B.beginRenderPass = function(a) {
            var desc = { colorAttachments: [{
                view: g(a.colorViewId), clearValue: { r: a.r, g: a.g, b: a.b, a: a.a },
                loadOp: 'clear', storeOp: 'store'
            }]};
            if (a.depthViewId > 0) {
                desc.depthStencilAttachment = {
                    view: g(a.depthViewId), depthClearValue: a.depthClear,
                    depthLoadOp: 'clear', depthStoreOp: 'store',
                    stencilClearValue: 0, stencilLoadOp: 'clear', stencilStoreOp: 'store'
                };
            }
            return reg(g(a.encId).beginRenderPass(desc));
        };
        B.copyBufferToBuffer = function(a) {
            g(a.encId).copyBufferToBuffer(g(a.srcId), a.srcOff, g(a.dstId), a.dstOff, a.size);
        };
        B.copyTextureToBuffer = function(a) {
            g(a.encId).copyTextureToBuffer(
                { texture: g(a.texId) },
                { buffer: g(a.bufId), bytesPerRow: a.bytesPerRow, rowsPerImage: a.rowsPerImage },
                { width: a.w, height: a.h });
        };
        B.encoderFinish = function(a) {
            var cmdBuf = g(a.encId).finish();
            rel(a.encId);
            return reg(cmdBuf);
        };

        // --- queue ---
        B.queueSubmit = function(a) {
            g(a.queueId).submit([g(a.cmdBufId)]);
            rel(a.cmdBufId);
        };

        // --- render pass ---
        B.renderPassEnd = function(a) { g(a.id).end(); };
        B.renderPassSetPipeline = function(a) { g(a.id).setPipeline(g(a.pId)); };
        B.renderPassSetVertexBuffer = function(a) { g(a.id).setVertexBuffer(a.slot, g(a.bId), a.off, a.sz); };
        B.renderPassSetIndexBuffer = function(a) { g(a.id).setIndexBuffer(g(a.bId), a.fmt, a.off, a.sz); };
        B.renderPassSetBindGroup = function(a) { g(a.id).setBindGroup(a.idx, g(a.bgId)); };
        B.renderPassSetViewport = function(a) { g(a.id).setViewport(a.x, a.y, a.w, a.h, a.minD, a.maxD); };
        B.renderPassSetScissorRect = function(a) { g(a.id).setScissorRect(a.x, a.y, a.w, a.h); };
        B.renderPassSetStencilReference = function(a) { g(a.id).setStencilReference(a.ref); };
        B.renderPassDraw = function(a) { g(a.id).draw(a.vc, a.ic, a.fv, a.fi); };
        B.renderPassDrawIndexed = function(a) { g(a.id).drawIndexed(a.ic, a.instC, a.fi, a.bv, a.fInst); };

        // --- release ---
        B.release = function(a) { rel(a.id); };
    """)
    private static native void initBridge();

    // =====================================================================
    // Typed dispatch methods — all take command + JSON args via JSString
    // =====================================================================

    @JS(args = {"cmd", "args"}, value = """
        var a = JSON.parse(args); return globalThis._bridge[cmd](a);
    """)
    private static native JSNumber callInt(JSString cmd, JSString args);

    @JS(args = {"cmd", "args"}, value = """
        var a = JSON.parse(args); globalThis._bridge[cmd](a);
    """)
    private static native void callVoid(JSString cmd, JSString args);

    @JS(args = {"cmd", "args"}, value = """
        var a = JSON.parse(args); return globalThis._bridge[cmd](a);
    """)
    private static native JSString callString(JSString cmd, JSString args);

    @JS(args = {"cmd", "args"}, value = """
        var a = JSON.parse(args); return globalThis._bridge[cmd](a);
    """)
    private static native JSBoolean callBool(JSString cmd, JSString args);

    @JS(args = {"cmd", "args"}, value = """
        var a = JSON.parse(args); return globalThis._bridge[cmd](a);
    """)
    private static native byte[] callBytes(JSString cmd, JSString args);

    // =====================================================================
    // Convenience dispatch wrappers
    // =====================================================================

    private static int dispatchInt(String cmd, String argsJson) {
        return callInt(JSString.of(cmd), JSString.of(argsJson)).asInt();
    }

    private static void dispatchVoid(String cmd, String argsJson) {
        callVoid(JSString.of(cmd), JSString.of(argsJson));
    }

    private static String dispatchString(String cmd, String argsJson) {
        return callString(JSString.of(cmd), JSString.of(argsJson)).asString();
    }

    private static boolean dispatchBool(String cmd, String argsJson) {
        return callBool(JSString.of(cmd), JSString.of(argsJson)).asBoolean();
    }

    private static byte[] dispatchBytes(String cmd, String argsJson) {
        return callBytes(JSString.of(cmd), JSString.of(argsJson));
    }

    // ===== Lifecycle =====

    @Override
    public boolean initialize() {
        ensureBridge();
        return dispatchBool("isAvailable", "{}");
    }

    @Override
    public boolean isAvailable() {
        ensureBridge();
        return dispatchBool("isAvailable", "{}");
    }

    // ===== Surface / Presentation =====

    @Override
    public long configureSurface(long instance, long device, WindowHandle window) {
        return dispatchInt("configureSurface", "{\"deviceId\":" + (int) device + "}");
    }

    @Override
    public long getSurfaceTextureView(long surface) {
        return dispatchInt("getSurfaceTextureView", "{\"ctxId\":" + (int) surface + "}");
    }

    @Override
    public void releaseSurfaceTextureView(long textureView) {
        dispatchVoid("release", "{\"id\":" + (int) textureView + "}");
    }

    @Override
    public boolean hasSurface() { return true; }

    @Override
    public int surfaceFormat() {
        String fmt = dispatchString("getPreferredCanvasFormat", "{}");
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
        int id = dispatchInt("getStoredAdapter", "{}");
        if (id <= 0) throw new IllegalStateException(
                "Adapter not initialized. WebGPU must be initialized by the host page.");
        return id;
    }

    @Override
    public void adapterRelease(long adapter) {}

    // ===== Device =====

    @Override
    public long adapterRequestDevice(long instance, long adapter) {
        int id = dispatchInt("getStoredDevice", "{}");
        if (id <= 0) throw new IllegalStateException(
                "Device not initialized. WebGPU must be initialized by the host page.");
        return id;
    }

    @Override
    public long deviceGetQueue(long device) {
        return dispatchInt("deviceGetQueue", "{\"deviceId\":" + (int) device + "}");
    }

    @Override
    public void deviceRelease(long device) {}

    // ===== Buffer =====

    @Override
    public long deviceCreateBuffer(long device, long size, int usage) {
        return dispatchInt("createBuffer",
                "{\"deviceId\":" + (int) device + ",\"size\":" + (int) size + ",\"usage\":" + usage + "}");
    }

    @Override
    public void bufferRelease(long buffer) {
        dispatchVoid("bufferRelease", "{\"id\":" + (int) buffer + "}");
    }

    @Override
    public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        byte[] bytes = new byte[size];
        data.get(bytes);
        // Encode bytes as JSON array
        String bytesJson = encodeByteArray(bytes);
        dispatchVoid("queueWriteBuffer",
                "{\"queueId\":" + (int) queue + ",\"bufferId\":" + (int) buffer
                        + ",\"offset\":" + offset + ",\"bytes\":" + bytesJson + "}");
    }

    @Override
    public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        dispatchVoid("startBufferMap",
                "{\"bufferId\":" + (int) buffer + ",\"size\":" + size + "}");
        for (int i = 0; i < maxPolls; i++) {
            if (dispatchBool("isMapDone", "{}")) return;
        }
        log.warn("bufferMapReadSync: map did not complete within {} polls", maxPolls);
    }

    @Override
    public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        byte[] data = dispatchBytes("bufferGetMappedRange",
                "{\"bufferId\":" + (int) buffer + ",\"offset\":" + offset + ",\"size\":" + size + "}");
        dest.put(data, 0, Math.min(size, data.length));
    }

    @Override
    public void bufferUnmap(long buffer) {
        dispatchVoid("bufferUnmap", "{\"bufferId\":" + (int) buffer + "}");
    }

    // ===== Texture =====

    @Override
    public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                    int format, int dimension, int usage) {
        String fmtStr = wgpuTextureFormatString(format);
        String dimStr = dimension == TEXTURE_DIMENSION_3D ? "3d" : "2d";
        return dispatchInt("createTexture",
                "{\"deviceId\":" + (int) device + ",\"w\":" + width + ",\"h\":" + height
                        + ",\"depth\":" + depthOrLayers + ",\"format\":\"" + fmtStr
                        + "\",\"dim\":\"" + dimStr + "\",\"usage\":" + usage + "}");
    }

    @Override
    public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        return dispatchInt("textureCreateView", "{\"texId\":" + (int) texture + "}");
    }

    @Override
    public void textureRelease(long texture) {
        dispatchVoid("textureRelease", "{\"id\":" + (int) texture + "}");
    }

    @Override
    public void textureViewRelease(long textureView) {
        dispatchVoid("release", "{\"id\":" + (int) textureView + "}");
    }

    @Override
    public void queueWriteTexture(long queue, long texture, int width, int height,
                                  int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        String bytesJson = encodeByteArray(bytes);
        dispatchVoid("queueWriteTexture",
                "{\"queueId\":" + (int) queue + ",\"textureId\":" + (int) texture
                        + ",\"w\":" + width + ",\"h\":" + height + ",\"depth\":" + depthOrLayers
                        + ",\"bytesPerRow\":" + bytesPerRow + ",\"bytes\":" + bytesJson + "}");
    }

    // ===== Sampler =====

    @Override
    public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                    int magFilter, int minFilter, int mipmapFilter,
                                    float lodMinClamp, float lodMaxClamp,
                                    int compare, float maxAnisotropy) {
        return dispatchInt("createSampler",
                "{\"deviceId\":" + (int) device
                        + ",\"addrU\":\"" + addressModeStr(addressU)
                        + "\",\"addrV\":\"" + addressModeStr(addressV)
                        + "\",\"addrW\":\"" + addressModeStr(addressW)
                        + "\",\"mag\":\"" + filterStr(magFilter)
                        + "\",\"min\":\"" + filterStr(minFilter)
                        + "\",\"mip\":\"" + filterStr(mipmapFilter)
                        + "\",\"lodMin\":" + lodMinClamp
                        + ",\"lodMax\":" + lodMaxClamp
                        + ",\"compare\":\"" + (compare != 0 ? compareString(compare) : "")
                        + "\",\"maxAniso\":" + (int) maxAnisotropy + "}");
    }

    @Override
    public void samplerRelease(long sampler) {
        dispatchVoid("release", "{\"id\":" + (int) sampler + "}");
    }

    // ===== Shader Module =====

    @Override
    public long deviceCreateShaderModule(long device, String wgsl) {
        return dispatchInt("createShaderModule",
                "{\"deviceId\":" + (int) device + ",\"wgsl\":" + jsonEscape(wgsl) + "}");
    }

    @Override
    public boolean shaderModuleIsValid(long shaderModule) { return shaderModule > 0; }

    @Override
    public void shaderModuleRelease(long shaderModule) {
        dispatchVoid("release", "{\"id\":" + (int) shaderModule + "}");
    }

    // ===== Bind Group Layout =====

    @Override
    public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        // entriesJson is already valid JSON, embed it as a string value
        String entriesJson = encodeBindGroupLayoutEntries(entries);
        return dispatchInt("createBindGroupLayout",
                "{\"deviceId\":" + (int) device + ",\"entriesJson\":" + jsonEscape(entriesJson) + "}");
    }

    @Override
    public void bindGroupLayoutRelease(long bindGroupLayout) {
        dispatchVoid("release", "{\"id\":" + (int) bindGroupLayout + "}");
    }

    // ===== Pipeline Layout =====

    @Override
    public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < bindGroupLayouts.length; i++) {
            if (i > 0) sb.append(",");
            sb.append((int) bindGroupLayouts[i]);
        }
        sb.append("]");
        return dispatchInt("createPipelineLayout",
                "{\"deviceId\":" + (int) device + ",\"layoutIdsJson\":" + jsonEscape(sb.toString()) + "}");
    }

    @Override
    public void pipelineLayoutRelease(long pipelineLayout) {
        dispatchVoid("release", "{\"id\":" + (int) pipelineLayout + "}");
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

        String depthFormat = desc.depthStencilFormat() > 0
                ? wgpuTextureFormatString(desc.depthStencilFormat()) : "";

        var json = new StringBuilder("{");
        json.append("\"deviceId\":").append((int) device);
        json.append(",\"layoutId\":").append((int) desc.pipelineLayout());
        json.append(",\"vsModId\":").append((int) desc.vertexModule());
        json.append(",\"vsEntry\":\"").append(desc.vertexEntryPoint()).append("\"");
        json.append(",\"fsModId\":").append((int) desc.fragmentModule());
        json.append(",\"fsEntry\":\"").append(desc.fragmentEntryPoint()).append("\"");
        json.append(",\"stride\":").append(stride);
        json.append(",\"attrsJson\":").append(jsonEscape(attrsJson));
        json.append(",\"topology\":\"").append(topologyString(desc.topology())).append("\"");
        json.append(",\"cullMode\":\"").append(cullModeString(desc.cullMode())).append("\"");
        json.append(",\"frontFace\":\"").append(frontFaceString(desc.frontFace())).append("\"");
        json.append(",\"colorFormat\":\"").append(wgpuTextureFormatString(desc.colorTargetFormat())).append("\"");
        json.append(",\"depthFormat\":\"").append(depthFormat).append("\"");
        json.append(",\"depthWrite\":").append(desc.depthWriteEnabled() == OPTIONAL_BOOL_TRUE);
        json.append(",\"depthCompare\":\"").append(compareString(desc.depthCompare())).append("\"");
        json.append(",\"sfCompare\":\"").append(compareString(sf.compare())).append("\"");
        json.append(",\"sfFail\":\"").append(stencilOpString(sf.failOp())).append("\"");
        json.append(",\"sfDepthFail\":\"").append(stencilOpString(sf.depthFailOp())).append("\"");
        json.append(",\"sfPass\":\"").append(stencilOpString(sf.passOp())).append("\"");
        json.append(",\"sbCompare\":\"").append(compareString(sb2.compare())).append("\"");
        json.append(",\"sbFail\":\"").append(stencilOpString(sb2.failOp())).append("\"");
        json.append(",\"sbDepthFail\":\"").append(stencilOpString(sb2.depthFailOp())).append("\"");
        json.append(",\"sbPass\":\"").append(stencilOpString(sb2.passOp())).append("\"");
        json.append(",\"stencilReadMask\":").append(desc.stencilReadMask());
        json.append(",\"stencilWriteMask\":").append(desc.stencilWriteMask());
        json.append(",\"blendCSrc\":\"").append(blendFactorString(desc.blendColorSrcFactor())).append("\"");
        json.append(",\"blendCDst\":\"").append(blendFactorString(desc.blendColorDstFactor())).append("\"");
        json.append(",\"blendCOp\":\"").append(blendOpString(desc.blendColorOperation())).append("\"");
        json.append(",\"blendASrc\":\"").append(blendFactorString(desc.blendAlphaSrcFactor())).append("\"");
        json.append(",\"blendADst\":\"").append(blendFactorString(desc.blendAlphaDstFactor())).append("\"");
        json.append(",\"blendAOp\":\"").append(blendOpString(desc.blendAlphaOperation())).append("\"");
        json.append("}");

        return dispatchInt("createRenderPipeline", json.toString());
    }

    @Override
    public void renderPipelineRelease(long renderPipeline) {
        dispatchVoid("release", "{\"id\":" + (int) renderPipeline + "}");
    }

    // ===== Bind Group =====

    @Override
    public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        String entriesJson = encodeBindGroupEntries(entries);
        return dispatchInt("createBindGroup",
                "{\"deviceId\":" + (int) device + ",\"layoutId\":" + (int) layout
                        + ",\"entriesJson\":" + jsonEscape(entriesJson) + "}");
    }

    @Override
    public void bindGroupRelease(long bindGroup) {
        dispatchVoid("release", "{\"id\":" + (int) bindGroup + "}");
    }

    // ===== Command Encoder =====

    @Override
    public long deviceCreateCommandEncoder(long device) {
        return dispatchInt("createCommandEncoder", "{\"deviceId\":" + (int) device + "}");
    }

    @Override
    public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        var ca = desc.colorAttachments();
        if (ca == null || ca.length == 0) throw new IllegalArgumentException("Need color attachment");
        var c = ca[0];
        int depthView = desc.depthStencil() != null ? (int) desc.depthStencil().textureView() : 0;
        float depthClear = desc.depthStencil() != null ? desc.depthStencil().depthClearValue() : 1.0f;

        return dispatchInt("beginRenderPass",
                "{\"encId\":" + (int) encoder + ",\"colorViewId\":" + (int) c.textureView()
                        + ",\"r\":" + c.clearR() + ",\"g\":" + c.clearG()
                        + ",\"b\":" + c.clearB() + ",\"a\":" + c.clearA()
                        + ",\"depthViewId\":" + depthView + ",\"depthClear\":" + depthClear + "}");
    }

    @Override
    public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                  long dst, int dstOffset, int size) {
        dispatchVoid("copyBufferToBuffer",
                "{\"encId\":" + (int) encoder + ",\"srcId\":" + (int) src + ",\"srcOff\":" + srcOffset
                        + ",\"dstId\":" + (int) dst + ",\"dstOff\":" + dstOffset + ",\"size\":" + size + "}");
    }

    @Override
    public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                   int width, int height,
                                                   int bytesPerRow, int rowsPerImage) {
        dispatchVoid("copyTextureToBuffer",
                "{\"encId\":" + (int) encoder + ",\"texId\":" + (int) texture + ",\"bufId\":" + (int) buffer
                        + ",\"w\":" + width + ",\"h\":" + height
                        + ",\"bytesPerRow\":" + bytesPerRow + ",\"rowsPerImage\":" + rowsPerImage + "}");
    }

    @Override
    public long commandEncoderFinish(long encoder) {
        return dispatchInt("encoderFinish", "{\"encId\":" + (int) encoder + "}");
    }

    @Override
    public void commandEncoderRelease(long encoder) {
        dispatchVoid("release", "{\"id\":" + (int) encoder + "}");
    }

    @Override
    public void commandBufferRelease(long commandBuffer) {
        dispatchVoid("release", "{\"id\":" + (int) commandBuffer + "}");
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queue, long commandBuffer) {
        dispatchVoid("queueSubmit",
                "{\"queueId\":" + (int) queue + ",\"cmdBufId\":" + (int) commandBuffer + "}");
    }

    // ===== Render Pass Encoder =====

    @Override
    public void renderPassEnd(long renderPass) {
        dispatchVoid("renderPassEnd", "{\"id\":" + (int) renderPass + "}");
    }

    @Override
    public void renderPassRelease(long renderPass) {
        dispatchVoid("release", "{\"id\":" + (int) renderPass + "}");
    }

    @Override
    public void renderPassSetPipeline(long renderPass, long pipeline) {
        dispatchVoid("renderPassSetPipeline",
                "{\"id\":" + (int) renderPass + ",\"pId\":" + (int) pipeline + "}");
    }

    @Override
    public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size) {
        dispatchVoid("renderPassSetVertexBuffer",
                "{\"id\":" + (int) renderPass + ",\"slot\":" + slot + ",\"bId\":" + (int) buffer
                        + ",\"off\":" + offset + ",\"sz\":" + size + "}");
    }

    @Override
    public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat,
                                          int offset, int size) {
        String fmt = indexFormat == INDEX_FORMAT_UINT32 ? "uint32" : "uint16";
        dispatchVoid("renderPassSetIndexBuffer",
                "{\"id\":" + (int) renderPass + ",\"bId\":" + (int) buffer
                        + ",\"fmt\":\"" + fmt + "\",\"off\":" + offset + ",\"sz\":" + size + "}");
    }

    @Override
    public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        dispatchVoid("renderPassSetBindGroup",
                "{\"id\":" + (int) renderPass + ",\"idx\":" + groupIndex + ",\"bgId\":" + (int) bindGroup + "}");
    }

    @Override
    public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                      float minDepth, float maxDepth) {
        dispatchVoid("renderPassSetViewport",
                "{\"id\":" + (int) renderPass + ",\"x\":" + x + ",\"y\":" + y
                        + ",\"w\":" + w + ",\"h\":" + h + ",\"minD\":" + minDepth + ",\"maxD\":" + maxDepth + "}");
    }

    @Override
    public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        dispatchVoid("renderPassSetScissorRect",
                "{\"id\":" + (int) renderPass + ",\"x\":" + x + ",\"y\":" + y
                        + ",\"w\":" + width + ",\"h\":" + height + "}");
    }

    @Override
    public void renderPassSetStencilReference(long renderPass, int ref) {
        dispatchVoid("renderPassSetStencilReference",
                "{\"id\":" + (int) renderPass + ",\"ref\":" + ref + "}");
    }

    @Override
    public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                               int firstVertex, int firstInstance) {
        dispatchVoid("renderPassDraw",
                "{\"id\":" + (int) renderPass + ",\"vc\":" + vertexCount + ",\"ic\":" + instanceCount
                        + ",\"fv\":" + firstVertex + ",\"fi\":" + firstInstance + "}");
    }

    @Override
    public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                      int firstIndex, int baseVertex, int firstInstance) {
        dispatchVoid("renderPassDrawIndexed",
                "{\"id\":" + (int) renderPass + ",\"ic\":" + indexCount + ",\"instC\":" + instanceCount
                        + ",\"fi\":" + firstIndex + ",\"bv\":" + baseVertex + ",\"fInst\":" + firstInstance + "}");
    }

    // =====================================================================
    // JSON encoding helpers
    // =====================================================================

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

    /**
     * Encodes a byte array as a JSON array of integers, e.g. {@code [0,255,128]}.
     */
    private static String encodeByteArray(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 4);
        sb.append("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(bytes[i] & 0xFF);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * JSON-escapes a string value, wrapping it in double quotes.
     * Handles backslash, double-quote, newlines, carriage returns, and tabs.
     */
    private static String jsonEscape(String value) {
        var sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // =====================================================================
    // String conversion helpers (same as TeaVmWgpuBindings)
    // =====================================================================

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
}

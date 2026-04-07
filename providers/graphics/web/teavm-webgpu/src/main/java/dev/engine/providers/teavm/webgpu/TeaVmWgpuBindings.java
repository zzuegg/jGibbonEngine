package dev.engine.providers.teavm.webgpu;

import dev.engine.graphics.webgpu.WgpuBindings;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSNumber;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Uint8Array;

import java.nio.ByteBuffer;

/**
 * Browser WebGPU implementation of {@link WgpuBindings} using TeaVM JSO.
 *
 * <p>Delegates to the browser's native {@code navigator.gpu} API via
 * {@code @JSBody} calls. All WebGPU objects are stored in a JavaScript-side
 * registry ({@code window._wgpu}) keyed by integer IDs. The Java side
 * passes these IDs as {@code long} handles.
 *
 * <p>Async operations (requestAdapter, requestDevice) are handled by
 * requiring the caller to use the dedicated async init path in
 * {@link TeaVmWgpuInit} before calling device-dependent methods.
 */
public class TeaVmWgpuBindings implements WgpuBindings {

    // ===== JS-side Handle Registry =====

    @JSBody(script = """
        if (!window._wgpu) {
            window._wgpu = {};
            window._wgpuNextId = 1;
        }
    """)
    static native void ensureRegistry();

    @JSBody(params = "obj", script = """
        var id = window._wgpuNextId++;
        window._wgpu[id] = obj;
        return id;
    """)
    static native int wgpuRegister(JSObject obj);

    @JSBody(params = "id", script = "return window._wgpu[id];")
    static native JSObject wgpuGet(int id);

    @JSBody(params = "id", script = "delete window._wgpu[id];")
    public static native void wgpuRelease(int id);

    // ===== Surface / Presentation =====

    @Override
    public long configureSurface(long instance, long device, dev.engine.graphics.window.WindowHandle window) {
        // On web, canvas is found by ID — window handle is not used
        return configureCanvasContext("canvas", (int) device);
    }

    @Override
    public long getSurfaceTextureView(long surface) {
        return getCurrentTextureView((int) surface);
    }

    @Override
    public void releaseSurfaceTextureView(long textureView) {
        wgpuRelease((int) textureView);
    }

    @Override
    public boolean hasSurface() {
        return true;
    }

    @Override
    public int surfaceFormat() {
        var fmt = getPreferredCanvasFormat();
        if ("rgba8unorm".equals(fmt)) return TEXTURE_FORMAT_RGBA8_UNORM;
        return TEXTURE_FORMAT_BGRA8_UNORM;
    }

    // ===== Lifecycle =====

    @Override
    public boolean initialize() {
        ensureRegistry();
        return isAvailable();
    }

    @Override
    public boolean isAvailable() {
        return hasWebGPU();
    }

    @JSBody(script = "return !!navigator.gpu;")
    private static native boolean hasWebGPU();

    // ===== Instance =====

    @Override
    public long createInstance() {
        // Browser WebGPU has no explicit instance — use a sentinel
        return 1;
    }

    @Override
    public void instanceProcessEvents(long instance) {
        // No-op — browser event loop handles this
    }

    @Override
    public void instanceRelease(long instance) {
        // No-op
    }

    // ===== Adapter =====

    @Override
    public long instanceRequestAdapter(long instance) {
        // This is called synchronously by WgpuRenderDevice.
        // In the browser, adapter/device must be obtained asynchronously
        // before this binding is used. TeaVmWgpuInit stores them.
        int id = getStoredAdapter();
        if (id <= 0) throw new IllegalStateException(
                "Adapter not initialized. Call TeaVmWgpuInit.initAsync() first.");
        return id;
    }

    @JSBody(script = "return window._wgpuAdapter || 0;")
    private static native int getStoredAdapter();

    @Override
    public void adapterRelease(long adapter) {
        // Browser adapters don't have explicit release
    }

    // ===== Device =====

    @Override
    public long adapterRequestDevice(long instance, long adapter) {
        int id = getStoredDevice();
        if (id <= 0) throw new IllegalStateException(
                "Device not initialized. Call TeaVmWgpuInit.initAsync() first.");
        return id;
    }

    @JSBody(script = "return window._wgpuDevice || 0;")
    private static native int getStoredDevice();

    @Override
    public long deviceGetQueue(long device) {
        return deviceGetQueueJS((int) device);
    }

    @JSBody(params = "deviceId", script = """
        var device = window._wgpu[deviceId];
        var queue = device.queue;
        var id = window._wgpuNextId++;
        window._wgpu[id] = queue;
        return id;
    """)
    private static native int deviceGetQueueJS(int deviceId);

    @Override
    public void deviceRelease(long device) {
        // Browser devices are GC'd
    }

    // ===== Buffer =====

    @Override
    public long deviceCreateBuffer(long device, long size, int usage) {
        return createBufferJS((int) device, (int) size, usage);
    }

    @JSBody(params = {"deviceId", "size", "usage"}, script = """
        var device = window._wgpu[deviceId];
        var buf = device.createBuffer({ size: size, usage: usage });
        var id = window._wgpuNextId++;
        window._wgpu[id] = buf;
        return id;
    """)
    private static native int createBufferJS(int deviceId, int size, int usage);

    @Override
    public void bufferRelease(long buffer) {
        bufferReleaseJS((int) buffer);
    }

    @JSBody(params = "bufferId", script = """
        var buf = window._wgpu[bufferId];
        if (buf) { buf.destroy(); delete window._wgpu[bufferId]; }
    """)
    private static native void bufferReleaseJS(int bufferId);

    @Override
    public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        byte[] bytes = new byte[size];
        data.get(bytes);
        queueWriteBufferBytes((int) queue, (int) buffer, offset, bytes);
    }

    @JSBody(params = {"queueId", "bufferId", "offset", "bytes"}, script = """
        var queue = window._wgpu[queueId];
        var buf = window._wgpu[bufferId];
        var u8 = new Uint8Array(bytes);
        queue.writeBuffer(buf, offset, u8);
    """)
    private static native void queueWriteBufferBytes(int queueId, int bufferId, int offset, byte[] bytes);

    @Override
    public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        // Uses TeaVM @Async to bridge the async mapAsync() Promise
        bufferMapReadAsync((int) buffer, size);
    }

    @Async
    private static native void bufferMapReadAsync(int bufferId, int size);

    private static void bufferMapReadAsync(int bufferId, int size, AsyncCallback<Void> callback) {
        bufferMapReadJS(bufferId, size, () -> callback.complete(null));
    }

    @JSBody(params = {"bufferId", "size", "callback"}, script = """
        var buf = window._wgpu[bufferId];
        buf.mapAsync(GPUMapMode.READ, 0, size).then(function() {
            callback();
        });
    """)
    private static native void bufferMapReadJS(int bufferId, int size, VoidCallback callback);

    @JSFunctor
    private interface VoidCallback extends org.teavm.jso.JSObject {
        void call();
    }

    @Override
    public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        byte[] data = bufferGetMappedRangeJS((int) buffer, offset, size);
        dest.put(data, 0, Math.min(data.length, size));
    }

    @JSBody(params = {"bufferId", "offset", "size"}, script = """
        var buf = window._wgpu[bufferId];
        var range = buf.getMappedRange(offset, size);
        return Array.from(new Uint8Array(range));
    """)
    private static native byte[] bufferGetMappedRangeJS(int bufferId, int offset, int size);

    @Override
    public void bufferUnmap(long buffer) {
        bufferUnmapJS((int) buffer);
    }

    @JSBody(params = "bufferId", script = "window._wgpu[bufferId].unmap();")
    private static native void bufferUnmapJS(int bufferId);

    // ===== Texture =====

    @Override
    public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                    int format, int dimension, int usage) {
        String fmtStr = wgpuTextureFormatString(format);
        String dimStr = dimension == TEXTURE_DIMENSION_3D ? "3d" : "2d";
        return createTextureJS((int) device, width, height, depthOrLayers, fmtStr, dimStr, usage);
    }

    @JSBody(params = {"deviceId", "w", "h", "depth", "format", "dim", "usage"}, script = """
        var device = window._wgpu[deviceId];
        var tex = device.createTexture({
            size: { width: w, height: h, depthOrArrayLayers: depth },
            format: format,
            dimension: dim,
            usage: usage
        });
        var id = window._wgpuNextId++;
        window._wgpu[id] = tex;
        return id;
    """)
    private static native int createTextureJS(int deviceId, int w, int h, int depth,
                                               String format, String dim, int usage);

    @Override
    public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        return textureCreateViewJS((int) texture);
    }

    @JSBody(params = "texId", script = """
        var tex = window._wgpu[texId];
        var view = tex.createView();
        var id = window._wgpuNextId++;
        window._wgpu[id] = view;
        return id;
    """)
    private static native int textureCreateViewJS(int texId);

    @Override
    public void textureRelease(long texture) {
        textureReleaseJS((int) texture);
    }

    @JSBody(params = "texId", script = """
        var tex = window._wgpu[texId];
        if (tex) { tex.destroy(); delete window._wgpu[texId]; }
    """)
    private static native void textureReleaseJS(int texId);

    @Override
    public void textureViewRelease(long textureView) {
        wgpuRelease((int) textureView);
    }

    @Override
    public void queueWriteTexture(long queue, long texture, int width, int height,
                                  int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        queueWriteTextureJS((int) queue, (int) texture, width, height, depthOrLayers, bytesPerRow, bytes);
    }

    @JSBody(params = {"queueId", "textureId", "width", "height", "depthOrLayers", "bytesPerRow", "bytes"}, script = """
        var queue = window._wgpu[queueId];
        var texture = window._wgpu[textureId];
        var u8 = new Uint8Array(bytes);
        queue.writeTexture(
            { texture: texture },
            u8,
            { bytesPerRow: bytesPerRow, rowsPerImage: height },
            { width: width, height: height, depthOrArrayLayers: depthOrLayers }
        );
    """)
    private static native void queueWriteTextureJS(int queueId, int textureId, int width, int height,
                                                    int depthOrLayers, int bytesPerRow, byte[] bytes);

    // ===== Sampler =====

    @Override
    public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                    int magFilter, int minFilter, int mipmapFilter,
                                    float lodMinClamp, float lodMaxClamp,
                                    int compare, float maxAnisotropy) {
        return createSamplerJS((int) device,
                addressModeStr(addressU), addressModeStr(addressV), addressModeStr(addressW),
                filterStr(magFilter), filterStr(minFilter), filterStr(mipmapFilter),
                lodMinClamp, lodMaxClamp, compare != 0 ? compareString(compare) : null, (int) maxAnisotropy);
    }

    @JSBody(params = {"deviceId", "addrU", "addrV", "addrW", "mag", "min", "mip",
                       "lodMin", "lodMax", "compare", "maxAniso"}, script = """
        var device = window._wgpu[deviceId];
        var desc = {
            addressModeU: addrU, addressModeV: addrV, addressModeW: addrW,
            magFilter: mag, minFilter: min, mipmapFilter: mip,
            lodMinClamp: lodMin, lodMaxClamp: lodMax,
            maxAnisotropy: maxAniso
        };
        if (compare) desc.compare = compare;
        var sampler = device.createSampler(desc);
        var id = window._wgpuNextId++;
        window._wgpu[id] = sampler;
        return id;
    """)
    private static native int createSamplerJS(int deviceId, String addrU, String addrV, String addrW,
                                               String mag, String min, String mip,
                                               float lodMin, float lodMax, String compare, int maxAniso);

    @Override
    public void samplerRelease(long sampler) {
        wgpuRelease((int) sampler);
    }

    // ===== Shader Module =====

    @Override
    public long deviceCreateShaderModule(long device, String wgsl) {
        return createShaderModuleJS((int) device, wgsl);
    }

    @JSBody(params = {"deviceId", "wgsl"}, script = """
        var device = window._wgpu[deviceId];
        var mod = device.createShaderModule({ code: wgsl });
        var id = window._wgpuNextId++;
        window._wgpu[id] = mod;
        return id;
    """)
    private static native int createShaderModuleJS(int deviceId, String wgsl);

    @Override
    public boolean shaderModuleIsValid(long shaderModule) {
        return shaderModule > 0;
    }

    @Override
    public void shaderModuleRelease(long shaderModule) {
        wgpuRelease((int) shaderModule);
    }

    // ===== Bind Group Layout =====

    @Override
    public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        return createBindGroupLayoutJS((int) device, encodeBindGroupLayoutEntries(entries));
    }

    @JSBody(params = {"deviceId", "entriesJson"}, script = """
        var device = window._wgpu[deviceId];
        var entries = JSON.parse(entriesJson);
        var layout = device.createBindGroupLayout({ entries: entries });
        var id = window._wgpuNextId++;
        window._wgpu[id] = layout;
        return id;
    """)
    private static native int createBindGroupLayoutJS(int deviceId, String entriesJson);

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

    @Override
    public void bindGroupLayoutRelease(long bindGroupLayout) {
        wgpuRelease((int) bindGroupLayout);
    }

    // ===== Pipeline Layout =====

    @Override
    public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        int[] ids = new int[bindGroupLayouts.length];
        for (int i = 0; i < bindGroupLayouts.length; i++) ids[i] = (int) bindGroupLayouts[i];
        return createPipelineLayoutJS((int) device, ids);
    }

    @JSBody(params = {"deviceId", "layoutIds"}, script = """
        var device = window._wgpu[deviceId];
        var layouts = [];
        for (var i = 0; i < layoutIds.length; i++) {
            layouts.push(window._wgpu[layoutIds[i]]);
        }
        var pl = device.createPipelineLayout({ bindGroupLayouts: layouts });
        var id = window._wgpuNextId++;
        window._wgpu[id] = pl;
        return id;
    """)
    private static native int createPipelineLayoutJS(int deviceId, int[] layoutIds);

    @Override
    public void pipelineLayoutRelease(long pipelineLayout) {
        wgpuRelease((int) pipelineLayout);
    }

    // ===== Render Pipeline =====

    @Override
    public long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc) {
        // Build vertex attributes JSON
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
                  .append(",\"shaderLocation\":").append(a.shaderLocation())
                  .append("}");
            }
            sb.append("]");
            attrsJson = sb.toString();
        }

        String colorFormat = wgpuTextureFormatString(desc.colorTargetFormat());
        String depthFormat = desc.depthStencilFormat() > 0
                ? wgpuTextureFormatString(desc.depthStencilFormat()) : "";
        String topology = topologyString(desc.topology());
        String cullMode = cullModeString(desc.cullMode());
        String frontFace = frontFaceString(desc.frontFace());

        StencilFaceState sf = desc.stencilFront();
        StencilFaceState sb2 = desc.stencilBack();

        return createRenderPipelineJS(
                (int) device,
                (int) desc.pipelineLayout(),
                (int) desc.vertexModule(),
                desc.vertexEntryPoint(),
                (int) desc.fragmentModule(),
                desc.fragmentEntryPoint(),
                stride, attrsJson,
                topology, cullMode, frontFace,
                colorFormat, depthFormat,
                desc.depthWriteEnabled() == OPTIONAL_BOOL_TRUE,
                compareString(desc.depthCompare()),
                compareString(sf.compare()),
                stencilOpString(sf.failOp()),
                stencilOpString(sf.depthFailOp()),
                stencilOpString(sf.passOp()),
                compareString(sb2.compare()),
                stencilOpString(sb2.failOp()),
                stencilOpString(sb2.depthFailOp()),
                stencilOpString(sb2.passOp()),
                desc.stencilReadMask(),
                desc.stencilWriteMask(),
                blendFactorString(desc.blendColorSrcFactor()),
                blendFactorString(desc.blendColorDstFactor()),
                blendOpString(desc.blendColorOperation()),
                blendFactorString(desc.blendAlphaSrcFactor()),
                blendFactorString(desc.blendAlphaDstFactor()),
                blendOpString(desc.blendAlphaOperation())
        );
    }

    @JSBody(params = {"deviceId", "layoutId", "vsModId", "vsEntry",
            "fsModId", "fsEntry", "stride", "attrsJson",
            "topology", "cullMode", "frontFace", "colorFormat", "depthFormat",
            "depthWrite", "depthCompare",
            "stencilFrontCompare", "stencilFrontFail", "stencilFrontDepthFail", "stencilFrontPass",
            "stencilBackCompare", "stencilBackFail", "stencilBackDepthFail", "stencilBackPass",
            "stencilReadMask", "stencilWriteMask",
            "blendColorSrc", "blendColorDst", "blendColorOp",
            "blendAlphaSrc", "blendAlphaDst", "blendAlphaOp"}, script = """
        var device = window._wgpu[deviceId];
        var desc = {
            layout: layoutId > 0 ? window._wgpu[layoutId] : 'auto',
            vertex: {
                module: window._wgpu[vsModId],
                entryPoint: vsEntry,
                buffers: stride > 0 ? [{
                    arrayStride: stride,
                    stepMode: 'vertex',
                    attributes: JSON.parse(attrsJson)
                }] : []
            },
            primitive: {
                topology: topology,
                frontFace: frontFace,
                cullMode: cullMode
            }
        };
        if (fsModId > 0) {
            desc.fragment = {
                module: window._wgpu[fsModId],
                entryPoint: fsEntry,
                targets: [{
                    format: colorFormat,
                    blend: {
                        color: { srcFactor: blendColorSrc, dstFactor: blendColorDst, operation: blendColorOp },
                        alpha: { srcFactor: blendAlphaSrc, dstFactor: blendAlphaDst, operation: blendAlphaOp }
                    }
                }]
            };
        }
        if (depthFormat && depthFormat.length > 0) {
            desc.depthStencil = {
                format: depthFormat,
                depthWriteEnabled: depthWrite,
                depthCompare: depthCompare,
                stencilFront: {
                    compare: stencilFrontCompare,
                    failOp: stencilFrontFail,
                    depthFailOp: stencilFrontDepthFail,
                    passOp: stencilFrontPass
                },
                stencilBack: {
                    compare: stencilBackCompare,
                    failOp: stencilBackFail,
                    depthFailOp: stencilBackDepthFail,
                    passOp: stencilBackPass
                },
                stencilReadMask: stencilReadMask,
                stencilWriteMask: stencilWriteMask
            };
        }
        var pipeline = device.createRenderPipeline(desc);
        var id = window._wgpuNextId++;
        window._wgpu[id] = pipeline;
        return id;
    """)
    private static native int createRenderPipelineJS(
            int deviceId, int layoutId, int vsModId, String vsEntry,
            int fsModId, String fsEntry, int stride, String attrsJson,
            String topology, String cullMode, String frontFace, String colorFormat, String depthFormat,
            boolean depthWrite, String depthCompare,
            String stencilFrontCompare, String stencilFrontFail, String stencilFrontDepthFail, String stencilFrontPass,
            String stencilBackCompare, String stencilBackFail, String stencilBackDepthFail, String stencilBackPass,
            int stencilReadMask, int stencilWriteMask,
            String blendColorSrc, String blendColorDst, String blendColorOp,
            String blendAlphaSrc, String blendAlphaDst, String blendAlphaOp);

    @Override
    public void renderPipelineRelease(long renderPipeline) {
        wgpuRelease((int) renderPipeline);
    }

    // ===== Bind Group =====

    @Override
    public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        String json = encodeBindGroupEntries(entries);
        return createBindGroupJS((int) device, (int) layout, json);
    }

    @JSBody(params = {"deviceId", "layoutId", "entriesJson"}, script = """
        var device = window._wgpu[deviceId];
        var raw = JSON.parse(entriesJson);
        var entries = [];
        for (var i = 0; i < raw.length; i++) {
            var e = raw[i];
            var entry = { binding: e.binding };
            if (e.type === 'buffer') {
                entry.resource = { buffer: window._wgpu[e.handle], offset: e.offset, size: e.size };
            } else if (e.type === 'textureView') {
                entry.resource = window._wgpu[e.handle];
            } else if (e.type === 'sampler') {
                entry.resource = window._wgpu[e.handle];
            }
            entries.push(entry);
        }
        var bg = device.createBindGroup({
            layout: window._wgpu[layoutId],
            entries: entries
        });
        var id = window._wgpuNextId++;
        window._wgpu[id] = bg;
        return id;
    """)
    private static native int createBindGroupJS(int deviceId, int layoutId, String entriesJson);

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
              .append(",\"size\":").append(e.size())
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void bindGroupRelease(long bindGroup) {
        wgpuRelease((int) bindGroup);
    }

    // ===== Command Encoder =====

    @Override
    public long deviceCreateCommandEncoder(long device) {
        return createCommandEncoderJS((int) device);
    }

    @JSBody(params = "deviceId", script = """
        var device = window._wgpu[deviceId];
        var enc = device.createCommandEncoder();
        var id = window._wgpuNextId++;
        window._wgpu[id] = enc;
        return id;
    """)
    private static native int createCommandEncoderJS(int deviceId);

    @Override
    public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        // Build color attachments
        var ca = desc.colorAttachments();
        if (ca == null || ca.length == 0) {
            throw new IllegalArgumentException("At least one color attachment is required");
        }
        // For simplicity, support single color attachment
        var c = ca[0];
        int depthView = desc.depthStencil() != null ? (int) desc.depthStencil().textureView() : 0;
        float depthClear = desc.depthStencil() != null ? desc.depthStencil().depthClearValue() : 1.0f;

        return beginRenderPassJS((int) encoder, (int) c.textureView(),
                c.clearR(), c.clearG(), c.clearB(), c.clearA(),
                depthView, depthClear);
    }

    @JSBody(params = {"encId", "colorViewId", "r", "g", "b", "a", "depthViewId", "depthClear"},
            script = """
        var enc = window._wgpu[encId];
        var colorAttachments = [{
            view: window._wgpu[colorViewId],
            clearValue: { r: r, g: g, b: b, a: a },
            loadOp: 'clear',
            storeOp: 'store'
        }];
        var desc = { colorAttachments: colorAttachments };
        if (depthViewId > 0) {
            desc.depthStencilAttachment = {
                view: window._wgpu[depthViewId],
                depthClearValue: depthClear,
                depthLoadOp: 'clear',
                depthStoreOp: 'store',
                stencilClearValue: 0,
                stencilLoadOp: 'clear',
                stencilStoreOp: 'store'
            };
        }
        var pass = enc.beginRenderPass(desc);
        var id = window._wgpuNextId++;
        window._wgpu[id] = pass;
        return id;
    """)
    private static native int beginRenderPassJS(int encId, int colorViewId,
                                                 float r, float g, float b, float a,
                                                 int depthViewId, float depthClear);

    @Override
    public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                  long dst, int dstOffset, int size) {
        copyBufToBufJS((int) encoder, (int) src, srcOffset, (int) dst, dstOffset, size);
    }

    @JSBody(params = {"encId", "srcId", "srcOff", "dstId", "dstOff", "size"}, script = """
        var enc = window._wgpu[encId];
        enc.copyBufferToBuffer(window._wgpu[srcId], srcOff, window._wgpu[dstId], dstOff, size);
    """)
    private static native void copyBufToBufJS(int encId, int srcId, int srcOff,
                                               int dstId, int dstOff, int size);

    @Override
    public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                   int width, int height,
                                                   int bytesPerRow, int rowsPerImage) {
        copyTexToBufferJS((int) encoder, (int) texture, (int) buffer,
                width, height, bytesPerRow, rowsPerImage);
    }

    @JSBody(params = {"encId", "texId", "bufId", "w", "h", "bytesPerRow", "rowsPerImage"}, script = """
        var enc = window._wgpu[encId];
        enc.copyTextureToBuffer(
            { texture: window._wgpu[texId] },
            { buffer: window._wgpu[bufId], bytesPerRow: bytesPerRow, rowsPerImage: rowsPerImage },
            { width: w, height: h }
        );
    """)
    private static native void copyTexToBufferJS(int encId, int texId, int bufId,
                                                  int w, int h, int bytesPerRow, int rowsPerImage);

    @Override
    public long commandEncoderFinish(long encoder) {
        return encoderFinishJS((int) encoder);
    }

    @JSBody(params = "encId", script = """
        var enc = window._wgpu[encId];
        var cmdBuf = enc.finish();
        delete window._wgpu[encId];
        var id = window._wgpuNextId++;
        window._wgpu[id] = cmdBuf;
        return id;
    """)
    private static native int encoderFinishJS(int encId);

    @Override
    public void commandEncoderRelease(long encoder) {
        wgpuRelease((int) encoder);
    }

    // ===== Command Buffer =====

    @Override
    public void commandBufferRelease(long commandBuffer) {
        wgpuRelease((int) commandBuffer);
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queue, long commandBuffer) {
        queueSubmitJS((int) queue, (int) commandBuffer);
    }

    @JSBody(params = {"queueId", "cmdBufId"}, script = """
        var queue = window._wgpu[queueId];
        queue.submit([window._wgpu[cmdBufId]]);
        delete window._wgpu[cmdBufId];
    """)
    private static native void queueSubmitJS(int queueId, int cmdBufId);

    // ===== Render Pass Encoder =====

    @Override
    public void renderPassEnd(long renderPass) {
        renderPassEndJS((int) renderPass);
    }

    @JSBody(params = "passId", script = "window._wgpu[passId].end();")
    private static native void renderPassEndJS(int passId);

    @Override
    public void renderPassRelease(long renderPass) {
        wgpuRelease((int) renderPass);
    }

    @Override
    public void renderPassSetPipeline(long renderPass, long pipeline) {
        renderPassSetPipelineJS((int) renderPass, (int) pipeline);
    }

    @JSBody(params = {"passId", "pipeId"}, script = """
        window._wgpu[passId].setPipeline(window._wgpu[pipeId]);
    """)
    private static native void renderPassSetPipelineJS(int passId, int pipeId);

    @Override
    public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size) {
        renderPassSetVertexBufferJS((int) renderPass, slot, (int) buffer, offset, size);
    }

    @JSBody(params = {"passId", "slot", "bufId", "offset", "size"}, script = """
        window._wgpu[passId].setVertexBuffer(slot, window._wgpu[bufId], offset, size);
    """)
    private static native void renderPassSetVertexBufferJS(int passId, int slot, int bufId,
                                                            int offset, int size);

    @Override
    public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat,
                                          int offset, int size) {
        String fmt = indexFormat == INDEX_FORMAT_UINT32 ? "uint32" : "uint16";
        renderPassSetIndexBufferJS((int) renderPass, (int) buffer, fmt, offset, size);
    }

    @JSBody(params = {"passId", "bufId", "fmt", "offset", "size"}, script = """
        window._wgpu[passId].setIndexBuffer(window._wgpu[bufId], fmt, offset, size);
    """)
    private static native void renderPassSetIndexBufferJS(int passId, int bufId, String fmt,
                                                           int offset, int size);

    @Override
    public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        renderPassSetBindGroupJS((int) renderPass, groupIndex, (int) bindGroup);
    }

    @JSBody(params = {"passId", "idx", "bgId"}, script = """
        window._wgpu[passId].setBindGroup(idx, window._wgpu[bgId]);
    """)
    private static native void renderPassSetBindGroupJS(int passId, int idx, int bgId);

    @Override
    public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                      float minDepth, float maxDepth) {
        renderPassSetViewportJS((int) renderPass, x, y, w, h, minDepth, maxDepth);
    }

    @JSBody(params = {"passId", "x", "y", "w", "h", "minD", "maxD"}, script = """
        window._wgpu[passId].setViewport(x, y, w, h, minD, maxD);
    """)
    private static native void renderPassSetViewportJS(int passId, float x, float y,
                                                        float w, float h, float minD, float maxD);

    @Override
    public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        renderPassSetScissorRectJS((int) renderPass, x, y, width, height);
    }

    @JSBody(params = {"passId", "x", "y", "w", "h"}, script = """
        window._wgpu[passId].setScissorRect(x, y, w, h);
    """)
    private static native void renderPassSetScissorRectJS(int passId, int x, int y, int w, int h);

    @Override
    public void renderPassSetStencilReference(long renderPass, int ref) {
        renderPassSetStencilRefJS((int) renderPass, ref);
    }

    @JSBody(params = {"passId", "ref"}, script = """
        window._wgpu[passId].setStencilReference(ref);
    """)
    private static native void renderPassSetStencilRefJS(int passId, int ref);

    @Override
    public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                               int firstVertex, int firstInstance) {
        renderPassDrawJS((int) renderPass, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @JSBody(params = {"passId", "vc", "ic", "fv", "fi"}, script = """
        window._wgpu[passId].draw(vc, ic, fv, fi);
    """)
    private static native void renderPassDrawJS(int passId, int vc, int ic, int fv, int fi);

    @Override
    public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                      int firstIndex, int baseVertex, int firstInstance) {
        renderPassDrawIndexedJS((int) renderPass, indexCount, instanceCount,
                firstIndex, baseVertex, firstInstance);
    }

    @JSBody(params = {"passId", "ic", "instC", "fi", "bv", "fInst"}, script = """
        window._wgpu[passId].drawIndexed(ic, instC, fi, bv, fInst);
    """)
    private static native void renderPassDrawIndexedJS(int passId, int ic, int instC,
                                                        int fi, int bv, int fInst);

    // ===== Canvas Surface Helpers (not in WgpuBindings interface) =====

    /**
     * Configures the canvas WebGPU context. Must be called after device init.
     *
     * @param canvasId the HTML canvas element ID
     * @param deviceId the device handle
     * @return the context handle for later getCurrentTexture calls
     */
    public static int configureCanvasContext(String canvasId, int deviceId) {
        return configureCanvasContextJS(canvasId, deviceId);
    }

    @JSBody(params = {"canvasId", "deviceId"}, script = """
        var canvas = document.getElementById(canvasId);
        var ctx = canvas.getContext('webgpu');
        var device = window._wgpu[deviceId];
        var format = navigator.gpu.getPreferredCanvasFormat();
        ctx.configure({ device: device, format: format });
        var id = window._wgpuNextId++;
        window._wgpu[id] = ctx;
        window._wgpuCanvasFormat = format;
        return id;
    """)
    private static native int configureCanvasContextJS(String canvasId, int deviceId);

    /**
     * Gets the current texture view from the canvas context for rendering.
     * This must be called each frame.
     *
     * @param contextId the canvas context handle
     * @return a texture view handle for use as a color attachment
     */
    public static int getCurrentTextureView(int contextId) {
        return getCurrentTextureViewJS(contextId);
    }

    @JSBody(params = "ctxId", script = """
        var ctx = window._wgpu[ctxId];
        var tex = ctx.getCurrentTexture();
        var view = tex.createView();
        var id = window._wgpuNextId++;
        window._wgpu[id] = view;
        return id;
    """)
    private static native int getCurrentTextureViewJS(int ctxId);

    /**
     * Returns the preferred canvas format string (e.g., "bgra8unorm").
     */
    @JSBody(script = "return window._wgpuCanvasFormat || 'bgra8unorm';")
    public static native String getPreferredCanvasFormat();

    // ===== String conversion helpers =====

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

    private static String vertexFormatString(int format) {
        return switch (format) {
            case VERTEX_FORMAT_UNORM8X4 -> "unorm8x4";
            case VERTEX_FORMAT_FLOAT32 -> "float32";
            case VERTEX_FORMAT_FLOAT32X2 -> "float32x2";
            case VERTEX_FORMAT_FLOAT32X3 -> "float32x3";
            case VERTEX_FORMAT_FLOAT32X4 -> "float32x4";
            default -> "float32x4";
        };
    }

    private static String topologyString(int topology) {
        return switch (topology) {
            case PRIMITIVE_TOPOLOGY_TRIANGLE_LIST -> "triangle-list";
            default -> "triangle-list";
        };
    }

    private static String cullModeString(int cullMode) {
        return switch (cullMode) {
            case CULL_MODE_FRONT -> "front";
            case CULL_MODE_BACK -> "back";
            default -> "none";
        };
    }

    private static String frontFaceString(int frontFace) {
        return switch (frontFace) {
            case FRONT_FACE_CW -> "cw";
            default -> "ccw";
        };
    }

    private static String compareString(int compare) {
        return switch (compare) {
            case COMPARE_NEVER -> "never";
            case COMPARE_LESS -> "less";
            case COMPARE_EQUAL -> "equal";
            case COMPARE_LESS_EQUAL -> "less-equal";
            case COMPARE_GREATER -> "greater";
            case COMPARE_NOT_EQUAL -> "not-equal";
            case COMPARE_GREATER_EQUAL -> "greater-equal";
            case COMPARE_ALWAYS -> "always";
            default -> "always";
        };
    }

    private static String stencilOpString(int op) {
        return switch (op) {
            case STENCIL_OP_KEEP -> "keep";
            case STENCIL_OP_ZERO -> "zero";
            case STENCIL_OP_REPLACE -> "replace";
            case STENCIL_OP_INVERT -> "invert";
            case STENCIL_OP_INCREMENT_CLAMP -> "increment-clamp";
            case STENCIL_OP_DECREMENT_CLAMP -> "decrement-clamp";
            case STENCIL_OP_INCREMENT_WRAP -> "increment-wrap";
            case STENCIL_OP_DECREMENT_WRAP -> "decrement-wrap";
            default -> "keep";
        };
    }

    private static String blendFactorString(int factor) {
        return switch (factor) {
            case BLEND_FACTOR_ZERO -> "zero";
            case BLEND_FACTOR_ONE -> "one";
            case BLEND_FACTOR_SRC -> "src";
            case BLEND_FACTOR_ONE_MINUS_SRC -> "one-minus-src";
            case BLEND_FACTOR_SRC_ALPHA -> "src-alpha";
            case BLEND_FACTOR_ONE_MINUS_SRC_ALPHA -> "one-minus-src-alpha";
            case BLEND_FACTOR_DST -> "dst";
            case BLEND_FACTOR_ONE_MINUS_DST -> "one-minus-dst";
            case BLEND_FACTOR_DST_ALPHA -> "dst-alpha";
            case BLEND_FACTOR_ONE_MINUS_DST_ALPHA -> "one-minus-dst-alpha";
            default -> "zero";
        };
    }

    private static String blendOpString(int op) {
        return switch (op) {
            case BLEND_OP_ADD -> "add";
            case BLEND_OP_SUBTRACT -> "subtract";
            case BLEND_OP_REVERSE_SUBTRACT -> "reverse-subtract";
            case BLEND_OP_MIN -> "min";
            case BLEND_OP_MAX -> "max";
            default -> "add";
        };
    }

    private static String addressModeStr(int mode) {
        return switch (mode) {
            case ADDRESS_MODE_REPEAT -> "repeat";
            case ADDRESS_MODE_MIRROR_REPEAT -> "mirror-repeat";
            case ADDRESS_MODE_CLAMP_TO_EDGE -> "clamp-to-edge";
            default -> "clamp-to-edge";
        };
    }

    private static String filterStr(int mode) {
        return switch (mode) {
            case FILTER_MODE_NEAREST -> "nearest";
            case FILTER_MODE_LINEAR -> "linear";
            default -> "nearest";
        };
    }

    private static UnsupportedOperationException unsupported(String msg) {
        return new UnsupportedOperationException("TeaVmWgpuBindings: " + msg);
    }
}

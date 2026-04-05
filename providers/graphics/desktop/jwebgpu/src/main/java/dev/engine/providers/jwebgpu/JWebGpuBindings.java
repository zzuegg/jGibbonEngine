package dev.engine.providers.jwebgpu;

import com.github.xpenatan.webgpu.*;
import dev.engine.graphics.webgpu.WgpuBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * jWebGPU-backed implementation of {@link WgpuBindings}.
 *
 * <p>Manages a bidirectional mapping between opaque {@code long} handles
 * and jWebGPU wrapper objects. Each jWebGPU object is assigned a unique
 * handle ID when created through this binding.
 */
public class JWebGpuBindings implements WgpuBindings {

    private static final Logger log = LoggerFactory.getLogger(JWebGpuBindings.class);

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    // Handle tracking — maps long handle IDs to jWebGPU objects
    private final AtomicLong nextHandle = new AtomicLong(1);
    private final Map<Long, Object> objects = new ConcurrentHashMap<>();

    private long assignHandle(Object obj) {
        long h = nextHandle.getAndIncrement();
        objects.put(h, obj);
        return h;
    }

    @SuppressWarnings("unchecked")
    private <T> T get(long handle, Class<T> type) {
        Object obj = objects.get(handle);
        if (obj == null) return null;
        return type.cast(obj);
    }

    private void removeHandle(long handle) {
        objects.remove(handle);
    }

    // ===== Lifecycle =====

    @Override
    public boolean initialize() {
        if (initialized.compareAndSet(false, true)) {
            try {
                JWebGPULoader.init((result, error) -> {
                    if (!result) {
                        throw new RuntimeException("Failed to load jWebGPU native library", error);
                    }
                });
                return true;
            } catch (Throwable t) {
                initialized.set(false);
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isAvailable() {
        try {
            return initialize();
        } catch (Throwable t) {
            return false;
        }
    }

    // ===== Surface =====

    private boolean surfaceConfigured = false;

    @Override
    public boolean hasSurface() { return surfaceConfigured; }

    @Override
    public long configureSurface(long instance, long device, dev.engine.graphics.window.WindowHandle window) {
        var inst = get(instance, WGPUInstance.class);
        var dev = get(device, WGPUDevice.class);
        if (inst == null || dev == null) return 0;

        var surfaceInfo = window.surfaceInfo();
        if (surfaceInfo == null) {
            log.info("WindowHandle does not provide surfaceInfo — rendering offscreen");
            return 0;
        }

        WGPUSurface surface;
        try {
            var displayHandle = wrapPointer(surfaceInfo.display());
            var windowHandle = wrapPointer(surfaceInfo.window());
            surface = switch (surfaceInfo.type()) {
                case WAYLAND -> inst.createLinuxSurface(true, windowHandle, displayHandle);
                case X11 -> inst.createLinuxSurface(false, windowHandle, displayHandle);
                case WINDOWS -> inst.createWindowsSurface(windowHandle);
                case COCOA -> inst.createMacSurface(windowHandle);
            };
        } catch (Throwable t) {
            log.warn("WebGPU surface creation failed: {} — rendering offscreen", t.getMessage());
            return 0;
        }

        if (surface == null || surface.native_isNULL()) {
            log.info("WebGPU surface creation returned null — rendering offscreen");
            return 0;
        }

        var config = WGPUSurfaceConfiguration.obtain();
        config.setDevice(dev);
        config.setFormat(WGPUTextureFormat.BGRA8Unorm);
        config.setWidth(window.width());
        config.setHeight(window.height());
        config.setPresentMode(WGPUPresentMode.Fifo);
        config.setAlphaMode(WGPUCompositeAlphaMode.Auto);
        config.setUsage(WGPUTextureUsage.RenderAttachment);

        try {
            surface.configure(config);
        } catch (Throwable t) {
            log.warn("WebGPU surface configure failed: {} — rendering offscreen", t.getMessage());
            return 0;
        }

        surfaceConfigured = true;
        log.info("WebGPU surface configured ({}x{}, {})", window.width(), window.height(), surfaceInfo.type());
        return assignHandle(surface);
    }

    @Override
    public long getSurfaceTextureView(long surface) {
        var surf = get(surface, WGPUSurface.class);
        if (surf == null) return 0;
        var surfTexture = WGPUSurfaceTexture.obtain();
        surf.getCurrentTexture(surfTexture);
        var texture = new WGPUTexture();
        surfTexture.getTexture(texture);
        if (texture.native_isNULL()) return 0;
        var viewDesc = WGPUTextureViewDescriptor.obtain();
        viewDesc.setFormat(WGPUTextureFormat.BGRA8Unorm);
        viewDesc.setDimension(WGPUTextureViewDimension._2D);
        viewDesc.setBaseMipLevel(0);
        viewDesc.setMipLevelCount(1);
        viewDesc.setBaseArrayLayer(0);
        viewDesc.setArrayLayerCount(1);
        viewDesc.setAspect(WGPUTextureAspect.All);
        var view = new WGPUTextureView();
        texture.createView(viewDesc, view);
        if (view.native_isNULL()) return 0;
        return assignHandle(view);
    }

    @Override
    public void releaseSurfaceTextureView(long textureView) {
        var view = get(textureView, WGPUTextureView.class);
        if (view != null) {
            view.release();
            removeHandle(textureView);
        }
    }

    @Override
    public void surfacePresent(long surface) {
        var surf = get(surface, WGPUSurface.class);
        if (surf != null) {
            surf.present();
        }
    }

    /**
     * Wraps a raw native pointer as an IDLBase for jWebGPU surface creation methods.
     * Uses native_new() + native_setAddress() to match jWebGPU's expected pattern.
     */
    private static com.github.xpenatan.jParser.idl.IDLBase wrapPointer(long address) {
        var handle = com.github.xpenatan.jParser.idl.IDLBase.native_new();
        handle.native_setAddress(address);
        return handle;
    }

    // ===== Instance =====

    @Override
    public long createInstance() {
        var instance = WGPU.setupInstance();
        return assignHandle(instance);
    }

    @Override
    public void instanceProcessEvents(long instance) {
        var inst = get(instance, WGPUInstance.class);
        if (inst != null) inst.processEvents();
    }

    @Override
    public void instanceRelease(long instance) {
        var inst = get(instance, WGPUInstance.class);
        if (inst != null) {
            inst.release();
            removeHandle(instance);
        }
    }

    // ===== Adapter =====

    @Override
    public long instanceRequestAdapter(long instance) {
        var inst = get(instance, WGPUInstance.class);
        if (inst == null) return 0;

        var holder = new WGPUAdapter[1];
        var opts = WGPURequestAdapterOptions.obtain();
        inst.requestAdapter(opts, WGPUCallbackMode.AllowSpontaneous,
                new WGPURequestAdapterCallback() {
                    @Override
                    protected void onCallback(WGPURequestAdapterStatus status,
                                              WGPUAdapter adapter, String message) {
                        holder[0] = adapter;
                    }
                });
        inst.processEvents();

        if (holder[0] == null) return 0;
        return assignHandle(holder[0]);
    }

    @Override
    public void adapterRelease(long adapter) {
        var adp = get(adapter, WGPUAdapter.class);
        if (adp != null) {
            adp.release();
            removeHandle(adapter);
        }
    }

    // ===== Device =====

    @Override
    public long adapterRequestDevice(long instance, long adapter) {
        var inst = get(instance, WGPUInstance.class);
        var adp = get(adapter, WGPUAdapter.class);
        if (inst == null || adp == null) return 0;

        var holder = new WGPUDevice[1];
        var desc = WGPUDeviceDescriptor.obtain();
        adp.requestDevice(desc, WGPUCallbackMode.AllowSpontaneous,
                new WGPURequestDeviceCallback() {
                    @Override
                    protected void onCallback(WGPURequestDeviceStatus status,
                                              WGPUDevice device, String message) {
                        holder[0] = device;
                    }
                },
                new WGPUUncapturedErrorCallback() {
                    @Override
                    protected void onCallback(WGPUErrorType type, String message) {
                        log.error("WebGPU error ({}): {}", type, message);
                    }
                });
        inst.processEvents();

        if (holder[0] == null) return 0;
        return assignHandle(holder[0]);
    }

    @Override
    public long deviceGetQueue(long device) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;
        return assignHandle(dev.getQueue());
    }

    @Override
    public void deviceRelease(long device) {
        var dev = get(device, WGPUDevice.class);
        if (dev != null) {
            dev.release();
            removeHandle(device);
        }
    }

    @Override
    public DeviceLimits deviceGetLimits(long device) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return null;

        // jwebgpu 0.1.15's WGPULimits doesn't include the nextInChain pointer that
        // newer wgpu-native expects as the first field. Call wgpuDeviceGetLimits
        // directly via FFM with a correctly-laid-out buffer.
        //
        // New WGPULimits layout: { nextInChain(ptr), maxTextureDimension1D(u32), ... }
        // jwebgpu's WGPULimits was built against the old layout WITHOUT nextInChain.
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            // Allocate WGPULimits struct: sizeof(WGPULimits) = 152 bytes
            var limitsStruct = arena.allocate(152);
            limitsStruct.set(java.lang.foreign.ValueLayout.JAVA_LONG, 0, 0L); // nextInChain = NULL

            // Look up wgpuDeviceGetLimits in the already-loaded native library
            var lookup = java.lang.foreign.SymbolLookup.loaderLookup();
            var symbol = lookup.find("wgpuDeviceGetLimits");
            if (symbol.isEmpty()) {
                log.warn("wgpuDeviceGetLimits symbol not found in loaded libraries");
                return null;
            }

            // WGPUStatus wgpuDeviceGetLimits(WGPUDevice device, WGPULimits* limits)
            // WGPUDevice is an opaque pointer, WGPULimits* is a pointer
            var linker = java.lang.foreign.Linker.nativeLinker();
            var getLimits = linker.downcallHandle(symbol.get(),
                    java.lang.foreign.FunctionDescriptor.of(
                            java.lang.foreign.ValueLayout.JAVA_INT,     // WGPUStatus return
                            java.lang.foreign.ValueLayout.ADDRESS,      // WGPUDevice
                            java.lang.foreign.ValueLayout.ADDRESS));    // WGPULimits*

            // Call: pass the device's native pointer and our struct
            var devicePtr = java.lang.foreign.MemorySegment.ofAddress(dev.native_getAddressLong());
            int status = (int) getLimits.invokeExact(devicePtr, limitsStruct);

            if (status != 0) {
                log.warn("wgpuDeviceGetLimits returned status {}", status);
                return null;
            }

            // Read fields at verified offsets (from C offsetof checks)
            int maxTex2D = limitsStruct.get(java.lang.foreign.ValueLayout.JAVA_INT, 12);
            int maxTex3D = limitsStruct.get(java.lang.foreign.ValueLayout.JAVA_INT, 16);
            long maxUniformBufSize = limitsStruct.get(java.lang.foreign.ValueLayout.JAVA_LONG, 64);
            long maxStorageBufSize = limitsStruct.get(java.lang.foreign.ValueLayout.JAVA_LONG, 72);
            int maxColorAttach = limitsStruct.get(java.lang.foreign.ValueLayout.JAVA_INT, 116);

            return new DeviceLimits(
                    maxTex2D,
                    maxTex3D,
                    (int) maxUniformBufSize,
                    (int) maxStorageBufSize,
                    maxColorAttach,
                    16.0f
            );
        } catch (Throwable t) {
            log.warn("Failed to query WebGPU device limits via FFM: {}", t.getMessage());
            return null;
        }
    }

    // ===== Buffer =====

    @Override
    public long deviceCreateBuffer(long device, long size, int usage) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var desc = WGPUBufferDescriptor.obtain();
        desc.setSize(size);
        desc.setUsage(WGPUBufferUsage.CUSTOM.setValue(usage));
        return assignHandle(dev.createBuffer(desc));
    }

    @Override
    public void bufferRelease(long buffer) {
        var buf = get(buffer, WGPUBuffer.class);
        if (buf != null) {
            buf.release();
            removeHandle(buffer);
        }
    }

    @Override
    public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        var q = get(queue, WGPUQueue.class);
        var buf = get(buffer, WGPUBuffer.class);
        if (q != null && buf != null) {
            q.writeBuffer(buf, offset, data, size);
        }
    }

    @Override
    public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        var inst = get(instance, WGPUInstance.class);
        var buf = get(buffer, WGPUBuffer.class);
        if (inst == null || buf == null) return;

        final boolean[] done = {false};
        buf.mapAsync(WGPUMapMode.Read, 0, size, WGPUCallbackMode.AllowSpontaneous,
                new WGPUBufferMapCallback() {
                    @Override
                    protected void onCallback(WGPUMapAsyncStatus status, String message) {
                        done[0] = true;
                    }
                });

        for (int i = 0; i < maxPolls && !done[0]; i++) {
            inst.processEvents();
        }
    }

    @Override
    public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        var buf = get(buffer, WGPUBuffer.class);
        if (buf != null) {
            buf.getConstMappedRange(offset, size, dest);
        }
    }

    @Override
    public void bufferUnmap(long buffer) {
        var buf = get(buffer, WGPUBuffer.class);
        if (buf != null) {
            buf.unmap();
        }
    }

    // ===== Texture =====

    @Override
    public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                    int format, int dimension, int usage) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var desc = WGPUTextureDescriptor.obtain();
        desc.setUsage(WGPUTextureUsage.CUSTOM.setValue(usage));
        desc.setDimension(dimension == TEXTURE_DIMENSION_3D
                ? WGPUTextureDimension._3D : WGPUTextureDimension._2D);
        desc.setFormat(mapTextureFormat(format));
        desc.setMipLevelCount(1);
        desc.setSampleCount(1);

        var size = desc.getSize();
        size.setWidth(width);
        size.setHeight(height);
        size.setDepthOrArrayLayers(depthOrLayers);

        var tex = new WGPUTexture();
        dev.createTexture(desc, tex);
        return assignHandle(tex);
    }

    @Override
    public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        var tex = get(texture, WGPUTexture.class);
        if (tex == null) return 0;

        var desc = WGPUTextureViewDescriptor.obtain();
        desc.setFormat(mapTextureFormat(format));
        desc.setMipLevelCount(1);
        desc.setBaseMipLevel(0);
        desc.setBaseArrayLayer(0);
        desc.setArrayLayerCount(arrayLayerCount);
        desc.setAspect(WGPUTextureAspect.All);

        desc.setDimension(switch (viewDimension) {
            case TEXTURE_VIEW_DIMENSION_3D -> WGPUTextureViewDimension._3D;
            case TEXTURE_VIEW_DIMENSION_2D_ARRAY -> WGPUTextureViewDimension._2DArray;
            case TEXTURE_VIEW_DIMENSION_CUBE -> WGPUTextureViewDimension.Cube;
            default -> WGPUTextureViewDimension._2D;
        });

        var view = new WGPUTextureView();
        tex.createView(desc, view);
        return assignHandle(view);
    }

    @Override
    public void textureRelease(long texture) {
        var tex = get(texture, WGPUTexture.class);
        if (tex != null) {
            tex.release();
            removeHandle(texture);
        }
    }

    @Override
    public void textureViewRelease(long textureView) {
        var view = get(textureView, WGPUTextureView.class);
        if (view != null) {
            view.release();
            removeHandle(textureView);
        }
    }

    @Override
    public void queueWriteTexture(long queue, long texture, int width, int height,
                                  int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        var q = get(queue, WGPUQueue.class);
        var tex = get(texture, WGPUTexture.class);
        if (q == null || tex == null) return;

        var destination = WGPUTexelCopyTextureInfo.obtain();
        destination.setTexture(tex);
        destination.setMipLevel(0);
        destination.setAspect(WGPUTextureAspect.All);

        var dataLayout = WGPUTexelCopyBufferLayout.obtain();
        dataLayout.setOffset(0);
        dataLayout.setBytesPerRow(bytesPerRow);
        dataLayout.setRowsPerImage(height);

        var writeSize = WGPUExtent3D.obtain();
        writeSize.setWidth(width);
        writeSize.setHeight(height);
        writeSize.setDepthOrArrayLayers(depthOrLayers);

        q.writeTexture(destination, data, data.remaining(), dataLayout, writeSize);
    }

    // ===== Sampler =====

    @Override
    public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                    int magFilter, int minFilter, int mipmapFilter,
                                    float lodMinClamp, float lodMaxClamp,
                                    int compare, float maxAnisotropy) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var desc = WGPUSamplerDescriptor.obtain();
        desc.setAddressModeU(mapAddressMode(addressU));
        desc.setAddressModeV(mapAddressMode(addressV));
        desc.setAddressModeW(mapAddressMode(addressW));
        desc.setMagFilter(mapFilterMode(magFilter));
        desc.setMinFilter(mapFilterMode(minFilter));
        desc.setMipmapFilter(mapMipmapFilterMode(mipmapFilter));
        desc.setLodMinClamp(lodMinClamp);
        desc.setLodMaxClamp(lodMaxClamp);
        desc.setMaxAnisotropy((int) maxAnisotropy);
        if (compare != 0) {
            desc.setCompare(mapCompareFunc(compare));
        }

        var sampler = new WGPUSampler();
        dev.createSampler(desc, sampler);
        return assignHandle(sampler);
    }

    @Override
    public void samplerRelease(long sampler) {
        var s = get(sampler, WGPUSampler.class);
        if (s != null) {
            s.release();
            removeHandle(sampler);
        }
    }

    // ===== Shader Module =====

    @Override
    public long deviceCreateShaderModule(long device, String wgsl) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var wgslDesc = WGPUShaderSourceWGSL.obtain();
        wgslDesc.setCode(wgsl);
        wgslDesc.getChain().setSType(WGPUSType.ShaderSourceWGSL);

        var shaderDesc = WGPUShaderModuleDescriptor.obtain();
        shaderDesc.setNextInChain(wgslDesc.getChain());

        var module = new WGPUShaderModule();
        dev.createShaderModule(shaderDesc, module);
        return assignHandle(module);
    }

    @Override
    public boolean shaderModuleIsValid(long shaderModule) {
        var module = get(shaderModule, WGPUShaderModule.class);
        return module != null && module.isValid();
    }

    @Override
    public void shaderModuleRelease(long shaderModule) {
        var module = get(shaderModule, WGPUShaderModule.class);
        if (module != null) {
            module.release();
            removeHandle(shaderModule);
        }
    }

    // ===== Bind Group Layout =====

    @Override
    public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var entryVec = WGPUVectorBindGroupLayoutEntry.obtain();

        for (var entry : entries) {
            var layoutEntry = WGPUBindGroupLayoutEntry.obtain();
            layoutEntry.setBinding(entry.binding());
            layoutEntry.setVisibility(WGPUShaderStage.CUSTOM.setValue(entry.visibility()));

            switch (entry.type()) {
                case UNIFORM_BUFFER -> {
                    var bufLayout = WGPUBufferBindingLayout.obtain();
                    bufLayout.setType(WGPUBufferBindingType.Uniform);
                    bufLayout.setMinBindingSize(0);
                    layoutEntry.setBuffer(bufLayout);
                }
                case READ_ONLY_STORAGE_BUFFER -> {
                    var bufLayout = WGPUBufferBindingLayout.obtain();
                    bufLayout.setType(WGPUBufferBindingType.ReadOnlyStorage);
                    bufLayout.setMinBindingSize(0);
                    layoutEntry.setBuffer(bufLayout);
                }
                case SAMPLED_TEXTURE -> {
                    var texLayout = WGPUTextureBindingLayout.obtain();
                    texLayout.setSampleType(WGPUTextureSampleType.Float);
                    texLayout.setViewDimension(WGPUTextureViewDimension._2D);
                    layoutEntry.setTexture(texLayout);
                }
                case FILTERING_SAMPLER -> {
                    var smpLayout = WGPUSamplerBindingLayout.obtain();
                    smpLayout.setType(WGPUSamplerBindingType.Filtering);
                    layoutEntry.setSampler(smpLayout);
                }
            }

            entryVec.push_back(layoutEntry);
        }

        var desc = WGPUBindGroupLayoutDescriptor.obtain();
        desc.setEntries(entryVec);

        var layout = new WGPUBindGroupLayout();
        dev.createBindGroupLayout(desc, layout);
        return assignHandle(layout);
    }

    @Override
    public void bindGroupLayoutRelease(long bindGroupLayout) {
        var layout = get(bindGroupLayout, WGPUBindGroupLayout.class);
        if (layout != null) {
            layout.release();
            removeHandle(bindGroupLayout);
        }
    }

    // ===== Pipeline Layout =====

    @Override
    public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var bgLayouts = WGPUVectorBindGroupLayout.obtain();
        for (long h : bindGroupLayouts) {
            var layout = get(h, WGPUBindGroupLayout.class);
            if (layout != null) bgLayouts.push_back(layout);
        }

        var desc = WGPUPipelineLayoutDescriptor.obtain();
        desc.setBindGroupLayouts(bgLayouts);

        var pipelineLayout = new WGPUPipelineLayout();
        dev.createPipelineLayout(desc, pipelineLayout);
        return assignHandle(pipelineLayout);
    }

    @Override
    public void pipelineLayoutRelease(long pipelineLayout) {
        var layout = get(pipelineLayout, WGPUPipelineLayout.class);
        if (layout != null) {
            layout.release();
            removeHandle(pipelineLayout);
        }
    }

    // ===== Render Pipeline =====

    @Override
    public long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var rpDesc = new WGPURenderPipelineDescriptor();
        rpDesc.setLayout(get(desc.pipelineLayout(), WGPUPipelineLayout.class));

        // Vertex state
        var vertexState = rpDesc.getVertex();
        vertexState.setModule(get(desc.vertexModule(), WGPUShaderModule.class));
        vertexState.setEntryPoint(desc.vertexEntryPoint());
        vertexState.setConstants(WGPUVectorConstantEntry.NULL);

        if (desc.vertexBufferLayout() != null) {
            var vbl = desc.vertexBufferLayout();
            var attrVec = WGPUVectorVertexAttribute.obtain();
            for (var attr : vbl.attributes()) {
                var wgpuAttr = WGPUVertexAttribute.obtain();
                wgpuAttr.setFormat(mapVertexFormat(attr.format()));
                wgpuAttr.setOffset(attr.offset());
                wgpuAttr.setShaderLocation(attr.shaderLocation());
                attrVec.push_back(wgpuAttr);
            }

            var bufLayout = WGPUVertexBufferLayout.obtain();
            bufLayout.setArrayStride(vbl.stride());
            bufLayout.setStepMode(WGPUVertexStepMode.Vertex);
            bufLayout.setAttributes(attrVec);

            var bufLayoutVec = WGPUVectorVertexBufferLayout.obtain();
            bufLayoutVec.push_back(bufLayout);
            vertexState.setBuffers(bufLayoutVec);
        } else {
            vertexState.setBuffers(WGPUVectorVertexBufferLayout.NULL);
        }

        // Primitive state
        var primitiveState = rpDesc.getPrimitive();
        primitiveState.setTopology(WGPUPrimitiveTopology.TriangleList);
        primitiveState.setStripIndexFormat(WGPUIndexFormat.Undefined);
        primitiveState.setFrontFace(desc.frontFace() == FRONT_FACE_CW ? WGPUFrontFace.CW : WGPUFrontFace.CCW);
        primitiveState.setCullMode(switch (desc.cullMode()) {
            case CULL_MODE_FRONT -> WGPUCullMode.Front;
            case CULL_MODE_BACK -> WGPUCullMode.Back;
            default -> WGPUCullMode.None;
        });

        // Depth/stencil state
        var depthStencil = WGPUDepthStencilState.obtain();
        depthStencil.setFormat(mapTextureFormat(desc.depthStencilFormat()));
        depthStencil.setDepthWriteEnabled(desc.depthWriteEnabled() == OPTIONAL_BOOL_TRUE
                ? WGPUOptionalBool.True : WGPUOptionalBool.False);
        depthStencil.setDepthCompare(mapCompareFunc(desc.depthCompare()));
        depthStencil.setStencilReadMask(desc.stencilReadMask());
        depthStencil.setStencilWriteMask(desc.stencilWriteMask());

        configStencilFace(depthStencil.getStencilFront(), desc.stencilFront());
        configStencilFace(depthStencil.getStencilBack(), desc.stencilBack());

        rpDesc.setDepthStencil(depthStencil);

        // Multisample
        var multisample = rpDesc.getMultisample();
        multisample.setCount(1);
        multisample.setMask(0xFFFFFFFF);
        multisample.setAlphaToCoverageEnabled(false);

        // Fragment state
        if (desc.fragmentModule() != 0) {
            var fragState = WGPUFragmentState.obtain();
            fragState.setNextInChain(WGPUChainedStruct.NULL);
            fragState.setModule(get(desc.fragmentModule(), WGPUShaderModule.class));
            fragState.setEntryPoint(desc.fragmentEntryPoint());
            fragState.setConstants(WGPUVectorConstantEntry.NULL);

            var colorTarget = WGPUColorTargetState.obtain();
            colorTarget.setFormat(mapTextureFormat(desc.colorTargetFormat()));
            colorTarget.setWriteMask(WGPUColorWriteMask.All);

            var blendState = WGPUBlendState.obtain();
            var color = blendState.getColor();
            color.setOperation(mapBlendOp(desc.blendColorOperation()));
            color.setSrcFactor(mapBlendFactor(desc.blendColorSrcFactor()));
            color.setDstFactor(mapBlendFactor(desc.blendColorDstFactor()));
            var alpha = blendState.getAlpha();
            alpha.setOperation(mapBlendOp(desc.blendAlphaOperation()));
            alpha.setSrcFactor(mapBlendFactor(desc.blendAlphaSrcFactor()));
            alpha.setDstFactor(mapBlendFactor(desc.blendAlphaDstFactor()));
            colorTarget.setBlend(blendState);

            var colorTargets = WGPUVectorColorTargetState.obtain();
            colorTargets.push_back(colorTarget);
            fragState.setTargets(colorTargets);
            rpDesc.setFragment(fragState);
        }

        var pipeline = new WGPURenderPipeline();
        dev.createRenderPipeline(rpDesc, pipeline);
        return assignHandle(pipeline);
    }

    @Override
    public void renderPipelineRelease(long renderPipeline) {
        var p = get(renderPipeline, WGPURenderPipeline.class);
        if (p != null) {
            p.release();
            removeHandle(renderPipeline);
        }
    }

    // ===== Bind Group =====

    @Override
    public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        var dev = get(device, WGPUDevice.class);
        var bgLayout = get(layout, WGPUBindGroupLayout.class);
        if (dev == null || bgLayout == null) return 0;

        var entryVec = WGPUVectorBindGroupEntry.obtain();

        for (var entry : entries) {
            var bgEntry = WGPUBindGroupEntry.obtain();
            bgEntry.reset();
            bgEntry.setBinding(entry.binding());

            switch (entry.resourceType()) {
                case BUFFER -> {
                    var buf = get(entry.handle(), WGPUBuffer.class);
                    if (buf != null) {
                        bgEntry.setBuffer(buf);
                        bgEntry.setOffset((int) entry.offset());
                        bgEntry.setSize(entry.size());
                    }
                }
                case TEXTURE_VIEW -> {
                    var view = get(entry.handle(), WGPUTextureView.class);
                    if (view != null) {
                        bgEntry.setTextureView(view);
                    }
                }
                case SAMPLER -> {
                    var s = get(entry.handle(), WGPUSampler.class);
                    if (s != null) {
                        bgEntry.setSampler(s);
                    }
                }
            }

            entryVec.push_back(bgEntry);
        }

        var desc = WGPUBindGroupDescriptor.obtain();
        desc.setLayout(bgLayout);
        desc.setEntries(entryVec);

        var bindGroup = new WGPUBindGroup();
        dev.createBindGroup(desc, bindGroup);
        return assignHandle(bindGroup);
    }

    @Override
    public void bindGroupRelease(long bindGroup) {
        var bg = get(bindGroup, WGPUBindGroup.class);
        if (bg != null) {
            bg.release();
            removeHandle(bindGroup);
        }
    }

    // ===== Command Encoder =====

    @Override
    public long deviceCreateCommandEncoder(long device) {
        var dev = get(device, WGPUDevice.class);
        if (dev == null) return 0;

        var desc = WGPUCommandEncoderDescriptor.obtain();
        var encoder = WGPUCommandEncoder.obtain();
        dev.createCommandEncoder(desc, encoder);
        return assignHandle(encoder);
    }

    @Override
    public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        var enc = get(encoder, WGPUCommandEncoder.class);
        if (enc == null) return 0;

        var rpDesc = WGPURenderPassDescriptor.obtain();

        // Color attachments
        var colorVec = WGPUVectorRenderPassColorAttachment.obtain();
        if (desc.colorAttachments() != null) {
            for (var ca : desc.colorAttachments()) {
                var attachment = WGPURenderPassColorAttachment.obtain();
                attachment.setView(get(ca.textureView(), WGPUTextureView.class));
                attachment.setResolveTarget(WGPUTextureView.NULL);
                attachment.setLoadOp(WGPULoadOp.Clear);
                attachment.setStoreOp(WGPUStoreOp.Store);
                attachment.getClearValue().setColor(ca.clearR(), ca.clearG(), ca.clearB(), ca.clearA());
                colorVec.push_back(attachment);
            }
        }
        rpDesc.setColorAttachments(colorVec);

        // Depth/stencil attachment
        if (desc.depthStencil() != null) {
            var ds = desc.depthStencil();
            var depthAttachment = WGPURenderPassDepthStencilAttachment.obtain();
            depthAttachment.setView(get(ds.textureView(), WGPUTextureView.class));
            depthAttachment.setDepthLoadOp(WGPULoadOp.Clear);
            depthAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            depthAttachment.setDepthClearValue(ds.depthClearValue());
            depthAttachment.setDepthReadOnly(false);
            depthAttachment.setStencilLoadOp(WGPULoadOp.Clear);
            depthAttachment.setStencilStoreOp(WGPUStoreOp.Store);
            depthAttachment.setStencilClearValue(ds.stencilClearValue());
            depthAttachment.setStencilReadOnly(false);
            rpDesc.setDepthStencilAttachment(depthAttachment);
        } else {
            rpDesc.setDepthStencilAttachment(WGPURenderPassDepthStencilAttachment.NULL);
        }
        rpDesc.setTimestampWrites(WGPURenderPassTimestampWrites.NULL);

        var rpe = WGPURenderPassEncoder.obtain();
        enc.beginRenderPass(rpDesc, rpe);
        return assignHandle(rpe);
    }

    @Override
    public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                 long dst, int dstOffset, int size) {
        var enc = get(encoder, WGPUCommandEncoder.class);
        var srcBuf = get(src, WGPUBuffer.class);
        var dstBuf = get(dst, WGPUBuffer.class);
        if (enc != null && srcBuf != null && dstBuf != null) {
            enc.copyBufferToBuffer(srcBuf, srcOffset, dstBuf, dstOffset, size);
        }
    }

    @Override
    public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                  int width, int height,
                                                  int bytesPerRow, int rowsPerImage) {
        var enc = get(encoder, WGPUCommandEncoder.class);
        var tex = get(texture, WGPUTexture.class);
        var buf = get(buffer, WGPUBuffer.class);
        if (enc == null || tex == null || buf == null) return;

        var srcInfo = WGPUTexelCopyTextureInfo.obtain();
        srcInfo.setTexture(tex);
        srcInfo.setMipLevel(0);
        srcInfo.setAspect(WGPUTextureAspect.All);

        var dstInfo = WGPUTexelCopyBufferInfo.obtain();
        dstInfo.setBuffer(buf);
        var layout = dstInfo.getLayout();
        layout.setOffset(0);
        layout.setBytesPerRow(bytesPerRow);
        layout.setRowsPerImage(rowsPerImage);

        var copySize = WGPUExtent3D.obtain();
        copySize.setWidth(width);
        copySize.setHeight(height);
        copySize.setDepthOrArrayLayers(1);

        enc.copyTextureToBuffer(srcInfo, dstInfo, copySize);
    }

    @Override
    public long commandEncoderFinish(long encoder) {
        var enc = get(encoder, WGPUCommandEncoder.class);
        if (enc == null) return 0;

        var desc = WGPUCommandBufferDescriptor.obtain();
        var cmdBuf = WGPUCommandBuffer.obtain();
        enc.finish(desc, cmdBuf);
        return assignHandle(cmdBuf);
    }

    @Override
    public void commandEncoderRelease(long encoder) {
        var enc = get(encoder, WGPUCommandEncoder.class);
        if (enc != null) {
            enc.release();
            removeHandle(encoder);
        }
    }

    // ===== Command Buffer =====

    @Override
    public void commandBufferRelease(long commandBuffer) {
        var cb = get(commandBuffer, WGPUCommandBuffer.class);
        if (cb != null) {
            cb.release();
            removeHandle(commandBuffer);
        }
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queue, long commandBuffer) {
        var q = get(queue, WGPUQueue.class);
        var cb = get(commandBuffer, WGPUCommandBuffer.class);
        if (q != null && cb != null) {
            q.submit(cb);
        }
    }

    // ===== Render Pass Encoder =====

    @Override
    public void renderPassEnd(long renderPass) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) rpe.end();
    }

    @Override
    public void renderPassRelease(long renderPass) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) {
            rpe.release();
            removeHandle(renderPass);
        }
    }

    @Override
    public void renderPassSetPipeline(long renderPass, long pipeline) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        var p = get(pipeline, WGPURenderPipeline.class);
        if (rpe != null && p != null) rpe.setPipeline(p);
    }

    @Override
    public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer, int offset, int size) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        var buf = get(buffer, WGPUBuffer.class);
        if (rpe != null && buf != null) rpe.setVertexBuffer(slot, buf, offset, size);
    }

    @Override
    public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat, int offset, int size) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        var buf = get(buffer, WGPUBuffer.class);
        if (rpe != null && buf != null) {
            rpe.setIndexBuffer(buf, WGPUIndexFormat.Uint32, offset, size);
        }
    }

    @Override
    public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        var bg = get(bindGroup, WGPUBindGroup.class);
        if (rpe != null && bg != null) rpe.setBindGroup(groupIndex, bg);
    }

    @Override
    public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                      float minDepth, float maxDepth) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) rpe.setViewport(x, y, w, h, minDepth, maxDepth);
    }

    @Override
    public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) rpe.setScissorRect(x, y, width, height);
    }

    @Override
    public void renderPassSetStencilReference(long renderPass, int ref) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) rpe.setStencilReference(ref);
    }

    @Override
    public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                               int firstVertex, int firstInstance) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) rpe.draw(vertexCount, instanceCount, firstVertex, firstInstance);
    }

    @Override
    public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                      int firstIndex, int baseVertex, int firstInstance) {
        var rpe = get(renderPass, WGPURenderPassEncoder.class);
        if (rpe != null) rpe.drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
    }

    // ===== Mapping helpers =====

    private static WGPUTextureFormat mapTextureFormat(int format) {
        return switch (format) {
            case TEXTURE_FORMAT_R8_UNORM -> WGPUTextureFormat.R8Unorm;
            case TEXTURE_FORMAT_RGBA8_UNORM -> WGPUTextureFormat.RGBA8Unorm;
            case TEXTURE_FORMAT_BGRA8_UNORM -> WGPUTextureFormat.BGRA8Unorm;
            case TEXTURE_FORMAT_R16_FLOAT -> WGPUTextureFormat.R16Float;
            case TEXTURE_FORMAT_RG16_FLOAT -> WGPUTextureFormat.RG16Float;
            case TEXTURE_FORMAT_RGBA16_FLOAT -> WGPUTextureFormat.RGBA16Float;
            case TEXTURE_FORMAT_R32_FLOAT -> WGPUTextureFormat.R32Float;
            case TEXTURE_FORMAT_RG32_FLOAT -> WGPUTextureFormat.RG32Float;
            case TEXTURE_FORMAT_RGBA32_FLOAT -> WGPUTextureFormat.RGBA32Float;
            case TEXTURE_FORMAT_R32_UINT -> WGPUTextureFormat.R32Uint;
            case TEXTURE_FORMAT_R32_SINT -> WGPUTextureFormat.R32Sint;
            case TEXTURE_FORMAT_DEPTH24_PLUS -> WGPUTextureFormat.Depth24Plus;
            case TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8 -> WGPUTextureFormat.Depth24PlusStencil8;
            case TEXTURE_FORMAT_DEPTH32_FLOAT -> WGPUTextureFormat.Depth32Float;
            default -> WGPUTextureFormat.RGBA8Unorm;
        };
    }

    private static WGPUVertexFormat mapVertexFormat(int format) {
        return switch (format) {
            case VERTEX_FORMAT_FLOAT32 -> WGPUVertexFormat.Float32;
            case VERTEX_FORMAT_FLOAT32X2 -> WGPUVertexFormat.Float32x2;
            case VERTEX_FORMAT_FLOAT32X3 -> WGPUVertexFormat.Float32x3;
            case VERTEX_FORMAT_FLOAT32X4 -> WGPUVertexFormat.Float32x4;
            default -> WGPUVertexFormat.Float32x4;
        };
    }

    private static WGPUFilterMode mapFilterMode(int mode) {
        return mode == FILTER_MODE_NEAREST ? WGPUFilterMode.Nearest : WGPUFilterMode.Linear;
    }

    private static WGPUMipmapFilterMode mapMipmapFilterMode(int mode) {
        return mode == MIPMAP_FILTER_MODE_LINEAR ? WGPUMipmapFilterMode.Linear : WGPUMipmapFilterMode.Nearest;
    }

    private static WGPUAddressMode mapAddressMode(int mode) {
        return switch (mode) {
            case ADDRESS_MODE_CLAMP_TO_EDGE -> WGPUAddressMode.ClampToEdge;
            case ADDRESS_MODE_MIRROR_REPEAT -> WGPUAddressMode.MirrorRepeat;
            default -> WGPUAddressMode.Repeat;
        };
    }

    private static WGPUCompareFunction mapCompareFunc(int func) {
        return switch (func) {
            case COMPARE_NEVER -> WGPUCompareFunction.Never;
            case COMPARE_LESS -> WGPUCompareFunction.Less;
            case COMPARE_EQUAL -> WGPUCompareFunction.Equal;
            case COMPARE_LESS_EQUAL -> WGPUCompareFunction.LessEqual;
            case COMPARE_GREATER -> WGPUCompareFunction.Greater;
            case COMPARE_NOT_EQUAL -> WGPUCompareFunction.NotEqual;
            case COMPARE_GREATER_EQUAL -> WGPUCompareFunction.GreaterEqual;
            case COMPARE_ALWAYS -> WGPUCompareFunction.Always;
            default -> WGPUCompareFunction.Less;
        };
    }

    private static WGPUStencilOperation mapStencilOp(int op) {
        return switch (op) {
            case STENCIL_OP_ZERO -> WGPUStencilOperation.Zero;
            case STENCIL_OP_REPLACE -> WGPUStencilOperation.Replace;
            case STENCIL_OP_INVERT -> WGPUStencilOperation.Invert;
            case STENCIL_OP_INCREMENT_CLAMP -> WGPUStencilOperation.IncrementClamp;
            case STENCIL_OP_DECREMENT_CLAMP -> WGPUStencilOperation.DecrementClamp;
            case STENCIL_OP_INCREMENT_WRAP -> WGPUStencilOperation.IncrementWrap;
            case STENCIL_OP_DECREMENT_WRAP -> WGPUStencilOperation.DecrementWrap;
            default -> WGPUStencilOperation.Keep;
        };
    }

    private static WGPUBlendFactor mapBlendFactor(int factor) {
        return switch (factor) {
            case BLEND_FACTOR_ZERO -> WGPUBlendFactor.Zero;
            case BLEND_FACTOR_ONE -> WGPUBlendFactor.One;
            case BLEND_FACTOR_SRC_ALPHA -> WGPUBlendFactor.SrcAlpha;
            case BLEND_FACTOR_ONE_MINUS_SRC_ALPHA -> WGPUBlendFactor.OneMinusSrcAlpha;
            case BLEND_FACTOR_DST -> WGPUBlendFactor.Dst;
            case BLEND_FACTOR_DST_ALPHA -> WGPUBlendFactor.DstAlpha;
            default -> WGPUBlendFactor.One;
        };
    }

    private static WGPUBlendOperation mapBlendOp(int op) {
        return WGPUBlendOperation.Add; // only Add is used
    }

    private void configStencilFace(WGPUStencilFaceState face, StencilFaceState state) {
        face.setCompare(mapCompareFunc(state.compare()));
        face.setPassOp(mapStencilOp(state.passOp()));
        face.setFailOp(mapStencilOp(state.failOp()));
        face.setDepthFailOp(mapStencilOp(state.depthFailOp()));
    }
}

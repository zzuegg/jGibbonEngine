package dev.engine.providers.wgpu;

import dev.engine.core.native_.NativeLibraryLoader;
import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.graphics.window.WindowHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.myworldvw.webgpu.webgpu_h.*;

import com.myworldvw.webgpu.*;

/**
 * FFM-based implementation of {@link WgpuBindings} using jextract-generated bindings
 * from wgpu-native. Replaces JNI wrappers with direct Foreign Function & Memory API calls.
 *
 * <p>All WebGPU handles are opaque {@link MemorySegment} pointers stored in a
 * concurrent map keyed by sequential long IDs, matching the handle pattern
 * used by the engine's render device.
 */
public class FfmWgpuBindings implements WgpuBindings {

    private static final Logger log = LoggerFactory.getLogger(FfmWgpuBindings.class);

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    // Handle tracking — maps long handle IDs to native MemorySegment pointers
    private final AtomicLong nextHandle = new AtomicLong(1);
    private final Map<Long, MemorySegment> handles = new ConcurrentHashMap<>();

    private long store(MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) return 0;
        long id = nextHandle.getAndIncrement();
        handles.put(id, ptr);
        return id;
    }

    private MemorySegment get(long handle) {
        return handles.getOrDefault(handle, MemorySegment.NULL);
    }

    private void remove(long handle) {
        handles.remove(handle);
    }

    // ===== Lifecycle =====

    @Override
    public boolean initialize() {
        if (initialized.get()) return true;
        if (initialized.compareAndSet(false, true)) {
            try {
                var loader = NativeLibraryLoader.defaultLoader();
                var result = loader.resolve(WgpuSpec.spec());
                if (!result.isAvailable()) {
                    log.error("wgpu-native library not available");
                    initialized.set(false);
                    return false;
                }
                var libPath = result.libraryPath().resolve("libwgpu_native.so");
                System.load(libPath.toAbsolutePath().toString());
                log.info("wgpu-native loaded via FFM from {}", libPath);
                return true;
            } catch (Throwable t) {
                log.error("Failed to initialize wgpu-native FFM bindings", t);
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
    public boolean hasSurface() {
        return surfaceConfigured;
    }

    @Override
    public long configureSurface(long instance, long device, WindowHandle window) {
        var inst = get(instance);
        var dev = get(device);
        if (inst.equals(MemorySegment.NULL) || dev.equals(MemorySegment.NULL)) return 0;

        var surfaceInfo = window.surfaceInfo();
        if (surfaceInfo == null) {
            log.info("WindowHandle does not provide surfaceInfo - rendering offscreen");
            return 0;
        }

        MemorySegment surface;
        try (var arena = Arena.ofConfined()) {
            var surfaceDesc = WGPUSurfaceDescriptor.allocate(arena);
            WGPUSurfaceDescriptor.nextInChain(surfaceDesc, MemorySegment.NULL);

            switch (surfaceInfo.type()) {
                case X11 -> {
                    var xlibSource = WGPUSurfaceSourceXlibWindow.allocate(arena);
                    var chain = WGPUSurfaceSourceXlibWindow.chain(xlibSource);
                    WGPUChainedStruct.sType(chain, WGPUSType_SurfaceSourceXlibWindow());
                    WGPUChainedStruct.next(chain, MemorySegment.NULL);
                    WGPUSurfaceSourceXlibWindow.display(xlibSource, MemorySegment.ofAddress(surfaceInfo.display()));
                    WGPUSurfaceSourceXlibWindow.window(xlibSource, surfaceInfo.window());
                    WGPUSurfaceDescriptor.nextInChain(surfaceDesc, xlibSource);
                }
                case WAYLAND -> {
                    var waylandSource = WGPUSurfaceSourceWaylandSurface.allocate(arena);
                    var chain = WGPUSurfaceSourceWaylandSurface.chain(waylandSource);
                    WGPUChainedStruct.sType(chain, WGPUSType_SurfaceSourceWaylandSurface());
                    WGPUChainedStruct.next(chain, MemorySegment.NULL);
                    WGPUSurfaceSourceWaylandSurface.display(waylandSource, MemorySegment.ofAddress(surfaceInfo.display()));
                    WGPUSurfaceSourceWaylandSurface.surface(waylandSource, MemorySegment.ofAddress(surfaceInfo.window()));
                    WGPUSurfaceDescriptor.nextInChain(surfaceDesc, waylandSource);
                }
                case WINDOWS -> {
                    var hwndSource = WGPUSurfaceSourceWindowsHWND.allocate(arena);
                    var chain = WGPUSurfaceSourceWindowsHWND.chain(hwndSource);
                    WGPUChainedStruct.sType(chain, WGPUSType_SurfaceSourceWindowsHWND());
                    WGPUChainedStruct.next(chain, MemorySegment.NULL);
                    WGPUSurfaceSourceWindowsHWND.hinstance(hwndSource, MemorySegment.ofAddress(surfaceInfo.display()));
                    WGPUSurfaceSourceWindowsHWND.hwnd(hwndSource, MemorySegment.ofAddress(surfaceInfo.window()));
                    WGPUSurfaceDescriptor.nextInChain(surfaceDesc, hwndSource);
                }
                case COCOA -> {
                    var metalSource = WGPUSurfaceSourceMetalLayer.allocate(arena);
                    var chain = WGPUSurfaceSourceMetalLayer.chain(metalSource);
                    WGPUChainedStruct.sType(chain, WGPUSType_SurfaceSourceMetalLayer());
                    WGPUChainedStruct.next(chain, MemorySegment.NULL);
                    WGPUSurfaceSourceMetalLayer.layer(metalSource, MemorySegment.ofAddress(surfaceInfo.window()));
                    WGPUSurfaceDescriptor.nextInChain(surfaceDesc, metalSource);
                }
            }

            surface = wgpuInstanceCreateSurface(inst, surfaceDesc);
        } catch (Throwable t) {
            log.warn("WebGPU surface creation failed: {} - rendering offscreen", t.getMessage());
            return 0;
        }

        if (surface == null || surface.equals(MemorySegment.NULL)) {
            log.info("WebGPU surface creation returned null - rendering offscreen");
            return 0;
        }

        // Configure the surface
        try (var arena = Arena.ofConfined()) {
            var config = WGPUSurfaceConfiguration.allocate(arena);
            WGPUSurfaceConfiguration.nextInChain(config, MemorySegment.NULL);
            WGPUSurfaceConfiguration.device(config, dev);
            WGPUSurfaceConfiguration.format(config, TEXTURE_FORMAT_BGRA8_UNORM);
            WGPUSurfaceConfiguration.usage(config, TEXTURE_USAGE_RENDER_ATTACHMENT);
            WGPUSurfaceConfiguration.width(config, window.width());
            WGPUSurfaceConfiguration.height(config, window.height());
            WGPUSurfaceConfiguration.presentMode(config, WGPUPresentMode_Fifo());
            WGPUSurfaceConfiguration.alphaMode(config, WGPUCompositeAlphaMode_Auto());
            WGPUSurfaceConfiguration.viewFormatCount(config, 0);
            WGPUSurfaceConfiguration.viewFormats(config, MemorySegment.NULL);

            wgpuSurfaceConfigure(surface, config);
        } catch (Throwable t) {
            log.warn("WebGPU surface configure failed: {} - rendering offscreen", t.getMessage());
            return 0;
        }

        surfaceConfigured = true;
        log.info("WebGPU surface configured ({}x{}, {})", window.width(), window.height(), surfaceInfo.type());
        return store(surface);
    }

    @Override
    public long getSurfaceTextureView(long surface) {
        var surf = get(surface);
        if (surf.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var surfTexture = WGPUSurfaceTexture.allocate(arena);
            wgpuSurfaceGetCurrentTexture(surf, surfTexture);

            var texture = WGPUSurfaceTexture.texture(surfTexture);
            if (texture.equals(MemorySegment.NULL)) return 0;

            var viewDesc = WGPUTextureViewDescriptor.allocate(arena);
            WGPUTextureViewDescriptor.nextInChain(viewDesc, MemorySegment.NULL);
            WGPUTextureViewDescriptor.format(viewDesc, TEXTURE_FORMAT_BGRA8_UNORM);
            WGPUTextureViewDescriptor.dimension(viewDesc, WGPUTextureViewDimension_2D());
            WGPUTextureViewDescriptor.baseMipLevel(viewDesc, 0);
            WGPUTextureViewDescriptor.mipLevelCount(viewDesc, 1);
            WGPUTextureViewDescriptor.baseArrayLayer(viewDesc, 0);
            WGPUTextureViewDescriptor.arrayLayerCount(viewDesc, 1);
            WGPUTextureViewDescriptor.aspect(viewDesc, WGPUTextureAspect_All());

            var view = wgpuTextureCreateView(texture, viewDesc);
            if (view.equals(MemorySegment.NULL)) return 0;
            return store(view);
        }
    }

    @Override
    public void releaseSurfaceTextureView(long textureView) {
        var view = get(textureView);
        if (!view.equals(MemorySegment.NULL)) {
            wgpuTextureViewRelease(view);
            remove(textureView);
        }
    }

    @Override
    public void surfacePresent(long surface) {
        var surf = get(surface);
        if (!surf.equals(MemorySegment.NULL)) {
            wgpuSurfacePresent(surf);
        }
    }

    // ===== Instance =====

    @Override
    public long createInstance() {
        try (var arena = Arena.ofConfined()) {
            var desc = WGPUInstanceDescriptor.allocate(arena);
            WGPUInstanceDescriptor.nextInChain(desc, MemorySegment.NULL);
            var instance = wgpuCreateInstance(desc);
            return store(instance);
        }
    }

    @Override
    public void instanceProcessEvents(long instance) {
        var inst = get(instance);
        if (!inst.equals(MemorySegment.NULL)) {
            wgpuInstanceProcessEvents(inst);
        }
    }

    @Override
    public void instanceRelease(long instance) {
        var inst = get(instance);
        if (!inst.equals(MemorySegment.NULL)) {
            wgpuInstanceRelease(inst);
            remove(instance);
        }
    }

    // ===== Adapter =====

    @Override
    public long instanceRequestAdapter(long instance) {
        var inst = get(instance);
        if (inst.equals(MemorySegment.NULL)) return 0;

        var callbackArena = Arena.ofShared();
        var adapterHolder = new MemorySegment[]{MemorySegment.NULL};
        var done = new boolean[]{false};

        try (var arena = Arena.ofConfined()) {
            var options = WGPURequestAdapterOptions.allocate(arena);
            WGPURequestAdapterOptions.nextInChain(options, MemorySegment.NULL);
            WGPURequestAdapterOptions.powerPreference(options, 0); // Undefined
            WGPURequestAdapterOptions.forceFallbackAdapter(options, 0);
            WGPURequestAdapterOptions.compatibleSurface(options, MemorySegment.NULL);

            var callbackInfo = WGPURequestAdapterCallbackInfo.allocate(arena);
            WGPURequestAdapterCallbackInfo.nextInChain(callbackInfo, MemorySegment.NULL);
            WGPURequestAdapterCallbackInfo.mode(callbackInfo, WGPUCallbackMode_AllowProcessEvents());

            var callbackFn = WGPURequestAdapterCallback.allocate(
                    (int status, MemorySegment adapter, MemorySegment message,
                     MemorySegment userdata1, MemorySegment userdata2) -> {
                        adapterHolder[0] = adapter;
                        done[0] = true;
                    }, callbackArena);

            WGPURequestAdapterCallbackInfo.callback(callbackInfo, callbackFn);
            WGPURequestAdapterCallbackInfo.userdata1(callbackInfo, MemorySegment.NULL);
            WGPURequestAdapterCallbackInfo.userdata2(callbackInfo, MemorySegment.NULL);

            wgpuInstanceRequestAdapter(arena, inst, options, callbackInfo);
        } catch (Throwable t) {
            log.error("Failed to request adapter", t);
            callbackArena.close();
            return 0;
        }

        // Poll until the callback fires
        for (int i = 0; i < 1000 && !done[0]; i++) {
            wgpuInstanceProcessEvents(inst);
        }

        callbackArena.close();

        if (!done[0] || adapterHolder[0].equals(MemorySegment.NULL)) {
            log.error("Adapter request failed or timed out");
            return 0;
        }

        return store(adapterHolder[0]);
    }

    @Override
    public void adapterRelease(long adapter) {
        var adp = get(adapter);
        if (!adp.equals(MemorySegment.NULL)) {
            wgpuAdapterRelease(adp);
            remove(adapter);
        }
    }

    // ===== Device =====

    @Override
    public long adapterRequestDevice(long instance, long adapter) {
        var inst = get(instance);
        var adp = get(adapter);
        if (inst.equals(MemorySegment.NULL) || adp.equals(MemorySegment.NULL)) return 0;

        var callbackArena = Arena.ofShared();
        var deviceHolder = new MemorySegment[]{MemorySegment.NULL};
        var done = new boolean[]{false};

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUDeviceDescriptor.allocate(arena);
            WGPUDeviceDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUDeviceDescriptor.requiredFeatureCount(desc, 0);
            WGPUDeviceDescriptor.requiredFeatures(desc, MemorySegment.NULL);
            WGPUDeviceDescriptor.requiredLimits(desc, MemorySegment.NULL);

            // Set label
            var labelView = WGPUDeviceDescriptor.label(desc);
            var emptyLabel = arena.allocateFrom("");
            WGPUStringView.data(labelView, emptyLabel);
            WGPUStringView.length(labelView, 0);

            // Uncaptured error callback — prevents wgpu-native's default handler from panicking
            var errorCbInfo = WGPUDeviceDescriptor.uncapturedErrorCallbackInfo(desc);
            WGPUUncapturedErrorCallbackInfo.nextInChain(errorCbInfo, MemorySegment.NULL);
            var errorFn = WGPUUncapturedErrorCallback.allocate(
                    (MemorySegment deviceSeg, int type, MemorySegment message,
                     MemorySegment userdata1, MemorySegment userdata2) -> {
                        // Must not throw — any exception here causes a double-panic in Rust
                        try {
                            log.error("WebGPU uncaptured error (type {})", type);
                        } catch (Throwable ignored) {}
                    }, callbackArena);
            WGPUUncapturedErrorCallbackInfo.callback(errorCbInfo, errorFn);

            // Device lost callback
            var deviceLostCbInfo = WGPUDeviceDescriptor.deviceLostCallbackInfo(desc);
            WGPUDeviceLostCallbackInfo.mode(deviceLostCbInfo, WGPUCallbackMode_AllowSpontaneous());
            var deviceLostFn = WGPUDeviceLostCallback.allocate(
                    (MemorySegment deviceSeg, int reason, MemorySegment message,
                     MemorySegment userdata1, MemorySegment userdata2) -> {
                        log.warn("WebGPU device lost (reason: {})", reason);
                    }, callbackArena);
            WGPUDeviceLostCallbackInfo.callback(deviceLostCbInfo, deviceLostFn);

            // Request device callback
            var requestCbInfo = WGPURequestDeviceCallbackInfo.allocate(arena);
            WGPURequestDeviceCallbackInfo.nextInChain(requestCbInfo, MemorySegment.NULL);
            WGPURequestDeviceCallbackInfo.mode(requestCbInfo, WGPUCallbackMode_AllowProcessEvents());

            var requestFn = WGPURequestDeviceCallback.allocate(
                    (int status, MemorySegment device, MemorySegment message,
                     MemorySegment userdata1, MemorySegment userdata2) -> {
                        deviceHolder[0] = device;
                        done[0] = true;
                    }, callbackArena);

            WGPURequestDeviceCallbackInfo.callback(requestCbInfo, requestFn);
            WGPURequestDeviceCallbackInfo.userdata1(requestCbInfo, MemorySegment.NULL);
            WGPURequestDeviceCallbackInfo.userdata2(requestCbInfo, MemorySegment.NULL);

            wgpuAdapterRequestDevice(arena, adp, desc, requestCbInfo);
        } catch (Throwable t) {
            log.error("Failed to request device", t);
            callbackArena.close();
            return 0;
        }

        for (int i = 0; i < 1000 && !done[0]; i++) {
            wgpuInstanceProcessEvents(inst);
        }

        // Keep callbackArena open - the device lost callback needs it for the device lifetime.

        if (!done[0] || deviceHolder[0].equals(MemorySegment.NULL)) {
            log.error("Device request failed or timed out");
            callbackArena.close();
            return 0;
        }

        return store(deviceHolder[0]);
    }

    @Override
    public long deviceGetQueue(long device) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;
        return store(wgpuDeviceGetQueue(dev));
    }

    @Override
    public void deviceRelease(long device) {
        var dev = get(device);
        if (!dev.equals(MemorySegment.NULL)) {
            wgpuDeviceRelease(dev);
            remove(device);
        }
    }

    @Override
    public DeviceLimits deviceGetLimits(long device) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return null;

        try (var arena = Arena.ofConfined()) {
            var limits = WGPULimits.allocate(arena);
            WGPULimits.nextInChain(limits, MemorySegment.NULL);

            int status = wgpuDeviceGetLimits(dev, limits);
            if (status != 0) {
                log.warn("wgpuDeviceGetLimits returned status {}", status);
                return null;
            }

            return new DeviceLimits(
                    WGPULimits.maxTextureDimension2D(limits),
                    WGPULimits.maxTextureDimension3D(limits),
                    (int) WGPULimits.maxUniformBufferBindingSize(limits),
                    (int) WGPULimits.maxStorageBufferBindingSize(limits),
                    WGPULimits.maxColorAttachments(limits),
                    16.0f // maxSamplerAnisotropy not directly in WGPULimits
            );
        }
    }

    // ===== Buffer =====

    @Override
    public long deviceCreateBuffer(long device, long size, int usage) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUBufferDescriptor.allocate(arena);
            WGPUBufferDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUBufferDescriptor.usage(desc, usage);
            WGPUBufferDescriptor.size(desc, size);
            WGPUBufferDescriptor.mappedAtCreation(desc, 0);

            return store(wgpuDeviceCreateBuffer(dev, desc));
        }
    }

    @Override
    public void bufferRelease(long buffer) {
        var buf = get(buffer);
        if (!buf.equals(MemorySegment.NULL)) {
            wgpuBufferRelease(buf);
            remove(buffer);
        }
    }

    @Override
    public void queueWriteBuffer(long queue, long buffer, int offset, ByteBuffer data, int size) {
        var q = get(queue);
        var buf = get(buffer);
        if (q.equals(MemorySegment.NULL) || buf.equals(MemorySegment.NULL)) return;

        var dataSegment = MemorySegment.ofBuffer(data);
        wgpuQueueWriteBuffer(q, buf, offset, dataSegment, size);
    }

    @Override
    public void bufferMapReadSync(long instance, long buffer, int size, int maxPolls) {
        var inst = get(instance);
        var buf = get(buffer);
        if (inst.equals(MemorySegment.NULL) || buf.equals(MemorySegment.NULL)) return;

        var callbackArena = Arena.ofShared();
        var done = new boolean[]{false};

        try (var arena = Arena.ofConfined()) {
            var callbackInfo = WGPUBufferMapCallbackInfo.allocate(arena);
            WGPUBufferMapCallbackInfo.nextInChain(callbackInfo, MemorySegment.NULL);
            WGPUBufferMapCallbackInfo.mode(callbackInfo, WGPUCallbackMode_AllowProcessEvents());

            var callbackFn = WGPUBufferMapCallback.allocate(
                    (int status, MemorySegment message,
                     MemorySegment userdata1, MemorySegment userdata2) -> {
                        done[0] = true;
                    }, callbackArena);

            WGPUBufferMapCallbackInfo.callback(callbackInfo, callbackFn);
            WGPUBufferMapCallbackInfo.userdata1(callbackInfo, MemorySegment.NULL);
            WGPUBufferMapCallbackInfo.userdata2(callbackInfo, MemorySegment.NULL);

            // MapMode_Read = 1
            wgpuBufferMapAsync(arena, buf, 1L, 0, size, callbackInfo);
        } catch (Throwable t) {
            log.error("Failed to initiate buffer map", t);
            callbackArena.close();
            return;
        }

        for (int i = 0; i < maxPolls && !done[0]; i++) {
            wgpuInstanceProcessEvents(inst);
        }

        callbackArena.close();
    }

    @Override
    public void bufferGetConstMappedRange(long buffer, int offset, int size, ByteBuffer dest) {
        var buf = get(buffer);
        if (buf.equals(MemorySegment.NULL)) return;

        var mapped = wgpuBufferGetConstMappedRange(buf, offset, size);
        if (mapped.equals(MemorySegment.NULL)) return;

        // Reinterpret to the correct size so we can copy
        var sized = mapped.reinterpret(size);
        dest.put(sized.asByteBuffer());
    }

    @Override
    public void bufferUnmap(long buffer) {
        var buf = get(buffer);
        if (!buf.equals(MemorySegment.NULL)) {
            wgpuBufferUnmap(buf);
        }
    }

    // ===== Texture =====

    @Override
    public long deviceCreateTexture(long device, int width, int height, int depthOrLayers,
                                    int format, int dimension, int usage) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUTextureDescriptor.allocate(arena);
            WGPUTextureDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUTextureDescriptor.usage(desc, usage);
            WGPUTextureDescriptor.dimension(desc, dimension == TEXTURE_DIMENSION_3D ? 2 : 1); // WGPUTextureDimension: 1=2D, 2=3D
            WGPUTextureDescriptor.format(desc, format);
            WGPUTextureDescriptor.mipLevelCount(desc, 1);
            WGPUTextureDescriptor.sampleCount(desc, 1);
            WGPUTextureDescriptor.viewFormatCount(desc, 0);
            WGPUTextureDescriptor.viewFormats(desc, MemorySegment.NULL);

            var sizeStruct = WGPUTextureDescriptor.size(desc);
            WGPUExtent3D.width(sizeStruct, width);
            WGPUExtent3D.height(sizeStruct, height);
            WGPUExtent3D.depthOrArrayLayers(sizeStruct, depthOrLayers);

            return store(wgpuDeviceCreateTexture(dev, desc));
        }
    }

    @Override
    public long textureCreateView(long texture, int format, int viewDimension, int arrayLayerCount) {
        var tex = get(texture);
        if (tex.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUTextureViewDescriptor.allocate(arena);
            WGPUTextureViewDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUTextureViewDescriptor.format(desc, format);
            WGPUTextureViewDescriptor.baseMipLevel(desc, 0);
            WGPUTextureViewDescriptor.mipLevelCount(desc, 1);
            WGPUTextureViewDescriptor.baseArrayLayer(desc, 0);
            WGPUTextureViewDescriptor.arrayLayerCount(desc, arrayLayerCount);
            WGPUTextureViewDescriptor.aspect(desc, WGPUTextureAspect_All());

            WGPUTextureViewDescriptor.dimension(desc, switch (viewDimension) {
                case TEXTURE_VIEW_DIMENSION_3D -> WGPUTextureViewDimension_3D();
                case TEXTURE_VIEW_DIMENSION_2D_ARRAY -> WGPUTextureViewDimension_2DArray();
                case TEXTURE_VIEW_DIMENSION_CUBE -> WGPUTextureViewDimension_Cube();
                default -> WGPUTextureViewDimension_2D();
            });

            return store(wgpuTextureCreateView(tex, desc));
        }
    }

    @Override
    public void textureRelease(long texture) {
        var tex = get(texture);
        if (!tex.equals(MemorySegment.NULL)) {
            wgpuTextureRelease(tex);
            remove(texture);
        }
    }

    @Override
    public void textureViewRelease(long textureView) {
        var view = get(textureView);
        if (!view.equals(MemorySegment.NULL)) {
            wgpuTextureViewRelease(view);
            remove(textureView);
        }
    }

    @Override
    public void queueWriteTexture(long queue, long texture, int width, int height,
                                  int depthOrLayers, int bytesPerRow, ByteBuffer data) {
        var q = get(queue);
        var tex = get(texture);
        if (q.equals(MemorySegment.NULL) || tex.equals(MemorySegment.NULL)) return;

        try (var arena = Arena.ofConfined()) {
            var destination = WGPUTexelCopyTextureInfo.allocate(arena);
            WGPUTexelCopyTextureInfo.texture(destination, tex);
            WGPUTexelCopyTextureInfo.mipLevel(destination, 0);
            WGPUTexelCopyTextureInfo.aspect(destination, WGPUTextureAspect_All());

            var dataLayout = WGPUTexelCopyBufferLayout.allocate(arena);
            WGPUTexelCopyBufferLayout.offset(dataLayout, 0);
            WGPUTexelCopyBufferLayout.bytesPerRow(dataLayout, bytesPerRow);
            WGPUTexelCopyBufferLayout.rowsPerImage(dataLayout, height);

            var writeSize = WGPUExtent3D.allocate(arena);
            WGPUExtent3D.width(writeSize, width);
            WGPUExtent3D.height(writeSize, height);
            WGPUExtent3D.depthOrArrayLayers(writeSize, depthOrLayers);

            var dataSegment = MemorySegment.ofBuffer(data);
            wgpuQueueWriteTexture(q, destination, dataSegment, data.remaining(), dataLayout, writeSize);
        }
    }

    // ===== Sampler =====

    @Override
    public long deviceCreateSampler(long device, int addressU, int addressV, int addressW,
                                    int magFilter, int minFilter, int mipmapFilter,
                                    float lodMinClamp, float lodMaxClamp,
                                    int compare, float maxAnisotropy) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUSamplerDescriptor.allocate(arena);
            WGPUSamplerDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUSamplerDescriptor.addressModeU(desc, addressU);
            WGPUSamplerDescriptor.addressModeV(desc, addressV);
            WGPUSamplerDescriptor.addressModeW(desc, addressW);
            WGPUSamplerDescriptor.magFilter(desc, magFilter);
            WGPUSamplerDescriptor.minFilter(desc, minFilter);
            WGPUSamplerDescriptor.mipmapFilter(desc, mipmapFilter);
            WGPUSamplerDescriptor.lodMinClamp(desc, lodMinClamp);
            WGPUSamplerDescriptor.lodMaxClamp(desc, lodMaxClamp);
            WGPUSamplerDescriptor.compare(desc, compare);
            WGPUSamplerDescriptor.maxAnisotropy(desc, (short) maxAnisotropy);

            return store(wgpuDeviceCreateSampler(dev, desc));
        }
    }

    @Override
    public void samplerRelease(long sampler) {
        var s = get(sampler);
        if (!s.equals(MemorySegment.NULL)) {
            wgpuSamplerRelease(s);
            remove(sampler);
        }
    }

    // ===== Shader Module =====

    @Override
    public long deviceCreateShaderModule(long device, String wgsl) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            // Allocate the WGSL source chain struct
            var wgslSource = WGPUShaderSourceWGSL.allocate(arena);
            var chain = WGPUShaderSourceWGSL.chain(wgslSource);
            WGPUChainedStruct.sType(chain, WGPUSType_ShaderSourceWGSL());
            WGPUChainedStruct.next(chain, MemorySegment.NULL);

            // Set the WGSL code as a WGPUStringView
            var codeBytes = arena.allocateFrom(wgsl);
            var codeView = WGPUShaderSourceWGSL.code(wgslSource);
            WGPUStringView.data(codeView, codeBytes);
            WGPUStringView.length(codeView, wgsl.length());

            // Create the shader module descriptor
            var desc = WGPUShaderModuleDescriptor.allocate(arena);
            WGPUShaderModuleDescriptor.nextInChain(desc, wgslSource);

            // Set label to empty string (required — NonNullInputString)
            var labelView = WGPUShaderModuleDescriptor.label(desc);
            var emptyStr = arena.allocateFrom("");
            WGPUStringView.data(labelView, emptyStr);
            WGPUStringView.length(labelView, 0);

            var result = wgpuDeviceCreateShaderModule(dev, desc);
            if (result == null || result.equals(MemorySegment.NULL)) {
                log.error("wgpuDeviceCreateShaderModule returned null");
                return 0;
            }
            return store(result);
        } catch (Throwable t) {
            log.error("Failed to create shader module: {}", t.getMessage(), t);
            return 0;
        }
    }

    @Override
    public boolean shaderModuleIsValid(long shaderModule) {
        var module = get(shaderModule);
        return !module.equals(MemorySegment.NULL);
    }

    @Override
    public void shaderModuleRelease(long shaderModule) {
        var module = get(shaderModule);
        if (!module.equals(MemorySegment.NULL)) {
            wgpuShaderModuleRelease(module);
            remove(shaderModule);
        }
    }

    // ===== Bind Group Layout =====

    @Override
    public long deviceCreateBindGroupLayout(long device, BindGroupLayoutEntry[] entries) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var entryArray = WGPUBindGroupLayoutEntry.allocateArray(entries.length, arena);

            for (int i = 0; i < entries.length; i++) {
                var entry = entries[i];
                var entrySlice = WGPUBindGroupLayoutEntry.asSlice(entryArray, i);
                WGPUBindGroupLayoutEntry.nextInChain(entrySlice, MemorySegment.NULL);
                WGPUBindGroupLayoutEntry.binding(entrySlice, entry.binding());
                WGPUBindGroupLayoutEntry.visibility(entrySlice, entry.visibility());

                switch (entry.type()) {
                    case UNIFORM_BUFFER -> {
                        var bufLayout = WGPUBindGroupLayoutEntry.buffer(entrySlice);
                        WGPUBufferBindingLayout.type(bufLayout, WGPUBufferBindingType_Uniform());
                        WGPUBufferBindingLayout.hasDynamicOffset(bufLayout, 0);
                        WGPUBufferBindingLayout.minBindingSize(bufLayout, 0);
                    }
                    case READ_ONLY_STORAGE_BUFFER -> {
                        var bufLayout = WGPUBindGroupLayoutEntry.buffer(entrySlice);
                        WGPUBufferBindingLayout.type(bufLayout, WGPUBufferBindingType_ReadOnlyStorage());
                        WGPUBufferBindingLayout.hasDynamicOffset(bufLayout, 0);
                        WGPUBufferBindingLayout.minBindingSize(bufLayout, 0);
                    }
                    case SAMPLED_TEXTURE -> {
                        var texLayout = WGPUBindGroupLayoutEntry.texture(entrySlice);
                        WGPUTextureBindingLayout.sampleType(texLayout, WGPUTextureSampleType_Float());
                        WGPUTextureBindingLayout.viewDimension(texLayout, WGPUTextureViewDimension_2D());
                        WGPUTextureBindingLayout.multisampled(texLayout, 0);
                    }
                    case FILTERING_SAMPLER -> {
                        var smpLayout = WGPUBindGroupLayoutEntry.sampler(entrySlice);
                        WGPUSamplerBindingLayout.type(smpLayout, WGPUSamplerBindingType_Filtering());
                    }
                }
            }

            var desc = WGPUBindGroupLayoutDescriptor.allocate(arena);
            WGPUBindGroupLayoutDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUBindGroupLayoutDescriptor.entryCount(desc, entries.length);
            WGPUBindGroupLayoutDescriptor.entries(desc, entryArray);

            return store(wgpuDeviceCreateBindGroupLayout(dev, desc));
        }
    }

    @Override
    public void bindGroupLayoutRelease(long bindGroupLayout) {
        var layout = get(bindGroupLayout);
        if (!layout.equals(MemorySegment.NULL)) {
            wgpuBindGroupLayoutRelease(layout);
            remove(bindGroupLayout);
        }
    }

    // ===== Pipeline Layout =====

    @Override
    public long deviceCreatePipelineLayout(long device, long[] bindGroupLayouts) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var layoutArray = arena.allocate(ValueLayout.ADDRESS, bindGroupLayouts.length);
            for (int i = 0; i < bindGroupLayouts.length; i++) {
                layoutArray.setAtIndex(ValueLayout.ADDRESS, i, get(bindGroupLayouts[i]));
            }

            var desc = WGPUPipelineLayoutDescriptor.allocate(arena);
            WGPUPipelineLayoutDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUPipelineLayoutDescriptor.bindGroupLayoutCount(desc, bindGroupLayouts.length);
            WGPUPipelineLayoutDescriptor.bindGroupLayouts(desc, layoutArray);

            return store(wgpuDeviceCreatePipelineLayout(dev, desc));
        }
    }

    @Override
    public void pipelineLayoutRelease(long pipelineLayout) {
        var layout = get(pipelineLayout);
        if (!layout.equals(MemorySegment.NULL)) {
            wgpuPipelineLayoutRelease(layout);
            remove(pipelineLayout);
        }
    }

    // ===== Render Pipeline =====

    @Override
    public long deviceCreateRenderPipeline(long device, RenderPipelineDescriptor desc) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var rpDesc = WGPURenderPipelineDescriptor.allocate(arena);
            WGPURenderPipelineDescriptor.nextInChain(rpDesc, MemorySegment.NULL);
            WGPURenderPipelineDescriptor.layout(rpDesc, get(desc.pipelineLayout()));

            // Vertex state
            var vertexState = WGPURenderPipelineDescriptor.vertex(rpDesc);
            WGPUVertexState.nextInChain(vertexState, MemorySegment.NULL);
            WGPUVertexState.module(vertexState, get(desc.vertexModule()));
            var vertexEntryStr = arena.allocateFrom(desc.vertexEntryPoint());
            var vertexEntryView = WGPUVertexState.entryPoint(vertexState);
            WGPUStringView.data(vertexEntryView, vertexEntryStr);
            WGPUStringView.length(vertexEntryView, desc.vertexEntryPoint().length());
            WGPUVertexState.constantCount(vertexState, 0);
            WGPUVertexState.constants(vertexState, MemorySegment.NULL);

            if (desc.vertexBufferLayout() != null) {
                var vbl = desc.vertexBufferLayout();
                var attrs = vbl.attributes();
                var attrArray = WGPUVertexAttribute.allocateArray(attrs.length, arena);
                for (int i = 0; i < attrs.length; i++) {
                    var attrSlice = WGPUVertexAttribute.asSlice(attrArray, i);
                    WGPUVertexAttribute.format(attrSlice, attrs[i].format());
                    WGPUVertexAttribute.offset(attrSlice, attrs[i].offset());
                    WGPUVertexAttribute.shaderLocation(attrSlice, attrs[i].shaderLocation());
                }

                var bufLayout = WGPUVertexBufferLayout.allocate(arena);
                WGPUVertexBufferLayout.arrayStride(bufLayout, vbl.stride());
                WGPUVertexBufferLayout.stepMode(bufLayout, VERTEX_STEP_MODE_VERTEX);
                WGPUVertexBufferLayout.attributeCount(bufLayout, attrs.length);
                WGPUVertexBufferLayout.attributes(bufLayout, attrArray);

                WGPUVertexState.bufferCount(vertexState, 1);
                WGPUVertexState.buffers(vertexState, bufLayout);
            } else {
                WGPUVertexState.bufferCount(vertexState, 0);
                WGPUVertexState.buffers(vertexState, MemorySegment.NULL);
            }

            // Primitive state
            var primitiveState = WGPURenderPipelineDescriptor.primitive(rpDesc);
            WGPUPrimitiveState.nextInChain(primitiveState, MemorySegment.NULL);
            WGPUPrimitiveState.topology(primitiveState, desc.topology());
            WGPUPrimitiveState.stripIndexFormat(primitiveState, WGPUIndexFormat_Undefined());
            WGPUPrimitiveState.frontFace(primitiveState, desc.frontFace());
            WGPUPrimitiveState.cullMode(primitiveState, desc.cullMode());
            WGPUPrimitiveState.unclippedDepth(primitiveState, 0);

            // Depth/stencil state
            var depthStencil = WGPUDepthStencilState.allocate(arena);
            WGPUDepthStencilState.nextInChain(depthStencil, MemorySegment.NULL);
            WGPUDepthStencilState.format(depthStencil, desc.depthStencilFormat());
            WGPUDepthStencilState.depthWriteEnabled(depthStencil, desc.depthWriteEnabled());
            WGPUDepthStencilState.depthCompare(depthStencil, desc.depthCompare());
            WGPUDepthStencilState.stencilReadMask(depthStencil, desc.stencilReadMask());
            WGPUDepthStencilState.stencilWriteMask(depthStencil, desc.stencilWriteMask());
            WGPUDepthStencilState.depthBias(depthStencil, 0);
            WGPUDepthStencilState.depthBiasSlopeScale(depthStencil, 0.0f);
            WGPUDepthStencilState.depthBiasClamp(depthStencil, 0.0f);

            var stencilFront = WGPUDepthStencilState.stencilFront(depthStencil);
            configStencilFace(stencilFront, desc.stencilFront());
            var stencilBack = WGPUDepthStencilState.stencilBack(depthStencil);
            configStencilFace(stencilBack, desc.stencilBack());

            WGPURenderPipelineDescriptor.depthStencil(rpDesc, depthStencil);

            // Multisample state
            var multisample = WGPURenderPipelineDescriptor.multisample(rpDesc);
            WGPUMultisampleState.nextInChain(multisample, MemorySegment.NULL);
            WGPUMultisampleState.count(multisample, 1);
            WGPUMultisampleState.mask(multisample, 0xFFFFFFFF);
            WGPUMultisampleState.alphaToCoverageEnabled(multisample, 0);

            // Fragment state
            if (desc.fragmentModule() != 0) {
                var fragState = WGPUFragmentState.allocate(arena);
                WGPUFragmentState.nextInChain(fragState, MemorySegment.NULL);
                WGPUFragmentState.module(fragState, get(desc.fragmentModule()));
                var fragEntryStr = arena.allocateFrom(desc.fragmentEntryPoint());
                var fragEntryView = WGPUFragmentState.entryPoint(fragState);
                WGPUStringView.data(fragEntryView, fragEntryStr);
                WGPUStringView.length(fragEntryView, desc.fragmentEntryPoint().length());
                WGPUFragmentState.constantCount(fragState, 0);
                WGPUFragmentState.constants(fragState, MemorySegment.NULL);

                // Color target
                var colorTarget = WGPUColorTargetState.allocate(arena);
                WGPUColorTargetState.nextInChain(colorTarget, MemorySegment.NULL);
                WGPUColorTargetState.format(colorTarget, desc.colorTargetFormat());
                WGPUColorTargetState.writeMask(colorTarget, COLOR_WRITE_MASK_ALL);

                // Blend state
                var blendState = WGPUBlendState.allocate(arena);
                var colorBlend = WGPUBlendState.color(blendState);
                WGPUBlendComponent.operation(colorBlend, desc.blendColorOperation());
                WGPUBlendComponent.srcFactor(colorBlend, desc.blendColorSrcFactor());
                WGPUBlendComponent.dstFactor(colorBlend, desc.blendColorDstFactor());
                var alphaBlend = WGPUBlendState.alpha(blendState);
                WGPUBlendComponent.operation(alphaBlend, desc.blendAlphaOperation());
                WGPUBlendComponent.srcFactor(alphaBlend, desc.blendAlphaSrcFactor());
                WGPUBlendComponent.dstFactor(alphaBlend, desc.blendAlphaDstFactor());

                WGPUColorTargetState.blend(colorTarget, blendState);

                WGPUFragmentState.targetCount(fragState, 1);
                WGPUFragmentState.targets(fragState, colorTarget);

                WGPURenderPipelineDescriptor.fragment(rpDesc, fragState);
            } else {
                WGPURenderPipelineDescriptor.fragment(rpDesc, MemorySegment.NULL);
            }

            return store(wgpuDeviceCreateRenderPipeline(dev, rpDesc));
        }
    }

    @Override
    public void renderPipelineRelease(long renderPipeline) {
        var p = get(renderPipeline);
        if (!p.equals(MemorySegment.NULL)) {
            wgpuRenderPipelineRelease(p);
            remove(renderPipeline);
        }
    }

    // ===== Bind Group =====

    @Override
    public long deviceCreateBindGroup(long device, long layout, BindGroupEntry[] entries) {
        var dev = get(device);
        var bgLayout = get(layout);
        if (dev.equals(MemorySegment.NULL) || bgLayout.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var entryArray = WGPUBindGroupEntry.allocateArray(entries.length, arena);

            for (int i = 0; i < entries.length; i++) {
                var entry = entries[i];
                var entrySlice = WGPUBindGroupEntry.asSlice(entryArray, i);

                // Zero out the entry (ensures null pointers for unused fields)
                entrySlice.fill((byte) 0);

                WGPUBindGroupEntry.nextInChain(entrySlice, MemorySegment.NULL);
                WGPUBindGroupEntry.binding(entrySlice, entry.binding());

                switch (entry.resourceType()) {
                    case BUFFER -> {
                        WGPUBindGroupEntry.buffer(entrySlice, get(entry.handle()));
                        WGPUBindGroupEntry.offset(entrySlice, entry.offset());
                        WGPUBindGroupEntry.size(entrySlice, entry.size());
                    }
                    case TEXTURE_VIEW -> {
                        WGPUBindGroupEntry.textureView(entrySlice, get(entry.handle()));
                    }
                    case SAMPLER -> {
                        WGPUBindGroupEntry.sampler(entrySlice, get(entry.handle()));
                    }
                }
            }

            var desc = WGPUBindGroupDescriptor.allocate(arena);
            WGPUBindGroupDescriptor.nextInChain(desc, MemorySegment.NULL);
            WGPUBindGroupDescriptor.layout(desc, bgLayout);
            WGPUBindGroupDescriptor.entryCount(desc, entries.length);
            WGPUBindGroupDescriptor.entries(desc, entryArray);

            return store(wgpuDeviceCreateBindGroup(dev, desc));
        }
    }

    @Override
    public void bindGroupRelease(long bindGroup) {
        var bg = get(bindGroup);
        if (!bg.equals(MemorySegment.NULL)) {
            wgpuBindGroupRelease(bg);
            remove(bindGroup);
        }
    }

    // ===== Command Encoder =====

    @Override
    public long deviceCreateCommandEncoder(long device) {
        var dev = get(device);
        if (dev.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUCommandEncoderDescriptor.allocate(arena);
            WGPUCommandEncoderDescriptor.nextInChain(desc, MemorySegment.NULL);

            return store(wgpuDeviceCreateCommandEncoder(dev, desc));
        }
    }

    @Override
    public long commandEncoderBeginRenderPass(long encoder, RenderPassDescriptor desc) {
        var enc = get(encoder);
        if (enc.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var rpDesc = WGPURenderPassDescriptor.allocate(arena);
            WGPURenderPassDescriptor.nextInChain(rpDesc, MemorySegment.NULL);
            WGPURenderPassDescriptor.occlusionQuerySet(rpDesc, MemorySegment.NULL);
            WGPURenderPassDescriptor.timestampWrites(rpDesc, MemorySegment.NULL);

            // Color attachments
            if (desc.colorAttachments() != null && desc.colorAttachments().length > 0) {
                var colorArray = WGPURenderPassColorAttachment.allocateArray(
                        desc.colorAttachments().length, arena);

                for (int i = 0; i < desc.colorAttachments().length; i++) {
                    var ca = desc.colorAttachments()[i];
                    var attachment = WGPURenderPassColorAttachment.asSlice(colorArray, i);

                    WGPURenderPassColorAttachment.nextInChain(attachment, MemorySegment.NULL);
                    WGPURenderPassColorAttachment.view(attachment, get(ca.textureView()));
                    WGPURenderPassColorAttachment.resolveTarget(attachment, MemorySegment.NULL);
                    WGPURenderPassColorAttachment.loadOp(attachment, WGPULoadOp_Clear());
                    WGPURenderPassColorAttachment.storeOp(attachment, WGPUStoreOp_Store());
                    WGPURenderPassColorAttachment.depthSlice(attachment, WGPU_DEPTH_SLICE_UNDEFINED());

                    var clearValue = WGPURenderPassColorAttachment.clearValue(attachment);
                    WGPUColor.r(clearValue, ca.clearR());
                    WGPUColor.g(clearValue, ca.clearG());
                    WGPUColor.b(clearValue, ca.clearB());
                    WGPUColor.a(clearValue, ca.clearA());
                }

                WGPURenderPassDescriptor.colorAttachmentCount(rpDesc, desc.colorAttachments().length);
                WGPURenderPassDescriptor.colorAttachments(rpDesc, colorArray);
            } else {
                WGPURenderPassDescriptor.colorAttachmentCount(rpDesc, 0);
                WGPURenderPassDescriptor.colorAttachments(rpDesc, MemorySegment.NULL);
            }

            // Depth/stencil attachment
            if (desc.depthStencil() != null) {
                var ds = desc.depthStencil();
                var depthAttachment = WGPURenderPassDepthStencilAttachment.allocate(arena);
                WGPURenderPassDepthStencilAttachment.view(depthAttachment, get(ds.textureView()));
                WGPURenderPassDepthStencilAttachment.depthLoadOp(depthAttachment, WGPULoadOp_Clear());
                WGPURenderPassDepthStencilAttachment.depthStoreOp(depthAttachment, WGPUStoreOp_Store());
                WGPURenderPassDepthStencilAttachment.depthClearValue(depthAttachment, ds.depthClearValue());
                WGPURenderPassDepthStencilAttachment.depthReadOnly(depthAttachment, 0);
                WGPURenderPassDepthStencilAttachment.stencilLoadOp(depthAttachment, WGPULoadOp_Clear());
                WGPURenderPassDepthStencilAttachment.stencilStoreOp(depthAttachment, WGPUStoreOp_Store());
                WGPURenderPassDepthStencilAttachment.stencilClearValue(depthAttachment, ds.stencilClearValue());
                WGPURenderPassDepthStencilAttachment.stencilReadOnly(depthAttachment, 0);
                WGPURenderPassDescriptor.depthStencilAttachment(rpDesc, depthAttachment);
            } else {
                WGPURenderPassDescriptor.depthStencilAttachment(rpDesc, MemorySegment.NULL);
            }

            return store(wgpuCommandEncoderBeginRenderPass(enc, rpDesc));
        }
    }

    @Override
    public void commandEncoderCopyBufferToBuffer(long encoder, long src, int srcOffset,
                                                 long dst, int dstOffset, int size) {
        var enc = get(encoder);
        var srcBuf = get(src);
        var dstBuf = get(dst);
        if (!enc.equals(MemorySegment.NULL) && !srcBuf.equals(MemorySegment.NULL)
                && !dstBuf.equals(MemorySegment.NULL)) {
            wgpuCommandEncoderCopyBufferToBuffer(enc, srcBuf, srcOffset, dstBuf, dstOffset, size);
        }
    }

    @Override
    public void commandEncoderCopyTextureToBuffer(long encoder, long texture, long buffer,
                                                  int width, int height,
                                                  int bytesPerRow, int rowsPerImage) {
        var enc = get(encoder);
        var tex = get(texture);
        var buf = get(buffer);
        if (enc.equals(MemorySegment.NULL) || tex.equals(MemorySegment.NULL)
                || buf.equals(MemorySegment.NULL)) return;

        try (var arena = Arena.ofConfined()) {
            var srcInfo = WGPUTexelCopyTextureInfo.allocate(arena);
            WGPUTexelCopyTextureInfo.texture(srcInfo, tex);
            WGPUTexelCopyTextureInfo.mipLevel(srcInfo, 0);
            WGPUTexelCopyTextureInfo.aspect(srcInfo, WGPUTextureAspect_All());

            var dstInfo = WGPUTexelCopyBufferInfo.allocate(arena);
            WGPUTexelCopyBufferInfo.buffer(dstInfo, buf);
            var dstLayout = WGPUTexelCopyBufferInfo.layout(dstInfo);
            WGPUTexelCopyBufferLayout.offset(dstLayout, 0);
            WGPUTexelCopyBufferLayout.bytesPerRow(dstLayout, bytesPerRow);
            WGPUTexelCopyBufferLayout.rowsPerImage(dstLayout, rowsPerImage);

            var copySize = WGPUExtent3D.allocate(arena);
            WGPUExtent3D.width(copySize, width);
            WGPUExtent3D.height(copySize, height);
            WGPUExtent3D.depthOrArrayLayers(copySize, 1);

            wgpuCommandEncoderCopyTextureToBuffer(enc, srcInfo, dstInfo, copySize);
        }
    }

    @Override
    public long commandEncoderFinish(long encoder) {
        var enc = get(encoder);
        if (enc.equals(MemorySegment.NULL)) return 0;

        try (var arena = Arena.ofConfined()) {
            var desc = WGPUCommandBufferDescriptor.allocate(arena);
            WGPUCommandBufferDescriptor.nextInChain(desc, MemorySegment.NULL);

            return store(wgpuCommandEncoderFinish(enc, desc));
        }
    }

    @Override
    public void commandEncoderRelease(long encoder) {
        var enc = get(encoder);
        if (!enc.equals(MemorySegment.NULL)) {
            wgpuCommandEncoderRelease(enc);
            remove(encoder);
        }
    }

    // ===== Command Buffer =====

    @Override
    public void commandBufferRelease(long commandBuffer) {
        var cb = get(commandBuffer);
        if (!cb.equals(MemorySegment.NULL)) {
            wgpuCommandBufferRelease(cb);
            remove(commandBuffer);
        }
    }

    // ===== Queue =====

    @Override
    public void queueSubmit(long queue, long commandBuffer) {
        var q = get(queue);
        var cb = get(commandBuffer);
        if (q.equals(MemorySegment.NULL) || cb.equals(MemorySegment.NULL)) return;

        try (var arena = Arena.ofConfined()) {
            var cmdBufArray = arena.allocate(ValueLayout.ADDRESS, 1);
            cmdBufArray.setAtIndex(ValueLayout.ADDRESS, 0, cb);
            wgpuQueueSubmit(q, 1, cmdBufArray);
        }
    }

    // ===== Render Pass Encoder =====

    @Override
    public void renderPassEnd(long renderPass) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderEnd(rpe);
        }
    }

    @Override
    public void renderPassRelease(long renderPass) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderRelease(rpe);
            remove(renderPass);
        }
    }

    @Override
    public void renderPassSetPipeline(long renderPass, long pipeline) {
        var rpe = get(renderPass);
        var p = get(pipeline);
        if (!rpe.equals(MemorySegment.NULL) && !p.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetPipeline(rpe, p);
        }
    }

    @Override
    public void renderPassSetVertexBuffer(long renderPass, int slot, long buffer,
                                          int offset, int size) {
        var rpe = get(renderPass);
        var buf = get(buffer);
        if (!rpe.equals(MemorySegment.NULL) && !buf.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetVertexBuffer(rpe, slot, buf, offset, size);
        }
    }

    @Override
    public void renderPassSetIndexBuffer(long renderPass, long buffer, int indexFormat,
                                         int offset, int size) {
        var rpe = get(renderPass);
        var buf = get(buffer);
        if (!rpe.equals(MemorySegment.NULL) && !buf.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetIndexBuffer(rpe, buf, indexFormat, offset, size);
        }
    }

    @Override
    public void renderPassSetBindGroup(long renderPass, int groupIndex, long bindGroup) {
        var rpe = get(renderPass);
        var bg = get(bindGroup);
        if (!rpe.equals(MemorySegment.NULL) && !bg.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetBindGroup(rpe, groupIndex, bg, 0, MemorySegment.NULL);
        }
    }

    @Override
    public void renderPassSetViewport(long renderPass, float x, float y, float w, float h,
                                      float minDepth, float maxDepth) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetViewport(rpe, x, y, w, h, minDepth, maxDepth);
        }
    }

    @Override
    public void renderPassSetScissorRect(long renderPass, int x, int y, int width, int height) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetScissorRect(rpe, x, y, width, height);
        }
    }

    @Override
    public void renderPassSetStencilReference(long renderPass, int ref) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderSetStencilReference(rpe, ref);
        }
    }

    @Override
    public void renderPassDraw(long renderPass, int vertexCount, int instanceCount,
                               int firstVertex, int firstInstance) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderDraw(rpe, vertexCount, instanceCount, firstVertex, firstInstance);
        }
    }

    @Override
    public void renderPassDrawIndexed(long renderPass, int indexCount, int instanceCount,
                                      int firstIndex, int baseVertex, int firstInstance) {
        var rpe = get(renderPass);
        if (!rpe.equals(MemorySegment.NULL)) {
            wgpuRenderPassEncoderDrawIndexed(rpe, indexCount, instanceCount,
                    firstIndex, baseVertex, firstInstance);
        }
    }

    // ===== Helpers =====

    private static void configStencilFace(MemorySegment face, StencilFaceState state) {
        WGPUStencilFaceState.compare(face, state.compare());
        WGPUStencilFaceState.passOp(face, state.passOp());
        WGPUStencilFaceState.failOp(face, state.failOp());
        WGPUStencilFaceState.depthFailOp(face, state.depthFailOp());
    }
}

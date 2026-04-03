package dev.engine.bindings.wgpu;

import dev.engine.core.native_.NativeLibraryLoader;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * FFM bindings to wgpu-native v24+ (webgpu.h C API).
 *
 * <p>Loads {@code libwgpu_native.so} (or platform equivalent) and binds
 * the core WebGPU C functions needed for rendering. The library is resolved
 * via {@link NativeLibraryLoader} which can auto-download from GitHub releases.
 *
 * <p>The WebGPU C API uses opaque pointer handles for all objects. Callback-based
 * async operations (requestAdapter, requestDevice) are wrapped into synchronous
 * helpers that use {@link Arena}-scoped upcall stubs and
 * {@code wgpuInstanceWaitAny} to block until the future completes.
 *
 * <h3>Key v24 API notes</h3>
 * <ul>
 *   <li>{@code wgpuInstanceRequestAdapter} takes {@code WGPURequestAdapterCallbackInfo}
 *       <b>by value</b> and returns {@code WGPUFuture} by value.</li>
 *   <li>Callbacks receive {@code WGPUStringView} <b>by value</b> (ptr + size_t).</li>
 *   <li>Use {@code wgpuInstanceWaitAny} to wait for futures synchronously.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * WgpuNative.ensureLoaded();
 * var instance = WgpuNative.createInstance(MemorySegment.NULL);
 * var adapter = WgpuNative.requestAdapterSync(instance, MemorySegment.NULL);
 * var device = WgpuNative.requestDeviceSync(instance, adapter, MemorySegment.NULL);
 * var queue = WgpuNative.deviceGetQueue(device);
 * // ... render ...
 * WgpuNative.deviceRelease(device);
 * WgpuNative.adapterRelease(adapter);
 * WgpuNative.instanceRelease(instance);
 * }</pre>
 */
public final class WgpuNative {

    // ── WebGPU enum constants ──────────────────────────────────────────

    // WGPUBufferUsage flags (WGPUFlags = uint64_t)
    public static final long BUFFER_USAGE_MAP_READ      = 0x0001L;
    public static final long BUFFER_USAGE_MAP_WRITE     = 0x0002L;
    public static final long BUFFER_USAGE_COPY_SRC      = 0x0004L;
    public static final long BUFFER_USAGE_COPY_DST      = 0x0008L;
    public static final long BUFFER_USAGE_INDEX         = 0x0010L;
    public static final long BUFFER_USAGE_VERTEX        = 0x0020L;
    public static final long BUFFER_USAGE_UNIFORM       = 0x0040L;
    public static final long BUFFER_USAGE_STORAGE       = 0x0080L;
    public static final long BUFFER_USAGE_INDIRECT      = 0x0100L;
    public static final long BUFFER_USAGE_QUERY_RESOLVE = 0x0200L;

    // WGPUTextureUsage flags
    public static final long TEXTURE_USAGE_COPY_SRC          = 0x01L;
    public static final long TEXTURE_USAGE_COPY_DST          = 0x02L;
    public static final long TEXTURE_USAGE_TEXTURE_BINDING   = 0x04L;
    public static final long TEXTURE_USAGE_STORAGE_BINDING   = 0x08L;
    public static final long TEXTURE_USAGE_RENDER_ATTACHMENT = 0x10L;

    // WGPUTextureDimension
    public static final int TEXTURE_DIMENSION_1D = 1;
    public static final int TEXTURE_DIMENSION_2D = 2;
    public static final int TEXTURE_DIMENSION_3D = 3;

    // WGPUTextureFormat (common ones — v24 webgpu.h)
    public static final int TEXTURE_FORMAT_RGBA8_UNORM      = 0x12;
    public static final int TEXTURE_FORMAT_RGBA8_UNORM_SRGB = 0x13;
    public static final int TEXTURE_FORMAT_BGRA8_UNORM      = 0x17;
    public static final int TEXTURE_FORMAT_BGRA8_UNORM_SRGB = 0x18;
    public static final int TEXTURE_FORMAT_DEPTH24_PLUS           = 0x28;
    public static final int TEXTURE_FORMAT_DEPTH24_PLUS_STENCIL8  = 0x29;
    public static final int TEXTURE_FORMAT_DEPTH32_FLOAT          = 0x2A;

    // WGPUTextureViewDimension (v24: 0=Undefined, 1=1D, 2=2D, 3=2DArray, 4=Cube, 5=CubeArray, 6=3D)
    public static final int TEXTURE_VIEW_DIMENSION_2D   = 0x02;
    public static final int TEXTURE_VIEW_DIMENSION_CUBE = 0x04;

    // WGPUTextureAspect
    public static final int TEXTURE_ASPECT_ALL          = 1;
    public static final int TEXTURE_ASPECT_STENCIL_ONLY = 2;
    public static final int TEXTURE_ASPECT_DEPTH_ONLY   = 3;

    // WGPUVertexFormat (v24 webgpu.h — values are NOT sequential from 0)
    public static final int VERTEX_FORMAT_UINT8X2   = 0x02;
    public static final int VERTEX_FORMAT_UINT8X4   = 0x03;
    public static final int VERTEX_FORMAT_FLOAT16X2 = 0x1A;
    public static final int VERTEX_FORMAT_FLOAT16X4 = 0x1B;
    public static final int VERTEX_FORMAT_FLOAT32   = 0x1C;
    public static final int VERTEX_FORMAT_FLOAT32X2 = 0x1D;
    public static final int VERTEX_FORMAT_FLOAT32X3 = 0x1E;
    public static final int VERTEX_FORMAT_FLOAT32X4 = 0x1F;
    public static final int VERTEX_FORMAT_UINT32    = 0x20;
    public static final int VERTEX_FORMAT_UINT32X2  = 0x21;
    public static final int VERTEX_FORMAT_UINT32X3  = 0x22;
    public static final int VERTEX_FORMAT_UINT32X4  = 0x23;
    public static final int VERTEX_FORMAT_SINT32    = 0x24;
    public static final int VERTEX_FORMAT_SINT32X2  = 0x25;
    public static final int VERTEX_FORMAT_SINT32X3  = 0x26;
    public static final int VERTEX_FORMAT_SINT32X4  = 0x27;

    // WGPUVertexStepMode
    // 0=Undefined, 1=VertexBufferNotUsed, 2=Vertex, 3=Instance
    public static final int VERTEX_STEP_MODE_VERTEX   = 2;
    public static final int VERTEX_STEP_MODE_INSTANCE = 3;

    // WGPUPrimitiveTopology
    public static final int PRIMITIVE_TOPOLOGY_POINT_LIST     = 1;
    public static final int PRIMITIVE_TOPOLOGY_LINE_LIST      = 2;
    public static final int PRIMITIVE_TOPOLOGY_LINE_STRIP     = 3;
    public static final int PRIMITIVE_TOPOLOGY_TRIANGLE_LIST  = 4;
    public static final int PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP = 5;

    // WGPUIndexFormat
    public static final int INDEX_FORMAT_UINT16 = 1;
    public static final int INDEX_FORMAT_UINT32 = 2;

    // WGPUFrontFace
    public static final int FRONT_FACE_CCW = 1;
    public static final int FRONT_FACE_CW  = 2;

    // WGPUCullMode
    public static final int CULL_MODE_NONE  = 1;
    public static final int CULL_MODE_FRONT = 2;
    public static final int CULL_MODE_BACK  = 3;

    // WGPUCompareFunction
    public static final int COMPARE_FUNCTION_NEVER         = 1;
    public static final int COMPARE_FUNCTION_LESS          = 2;
    public static final int COMPARE_FUNCTION_EQUAL         = 3;
    public static final int COMPARE_FUNCTION_LESS_EQUAL    = 4;
    public static final int COMPARE_FUNCTION_GREATER       = 5;
    public static final int COMPARE_FUNCTION_NOT_EQUAL     = 6;
    public static final int COMPARE_FUNCTION_GREATER_EQUAL = 7;
    public static final int COMPARE_FUNCTION_ALWAYS        = 8;

    // WGPUBlendFactor
    public static final int BLEND_FACTOR_ZERO                  = 1;
    public static final int BLEND_FACTOR_ONE                   = 2;
    public static final int BLEND_FACTOR_SRC                   = 3;
    public static final int BLEND_FACTOR_ONE_MINUS_SRC         = 4;
    public static final int BLEND_FACTOR_SRC_ALPHA             = 5;
    public static final int BLEND_FACTOR_ONE_MINUS_SRC_ALPHA   = 6;
    public static final int BLEND_FACTOR_DST                   = 7;
    public static final int BLEND_FACTOR_ONE_MINUS_DST         = 8;
    public static final int BLEND_FACTOR_DST_ALPHA             = 9;
    public static final int BLEND_FACTOR_ONE_MINUS_DST_ALPHA   = 10;

    // WGPUBlendOperation
    public static final int BLEND_OPERATION_ADD              = 1;
    public static final int BLEND_OPERATION_SUBTRACT         = 2;
    public static final int BLEND_OPERATION_REVERSE_SUBTRACT = 3;
    public static final int BLEND_OPERATION_MIN              = 4;
    public static final int BLEND_OPERATION_MAX              = 5;

    // WGPUColorWriteMask
    public static final long COLOR_WRITE_MASK_NONE  = 0x00L;
    public static final long COLOR_WRITE_MASK_RED   = 0x01L;
    public static final long COLOR_WRITE_MASK_GREEN = 0x02L;
    public static final long COLOR_WRITE_MASK_BLUE  = 0x04L;
    public static final long COLOR_WRITE_MASK_ALPHA = 0x08L;
    public static final long COLOR_WRITE_MASK_ALL   = 0x0FL;

    // WGPULoadOp
    public static final int LOAD_OP_LOAD  = 1;
    public static final int LOAD_OP_CLEAR = 2;

    // WGPUStoreOp
    public static final int STORE_OP_STORE   = 1;
    public static final int STORE_OP_DISCARD = 2;

    // WGPUFilterMode
    public static final int FILTER_MODE_NEAREST = 1;
    public static final int FILTER_MODE_LINEAR  = 2;

    // WGPUMipmapFilterMode
    public static final int MIPMAP_FILTER_MODE_NEAREST = 1;
    public static final int MIPMAP_FILTER_MODE_LINEAR  = 2;

    // WGPUAddressMode
    public static final int ADDRESS_MODE_CLAMP_TO_EDGE = 1;
    public static final int ADDRESS_MODE_REPEAT        = 2;
    public static final int ADDRESS_MODE_MIRROR_REPEAT = 3;

    // WGPUShaderStage flags
    public static final long SHADER_STAGE_VERTEX   = 0x01L;
    public static final long SHADER_STAGE_FRAGMENT = 0x02L;
    public static final long SHADER_STAGE_COMPUTE  = 0x04L;

    // WGPUBufferBindingType (v24: 0=BindingNotUsed, 1=Undefined, 2=Uniform, 3=Storage, 4=ReadOnlyStorage)
    public static final int BUFFER_BINDING_TYPE_UNIFORM           = 0x02;
    public static final int BUFFER_BINDING_TYPE_STORAGE           = 0x03;
    public static final int BUFFER_BINDING_TYPE_READ_ONLY_STORAGE = 0x04;

    // WGPUSamplerBindingType (v24: 0=BindingNotUsed, 1=Undefined, 2=Filtering, 3=NonFiltering, 4=Comparison)
    public static final int SAMPLER_BINDING_TYPE_FILTERING     = 0x02;
    public static final int SAMPLER_BINDING_TYPE_NON_FILTERING = 0x03;
    public static final int SAMPLER_BINDING_TYPE_COMPARISON    = 0x04;

    // WGPUTextureSampleType (v24: 0=BindingNotUsed, 1=Undefined, 2=Float, 3=UnfilterableFloat, 4=Depth, 5=Sint, 6=Uint)
    public static final int TEXTURE_SAMPLE_TYPE_FLOAT              = 0x02;
    public static final int TEXTURE_SAMPLE_TYPE_UNFILTERABLE_FLOAT = 0x03;
    public static final int TEXTURE_SAMPLE_TYPE_DEPTH              = 0x04;
    public static final int TEXTURE_SAMPLE_TYPE_SINT               = 0x05;
    public static final int TEXTURE_SAMPLE_TYPE_UINT               = 0x06;

    // WGPUMapMode flags
    public static final long MAP_MODE_READ  = 0x01L;
    public static final long MAP_MODE_WRITE = 0x02L;

    // WGPUMapAsyncStatus
    public static final int MAP_ASYNC_STATUS_SUCCESS = 0x00000001;

    // WGPURequestAdapterStatus
    public static final int REQUEST_ADAPTER_STATUS_SUCCESS = 0x00000001;

    // WGPURequestDeviceStatus
    public static final int REQUEST_DEVICE_STATUS_SUCCESS = 0x00000001;

    // WGPUCallbackMode
    public static final int CALLBACK_MODE_WAIT_ANY_ONLY        = 0x00000001;
    public static final int CALLBACK_MODE_ALLOW_PROCESS_EVENTS = 0x00000002;
    public static final int CALLBACK_MODE_ALLOW_SPONTANEOUS    = 0x00000003;

    // WGPUWaitStatus
    public static final int WAIT_STATUS_SUCCESS           = 0x00000001;
    public static final int WAIT_STATUS_TIMED_OUT         = 0x00000002;
    public static final int WAIT_STATUS_UNSUPPORTED_TIMEOUT = 0x00000003;
    public static final int WAIT_STATUS_UNSUPPORTED_COUNT = 0x00000004;
    public static final int WAIT_STATUS_UNSUPPORTED_MIXED_SOURCES = 0x00000005;

    // WGPUSType for chained structs
    public static final int STYPE_SHADER_SOURCE_SPIRV = 0x00000001;
    public static final int STYPE_SHADER_SOURCE_WGSL  = 0x00000002;

    // ── Struct layouts (for by-value passing) ──────────────────────────

    /**
     * WGPUStringView: { char const* data; size_t length; }
     */
    public static final StructLayout STRING_VIEW_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("data"),
            ValueLayout.JAVA_LONG.withName("length")
    );

    /**
     * WGPUFuture: { uint64_t id; }
     */
    public static final StructLayout FUTURE_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("id")
    );

    /**
     * WGPUChainedStruct: { WGPUChainedStruct const* next; WGPUSType sType; }
     * Size: 8 (ptr) + 4 (enum) + 4 (padding) = 16
     */
    public static final StructLayout CHAINED_STRUCT_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("next"),
            ValueLayout.JAVA_INT.withName("sType"),
            MemoryLayout.paddingLayout(4)
    );

    /**
     * WGPURequestAdapterCallbackInfo: {
     *     WGPUChainedStruct const* nextInChain;
     *     WGPUCallbackMode mode; // uint32_t enum
     *     // 4 bytes padding
     *     WGPURequestAdapterCallback callback; // function pointer
     *     void* userdata1;
     *     void* userdata2;
     * }
     * Size: 8 + 4 + 4(pad) + 8 + 8 + 8 = 40
     */
    public static final StructLayout REQUEST_ADAPTER_CALLBACK_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("nextInChain"),
            ValueLayout.JAVA_INT.withName("mode"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("callback"),
            ValueLayout.ADDRESS.withName("userdata1"),
            ValueLayout.ADDRESS.withName("userdata2")
    );

    /**
     * WGPURequestDeviceCallbackInfo (same layout as adapter callback info).
     */
    public static final StructLayout REQUEST_DEVICE_CALLBACK_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("nextInChain"),
            ValueLayout.JAVA_INT.withName("mode"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("callback"),
            ValueLayout.ADDRESS.withName("userdata1"),
            ValueLayout.ADDRESS.withName("userdata2")
    );

    /**
     * WGPUFutureWaitInfo: { WGPUFuture future; WGPUBool completed; }
     * Size: 8 + 4 + 4(pad) = 16
     */
    public static final StructLayout FUTURE_WAIT_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("future_id"),
            ValueLayout.JAVA_INT.withName("completed"),
            MemoryLayout.paddingLayout(4)
    );

    /**
     * WGPUBufferMapCallbackInfo: same layout as request adapter/device callback info.
     * { nextInChain(8), mode(4), pad(4), callback(8), userdata1(8), userdata2(8) } = 40
     */
    public static final StructLayout BUFFER_MAP_CALLBACK_INFO_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("nextInChain"),
            ValueLayout.JAVA_INT.withName("mode"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("callback"),
            ValueLayout.ADDRESS.withName("userdata1"),
            ValueLayout.ADDRESS.withName("userdata2")
    );

    // ── Library loading ────────────────────────────────────────────────

    private static volatile SymbolLookup library;

    // ── Bound method handles ───────────────────────────────────────────

    // Instance
    private static MethodHandle h_wgpuCreateInstance;
    private static MethodHandle h_wgpuInstanceRelease;
    private static MethodHandle h_wgpuInstanceRequestAdapter;
    private static MethodHandle h_wgpuInstanceWaitAny;

    // Adapter
    private static MethodHandle h_wgpuAdapterRequestDevice;
    private static MethodHandle h_wgpuAdapterRelease;

    // Device
    private static MethodHandle h_wgpuDeviceGetQueue;
    private static MethodHandle h_wgpuDevicePoll;
    private static MethodHandle h_wgpuDeviceCreateShaderModule;
    private static MethodHandle h_wgpuDeviceCreateBuffer;
    private static MethodHandle h_wgpuDeviceCreateTexture;
    private static MethodHandle h_wgpuDeviceCreateSampler;
    private static MethodHandle h_wgpuDeviceCreateRenderPipeline;
    private static MethodHandle h_wgpuDeviceCreateComputePipeline;
    private static MethodHandle h_wgpuDeviceCreateCommandEncoder;
    private static MethodHandle h_wgpuDeviceCreateBindGroupLayout;
    private static MethodHandle h_wgpuDeviceCreateBindGroup;
    private static MethodHandle h_wgpuDeviceCreatePipelineLayout;
    private static MethodHandle h_wgpuDeviceRelease;

    // Queue
    private static MethodHandle h_wgpuQueueSubmit;
    private static MethodHandle h_wgpuQueueWriteBuffer;
    private static MethodHandle h_wgpuQueueWriteTexture;

    // Command encoder
    private static MethodHandle h_wgpuCommandEncoderBeginRenderPass;
    private static MethodHandle h_wgpuCommandEncoderBeginComputePass;
    private static MethodHandle h_wgpuCommandEncoderFinish;
    private static MethodHandle h_wgpuCommandEncoderCopyBufferToBuffer;
    private static MethodHandle h_wgpuCommandEncoderCopyTextureToBuffer;
    private static MethodHandle h_wgpuCommandEncoderRelease;

    // Render pass encoder
    private static MethodHandle h_wgpuRenderPassEncoderSetPipeline;
    private static MethodHandle h_wgpuRenderPassEncoderSetVertexBuffer;
    private static MethodHandle h_wgpuRenderPassEncoderSetIndexBuffer;
    private static MethodHandle h_wgpuRenderPassEncoderSetBindGroup;
    private static MethodHandle h_wgpuRenderPassEncoderDraw;
    private static MethodHandle h_wgpuRenderPassEncoderDrawIndexed;
    private static MethodHandle h_wgpuRenderPassEncoderSetViewport;
    private static MethodHandle h_wgpuRenderPassEncoderSetScissorRect;
    private static MethodHandle h_wgpuRenderPassEncoderEnd;
    private static MethodHandle h_wgpuRenderPassEncoderRelease;

    // Compute pass encoder
    private static MethodHandle h_wgpuComputePassEncoderSetPipeline;
    private static MethodHandle h_wgpuComputePassEncoderSetBindGroup;
    private static MethodHandle h_wgpuComputePassEncoderDispatchWorkgroups;
    private static MethodHandle h_wgpuComputePassEncoderEnd;
    private static MethodHandle h_wgpuComputePassEncoderRelease;

    // Buffer
    private static MethodHandle h_wgpuBufferMapAsync;
    private static MethodHandle h_wgpuBufferGetMappedRange;
    private static MethodHandle h_wgpuBufferUnmap;
    private static MethodHandle h_wgpuBufferRelease;
    private static MethodHandle h_wgpuBufferGetSize;

    // Texture
    private static MethodHandle h_wgpuTextureCreateView;
    private static MethodHandle h_wgpuTextureRelease;
    private static MethodHandle h_wgpuTextureViewRelease;

    // Surface
    private static MethodHandle h_wgpuInstanceCreateSurface;
    private static MethodHandle h_wgpuSurfaceGetCurrentTexture;
    private static MethodHandle h_wgpuSurfaceConfigure;
    private static MethodHandle h_wgpuSurfacePresent;
    private static MethodHandle h_wgpuSurfaceRelease;

    // Release functions
    private static MethodHandle h_wgpuShaderModuleRelease;
    private static MethodHandle h_wgpuRenderPipelineRelease;
    private static MethodHandle h_wgpuComputePipelineRelease;
    private static MethodHandle h_wgpuSamplerRelease;
    private static MethodHandle h_wgpuBindGroupRelease;
    private static MethodHandle h_wgpuBindGroupLayoutRelease;
    private static MethodHandle h_wgpuPipelineLayoutRelease;
    private static MethodHandle h_wgpuCommandBufferRelease;

    private WgpuNative() {}

    // ── Loading ────────────────────────────────────────────────────────

    /**
     * Returns true if the wgpu-native library is available (or can be downloaded).
     */
    public static boolean isAvailable() {
        try {
            ensureLoaded();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loads the wgpu-native library if not already loaded.
     *
     * @throws UnsatisfiedLinkError if the library cannot be found, downloaded, or loaded
     */
    public static synchronized void ensureLoaded() {
        if (library != null) return;

        Path libPath = findLibraryPath();
        if (libPath == null) {
            throw new UnsatisfiedLinkError(
                    "wgpu-native library not found. Install it or ensure network access for auto-download.");
        }

        var arena = Arena.global();
        var lookup = SymbolLookup.libraryLookup(libPath, arena);
        library = lookup;
        bindAllFunctions();
    }

    private static Path findLibraryPath() {
        var spec = WgpuSpec.spec();
        var libName = spec.libraries().getFirst();

        // 1. Check tools/lib/ relative to working dir (walking up)
        var dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            var candidate = dir.resolve("tools/lib/" + libName);
            if (Files.exists(candidate)) return candidate;
            dir = dir.getParent();
        }

        // 2. Try NativeLibraryLoader (cache or download)
        try {
            var loader = NativeLibraryLoader.defaultLoader();
            var result = loader.resolve(spec);
            if (result.isAvailable() && result.libraryPath() != null) {
                var candidate = result.libraryPath().resolve(libName);
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ── Instance ───────────────────────────────────────────────────────

    /**
     * Creates a WGPUInstance. Pass {@link MemorySegment#NULL} for default descriptor.
     *
     * @param descriptor pointer to WGPUInstanceDescriptor, or NULL
     * @return opaque WGPUInstance handle
     */
    public static MemorySegment createInstance(MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuCreateInstance.invokeExact(descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    /** Releases a WGPUInstance. */
    public static void instanceRelease(MemorySegment instance) {
        try {
            h_wgpuInstanceRelease.invokeExact(instance);
        } catch (Throwable t) { throw rethrow(t); }
    }

    /**
     * Synchronous adapter request.
     *
     * <p>Builds a {@code WGPURequestAdapterCallbackInfo} struct, passes it by value
     * to {@code wgpuInstanceRequestAdapter}, then uses {@code wgpuInstanceWaitAny}
     * to block until the future completes.
     *
     * @param instance WGPUInstance
     * @param options  pointer to WGPURequestAdapterOptions, or NULL
     * @return WGPUAdapter handle
     * @throws RuntimeException if adapter request fails
     */
    public static MemorySegment requestAdapterSync(MemorySegment instance, MemorySegment options) {
        try (var arena = Arena.ofConfined()) {
            // Slots to receive results in the callback
            var statusSlot = arena.allocate(ValueLayout.JAVA_INT);
            var adapterSlot = arena.allocate(ValueLayout.ADDRESS);

            // Create upcall stub for the callback:
            // void(WGPURequestAdapterStatus status, WGPUAdapter adapter,
            //      WGPUStringView message, void* userdata1, void* userdata2)
            //
            // WGPUStringView is passed by value as two fields: (ptr data, long length)
            var callbackDesc = FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT,   // status (enum, uint32)
                    ValueLayout.ADDRESS,    // adapter (opaque ptr)
                    STRING_VIEW_LAYOUT,     // message (by value: data + length)
                    ValueLayout.ADDRESS,    // userdata1
                    ValueLayout.ADDRESS     // userdata2
            );

            var callbackStub = Linker.nativeLinker().upcallStub(
                    MethodHandles.lookup().findStatic(
                            WgpuNative.class, "adapterCallback",
                            MethodType.methodType(void.class,
                                    int.class, MemorySegment.class,
                                    MemorySegment.class,     // WGPUStringView by value -> MemorySegment
                                    MemorySegment.class, MemorySegment.class)),
                    callbackDesc,
                    arena
            );

            // Build WGPURequestAdapterCallbackInfo by value.
            // AllowSpontaneous mode: wgpu-native fires the callback inline during
            // the requestAdapter call for synchronous adapter enumeration.
            var callbackInfo = arena.allocate(REQUEST_ADAPTER_CALLBACK_INFO_LAYOUT);
            callbackInfo.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);   // nextInChain
            callbackInfo.set(ValueLayout.JAVA_INT, 8, CALLBACK_MODE_ALLOW_SPONTANEOUS); // mode
            // padding at 12
            callbackInfo.set(ValueLayout.ADDRESS, 16, callbackStub);        // callback
            callbackInfo.set(ValueLayout.ADDRESS, 24, statusSlot);          // userdata1
            callbackInfo.set(ValueLayout.ADDRESS, 32, adapterSlot);         // userdata2

            // Call wgpuInstanceRequestAdapter — returns WGPUFuture by value.
            // With AllowSpontaneous, the callback fires during this call.
            try {
                var ignored = (MemorySegment) h_wgpuInstanceRequestAdapter.invokeExact(
                        (SegmentAllocator) arena,
                        instance, options, callbackInfo);
            } catch (Throwable t) { throw rethrow(t); }

            int status = statusSlot.get(ValueLayout.JAVA_INT, 0);
            var adapter = adapterSlot.get(ValueLayout.ADDRESS, 0);

            if (status != REQUEST_ADAPTER_STATUS_SUCCESS || adapter.equals(MemorySegment.NULL)) {
                throw new RuntimeException("wgpuInstanceRequestAdapter failed with status " + status);
            }
            return adapter;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create upcall stub", e);
        }
    }

    /**
     * Adapter callback — invoked by wgpu-native when adapter request completes.
     * userdata1 = pointer to int (status slot), userdata2 = pointer to ptr (adapter slot).
     */
    @SuppressWarnings("unused")
    public static void adapterCallback(int status, MemorySegment adapter,
                                        MemorySegment message,
                                        MemorySegment userdata1, MemorySegment userdata2) {
        userdata1.reinterpret(ValueLayout.JAVA_INT.byteSize())
                .set(ValueLayout.JAVA_INT, 0, status);
        userdata2.reinterpret(ValueLayout.ADDRESS.byteSize())
                .set(ValueLayout.ADDRESS, 0, adapter);
    }

    // ── Adapter ────────────────────────────────────────────────────────

    /**
     * Synchronous device request.
     *
     * @param instance WGPUInstance (needed for WaitAny)
     * @param adapter WGPUAdapter
     * @param descriptor pointer to WGPUDeviceDescriptor, or NULL
     * @return WGPUDevice handle
     */
    public static MemorySegment requestDeviceSync(MemorySegment instance,
                                                   MemorySegment adapter,
                                                   MemorySegment descriptor) {
        try (var arena = Arena.ofConfined()) {
            var statusSlot = arena.allocate(ValueLayout.JAVA_INT);
            var deviceSlot = arena.allocate(ValueLayout.ADDRESS);

            var callbackDesc = FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT,   // status
                    ValueLayout.ADDRESS,    // device
                    STRING_VIEW_LAYOUT,     // message (by value)
                    ValueLayout.ADDRESS,    // userdata1
                    ValueLayout.ADDRESS     // userdata2
            );

            var callbackStub = Linker.nativeLinker().upcallStub(
                    MethodHandles.lookup().findStatic(
                            WgpuNative.class, "deviceCallback",
                            MethodType.methodType(void.class,
                                    int.class, MemorySegment.class,
                                    MemorySegment.class,
                                    MemorySegment.class, MemorySegment.class)),
                    callbackDesc,
                    arena
            );

            var callbackInfo = arena.allocate(REQUEST_DEVICE_CALLBACK_INFO_LAYOUT);
            callbackInfo.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
            callbackInfo.set(ValueLayout.JAVA_INT, 8, CALLBACK_MODE_ALLOW_SPONTANEOUS);
            callbackInfo.set(ValueLayout.ADDRESS, 16, callbackStub);
            callbackInfo.set(ValueLayout.ADDRESS, 24, statusSlot);
            callbackInfo.set(ValueLayout.ADDRESS, 32, deviceSlot);

            // With AllowSpontaneous, the callback fires during the call
            try {
                var ignored = (MemorySegment) h_wgpuAdapterRequestDevice.invokeExact(
                        (SegmentAllocator) arena, adapter, descriptor, callbackInfo);
            } catch (Throwable t) { throw rethrow(t); }

            int status = statusSlot.get(ValueLayout.JAVA_INT, 0);
            var device = deviceSlot.get(ValueLayout.ADDRESS, 0);

            if (status != REQUEST_DEVICE_STATUS_SUCCESS || device.equals(MemorySegment.NULL)) {
                throw new RuntimeException("wgpuAdapterRequestDevice failed with status " + status);
            }
            return device;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create upcall stub", e);
        }
    }

    /** Device callback. */
    @SuppressWarnings("unused")
    public static void deviceCallback(int status, MemorySegment device,
                                       MemorySegment message,
                                       MemorySegment userdata1, MemorySegment userdata2) {
        userdata1.reinterpret(ValueLayout.JAVA_INT.byteSize())
                .set(ValueLayout.JAVA_INT, 0, status);
        userdata2.reinterpret(ValueLayout.ADDRESS.byteSize())
                .set(ValueLayout.ADDRESS, 0, device);
    }

    public static void adapterRelease(MemorySegment adapter) {
        try {
            h_wgpuAdapterRelease.invokeExact(adapter);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Device ─────────────────────────────────────────────────────────

    public static MemorySegment deviceGetQueue(MemorySegment device) {
        try {
            return (MemorySegment) h_wgpuDeviceGetQueue.invokeExact(device);
        } catch (Throwable t) { throw rethrow(t); }
    }

    /**
     * Creates a shader module from a WGSL source string.
     *
     * <p>Builds the required WGPUShaderModuleDescriptor with a chained
     * WGPUShaderSourceWGSL struct internally.
     *
     * @param device the WGPUDevice
     * @param wgslSource the WGSL shader source code
     * @param arena the arena for allocating descriptor memory
     * @return WGPUShaderModule handle
     */
    public static MemorySegment deviceCreateShaderModuleWGSL(MemorySegment device,
                                                              String wgslSource,
                                                              Arena arena) {
        // Layout of WGPUShaderSourceWGSL:
        //   WGPUChainedStruct chain { next(8), sType(4), pad(4) }  = 16
        //   WGPUStringView code { data(8), length(8) }             = 16
        // Total: 32 bytes
        var codeBytes = arena.allocateFrom(wgslSource);
        var wgslStruct = arena.allocate(32, 8);
        wgslStruct.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);           // chain.next
        wgslStruct.set(ValueLayout.JAVA_INT, 8, STYPE_SHADER_SOURCE_WGSL);   // chain.sType
        // padding at 12
        wgslStruct.set(ValueLayout.ADDRESS, 16, codeBytes);                   // code.data
        wgslStruct.set(ValueLayout.JAVA_LONG, 24, (long) wgslSource.length()); // code.length

        // Layout of WGPUShaderModuleDescriptor:
        //   nextInChain (8) + label.data (8) + label.length (8) = 24
        var descriptor = arena.allocate(24, 8);
        descriptor.set(ValueLayout.ADDRESS, 0, wgslStruct);           // nextInChain -> wgslStruct
        descriptor.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);   // label.data
        descriptor.set(ValueLayout.JAVA_LONG, 16, 0L);                // label.length

        try {
            return (MemorySegment) h_wgpuDeviceCreateShaderModule.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    /**
     * Creates a GPU buffer.
     *
     * @param device the WGPUDevice
     * @param size buffer size in bytes
     * @param usage WGPUBufferUsage flags
     * @param mappedAtCreation whether to map the buffer at creation
     * @param arena the arena for descriptor allocation
     * @return WGPUBuffer handle
     */
    public static MemorySegment deviceCreateBuffer(MemorySegment device,
                                                    long size, long usage,
                                                    boolean mappedAtCreation,
                                                    Arena arena) {
        // WGPUBufferDescriptor layout:
        //   nextInChain (8)
        //   label { data(8), length(8) }
        //   usage (8, uint64 WGPUFlags)
        //   size (8, uint64)
        //   mappedAtCreation (4, uint32 WGPUBool)
        //   pad (4)
        // Total: 48
        var desc = arena.allocate(48, 8);
        desc.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);   // nextInChain
        desc.set(ValueLayout.ADDRESS, 8, MemorySegment.NULL);   // label.data
        desc.set(ValueLayout.JAVA_LONG, 16, 0L);                // label.length
        desc.set(ValueLayout.JAVA_LONG, 24, usage);             // usage
        desc.set(ValueLayout.JAVA_LONG, 32, size);              // size
        desc.set(ValueLayout.JAVA_INT, 40, mappedAtCreation ? 1 : 0); // mappedAtCreation

        try {
            return (MemorySegment) h_wgpuDeviceCreateBuffer.invokeExact(device, desc);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateTexture(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateTexture.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateSampler(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateSampler.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateRenderPipeline(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateRenderPipeline.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateComputePipeline(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateComputePipeline.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateCommandEncoder(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateCommandEncoder.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateBindGroupLayout(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateBindGroupLayout.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreateBindGroup(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreateBindGroup.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment deviceCreatePipelineLayout(MemorySegment device, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuDeviceCreatePipelineLayout.invokeExact(device, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    /**
     * Polls the device for completed work. wgpu-native extension (not standard WebGPU).
     *
     * @param device the WGPUDevice
     * @param wait if true (1), blocks until all submitted work is done
     * @return true if the queue is empty (all work completed)
     */
    public static boolean devicePoll(MemorySegment device, boolean wait) {
        try {
            int result = (int) h_wgpuDevicePoll.invokeExact(device, wait ? 1 : 0, MemorySegment.NULL);
            return result != 0;
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void deviceRelease(MemorySegment device) {
        try {
            h_wgpuDeviceRelease.invokeExact(device);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Queue ──────────────────────────────────────────────────────────

    public static void queueSubmit(MemorySegment queue, long commandCount, MemorySegment commandBuffers) {
        try {
            h_wgpuQueueSubmit.invokeExact(queue, commandCount, commandBuffers);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void queueWriteBuffer(MemorySegment queue, MemorySegment buffer,
                                         long bufferOffset, MemorySegment data, long size) {
        try {
            h_wgpuQueueWriteBuffer.invokeExact(queue, buffer, bufferOffset, data, size);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void queueWriteTexture(MemorySegment queue,
                                          MemorySegment destination,
                                          MemorySegment data, long dataSize,
                                          MemorySegment dataLayout,
                                          MemorySegment writeSize) {
        try {
            h_wgpuQueueWriteTexture.invokeExact(queue, destination, data, dataSize, dataLayout, writeSize);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Command Encoder ────────────────────────────────────────────────

    public static MemorySegment commandEncoderBeginRenderPass(MemorySegment encoder, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuCommandEncoderBeginRenderPass.invokeExact(encoder, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment commandEncoderBeginComputePass(MemorySegment encoder, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuCommandEncoderBeginComputePass.invokeExact(encoder, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static MemorySegment commandEncoderFinish(MemorySegment encoder, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuCommandEncoderFinish.invokeExact(encoder, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void commandEncoderCopyBufferToBuffer(MemorySegment encoder,
                                                         MemorySegment source, long sourceOffset,
                                                         MemorySegment destination, long destinationOffset,
                                                         long size) {
        try {
            h_wgpuCommandEncoderCopyBufferToBuffer.invokeExact(
                    encoder, source, sourceOffset, destination, destinationOffset, size);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void commandEncoderCopyTextureToBuffer(MemorySegment encoder,
                                                          MemorySegment source,
                                                          MemorySegment destination,
                                                          MemorySegment copySize) {
        try {
            h_wgpuCommandEncoderCopyTextureToBuffer.invokeExact(encoder, source, destination, copySize);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void commandEncoderRelease(MemorySegment encoder) {
        try {
            h_wgpuCommandEncoderRelease.invokeExact(encoder);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Render Pass Encoder ────────────────────────────────────────────

    public static void renderPassEncoderSetPipeline(MemorySegment encoder, MemorySegment pipeline) {
        try {
            h_wgpuRenderPassEncoderSetPipeline.invokeExact(encoder, pipeline);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderSetVertexBuffer(MemorySegment encoder,
                                                         int slot, MemorySegment buffer,
                                                         long offset, long size) {
        try {
            h_wgpuRenderPassEncoderSetVertexBuffer.invokeExact(encoder, slot, buffer, offset, size);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderSetIndexBuffer(MemorySegment encoder,
                                                        MemorySegment buffer,
                                                        int format, long offset, long size) {
        try {
            h_wgpuRenderPassEncoderSetIndexBuffer.invokeExact(encoder, buffer, format, offset, size);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderSetBindGroup(MemorySegment encoder,
                                                      int groupIndex, MemorySegment bindGroup,
                                                      long dynamicOffsetCount,
                                                      MemorySegment dynamicOffsets) {
        try {
            h_wgpuRenderPassEncoderSetBindGroup.invokeExact(
                    encoder, groupIndex, bindGroup, dynamicOffsetCount, dynamicOffsets);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderDraw(MemorySegment encoder,
                                              int vertexCount, int instanceCount,
                                              int firstVertex, int firstInstance) {
        try {
            h_wgpuRenderPassEncoderDraw.invokeExact(
                    encoder, vertexCount, instanceCount, firstVertex, firstInstance);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderDrawIndexed(MemorySegment encoder,
                                                     int indexCount, int instanceCount,
                                                     int firstIndex, int baseVertex,
                                                     int firstInstance) {
        try {
            h_wgpuRenderPassEncoderDrawIndexed.invokeExact(
                    encoder, indexCount, instanceCount, firstIndex, baseVertex, firstInstance);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderSetViewport(MemorySegment encoder,
                                                     float x, float y, float w, float h,
                                                     float minDepth, float maxDepth) {
        try {
            h_wgpuRenderPassEncoderSetViewport.invokeExact(encoder, x, y, w, h, minDepth, maxDepth);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderSetScissorRect(MemorySegment encoder,
                                                        int x, int y, int w, int h) {
        try {
            h_wgpuRenderPassEncoderSetScissorRect.invokeExact(encoder, x, y, w, h);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderEnd(MemorySegment encoder) {
        try {
            h_wgpuRenderPassEncoderEnd.invokeExact(encoder);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPassEncoderRelease(MemorySegment encoder) {
        try {
            h_wgpuRenderPassEncoderRelease.invokeExact(encoder);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Compute Pass Encoder ───────────────────────────────────────────

    public static void computePassEncoderSetPipeline(MemorySegment encoder, MemorySegment pipeline) {
        try {
            h_wgpuComputePassEncoderSetPipeline.invokeExact(encoder, pipeline);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void computePassEncoderSetBindGroup(MemorySegment encoder,
                                                       int groupIndex, MemorySegment bindGroup,
                                                       long dynamicOffsetCount,
                                                       MemorySegment dynamicOffsets) {
        try {
            h_wgpuComputePassEncoderSetBindGroup.invokeExact(
                    encoder, groupIndex, bindGroup, dynamicOffsetCount, dynamicOffsets);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void computePassEncoderDispatchWorkgroups(MemorySegment encoder,
                                                             int x, int y, int z) {
        try {
            h_wgpuComputePassEncoderDispatchWorkgroups.invokeExact(encoder, x, y, z);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void computePassEncoderEnd(MemorySegment encoder) {
        try {
            h_wgpuComputePassEncoderEnd.invokeExact(encoder);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void computePassEncoderRelease(MemorySegment encoder) {
        try {
            h_wgpuComputePassEncoderRelease.invokeExact(encoder);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Buffer ─────────────────────────────────────────────────────────

    public static MemorySegment bufferGetMappedRange(MemorySegment buffer, long offset, long size) {
        try {
            return (MemorySegment) h_wgpuBufferGetMappedRange.invokeExact(buffer, offset, size);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void bufferUnmap(MemorySegment buffer) {
        try {
            h_wgpuBufferUnmap.invokeExact(buffer);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void bufferRelease(MemorySegment buffer) {
        try {
            h_wgpuBufferRelease.invokeExact(buffer);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static long bufferGetSize(MemorySegment buffer) {
        try {
            return (long) h_wgpuBufferGetSize.invokeExact(buffer);
        } catch (Throwable t) { throw rethrow(t); }
    }

    /**
     * Synchronous buffer mapping. Maps a buffer for reading or writing
     * using a callback + WaitAny pattern (same as adapter/device request).
     *
     * @param instance the WGPUInstance (needed for WaitAny)
     * @param buffer the WGPUBuffer to map
     * @param mode MAP_MODE_READ or MAP_MODE_WRITE flags
     * @param offset byte offset
     * @param size byte size
     */
    /**
     * Synchronous buffer mapping. Maps a buffer for reading or writing.
     * Uses {@code wgpuDevicePoll(wait=true)} to wait for the map to complete.
     *
     * @param device the WGPUDevice (needed for polling)
     * @param buffer the WGPUBuffer to map
     * @param mode MAP_MODE_READ or MAP_MODE_WRITE flags
     * @param offset byte offset
     * @param size byte size
     */
    public static void bufferMapSync(MemorySegment device, MemorySegment buffer,
                                      long mode, long offset, long size) {
        try (var arena = Arena.ofConfined()) {
            var statusSlot = arena.allocate(ValueLayout.JAVA_INT);

            // callback: void(WGPUMapAsyncStatus status, WGPUStringView message, void* userdata1, void* userdata2)
            var callbackDesc = FunctionDescriptor.ofVoid(
                    ValueLayout.JAVA_INT,   // status
                    STRING_VIEW_LAYOUT,     // message (by value)
                    ValueLayout.ADDRESS,    // userdata1
                    ValueLayout.ADDRESS     // userdata2
            );

            var callbackStub = Linker.nativeLinker().upcallStub(
                    MethodHandles.lookup().findStatic(
                            WgpuNative.class, "bufferMapCallback",
                            MethodType.methodType(void.class,
                                    int.class, MemorySegment.class,
                                    MemorySegment.class, MemorySegment.class)),
                    callbackDesc,
                    arena
            );

            // Use AllowSpontaneous so wgpu-native fires the callback inline.
            // wgpuInstanceWaitAny is not implemented in wgpu-native v24,
            // so we cannot use WaitAnyOnly mode.
            var callbackInfo = arena.allocate(BUFFER_MAP_CALLBACK_INFO_LAYOUT);
            callbackInfo.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);   // nextInChain
            callbackInfo.set(ValueLayout.JAVA_INT, 8, CALLBACK_MODE_ALLOW_SPONTANEOUS);
            callbackInfo.set(ValueLayout.ADDRESS, 16, callbackStub);
            callbackInfo.set(ValueLayout.ADDRESS, 24, statusSlot);
            callbackInfo.set(ValueLayout.ADDRESS, 32, MemorySegment.NULL);

            try {
                var ignored = (MemorySegment) h_wgpuBufferMapAsync.invokeExact(
                        (SegmentAllocator) arena, buffer, mode, offset, size, callbackInfo);

                // With AllowSpontaneous, wgpu-native may fire the callback inline.
                // If not, use wgpuDevicePoll(wait=true) to block until GPU work
                // completes and the callback fires.
                int status = statusSlot.get(ValueLayout.JAVA_INT, 0);
                if (status == 0) {
                    // Poll the device to wait for the map operation to complete
                    devicePoll(device, true);
                    status = statusSlot.get(ValueLayout.JAVA_INT, 0);
                }

                if (status != MAP_ASYNC_STATUS_SUCCESS) {
                    throw new RuntimeException("wgpuBufferMapAsync failed with status " + status);
                }
            } catch (Throwable t) { throw rethrow(t); }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("Failed to create upcall stub for bufferMapAsync", e);
        }
    }

    /** Buffer map callback. */
    @SuppressWarnings("unused")
    public static void bufferMapCallback(int status, MemorySegment message,
                                          MemorySegment userdata1, MemorySegment userdata2) {
        userdata1.reinterpret(ValueLayout.JAVA_INT.byteSize())
                .set(ValueLayout.JAVA_INT, 0, status);
    }

    /**
     * wgpuInstanceWaitAny - waits for futures to complete.
     */
    public static int instanceWaitAny(MemorySegment instance, long futureCount,
                                       MemorySegment futures, long timeoutNS) {
        try {
            return (int) h_wgpuInstanceWaitAny.invokeExact(instance, futureCount, futures, timeoutNS);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Texture ────────────────────────────────────────────────────────

    public static MemorySegment textureCreateView(MemorySegment texture, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuTextureCreateView.invokeExact(texture, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void textureRelease(MemorySegment texture) {
        try {
            h_wgpuTextureRelease.invokeExact(texture);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void textureViewRelease(MemorySegment textureView) {
        try {
            h_wgpuTextureViewRelease.invokeExact(textureView);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Surface ────────────────────────────────────────────────────────

    public static MemorySegment instanceCreateSurface(MemorySegment instance, MemorySegment descriptor) {
        try {
            return (MemorySegment) h_wgpuInstanceCreateSurface.invokeExact(instance, descriptor);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void surfaceGetCurrentTexture(MemorySegment surface, MemorySegment surfaceTexture) {
        try {
            h_wgpuSurfaceGetCurrentTexture.invokeExact(surface, surfaceTexture);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void surfaceConfigure(MemorySegment surface, MemorySegment config) {
        try {
            h_wgpuSurfaceConfigure.invokeExact(surface, config);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void surfacePresent(MemorySegment surface) {
        try {
            h_wgpuSurfacePresent.invokeExact(surface);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void surfaceRelease(MemorySegment surface) {
        try {
            h_wgpuSurfaceRelease.invokeExact(surface);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Release functions ──────────────────────────────────────────────

    public static void shaderModuleRelease(MemorySegment module) {
        try {
            h_wgpuShaderModuleRelease.invokeExact(module);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void renderPipelineRelease(MemorySegment pipeline) {
        try {
            h_wgpuRenderPipelineRelease.invokeExact(pipeline);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void computePipelineRelease(MemorySegment pipeline) {
        try {
            h_wgpuComputePipelineRelease.invokeExact(pipeline);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void samplerRelease(MemorySegment sampler) {
        try {
            h_wgpuSamplerRelease.invokeExact(sampler);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void bindGroupRelease(MemorySegment bindGroup) {
        try {
            h_wgpuBindGroupRelease.invokeExact(bindGroup);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void bindGroupLayoutRelease(MemorySegment layout) {
        try {
            h_wgpuBindGroupLayoutRelease.invokeExact(layout);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void pipelineLayoutRelease(MemorySegment layout) {
        try {
            h_wgpuPipelineLayoutRelease.invokeExact(layout);
        } catch (Throwable t) { throw rethrow(t); }
    }

    public static void commandBufferRelease(MemorySegment commandBuffer) {
        try {
            h_wgpuCommandBufferRelease.invokeExact(commandBuffer);
        } catch (Throwable t) { throw rethrow(t); }
    }

    // ── Internal: function binding ─────────────────────────────────────

    private static void bindAllFunctions() {
        var linker = Linker.nativeLinker();

        // Common descriptors
        var voidPtr = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);
        var ptrToPtr = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS);
        var ptrPtrToPtr = FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS);

        // Instance
        h_wgpuCreateInstance = bind(linker, "wgpuCreateInstance", ptrToPtr);
        h_wgpuInstanceRelease = bind(linker, "wgpuInstanceRelease", voidPtr);

        // wgpuInstanceRequestAdapter(WGPUInstance, WGPURequestAdapterOptions*, WGPURequestAdapterCallbackInfo)
        //   → WGPUFuture (by value)
        // The callbackInfo is passed by value (struct), and WGPUFuture returned by value
        h_wgpuInstanceRequestAdapter = bind(linker, "wgpuInstanceRequestAdapter",
                FunctionDescriptor.of(FUTURE_LAYOUT,
                        ValueLayout.ADDRESS,                        // instance
                        ValueLayout.ADDRESS,                        // options*
                        REQUEST_ADAPTER_CALLBACK_INFO_LAYOUT));     // callbackInfo (by value)

        // wgpuInstanceWaitAny(WGPUInstance, size_t futureCount, WGPUFutureWaitInfo*, uint64_t timeoutNS) → WGPUWaitStatus
        h_wgpuInstanceWaitAny = bind(linker, "wgpuInstanceWaitAny",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,     // instance
                        ValueLayout.JAVA_LONG,   // futureCount (size_t)
                        ValueLayout.ADDRESS,     // futures*
                        ValueLayout.JAVA_LONG)); // timeoutNS (uint64)

        // Adapter
        h_wgpuAdapterRequestDevice = bind(linker, "wgpuAdapterRequestDevice",
                FunctionDescriptor.of(FUTURE_LAYOUT,
                        ValueLayout.ADDRESS,                       // adapter
                        ValueLayout.ADDRESS,                       // descriptor*
                        REQUEST_DEVICE_CALLBACK_INFO_LAYOUT));     // callbackInfo (by value)
        h_wgpuAdapterRelease = bind(linker, "wgpuAdapterRelease", voidPtr);

        // Device
        h_wgpuDeviceGetQueue = bind(linker, "wgpuDeviceGetQueue", ptrToPtr);
        h_wgpuDevicePoll = bind(linker, "wgpuDevicePoll",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        h_wgpuDeviceCreateShaderModule = bind(linker, "wgpuDeviceCreateShaderModule", ptrPtrToPtr);
        h_wgpuDeviceCreateBuffer = bind(linker, "wgpuDeviceCreateBuffer", ptrPtrToPtr);
        h_wgpuDeviceCreateTexture = bind(linker, "wgpuDeviceCreateTexture", ptrPtrToPtr);
        h_wgpuDeviceCreateSampler = bind(linker, "wgpuDeviceCreateSampler", ptrPtrToPtr);
        h_wgpuDeviceCreateRenderPipeline = bind(linker, "wgpuDeviceCreateRenderPipeline", ptrPtrToPtr);
        h_wgpuDeviceCreateComputePipeline = bind(linker, "wgpuDeviceCreateComputePipeline", ptrPtrToPtr);
        h_wgpuDeviceCreateCommandEncoder = bind(linker, "wgpuDeviceCreateCommandEncoder", ptrPtrToPtr);
        h_wgpuDeviceCreateBindGroupLayout = bind(linker, "wgpuDeviceCreateBindGroupLayout", ptrPtrToPtr);
        h_wgpuDeviceCreateBindGroup = bind(linker, "wgpuDeviceCreateBindGroup", ptrPtrToPtr);
        h_wgpuDeviceCreatePipelineLayout = bind(linker, "wgpuDeviceCreatePipelineLayout", ptrPtrToPtr);
        h_wgpuDeviceRelease = bind(linker, "wgpuDeviceRelease", voidPtr);

        // Queue
        h_wgpuQueueSubmit = bind(linker, "wgpuQueueSubmit",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        h_wgpuQueueWriteBuffer = bind(linker, "wgpuQueueWriteBuffer",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        h_wgpuQueueWriteTexture = bind(linker, "wgpuQueueWriteTexture",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        // Command encoder
        h_wgpuCommandEncoderBeginRenderPass = bind(linker, "wgpuCommandEncoderBeginRenderPass", ptrPtrToPtr);
        h_wgpuCommandEncoderBeginComputePass = bind(linker, "wgpuCommandEncoderBeginComputePass", ptrPtrToPtr);
        h_wgpuCommandEncoderFinish = bind(linker, "wgpuCommandEncoderFinish", ptrPtrToPtr);
        h_wgpuCommandEncoderCopyBufferToBuffer = bind(linker, "wgpuCommandEncoderCopyBufferToBuffer",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        h_wgpuCommandEncoderCopyTextureToBuffer = bind(linker, "wgpuCommandEncoderCopyTextureToBuffer",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        h_wgpuCommandEncoderRelease = bind(linker, "wgpuCommandEncoderRelease", voidPtr);

        // Render pass encoder
        h_wgpuRenderPassEncoderSetPipeline = bind(linker, "wgpuRenderPassEncoderSetPipeline",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        h_wgpuRenderPassEncoderSetVertexBuffer = bind(linker, "wgpuRenderPassEncoderSetVertexBuffer",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        h_wgpuRenderPassEncoderSetIndexBuffer = bind(linker, "wgpuRenderPassEncoderSetIndexBuffer",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        h_wgpuRenderPassEncoderSetBindGroup = bind(linker, "wgpuRenderPassEncoderSetBindGroup",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        h_wgpuRenderPassEncoderDraw = bind(linker, "wgpuRenderPassEncoderDraw",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        h_wgpuRenderPassEncoderDrawIndexed = bind(linker, "wgpuRenderPassEncoderDrawIndexed",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        h_wgpuRenderPassEncoderSetViewport = bind(linker, "wgpuRenderPassEncoderSetViewport",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT,
                        ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT));
        h_wgpuRenderPassEncoderSetScissorRect = bind(linker, "wgpuRenderPassEncoderSetScissorRect",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        h_wgpuRenderPassEncoderEnd = bind(linker, "wgpuRenderPassEncoderEnd", voidPtr);
        h_wgpuRenderPassEncoderRelease = bind(linker, "wgpuRenderPassEncoderRelease", voidPtr);

        // Compute pass encoder
        h_wgpuComputePassEncoderSetPipeline = bind(linker, "wgpuComputePassEncoderSetPipeline",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        h_wgpuComputePassEncoderSetBindGroup = bind(linker, "wgpuComputePassEncoderSetBindGroup",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        h_wgpuComputePassEncoderDispatchWorkgroups = bind(linker, "wgpuComputePassEncoderDispatchWorkgroups",
                FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        h_wgpuComputePassEncoderEnd = bind(linker, "wgpuComputePassEncoderEnd", voidPtr);
        h_wgpuComputePassEncoderRelease = bind(linker, "wgpuComputePassEncoderRelease", voidPtr);

        // Buffer
        // wgpuBufferMapAsync(WGPUBuffer, WGPUMapMode mode, size_t offset, size_t size, WGPUBufferMapCallbackInfo) -> WGPUFuture
        h_wgpuBufferMapAsync = bind(linker, "wgpuBufferMapAsync",
                FunctionDescriptor.of(FUTURE_LAYOUT,
                        ValueLayout.ADDRESS,       // buffer
                        ValueLayout.JAVA_LONG,     // mode (WGPUMapModeFlags = uint64)
                        ValueLayout.JAVA_LONG,     // offset
                        ValueLayout.JAVA_LONG,     // size
                        BUFFER_MAP_CALLBACK_INFO_LAYOUT)); // callbackInfo (by value)
        h_wgpuBufferGetMappedRange = bind(linker, "wgpuBufferGetMappedRange",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
        h_wgpuBufferUnmap = bind(linker, "wgpuBufferUnmap", voidPtr);
        h_wgpuBufferRelease = bind(linker, "wgpuBufferRelease", voidPtr);
        h_wgpuBufferGetSize = bind(linker, "wgpuBufferGetSize",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

        // Texture
        h_wgpuTextureCreateView = bind(linker, "wgpuTextureCreateView", ptrPtrToPtr);
        h_wgpuTextureRelease = bind(linker, "wgpuTextureRelease", voidPtr);
        h_wgpuTextureViewRelease = bind(linker, "wgpuTextureViewRelease", voidPtr);

        // Surface
        h_wgpuInstanceCreateSurface = bind(linker, "wgpuInstanceCreateSurface", ptrPtrToPtr);
        h_wgpuSurfaceGetCurrentTexture = bind(linker, "wgpuSurfaceGetCurrentTexture",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        h_wgpuSurfaceConfigure = bind(linker, "wgpuSurfaceConfigure",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        h_wgpuSurfacePresent = bind(linker, "wgpuSurfacePresent", voidPtr);
        h_wgpuSurfaceRelease = bind(linker, "wgpuSurfaceRelease", voidPtr);

        // Release functions
        h_wgpuShaderModuleRelease = bind(linker, "wgpuShaderModuleRelease", voidPtr);
        h_wgpuRenderPipelineRelease = bind(linker, "wgpuRenderPipelineRelease", voidPtr);
        h_wgpuComputePipelineRelease = bind(linker, "wgpuComputePipelineRelease", voidPtr);
        h_wgpuSamplerRelease = bind(linker, "wgpuSamplerRelease", voidPtr);
        h_wgpuBindGroupRelease = bind(linker, "wgpuBindGroupRelease", voidPtr);
        h_wgpuBindGroupLayoutRelease = bind(linker, "wgpuBindGroupLayoutRelease", voidPtr);
        h_wgpuPipelineLayoutRelease = bind(linker, "wgpuPipelineLayoutRelease", voidPtr);
        h_wgpuCommandBufferRelease = bind(linker, "wgpuCommandBufferRelease", voidPtr);
    }

    private static MethodHandle bind(Linker linker, String name, FunctionDescriptor desc) {
        var symbol = library.find(name).orElseThrow(() ->
                new UnsatisfiedLinkError("wgpu symbol not found: " + name));
        return linker.downcallHandle(symbol, desc);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }
}

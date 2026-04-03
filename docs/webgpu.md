# WebGPU / wgpu-native Integration Notes

## Library Loading

wgpu-native is auto-downloaded via `NativeLibraryLoader` using `WgpuSpec.java`.
The library caches to `~/.engine/natives/wgpu-native/<version>/<platform>/`.

Release archives from https://github.com/gfx-rs/wgpu-native/releases contain:
- `lib/libwgpu_native.so` (Linux), `.dylib` (macOS), `.dll` (Windows)
- `include/webgpu/webgpu.h` — the authoritative API reference
- `include/webgpu/wgpu.h` — wgpu-native extensions

The `NativeLibraryLoader.flattenToTarget()` extracts `.so` files from any subdirectory,
so the `lib/` nesting is handled automatically.

## webgpu.h v24+ API: Struct-by-Value Passing

**Critical gotcha:** wgpu-native v24+ uses the new WebGPU callback API where
`WGPURequestAdapterCallbackInfo` and `WGPURequestDeviceCallbackInfo` are passed
**by value** (not by pointer) to `wgpuInstanceRequestAdapter` / `wgpuAdapterRequestDevice`.

These functions also **return `WGPUFuture` by value** (a struct containing a single `uint64_t id`).

In Java FFM, this requires:
1. Using `StructLayout` in the `FunctionDescriptor` for by-value struct parameters
2. The downcall handle's first parameter becomes `SegmentAllocator` (for the return struct)
3. Must cast to `(SegmentAllocator)` when using `invokeExact` — passing `Arena` directly
   causes `WrongMethodTypeException` because `invokeExact` checks exact types

```java
// Correct:
var result = (MemorySegment) handle.invokeExact((SegmentAllocator) arena, instance, options, callbackInfo);
// Wrong (WrongMethodTypeException):
var result = (MemorySegment) handle.invokeExact(arena, instance, options, callbackInfo);
```

## Callback Signatures

The v24 callback for adapter/device requests receives `WGPUStringView` **by value**:
```c
void callback(WGPURequestAdapterStatus status, WGPUAdapter adapter,
              WGPUStringView message, void* userdata1, void* userdata2)
```

`WGPUStringView` = `{ char const* data; size_t length; }` — 16 bytes on x86_64.
In the FFM upcall stub, this is received as a single `MemorySegment` parameter
when `STRING_VIEW_LAYOUT` is used in the `FunctionDescriptor`.

## Callback Mode: Use AllowSpontaneous

wgpu-native processes adapter and device requests synchronously in most cases.
Using `WGPUCallbackMode_AllowSpontaneous` (0x03) makes the callback fire inline
during the `requestAdapter`/`requestDevice` call, avoiding the need for
`wgpuInstanceWaitAny`.

Using `WGPUCallbackMode_WaitAnyOnly` (0x01) with `wgpuInstanceWaitAny` causes
a Rust panic (`panic_cannot_unwind`) in wgpu-native v24.0.3.1 — likely because
the operation already completed and the future is invalid by the time WaitAny runs.

## WGPUSType Values

- `WGPUSType_ShaderSourceSPIRV = 0x00000001`
- `WGPUSType_ShaderSourceWGSL  = 0x00000002`

(Not 3 — the WebGPU spec renumbered these in the v24 header.)

## WGPUFlags = uint64_t

`WGPUBufferUsage`, `WGPUTextureUsage`, `WGPUColorWriteMask`, `WGPUShaderStage`,
`WGPUMapMode` are all `uint64_t` (via `typedef uint64_t WGPUFlags`).
Use `ValueLayout.JAVA_LONG` in FFM.

## WGPUBool = uint32_t

`WGPUBool` is `uint32_t`, not `bool`. Use `ValueLayout.JAVA_INT` in FFM.

## Vulkan Backend Warning

wgpu-native prints `WARNING: radv is not a conformant Vulkan implementation` on
AMD GPUs. This is harmless — radv works fine for wgpu's purposes.

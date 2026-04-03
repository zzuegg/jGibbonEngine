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

## Enum Renumbering in v24

**Critical gotcha:** wgpu-native v24 renumbered many WebGPU enums compared to earlier
versions. Enums that previously started at 1 for the first "real" value now often have
a `BindingNotUsed = 0` and `Undefined = 1` sentinel, pushing all values up by 1.

Affected enums (values shifted +1 compared to pre-v24):
- `WGPUBufferBindingType`: Uniform=2, Storage=3, ReadOnlyStorage=4
- `WGPUSamplerBindingType`: Filtering=2, NonFiltering=3, Comparison=4
- `WGPUTextureSampleType`: Float=2, UnfilterableFloat=3, Depth=4, Sint=5, Uint=6

`WGPUVertexFormat` was completely renumbered with explicit hex values starting from
`Uint8=0x01` through `Unorm8x4BGRA=0x29`. Key values:
- Float32=0x1C, Float32x2=0x1D, Float32x3=0x1E, Float32x4=0x1F
- Uint32=0x20, Sint32=0x24

`WGPUTextureFormat` was also renumbered: RGBA8Unorm=0x12, BGRA8Unorm=0x17,
Depth24Plus=0x28, Depth32Float=0x2A.

`WGPUTextureViewDimension` changed: Cube=4 (was 5 in some older headers).

Always verify enum values against the actual `webgpu.h` from the release zip.

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

## Struct Layout Gotchas (v24)

Many WebGPU v24 structs do NOT have `nextInChain`, unlike what you might assume
from the general pattern. Always check the actual header.

### Structs WITHOUT `nextInChain`:
- `WGPUTexelCopyTextureInfo` — `{ texture(8), mipLevel(4), origin{x,y,z}(12), aspect(4) }` = 32 bytes
- `WGPUTexelCopyBufferInfo` — `{ layout{offset(8), bytesPerRow(4), rowsPerImage(4)}, buffer(8) }` = 24 bytes
- `WGPUTexelCopyBufferLayout` — `{ offset(8), bytesPerRow(4), rowsPerImage(4) }` = 16 bytes
- `WGPURenderPassDepthStencilAttachment` — starts with `view(ptr)`, no chain
- `WGPUVertexBufferLayout` — starts with `stepMode(uint32)`, no chain
- `WGPUVertexAttribute` — `{ format(4), pad(4), offset(8), shaderLocation(4), pad(4) }` = 24 bytes
- `WGPUBlendState` — `{ color(12), alpha(12) }` = 24 bytes

### Structs WITH `nextInChain`:
- `WGPURenderPassColorAttachment` — HAS nextInChain, includes `depthSlice` field
- `WGPUPrimitiveState` — HAS nextInChain AND `unclippedDepth` (WGPUBool) field at end (32 bytes total)
- `WGPUMultisampleState` — HAS nextInChain (24 bytes total)
- `WGPUDepthStencilState` — HAS nextInChain, `depthWriteEnabled` is `WGPUOptionalBool` (72 bytes total)
- `WGPUColorTargetState` — HAS nextInChain, `writeMask` is `uint64_t` (32 bytes total)
- All Descriptor structs (TextureDescriptor, SamplerDescriptor, etc.)
- All State structs used inline in pipeline descriptor

### WGPUOptionalBool vs WGPUBool:
- `WGPUBool` = `uint32_t`: 0 = false, non-zero = true
- `WGPUOptionalBool` = `uint32_t` enum: 0 = False, 1 = True, 2 = Undefined
- `WGPUDepthStencilState.depthWriteEnabled` uses `WGPUOptionalBool`

### WGPUTexelCopyBufferInfo vs WGPUTexelCopyBufferLayout:
- `wgpuQueueWriteTexture` uses `WGPUTexelCopyBufferLayout const *` for the data layout (16 bytes, no buffer field)
- `wgpuCommandEncoderCopyTextureToBuffer` uses `WGPUTexelCopyBufferInfo const *` for the destination (24 bytes, HAS buffer field)
- `WGPUTexelCopyBufferInfo` contains `WGPUTexelCopyBufferLayout` inline as its first field, then `buffer` after

### WGPURenderPipelineDescriptor layout (v24):
```
  0: nextInChain (ptr, 8)
  8: label.data (ptr, 8)
 16: label.length (size_t, 8)
 24: layout (ptr, 8)
 32: vertex (WGPUVertexState, 64 bytes inline)
 96: primitive (WGPUPrimitiveState, 32 bytes inline — includes unclippedDepth)
128: depthStencil* (ptr, 8)
136: multisample (WGPUMultisampleState, 24 bytes inline)
160: fragment* (ptr, 8)
Total: 168
```

### WGPUTextureDescriptor layout (v24):
```
  0: nextInChain (ptr, 8)
  8: label.data (ptr, 8)
 16: label.length (size_t, 8)
 24: usage (uint64, 8)
 32: dimension (uint32, 4)
 36: size.width (uint32, 4)      -- NO padding before size (Extent3D has align 4)
 40: size.height (uint32, 4)
 44: size.depthOrArrayLayers (uint32, 4)
 48: format (uint32, 4)
 52: mipLevelCount (uint32, 4)
 56: sampleCount (uint32, 4)
 60: pad (4)                     -- align to 8 for viewFormatCount
 64: viewFormatCount (size_t, 8)
 72: viewFormats (ptr, 8)
Total: 80
```

## wgpuInstanceWaitAny is NOT implemented in v24

`wgpuInstanceWaitAny` is listed in `unimplemented.rs` in wgpu-native v24.0.3.1.
Calling it causes a Rust panic. Instead:
- Use `CALLBACK_MODE_ALLOW_SPONTANEOUS` for adapter/device requests (callback fires inline)
- Use `wgpuDevicePoll(device, wait=true, NULL)` for buffer mapping (blocks until GPU work completes)

`wgpuDevicePoll` is a wgpu-native extension (defined in `wgpu.h`, not `webgpu.h`).

## Shader Entry Point Names (Slang WGSL Output)

Slang preserves original entry point names when compiling to WGSL. If your Slang shader
declares `vertexMain` and `fragmentMain`, the generated WGSL will use those exact names
(e.g., `@vertex fn vertexMain(...)`). This is different from GLSL where Slang always
renames entry points to `main`.

The `WGPUVertexState` and `WGPUFragmentState` structs contain a `WGPUStringView entryPoint`
field (data pointer + length). This must match the entry point name in the WGSL source
exactly. A mismatch causes wgpu-native to SIGABRT with:
```
"Error matching ShaderStages(VERTEX) shader requirements against the pipeline"
```

The fix: `ShaderSource` carries an `entryPoint` field (defaults to `"main"` for GLSL).
The `ShaderManager` sets it to `"vertexMain"`/`"fragmentMain"` for WGSL targets,
and `WgpuRenderDevice.buildRenderPipeline` reads the entry point from `ShaderSource`
rather than hardcoding names.

## Vulkan Backend Warning

wgpu-native prints `WARNING: radv is not a conformant Vulkan implementation` on
AMD GPUs. This is harmless — radv works fine for wgpu's purposes.

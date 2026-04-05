# WebGPU / wgpu-native Integration Notes

## WGPUStringView Convention (v27+)

All string fields in WebGPU v27 descriptors use `WGPUStringView` with two fields: `data` (pointer) and `length` (size_t).

For **null-terminated strings**, the C convention is `{char_ptr, WGPU_STRLEN}` where `WGPU_STRLEN = SIZE_MAX` (which is `-1L` as a signed long in Java).

**Never use explicit string lengths** (e.g., `str.length()`) — always use `WGPU_STRLEN()` from the generated bindings when the string is null-terminated (which `arena.allocateFrom(str)` always produces).

```java
// WRONG — may cause issues with wgpu-native's string validation
WGPUStringView.data(view, arena.allocateFrom("hello"));
WGPUStringView.length(view, 5);

// CORRECT — uses WGPU_STRLEN sentinel for null-terminated strings
WGPUStringView.data(view, arena.allocateFrom("hello"));
WGPUStringView.length(view, WGPU_STRLEN());  // -1L
```

This applies to ALL label fields, shader entry points, and shader source code.

## Descriptor Labels Are Required

Every descriptor that has a `label` field **must** have it set to a valid `WGPUStringView`. An uninitialized label (zeroed memory) can cause crashes or validation errors. Set all labels:

- `WGPUSurfaceDescriptor`
- `WGPUDeviceDescriptor`
- `WGPUBufferDescriptor`
- `WGPUTextureDescriptor`
- `WGPUTextureViewDescriptor`
- `WGPUSamplerDescriptor`
- `WGPUShaderModuleDescriptor`
- `WGPUBindGroupLayoutDescriptor`
- `WGPUPipelineLayoutDescriptor`
- `WGPURenderPipelineDescriptor`
- `WGPUBindGroupDescriptor`
- `WGPUCommandEncoderDescriptor`
- `WGPUCommandBufferDescriptor`
- `WGPURenderPassDescriptor`

## WGPURenderPipelineDescriptor: By-Value vs By-Pointer

In the C struct:
- `.vertex` — **by value** (embedded `WGPUVertexState` struct)
- `.primitive` — **by value** (embedded `WGPUPrimitiveState` struct)
- `.multisample` — **by value** (embedded `WGPUMultisampleState` struct)
- `.fragment` — **by pointer** (`WGPUFragmentState*`, can be NULL)
- `.depthStencil` — **by pointer** (`WGPUDepthStencilState*`, can be NULL)

In Java FFM, "by value" fields are accessed via the getter that returns a slice of the parent struct (e.g., `WGPURenderPipelineDescriptor.vertex(rpDesc)` returns a `MemorySegment` slice). You write fields directly into this slice. "By pointer" fields are set with a pointer to a separately allocated struct.

## WGPUMultisampleState Defaults

```java
WGPUMultisampleState.count(multisample, 1);
WGPUMultisampleState.mask(multisample, 0xFFFFFFFF);  // All bits set
WGPUMultisampleState.alphaToCoverageEnabled(multisample, 0);
```

The `mask` field type is `int` (not long) in the Java bindings. `0xFFFFFFFF` is the correct value (all sample bits enabled).

## WGPURenderPassColorAttachment: depthSlice

Always set `depthSlice` to `WGPU_DEPTH_SLICE_UNDEFINED()` for 2D texture views. This constant is available from the generated bindings.

## WGPUColorTargetState

Only `format` and `writeMask` are required. The `blend` field is a pointer and is optional (can be `MemorySegment.NULL` for no blending, or point to a `WGPUBlendState` for custom blending).

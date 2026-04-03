# Unified Rendering API Design

**Date:** 2026-04-03  
**Status:** Approved  
**Scope:** Refactor the low-level rendering interface to be safe, easy, extensible, and backend-agnostic (OpenGL 4.5 + Vulkan 1.3)

---

## 1. Goals

- Unified API that works identically on OpenGL and Vulkan
- Safe by default: Cleaner-backed handles, leak warnings, optional validation
- Property-based render state system reusing existing PropertyKey/PropertyMap
- Full modern feature set: compute, bindless textures, MRT, push constants, auto-mipmaps
- Screenshot test infrastructure with three-tier comparison
- Parallel migration: new API alongside old, incremental switchover

## 2. Non-Goals (Deferred)

- Render pass abstraction (deferred to future work)
- Async compute queues (design allows future addition)
- MSAA resolve (added when MSAA support lands)
- WebGPU backend updates (separate effort)

---

## 3. Property-Based Render State

### 3.1 Core Render State Keys

Render state reuses `PropertyKey<T>` / `PropertyMap` from the existing property system. Defined as static constants on a `RenderState` interface:

```java
public interface RenderState {
    PropertyKey<Boolean>     DEPTH_TEST  = PropertyKey.of("depthTest", Boolean.class);
    PropertyKey<Boolean>     DEPTH_WRITE = PropertyKey.of("depthWrite", Boolean.class);
    PropertyKey<CompareFunc> DEPTH_FUNC  = PropertyKey.of("depthFunc", CompareFunc.class);
    PropertyKey<BlendMode>   BLEND_MODE  = PropertyKey.of("blendMode", BlendMode.class);
    PropertyKey<CullMode>    CULL_MODE   = PropertyKey.of("cullMode", CullMode.class);
    PropertyKey<FrontFace>   FRONT_FACE  = PropertyKey.of("frontFace", FrontFace.class);
    PropertyKey<Boolean>     WIREFRAME   = PropertyKey.of("wireframe", Boolean.class);
    PropertyKey<Float>       LINE_WIDTH  = PropertyKey.of("lineWidth", Float.class);
}
```

### 3.2 Value Types (Interfaces with Static Instances)

Following project convention — no enums for extensible types:

- **CompareFunc:** LESS, LEQUAL, GREATER, GEQUAL, EQUAL, NOT_EQUAL, ALWAYS, NEVER
- **BlendMode:** NONE, ALPHA, ADDITIVE, MULTIPLY, PREMULTIPLIED
- **CullMode:** NONE, BACK, FRONT
- **FrontFace:** CCW, CW

### 3.3 Three-Layer Resolution

```
forced properties  >  material properties  >  global defaults
```

- **Global defaults:** Safe baseline set by the renderer (depth on, blend off, cull back, CCW)
- **Material overrides:** Each `MaterialData` can include RenderState keys alongside material keys
- **Forced properties:** Override everything — for debug visualization, multi-pass rendering, etc.

```java
renderer.setDefault(RenderState.DEPTH_TEST, true);
renderer.forceProperty(RenderState.WIREFRAME, true);
renderer.clearForced(RenderState.WIREFRAME);
```

Force/clear works on any `PropertyKey<T>`, not just render state — can force albedo color, shader hint, etc.

### 3.4 Backend Translation

The renderer resolves the final property map per draw call, diffs against previous state, emits only changed state commands. Each backend maps properties to native calls:

- OpenGL: `glEnable/glDisable`, `glDepthFunc`, `glBlendFunc`, etc.
- Vulkan: `vkCmdSetDepthTestEnable`, `vkCmdSetCullMode`, etc. (VK_EXT_extended_dynamic_state, core 1.3)

---

## 4. Unified Resource API

### 4.1 Resource Creation

Handle-based, Cleaner-backed. All handles auto-cleanup via Cleaner as safety net. Explicit `destroy*()` still preferred. `device.close()` logs warnings for leaked handles.

```java
// Buffers
Handle<BufferResource> vbo = device.createBuffer(BufferDescriptor.vertex(size));
Handle<BufferResource> ubo = device.createBuffer(BufferDescriptor.uniform(size));
Handle<BufferResource> ssbo = device.createBuffer(BufferDescriptor.storage(size));

// Textures
Handle<TextureResource> tex = device.createTexture(TextureDescriptor.rgba(512, 512));

// Samplers
Handle<SamplerResource> sampler = device.createSampler(SamplerDescriptor.trilinear());

// Render targets (MRT)
Handle<RenderTargetResource> fbo = device.createRenderTarget(
    RenderTargetDescriptor.builder()
        .colorAttachment(TextureFormat.RGBA8)
        .colorAttachment(TextureFormat.RGBA8)
        .depth(TextureFormat.DEPTH32F)
        .size(1024, 1024)
        .build());

// Graphics pipelines
Handle<PipelineResource> pipeline = device.createPipeline(
    PipelineDescriptor.of(vertexShader, fragmentShader)
        .withVertexFormat(format));

// Compute pipelines
Handle<PipelineResource> compute = device.createComputePipeline(
    ComputePipelineDescriptor.of(computeShader));
```

### 4.2 Bindless Textures / Descriptor Indexing

Unified across backends via `getTextureIndex()`:

```java
int texIndex = device.getTextureIndex(textureHandle);
```

- **OpenGL:** `ARB_bindless_texture` — `glGetTextureHandleARB()` + `glMakeTextureHandleResidentARB()`, uint64 handle stored in SSBO/UBO
- **Vulkan:** `VK_EXT_descriptor_indexing` — slot in large descriptor array, integer index

Shader code (Slang) uses a unified intrinsic to abstract the difference.

This is a **required feature** — both backends must support it.

### 4.3 Push Constants

Up to 128 bytes of inline data per draw/dispatch, no buffer allocation needed:

```java
recorder.pushConstants(data);  // ByteBuffer or MemorySegment, max 128 bytes
```

- **Vulkan:** Native `vkCmdPushConstants`
- **OpenGL:** Emulated via a small internal UBO that the backend manages transparently. The backend maintains a dedicated push-constant UBO, updates it with `glNamedBufferSubData`, and binds it to a reserved slot (e.g., binding 15).

### 4.4 Texture Formats

Extended for compute and HDR:

```java
// Existing
RGBA8, RGB8, R8, DEPTH24, DEPTH32F

// New
RGBA16F, RGBA32F       // HDR, compute
RG16F, RG32F           // velocity buffers
R16F, R32F             // single channel float
R32UI, R32I            // integer textures for compute
```

---

## 5. Texture System & Auto-Mipmaps

### 5.1 MipMode

```java
public interface MipMode {
    MipMode AUTO = ...;           // generate when bound with mipmap sampler
    MipMode NONE = ...;           // single level
    static MipMode levels(int n); // explicit count
}
```

Default for color textures: `AUTO`. Default for depth textures: `NONE`.

### 5.2 Auto-Generation Lifecycle

1. `uploadTexture(handle, pixels)` — marks mips dirty
2. Render-to-texture (FBO color attachment written to) — marks mips dirty
3. On `bindTexture(unit, handle, sampler)`:
   - If sampler uses mipmaps AND mips are dirty:
     - OpenGL: `glGenerateMipmap()`
     - Vulkan: blit chain via `vkCmdBlitImage()` from level 0 down
   - If sampler doesn't use mipmaps: no-op, zero cost
4. Mips regenerate automatically after render-to-texture

---

## 6. Command Recording & Draw Calls

### 6.1 Unified CommandRecorder

Supports graphics, compute, state, and barriers in one command list:

```java
CommandRecorder recorder = new CommandRecorder();

// Render state
recorder.setRenderState(properties);

// Low-level binds (power user)
recorder.bindPipeline(pipeline);
recorder.bindVertexBuffer(vbo, vertexInput);
recorder.bindIndexBuffer(ibo);
recorder.bindUniformBuffer(0, ubo);
recorder.bindTexture(0, texture, sampler);
recorder.bindStorageBuffer(0, ssbo);
recorder.pushConstants(data);
recorder.drawIndexed(indexCount, firstIndex);

// Safe bundled draw (convenience)
recorder.draw(DrawCall.indexed()
    .pipeline(pipeline)
    .vertices(vbo, vertexInput)
    .indices(ibo)
    .uniform(0, ubo)
    .texture(0, tex, sampler)
    .pushConstants(data)
    .count(indexCount)
    .build());

// Compute
recorder.bindComputePipeline(computePipeline);
recorder.bindStorageBuffer(0, inputBuffer);
recorder.dispatch(groupsX, groupsY, groupsZ);

// Barriers
recorder.memoryBarrier(BarrierScope.STORAGE_BUFFER);

// Framebuffer
recorder.bindRenderTarget(fbo);
recorder.clear(0.1f, 0.1f, 0.1f, 1.0f);
recorder.viewport(0, 0, width, height);

CommandList commands = recorder.finish();
device.submit(commands);
```

### 6.2 New RenderCommand Types

Added to sealed hierarchy:

- `SetRenderState(PropertyMap properties)`
- `PushConstants(ByteBuffer data)`
- `BindComputePipeline(Handle<PipelineResource>)`
- `Dispatch(int groupsX, int groupsY, int groupsZ)`
- `MemoryBarrier(BarrierScope scope)`

Deprecated (replaced by SetRenderState):
- `SetDepthTest`, `SetBlending`, `SetCullFace`, `SetWireframe`

### 6.3 BarrierScope

```java
public interface BarrierScope {
    BarrierScope STORAGE_BUFFER = ...;  // SSBO writes visible to reads
    BarrierScope TEXTURE = ...;         // image writes visible to sampling
    BarrierScope ALL = ...;             // full pipeline barrier
}
```

### 6.4 DrawCall Builder

Validates completeness at build time (optional):

```java
DrawCall.setValidation(true);   // debug: validates pipeline, vertex buffer, counts
DrawCall.setValidation(false);  // release: zero overhead

// Or per-recorder
new CommandRecorder(ValidationMode.DISABLED);
```

---

## 7. Feature Parity Matrix

### 7.1 Currently Working (Both Backends)

- Buffer create/write/destroy (vertex, index, uniform)
- Pipeline creation (GLSL on GL, SPIRV on VK)
- Vertex input / format binding
- Draw + DrawIndexed
- Viewport, scissor
- Frame lifecycle (begin/end)
- Framebuffer readback
- Capability queries

### 7.2 OpenGL Only (Need Vulkan Implementation)

- Textures (create, upload, bind)
- Samplers (create, bind)
- Render targets / FBOs
- Storage buffer binding
- Depth test, blending, cull face, wireframe toggles
- Streaming buffers (persistent mapped)
- GPU fences (real implementation)
- GPU timers
- Bindless textures

### 7.3 New Features (Both Backends)

- Property-based render state system
- Push constants
- Compute pipeline creation + dispatch
- Memory barriers
- Auto-mipmap generation
- Unified bindless / descriptor indexing
- DrawCall builder with optional validation
- HDR texture formats (RGBA16F, RGBA32F)
- Integer texture formats (R32UI, R32I)
- Cleaner-based handle safety net + leak logging
- Validation mode toggle
- ComputePipelineDescriptor

### 7.4 Refactoring Targets

| Component | Refactoring | Reason |
|-----------|------------|--------|
| Individual state commands | Replace with `SetRenderState(PropertyMap)` | Unified property system |
| `GlRenderDevice` resource maps | Extract shared `ResourceRegistry` | Reduce duplication across backends |
| `VkRenderDevice` (1032 lines) | Split into resource mgmt, command exec, swapchain | Matches GL's factored design |
| `ScreenshotHelper` | Expand into 3-tier test harness | Regression + cross-backend testing |
| Texture mip tracking | Shared mip-dirty logic in common layer | Both backends need same tracking |
| Descriptor management | Shared "what's bound" tracking | GL uses binds, VK uses descriptor sets, tracking is shared |
| `CommandRecorder` | Add new command types, deprecate old state toggles | Unified render state |
| `PipelineDescriptor` | Add compute variant | Compute support |

---

## 8. Screenshot Testing Infrastructure

### 8.1 Three-Tier Comparison

| Tier | What | Tolerance | Catches |
|------|------|-----------|---------|
| Per-backend regression | Same backend vs its own reference | Tight (max 1 channel diff) | Shader bugs, state leaks, resource issues |
| Cross-backend similarity | OpenGL output vs Vulkan output | Loose (5% pixel diff at threshold 3) | Fundamental rendering differences, wrong winding |
| Human review | All saved to build/screenshots/ | Visual | Subtle issues numbers miss |

### 8.2 Directory Structure

```
examples/src/test/
├── resources/reference/
│   ├── opengl/
│   │   ├── triangle.png
│   │   ├── spinning_cube.png
│   │   └── ...
│   └── vulkan/
│       ├── triangle.png
│       └── ...
└── java/dev/engine/examples/
    ├── ScreenshotTestSuite.java      // shared test scene definitions
    ├── OpenGlScreenshotTest.java     // per-backend regression
    ├── VulkanScreenshotTest.java
    └── CrossBackendTest.java         // cross-backend comparison
```

### 8.3 Test Scene Library

Reusable scenes exercising specific features:

**Basic:** coloredTriangle, indexedCubeDepthTest, texturedQuad  
**Materials & State:** alphaBlending, multipleBlendModes, cullModes, wireframe  
**Advanced:** multiRenderTarget, computeBufferWrite, bindlessTextures, pushConstants  
**Scene:** multiEntityScene, primitiveMeshes, pbrMaterials

### 8.4 Execution Pattern

```java
@Test void triangle_opengl() {
    var result = harness.render("triangle", Backend.OPENGL,
        r -> ScreenshotTestSuite.coloredTriangle(r));
    harness.assertMatchesReference(result, "opengl/triangle.png",
        Tolerance.tight());
}

@Test void triangle_crossBackend() {
    var gl = harness.render("triangle", Backend.OPENGL, ...);
    var vk = harness.render("triangle", Backend.VULKAN, ...);
    harness.assertSimilar(gl, vk, Tolerance.loose());
}
```

### 8.5 Test Ordering

Screenshot tests are created BEFORE refactoring starts. They capture current correct output as reference. Each refactoring step must pass all existing tests.

---

## 9. Migration Strategy

### Phase 1: Screenshot Tests
- Create comprehensive screenshot tests against current implementation
- Capture reference images for both backends
- Verify cross-backend similarity baseline

### Phase 2: New API Types
- Add new types (RenderState, CompareFunc, BlendMode, CullMode, etc.)
- Add new RenderCommand variants
- Add DrawCall builder
- No backend changes yet

### Phase 3: Vulkan Feature Parity
- Implement textures, samplers, render targets in Vulkan
- Implement state commands (depth, blend, cull, wireframe)
- Implement storage buffer binding
- Implement streaming buffers, fences
- Screenshot tests verify each feature

### Phase 4: New Features (Both Backends)
- Property-based render state
- Push constants
- Compute pipelines + dispatch + barriers
- Auto-mipmap generation
- Unified bindless/descriptor indexing
- HDR/integer texture formats
- Cleaner-based handle safety

### Phase 5: Refactoring
- Extract shared ResourceRegistry
- Split VkRenderDevice into focused classes
- Deprecate old state commands
- Update CommandRecorder
- Migrate examples to new API

### Phase 6: Cleanup
- Remove deprecated commands
- Remove old API surface
- Final screenshot test pass

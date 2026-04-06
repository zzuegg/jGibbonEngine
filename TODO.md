# Engine Code Review TODO

Full code review performed 2026-04-05 across all 467 source files.

## Critical Bugs

- [x] **Thread safety: Engine.run() race condition** — Fixed: switched AbstractScene from TransactionBuffer to TransactionBus. Per-subscriber synchronized double-buffer swap ensures emit() and drain() never access the write buffer concurrently.
- [x] **Profiler.lastFrame() returns currentFrame** — Fixed: `lastFrame()` now returns `lastFrame`, added `currentFrame()` for in-progress data. Tests updated.
- [x] **GPU buffer leak: per-entity UBOs never cleaned up** — Mitigated: Cleaner safety net now automatically destroys unreachable UBO handles. Explicit cleanup on EntityRemoved still recommended for deterministic resource release.
- [x] **MeshRenderer leaks entity maps on EntityRemoved** — Fixed: `meshDataAssignments`, `meshAssignments`, `materialAssignments` now removed on entity removal.
- [x] **DrawCommand.materialData raw unchecked generic** — Fixed: changed to `PropertyMap<MaterialData>`.
- [x] **Stale shader binding after entity reuse** — Fixed: entityShaders now keyed by Handle<?> (includes generation). WeakHashMap prevents blocking GC. Renderer clears entity shader on EntityRemoved.
- [x] **Engine.shutdown() doesn't shut down AssetManager** — Fixed: added `assets.shutdown()` to `Engine.shutdown()`.
- [x] **Renderer.close() doesn't clean up MeshManager** — Fixed: added `MeshManager.close()` and wired into `Renderer.close()`.

## Hardcoded Values (should be configurable/dynamic)

- [ ] **Primitive topology hardcoded to TRIANGLES** — All draw commands in Gl/Vk/Wgpu RenderDevice. Topology should be part of PipelineDescriptor or DrawCommand. Prevents drawing lines, points, triangle strips.
- [ ] **Clear color hardcoded (0.05, 0.05, 0.08)** — `Renderer.java:65`, `VkRenderDevice.java:60`. Duplicated magic constant. Default should come from config.
- [ ] **Push constant UBO size hardcoded to 128 bytes** — `GlRenderDevice.java:96`, `VkDescriptorManager.java:101`. Should be configurable via GraphicsConfig or DeviceCapability.
- [ ] **Texture/sampler array sizes: magic numbers** — GL: boundTextures[32], boundSamplers[32]. Vk: currentTextures[8], pendingUboBuffers[16], pendingSsboBuffers[8]. Should be queried from DeviceCapability or configurable.
- [ ] **MAX_FRAMES_IN_FLIGHT=2 hardcoded** — `VkRenderDevice.java:47`. Should be configurable through VulkanConfig.
- [ ] **MAX_SETS_PER_FRAME=256 hardcoded** — `VkDescriptorManager.java:17`. Could be insufficient for complex scenes. Should auto-grow or be configurable.
- [x] **Global param bindings hardcoded** — Fixed: extracted `GlobalParamNames` constants class (`ENGINE`, `CAMERA`, `OBJECT`). Used in `Renderer` and `UniformManager`.
- [ ] **Blend function hardcoded to SRC_ALPHA/ONE_MINUS_SRC_ALPHA** — `GlRenderDevice.java:623`. BlendMode exists but only supports NONE vs one hardcoded alpha blend. Need configurable src/dst factors, blend equation.
- [ ] **All shaders forced to STANDARD_FORMAT vertex layout** — `ShaderManager.java:255`. Uses PrimitiveMeshes.STANDARD_FORMAT for ALL pipelines. Custom vertex formats (tangents, colors, bone weights) won't work.
- [ ] **Shader entry points hardcoded ("vertexMain"/"fragmentMain")** — `ShaderManager.java:227`. Should be configurable per shader for custom entry points.
- [ ] **Camera defaults (near=0.1, far=1000) not in config** — `Camera.java:15`. Should be configurable through EngineConfig or named constants.
- [ ] **Deprecated cull face/front face in legacy commands** — `GlRenderDevice.java:631`. Legacy SetCullFace hardcodes GL_BACK/GL_CCW. Remove deprecated path since SetRenderState replacement exists.

## Missing Configuration

- [ ] **EngineConfig missing common options** — Missing: FPS cap, VSync toggle, MSAA sample count, anisotropic filtering, gamma/sRGB mode, fullscreen mode, monitor selection, cursor visibility, debug overlay toggle.
- [ ] **GraphicsConfig missing graphics settings** — Only has headless and validation. Missing: MSAA, VSync mode, sRGB framebuffer, aniso level, GPU selection, shader cache directory, max texture size override.
- [ ] **WindowDescriptor too minimal** — Only title/width/height. Missing: resizable, fullscreen, decorated, transparent, always-on-top, min/max size, initial position, high-DPI flag, cursor mode.

## Designed but Not Implemented (from NOTES.md)

- [ ] **DoubleBufferedPipeline + FrameSnapshot unused** — Implemented classes never used by Engine or BaseApplication. Threaded Engine.run() directly accesses scene with no snapshot mechanism.
- [ ] **RenderGraph not integrated into Renderer** — RenderGraph, RenderPass, PassBuilder, PassContext all implemented but Renderer has monolithic renderFrame(). Graph should drive shadow/geometry/post-process passes.
- [ ] **PostProcessChain not integrated** — Implemented but never called from Renderer. No hook for bloom, tone mapping, FXAA, etc.
- [ ] **UploadStrategy not used by Renderer** — Interface + PerObjectUploadStrategy exist but Renderer does inline upload. PerObjectUploadStrategy even has a TODO comment.
- [ ] **EventBus never instantiated** — Fully implemented with subscribe/publish/unsubscribe but not used by Engine, Renderer, AssetManager, or anything.
- [x] **TransactionBus never used** — Fixed: AbstractScene now uses TransactionBus instead of TransactionBuffer.
- [ ] **Asset dependency tracking** — NOTES.md: "Assets can depend on other assets. Manager resolves dependencies transitively." Not implemented.
- [ ] **Asset eviction/reference counting** — NOTES.md: "Reference counting or GC-based eviction for unused assets." Cache is unbounded ConcurrentHashMap. No LRU, no memory budget.
- [ ] **Async asset loading with placeholders** — NOTES.md: "Callers get a handle immediately; placeholder assets used until loading completes." API blocks synchronously. No placeholder/fallback system.
- [ ] **Disk shader compilation cache** — NOTES.md: "Cache is persistent across sessions (disk cache)." Only in-memory cache exists. Every restart recompiles all shaders.
- [ ] **Shader hot-reload** — Infrastructure exists (AssetManager has watchForReload, ShaderManager has invalidate) but never wired together.
- [ ] **Error/fallback shader** — NOTES.md: "Fallback/error shader always available." ShaderManager returns null on failure, entities disappear. Should render visible error (e.g. magenta).
- [ ] **Object versioning system** — NOTES.md extensively describes monotonic version counters, hierarchical versioning, cache invalidation. VersionCounter class exists but is unused.
- [ ] **Static vs Dynamic object distinction** — NOTES.md describes static/dynamic categorization for persistent shadow maps, skipping updates. No mechanism to mark entities.
- [ ] **Multi-window rendering** — NOTES.md describes per-window render threads. Architecture is single-window only.

## Performance Issues

- [ ] **New CommandRecorder per draw call** — `Renderer.java:251`. Creates and submits a CommandRecorder for every entity. Prevents any command batching. Should batch into one recorder or sort by state.
- [ ] **No frustum culling** — Renderer draws ALL entities every frame regardless of camera frustum. No spatial data structure (BVH, octree). Critical for non-trivial scenes.
- [ ] **No draw call sorting** — Entities drawn in arbitrary HashMap order. No sorting by pipeline/material/depth. Causes maximum state thrashing.
- [ ] **MaterialData.set() copies entire PropertyMap** — `MaterialData.java:72`. O(n) per property change. Consider builder or mutable-then-freeze.
- [x] **WeakCache.getOrCreate() linear scan** — Fixed: O(1) lookup via temporary IdentityWeakReference for HashMap.get() instead of iterating all entries.
- [ ] **HierarchicalScene.getWorldTransform() recursive, uncached** — Walks parent chain every call. O(depth) per entity per frame. Cache world transforms, invalidate on change.
- [ ] **AbstractScene.query() linear scan** — Iterates all entities per query. No component index. O(n) per query.
- [ ] **Entity HashMap for 2-4 components** — HashMap overhead for typical small component counts. Array-based map would be more efficient.

## Missing Renderer Features

- [ ] **No lighting system** — LightData/LightType exist in core but Renderer has zero light handling. No light buffer upload, no light culling. MeshRenderer ignores LightData components.
- [ ] **No shadow mapping** — Extensively designed in NOTES.md. No shadow pass, no shadow maps, no light-space matrices. LightData.CASTS_SHADOWS exists but nothing reads it.
- [x] **RenderStats never populated** — Fixed: `Renderer.renderFrame()` now records draw calls, pipeline binds, buffer binds, and texture binds. `Engine.renderStats()` delegates to `renderer.renderStats()`.

## Architectural Cleanup

- [x] **Deprecated render commands removed** — SetDepthTest/SetBlending/SetCullFace/SetWireframe removed from RenderCommand, CommandRecorder, and all three backends. Examples migrated to SetRenderState. GlfwWindowToolkit wrapper in graphics:opengl deleted. DebugUiOverlay 2-arg init removed. GraphicsBackendFactory/GraphicsConfigLegacy kept for now (still used by examples and tests).
- [x] **ResourceCleaner.register() package-private** — Fixed: made `public`.
- [x] **NativeResource not used by GPU resources** — Fixed: Handle<T> now supports Cleaner registration. GpuResourceManager registers cleanup actions on all created handles. Leaked handles are automatically cleaned by GC with warn-level logging.
- [x] **GpuResourceManager deferred deletion delay doesn't match frames-in-flight** — Fixed: replaced double-buffer with ring of N+1 queues (N = FRAMES_IN_FLIGHT). Vulkan reports MAX_FRAMES_IN_FLIGHT=2, so resources are deferred 3 frames. Added DeviceCapability.FRAMES_IN_FLIGHT.

## Presentation / VSync

- [ ] **Present mode configurable via GraphicsConfig** — Currently each backend and example hardcodes present mode. Add `presentMode(PresentMode)` to GraphicsConfig/EngineConfig so it flows through to Vulkan, WebGPU, and OpenGL (swap interval). Enum: FIFO (vsync), IMMEDIATE (no vsync), MAILBOX (triple-buffered).
- [ ] **WebGPU: use Immediate on X11, Mailbox on Wayland** — Currently always falls back to Mailbox. Should detect the surface type and use Immediate when supported (X11/XWayland).
- [ ] **Wayland Vulkan vsync cap** — On native Wayland, `vkQueuePresentKHR` blocks to refresh rate regardless of present mode. Documented limitation, no engine-side fix.

## API Tracing / Debugging

- [x] **WebGPU API tracing** — Implemented: `TracingWgpuBindings` decorator logs every WebGPU API call at TRACE level with full parameters.
- [ ] **Vulkan API tracing** — Add `TracingVkBindings` decorator for VkBindings, same pattern as WebGPU.
- [ ] **OpenGL API tracing** — Add `TracingGlBindings` decorator for GlBindings, same pattern as WebGPU.
- [ ] **GraphicsConfig logging flag** — Add a `traceApi(boolean)` option to GraphicsConfig that automatically wraps the backend bindings with the tracing decorator.

## Previously Identified (from 2026-04-04 review)

- [ ] **WebGPU compute shader support** — `WgpuRenderDevice.java`. Push constants, compute pipeline, dispatch, storage images, texture copy/blit unimplemented.
- [ ] **Vulkan streaming buffer** — `VkRenderDevice.java:603-605`. Returns null. Needs persistent mapped buffer with ring buffer regions.
- [ ] **Vulkan storage image (bindImage)** — `VkRenderDevice.java:1006-1008`. Needs VK_DESCRIPTOR_TYPE_STORAGE_IMAGE support.
- [ ] **SDL3 window property stubs** — `Sdl3WindowToolkit.java`. get(), set(), sizeRef(), focusedRef(), swapBuffers() are stubs.
- [ ] **Async buffer mapping for TeaVM WebGPU** — Browser can't do synchronous mapping. Needs promise wrapper or skip readback on web.
- [ ] **Slang generic specialization parsing robustness** — `SlangCompilerNative.java:245-276`. Current regex works. Revisit when it breaks.
- [ ] **WGSL binding extraction regex** — `WgpuRenderDevice.java:623-625`. Works for current shaders. Revisit on Slang output changes.

## Backend Feature Parity Matrix

Cross-backend audit performed 2026-04-06. ✅ = implemented, ⚠️ = partial/fallback, ❌ = missing.

### Texture Formats (16 defined in TextureFormat)

| Format | OpenGL | Vulkan | WebGPU | Notes |
|---|---|---|---|---|
| RGBA8 | ✅ | ✅ | ✅ | |
| RGB8 | ✅ | ✅ | ⚠️ → RGBA8 | WebGPU lacks native RGB8; logged |
| BGRA8 | ⚠️ → RGBA8 | ✅ | ✅ | GL lacks native BGRA internal format; logged |
| R8 | ✅ | ✅ | ✅ | |
| RGBA16F | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| RGBA32F | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| RG16F | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| RG32F | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| R16F | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| R32F | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| R32UI | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| R32I | ✅ | ✅ | ✅ | GL added 2026-04-06 |
| DEPTH24 | ✅ | ✅ | ✅ | |
| DEPTH32F | ✅ | ✅ | ✅ | |
| DEPTH24_STENCIL8 | ✅ | ✅ | ✅ | |
| DEPTH32F_STENCIL8 | ✅ | ✅ | ⚠️ → D24S8 | WebGPU lacks this; falls back with warning |

### RenderCommand Support

| Command | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| BindPipeline | ✅ | ✅ | ✅ | |
| BindVertexBuffer | ✅ | ✅ | ✅ | |
| BindIndexBuffer | ✅ | ✅ | ✅ | UINT32 only on all backends |
| BindUniformBuffer | ✅ | ✅ | ✅ | |
| BindTexture | ✅ | ✅ | ✅ | |
| BindSampler | ✅ | ✅ | ✅ | |
| BindStorageBuffer | ✅ | ✅ | ✅ | |
| BindImage | ✅ | ❌ warn | ❌ warn | VK needs storage image descriptors |
| Draw | ✅ | ✅ | ✅ | |
| DrawIndexed | ✅ | ✅ | ✅ | |
| DrawInstanced | ✅ | ✅ | ✅ | |
| DrawIndexedInstanced | ✅ | ✅ | ✅ | |
| DrawIndirect | ✅ | ✅ | ❌ warn | |
| DrawIndexedIndirect | ✅ | ✅ | ❌ warn | |
| BindRenderTarget | ✅ | ✅ | ✅ | |
| SetRenderState | ✅ | ✅ | ✅ | |
| PushConstants | ✅ UBO@15 | ✅ native | ❌ warn | |
| BindComputePipeline | ✅ | ✅ | ❌ warn | |
| Dispatch | ✅ | ✅ | ❌ warn | |
| MemoryBarrier | ✅ | ✅ | ⚠️ implicit | WebGPU handles automatically |
| CopyBuffer | ✅ | ✅ | ✅ | |
| CopyTexture | ✅ | ❌ warn | ❌ warn | VK needs render pass pause |
| BlitTexture | ✅ | ❌ warn | ❌ warn | VK needs render pass pause |
| Clear | ✅ | ✅ | ✅ | |
| Viewport | ✅ | ✅ | ✅ | |
| Scissor | ✅ | ✅ | ✅ | |

### Render State Properties (16 defined)

| Property | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| DEPTH_TEST | ✅ | ✅ dynamic | ✅ pipeline | |
| DEPTH_WRITE | ✅ | ✅ dynamic | ✅ pipeline | |
| DEPTH_FUNC | ✅ | ✅ dynamic | ✅ pipeline | All 8 CompareFuncs mapped on all backends |
| BLEND_MODE | ✅ | ✅ variant | ✅ pipeline | |
| CULL_MODE | ✅ | ✅ dynamic | ✅ pipeline | |
| FRONT_FACE | ✅ | ✅ dynamic | ✅ pipeline | |
| WIREFRAME | ✅ | ✅ variant | ❌ warn | WebGPU API limitation |
| LINE_WIDTH | ✅ | ❌ | ❌ | GL-only |
| SCISSOR_TEST | ✅ | ❌ | ❌ | GL-only |
| STENCIL_TEST | ✅ | ✅ dynamic | ✅ pipeline | |
| STENCIL_FUNC | ✅ | ✅ dynamic | ✅ pipeline | |
| STENCIL_REF | ✅ | ✅ dynamic | ✅ pipeline | |
| STENCIL_MASK | ✅ | ✅ dynamic | ✅ pipeline | |
| STENCIL_FAIL | ✅ | ✅ dynamic | ✅ pipeline | All 8 StencilOps mapped on all backends |
| STENCIL_DEPTH_FAIL | ✅ | ✅ dynamic | ✅ pipeline | |
| STENCIL_PASS | ✅ | ✅ dynamic | ✅ pipeline | |

### Vertex Attribute Types

| Type | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| FLOAT (1-4) | ✅ | ✅ | ✅ | |
| INT (1-4) | ✅ | ✅ | ❌ warn→FLOAT32X4 | |
| BYTE | ✅ | ⚠️ normalized | ❌ warn→FLOAT32X4 | |
| UNSIGNED_BYTE | ✅ | ⚠️ normalized | ⚠️ 4-comp normalized only | |

### Sampler Features

| Feature | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| Wrap: Repeat/Clamp/Mirror | ✅ | ✅ | ✅ | |
| Wrap: Clamp to Border | ✅ | ✅ | ❌ | WebGPU API limitation |
| Min/Mag/Mipmap Filters | ✅ | ✅ | ✅ | |
| Anisotropy | ✅ | ✅ | ✅ | |
| Compare Function | ✅ | ✅ | ✅ | |
| LOD Min/Max | ✅ | ✅ | ✅ | |
| LOD Bias | ✅ | ✅ | ❌ | WebGPU API limitation |
| Border Color | ✅ | ✅ | ❌ | WebGPU API limitation |
| Mipmap Generation | ✅ lazy | ✅ lazy blit | ❌ | WebGPU has no runtime mipmap gen |

### Compute Shaders

| Feature | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| Pipeline creation | ✅ GLSL | ✅ SPIRV | ❌ | COMPUTE_SHADERS capability now returns false on WebGPU |
| Dispatch | ✅ | ✅ | ❌ warn | |
| BindImage (storage) | ✅ | ❌ warn | ❌ warn | VK needs descriptor type support |
| Memory Barriers | ✅ | ✅ | implicit | |

### Buffer & Draw Infrastructure

| Feature | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| Index UINT32 | ✅ | ✅ | ✅ | |
| Index UINT16 | ❌ | ❌ (defined) | ❌ | Hardcoded UINT32 on all backends |
| Primitive topology | ❌ triangles | ❌ triangles | ❌ triangles | Hardcoded on all backends |
| Buffer CPU readback | ❌ (API unused) | ✅ staging | ✅ async map | GL has glMapNamedBufferRange declared but unused |
| Indirect draw | ✅ + multi-draw | ✅ | ❌ warn | |
| readFramebuffer | ✅ Y-flip | ✅ BGRA→RGBA | ✅ 256-align | |

### DeviceCapability Queries

| Capability | GL | VK | WebGPU | Notes |
|---|---|---|---|---|
| MAX_TEXTURE_SIZE | ✅ queried | ✅ queried | ⚠️ hardcoded 8192 | WebGPU device limits broken (jwebgpu) |
| MAX_FRAMEBUFFER_W/H | ✅ queried | ✅ queried | ⚠️ hardcoded 8192 | |
| MAX_ANISOTROPY | ✅ queried | ❌ | ⚠️ hardcoded 16 | VK should query from physical device |
| MAX_UNIFORM_BUFFER_SIZE | ✅ queried | ❌ | ⚠️ hardcoded 65536 | |
| MAX_STORAGE_BUFFER_SIZE | ✅ queried | ❌ | ⚠️ hardcoded 128MB | |
| COMPUTE_SHADERS | ✅ true | ✅ true | false | VK added 2026-04-06 |
| GEOMETRY_SHADERS | ✅ true | ✅ true | false | VK added 2026-04-06 |
| TESSELLATION | ✅ true | ✅ true | false | GL: declared but no commands implemented. VK added 2026-04-06 |
| ANISOTROPIC_FILTERING | ✅ | ✅ true | ✅ | VK added 2026-04-06 |
| BINDLESS_TEXTURES | ✅ ext check | ❌ | false | |
| BACKEND_NAME | ✅ | ✅ | ✅ | |
| SHADER_TARGET | ✅ GLSL | ✅ SPIRV | ✅ WGSL | |
| DEVICE_NAME | ✅ queried | ✅ queried | ⚠️ hardcoded | |
| API_VERSION | ✅ queried | ✅ queried | ⚠️ hardcoded | VK added 2026-04-06 |

### Cross-Backend Gaps (prioritized)

- [ ] **WebGPU: no mipmap generation** — Textures with mipmap samplers sample only mip 0. Need compute or blit-based mipmap generation.
- [ ] **WebGPU: no compute pipeline** — COMPUTE_SHADERS now correctly returns false. Implement when jwebgpu supports compute.
- [ ] **WebGPU: no indirect draw** — DrawIndirect/DrawIndexedIndirect logged as warnings.
- [ ] **WebGPU: no PushConstants** — Could be emulated via UBO (like GL does at binding 15).
- [ ] **Vulkan: CopyTexture/BlitTexture stubs** — Requires pausing the render pass. GL fully implements both.
- [ ] **Vulkan: no BindImage** — Blocks compute shaders that write to textures. Needs storage image descriptor pool.
- [x] **Vulkan: missing DeviceCapability queries** — Added COMPUTE_SHADERS, GEOMETRY_SHADERS, TESSELLATION, ANISOTROPIC_FILTERING, API_VERSION. Still missing: MAX_ANISOTROPY, MAX_UNIFORM/STORAGE_BUFFER_SIZE (need new VkBindings methods).
- [ ] **All backends: primitive topology hardcoded** — Cannot draw lines, points, or triangle strips. Should be part of PipelineDescriptor.
- [ ] **All backends: UINT16 index buffers unsupported** — Wastes memory for small meshes. VK has constant defined but unused.
- [ ] **GL: buffer CPU readback** — glMapNamedBufferRange declared in GlBindings but never called. No staging readback path.
- [ ] **VK/WebGPU: LINE_WIDTH and SCISSOR_TEST not handled** — GL-only render states.
- [ ] **WebGPU: vertex INT type unsupported** — Falls back to FLOAT32X4 with warning.
- [ ] **WebGPU: sampler border color / LOD bias unsupported** — API limitation.

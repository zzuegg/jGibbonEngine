# Engine Code Review TODO

Full code review performed 2026-04-05 across all 467 source files.
Deep in-depth review performed 2026-04-06 across all 497 source files.

## Critical Bugs (2026-04-06 review)

- [x] **Thread safety: Engine.run() race condition** — Fixed: switched AbstractScene from TransactionBuffer to TransactionBus. Per-subscriber synchronized double-buffer swap ensures emit() and drain() never access the write buffer concurrently.
- [x] **Profiler.lastFrame() returns currentFrame** — Fixed: `lastFrame()` now returns `lastFrame`, added `currentFrame()` for in-progress data. Tests updated.
- [x] **GPU buffer leak: per-entity UBOs never cleaned up** — Mitigated: Cleaner safety net now automatically destroys unreachable UBO handles. Explicit cleanup on EntityRemoved still recommended for deterministic resource release.
- [x] **MeshRenderer leaks entity maps on EntityRemoved** — Fixed: `meshDataAssignments`, `meshAssignments`, `materialAssignments` now removed on entity removal.
- [x] **DrawCommand.materialData raw unchecked generic** — Fixed: changed to `PropertyMap<MaterialData>`.
- [x] **Stale shader binding after entity reuse** — Fixed: entityShaders now keyed by Handle<?> (includes generation). WeakHashMap prevents blocking GC. Renderer clears entity shader on EntityRemoved.
- [x] **Engine.shutdown() doesn't shut down AssetManager** — Fixed: added `assets.shutdown()` to `Engine.shutdown()`.
- [x] **Renderer.close() doesn't clean up MeshManager** — Fixed: added `MeshManager.close()` and wired into `Renderer.close()`.

## New Bugs Found (2026-04-06 deep review)

- [x] **NPE in BaseApplication when debugOverlay=false** — Fixed: Engine no longer allocates DebugUiOverlay when disabled (set to null), BaseApplication already null-checks debugUi().
- [x] **UniformManager material write loop skips Vec4/Mat4/Vec2 alignment** — Fixed: write loop now aligns Vec4/Mat4 to 16 bytes and Vec2 to 8 bytes, matching the size calculation.
- [x] **TransactionBus.drain() returns list that gets cleared on next swap** — Fixed: swap() now returns an owned snapshot (old writeBuffer) and allocates a fresh writeBuffer. Caller's list is never mutated.
- [x] **Vec2/Vec3/Vec4/Quat normalize() division by zero** — Fixed: normalize() returns ZERO (or IDENTITY for Quat) when length is zero.
- [x] **Transform.lookingAt() NaN when target == position** — Fixed: early return `this` when target equals position.
- [x] **ZipAssetSource never closes ZipFile** — Fixed: implements AutoCloseable with close() that closes the ZipFile.
- [x] **EventBus.Subscription.unsubscribe() race condition** — Fixed: uses AtomicBoolean.compareAndSet() for thread-safe unsubscribe.
- [x] **FileWatcher only watches one directory level** — Fixed: walkFileTree registers all subdirectories recursively. Event paths resolved relative to root directory.
- [x] **ResourceStats frame counters not thread-safe** — Fixed: all frame counters (created/destroyed/used/updated) now use AtomicInteger. swapFrame() uses getAndSet(0) for atomic swap+reset.
- [x] **Hierarchy mutations don't emit transactions** — Fixed: Entity.setParent() and HierarchicalScene.removeParent() now emit ComponentChanged(Hierarchy) for child, new parent, and old parent. Tests added.
- [x] **ObjLoader mishandles mixed attribute faces** — Fixed: vertices missing texcoords or normals are padded with defaults (0,0 for TC, 0,0,1 for normals) to maintain consistent stride.

## Security / Robustness

- [x] **FileSystemAssetSource path traversal** — Fixed: root normalized at construction, resolved paths checked with `startsWith(root)` after normalization. SecurityException on traversal attempts.
- [ ] **ZipAssetSource suffix search is O(n)** — `ZipAssetSource.findEntry()` iterates all zip entries on every lookup when exact match fails. For large archives, this is slow. Could build a lookup map at construction time.
- [x] **AssetManager.sources/loaders are unsynchronized ArrayLists** — Fixed: swapped to CopyOnWriteArrayList.
- [x] **ShaderManager falls back to raw FileReader** — Fixed: removed FileReader fallback. Now only loads through AssetManager with error logging.

## Missing Math Functionality

- [x] **Mat4 missing inverse() method** — Fixed: added inverse(), determinant(), toMat3().
- [x] **Mat4 missing orthographic projection** — Fixed: added Mat4.ortho(), Camera now uses it instead of private method.
- [x] **No Mat3 type** — Fixed: added Mat3 record with mul, transform, transpose, determinant, inverse, normalMatrix, scale.
- [x] **Vec3 missing common operations** — Fixed: added reflect, clamp, min, max, abs, distance, distanceSquared.

## Dead / Redundant Code

- [ ] **MeshRenderer dual material system** — `MeshRenderer.java` maintains both `materials` (MutablePropertyMap) for legacy transactions AND `materialData` (MaterialData) for the Component path. The Renderer only uses `getMaterialData()`. The legacy `materials` map and `collectBatch()` snapshot are effectively dead code. DrawCommand's `materialData` field (from collectBatch) is also unused by the renderer.
- [ ] **UniformManager.objectUbos never shrinks** — `UniformManager.java:33`. UBO handles keyed by `"obj_" + entity.index()` grow unboundedly. Destroyed entities leave orphaned UBOs. Index reuse means some get overwritten, but the map never shrinks. Same issue with `materialUbos`.
- [ ] **MeshManager.createMeshFromData() duplicates uploadMeshData()** — `MeshManager.java:57-64` and `MeshManager.java:94-119` contain nearly identical buffer creation and upload code. Should reuse.
- [ ] **ImageLoader incompatible with TeaVM** — `ImageLoader.java` uses `javax.imageio.ImageIO` and `java.awt.image.BufferedImage`, which don't exist in TeaVM. Web platform needs an alternative image loading path.
- [x] **DebugUiOverlay allocated when disabled** — Fixed: set to null when debugOverlay=false, null-check on shutdown.
- [ ] **WgpuRenderDevice.memoryFactory unused externally** — The `IntFunction<NativeMemory> memoryFactory` constructor parameter was only used internally (defaulting to `createDefaultMemory`). No external caller ever passed a custom factory. Dead parameter — remove or document intended use for TeaVM.

**Not dead code (do not remove):**
- TeaVM stub classes (`TConcurrentLinkedQueue`, `TCountDownLatch`, `TCleaner` in `providers/windowing/web/teavm-windowing`) — required for TeaVM web target compatibility.

## Hardcoded Values (should be configurable/dynamic)

- [ ] **Primitive topology hardcoded to TRIANGLES** — All draw commands in Gl/Vk/Wgpu RenderDevice. Topology should be part of PipelineDescriptor or DrawCommand. Prevents drawing lines, points, triangle strips.
- [x] **Clear color hardcoded (0.05, 0.05, 0.08)** — Not duplicated: Renderer has setClearColor() API, VkRenderDevice reads it from the Clear command. Both just have matching defaults.
- [ ] **Push constant UBO size hardcoded to 128 bytes** — `GlRenderDevice.java:96`, `VkDescriptorManager.java:101`. Should be configurable via GraphicsConfig or DeviceCapability.
- [ ] **Texture/sampler array sizes: magic numbers** — GL: boundTextures[32], boundSamplers[32]. Vk: currentTextures[8], pendingUboBuffers[16], pendingSsboBuffers[8]. Should be queried from DeviceCapability or configurable.
- [ ] **MAX_FRAMES_IN_FLIGHT=2 hardcoded** — `VkRenderDevice.java:47`. Should be configurable through VulkanConfig.
- [ ] **MAX_SETS_PER_FRAME=256 hardcoded** — `VkDescriptorManager.java:17`. Could be insufficient for complex scenes. Should auto-grow or be configurable.
- [x] **Global param bindings hardcoded** — Fixed: extracted `GlobalParamNames` constants class (`ENGINE`, `CAMERA`, `OBJECT`). Used in `Renderer` and `UniformManager`.
- [ ] **Blend function hardcoded to SRC_ALPHA/ONE_MINUS_SRC_ALPHA** — `GlRenderDevice.java:623`. BlendMode exists but only supports NONE vs one hardcoded alpha blend. Need configurable src/dst factors, blend equation.
- [ ] **All shaders forced to STANDARD_FORMAT vertex layout** — `ShaderManager.java:255`. Uses PrimitiveMeshes.STANDARD_FORMAT for ALL pipelines. Custom vertex formats (tangents, colors, bone weights) won't work.
- [ ] **Shader entry points hardcoded ("vertexMain"/"fragmentMain")** — `ShaderManager.java:227`. Should be configurable per shader for custom entry points.
- [ ] **Camera defaults (near=0.1, far=1000) not in config** — `Camera.java:15`. Should be configurable through EngineConfig or named constants.
- [x] **Deprecated cull face/front face in legacy commands** — Fixed: SetCullFace and all legacy render commands removed from all backends.

## Missing Configuration

- [x] **EngineConfig missing common options** — Added debugOverlay toggle. FPS cap via maxFrames already existed. VSync now in GraphicsConfig.presentMode. Window options in WindowDescriptor.
- [x] **GraphicsConfig missing graphics settings** — Added msaaSamples, srgb, maxAnisotropy, presentMode. Backends can read these during device creation.
- [x] **Wire new GraphicsConfig settings into backends** — Partial: GraphicsConfig passed to all backend constructors. maxAnisotropy caps sampler anisotropy across all backends. sRGB partially wired (GL default framebuffer, VK swapchain auto-select) but not fully tested — custom render targets and WebGPU still missing. MSAA deferred to render pipeline.
- [x] **Wire WindowDescriptor fields into window toolkits** — Fixed: GLFW applies resizable, decorated, highDpi, fullscreen. SDL3 applies resizable, borderless, fullscreen, highDpi.
- [x] **Wire presentMode into WebGPU/OpenGL backends** — Fixed: GraphicsConfig.create() sets SWAP_INTERVAL on window for GL (works with GLFW + SDL3). WebGPU calls setPresentMode() before configureSurface().
- [x] **WindowDescriptor too minimal** — Added resizable, decorated, fullscreen, highDpi fields with builder pattern. Backward-compatible 3-arg constructor preserved.
- [x] **VulkanConfig.Builder doesn't forward GraphicsConfig settings** — Fixed: Builder now exposes msaaSamples, srgb, maxAnisotropy setters and forwards them to GraphicsConfig.

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

- [x] **Present mode configurable via GraphicsConfig** — Added `PresentMode` enum (FIFO/IMMEDIATE/MAILBOX) to `GraphicsConfig`. VulkanConfig uses it for swapchain creation. VulkanConfig.PresentMode removed in favor of shared enum.
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
- [x] **Vulkan: missing DeviceCapability queries** — Fixed: all three backends (GL, VK, WebGPU) now use CapabilityRegistry pattern. VK queries MAX_ANISOTROPY, MAX_UNIFORM_BUFFER_SIZE, MAX_STORAGE_BUFFER_SIZE from VkPhysicalDeviceLimits. WebGPU still uses hardcoded defaults (jwebgpu deviceGetLimits SIGABRT bug).
- [ ] **All backends: primitive topology hardcoded** — Cannot draw lines, points, or triangle strips. Should be part of PipelineDescriptor.
- [ ] **All backends: UINT16 index buffers unsupported** — Wastes memory for small meshes. VK has constant defined but unused.
- [ ] **GL: buffer CPU readback** — glMapNamedBufferRange declared in GlBindings but never called. No staging readback path.
- [ ] **VK/WebGPU: LINE_WIDTH and SCISSOR_TEST not handled** — GL-only render states.
- [ ] **WebGPU: vertex INT type unsupported** — Falls back to FLOAT32X4 with warning.
- [ ] **WebGPU: sampler border color / LOD bias unsupported** — API limitation.

## Architecture / Design Improvements (2026-04-06 deep review)

- [ ] **Renderer should batch all draws into one CommandRecorder** — Currently creates+submits a new CommandRecorder per entity (`Renderer.java:268-308`). This is the single biggest performance issue. Should collect all draw commands into one or few CommandLists, sorted by pipeline/material state to minimize GPU state changes.
- [ ] **UniformManager material layout should be cached per key-set** — The std140 alignment calculation in `uploadAndBindMaterial()` runs per-entity per-frame. The layout only changes when the material's key set changes. Cache the field offsets and total size keyed by the sorted key-set hash.
- [ ] **WeakCache lookup allocates temporary IdentityWeakReference** — `WeakCache.getOrCreate()` creates a short-lived `IdentityWeakReference` for every lookup. On hot paths (per-frame mesh/texture resolution), this generates GC pressure. Consider caching the lookup key or using a different map strategy.
- [ ] **Camera should be a Component** — Camera is currently a standalone mutable object managed by Renderer. Should be a Component on an Entity so it participates in the scene graph, supports multiple cameras naturally, and gets world transforms from the hierarchy.
- [ ] **LightData should be a Component** — LightData exists but is not a Component and not integrated with the scene or renderer. Should implement Component so lights can be attached to entities and participate in the transaction system.
- [ ] **Scene.query() should support component indexing** — `AbstractScene.query()` does a linear scan of all entities per query. For scenes with many entities, this is O(n) per query. Maintain per-component-type index maps for O(1) lookup by component type.
- [ ] **Entity component storage should use array for small counts** — `Entity.java:28`. HashMap for 2-4 components has significant overhead. An array-based map (or even a flat array with linear scan) would be more cache-friendly and use less memory per entity.
- [ ] **Renderer.renderFrame should separate transaction processing from drawing** — Currently interleaves transaction handling, renderable resolution, and drawing in one method. Separation would allow: transaction processing on logic thread, renderable resolution cached across frames, draw batching/sorting.
- [ ] **PipelineDescriptor should include primitive topology** — Currently all pipelines are created without topology info, and all backends hardcode TRIANGLES. Add `PrimitiveTopology` (TRIANGLES, LINES, POINTS, TRIANGLE_STRIP, LINE_STRIP) to PipelineDescriptor.
- [ ] **MaterialData.set() should be O(1) not O(n)** — `MaterialData.java:72-79`. Every `set()` call copies the entire PropertyMap to create a new immutable MaterialData. For materials with many properties being set one at a time, this is O(n²). Consider a builder pattern: `MaterialData.builder().set(...).set(...).build()`.
- [ ] **AbstractScene should not expose setLocalTransform(Handle)** — The legacy `setLocalTransform(Handle, Mat4)` decomposition to Transform is lossy (ignores rotation/scale from the matrix, hardcodes Quat.IDENTITY). Users should use `entity.add(Transform.at(...))` instead.
- [ ] **ShaderManager has three separate caches** — `shaderCache`, `resolvedShaders`, and `entityShaders` all cache overlapping shader data. The relationship between them is unclear and `invalidateAll()` has to clear all three. Unify into a single cache with clear ownership semantics.

## Missing Low-Level Backend Features (2026-04-07 deep backend review)

Features that modern game engines require and that each API supports natively, but are not yet exposed.

### Across All Backends

- [ ] **WebGPU sRGB framebuffer not wired** — GL enables `GL_FRAMEBUFFER_SRGB`, VK auto-selects sRGB surface format, but WebGPU still uses `bgra8unorm`. Needs `bgra8unorm-srgb` surface format constant and wiring through config.
- [ ] **Vulkan pipeline cache not persisted to disk** — Cache is created in-memory and speeds up pipeline creation within a session, but data is not saved/loaded across runs. Add save on close + load on init via `getPipelineCacheData`/`createPipelineCache(initialData)`.

- [ ] **No MSAA (multisample anti-aliasing)** — GraphicsConfig declares `msaaSamples` but no backend reads it. GL needs `glTextureStorage2DMultisample` + `glBlitNamedFramebuffer` resolve. VK needs multisampled images + resolve attachments in render pass. WebGPU needs `multisample` in pipeline descriptor + resolve target. Critical for visual quality.
- [ ] **No sRGB framebuffer support** — GraphicsConfig declares `srgb` but no backend creates sRGB swapchain/framebuffer formats. GL needs `GL_FRAMEBUFFER_SRGB`. VK needs `VK_FORMAT_B8G8R8A8_SRGB`. WebGPU needs `bgra8unorm-srgb`. Without this, all rendering is in linear space with no gamma correction.
- [ ] **No occlusion queries** — GL has `GL_SAMPLES_PASSED`, VK has `VK_QUERY_TYPE_OCCLUSION`, WebGPU has `occlusion-query`. Needed for: conditional rendering, visibility-based LOD, GPU-driven culling.
- [ ] **No timestamp queries / GPU profiling** — GL has `GlGpuTimer` (exists but unused by engine). VK has `VK_QUERY_TYPE_TIMESTAMP`. WebGPU has `timestamp` query set. Needed for: per-pass GPU timing, profiler overlay, performance debugging.
- [ ] **No texture sub-image upload (mip levels, array layers, 3D slices)** — `uploadTexture()` always writes the full mip 0 level. No API to upload specific mip levels, individual array layers, cube faces independently, or 3D slices. Needed for: streaming textures, dynamic cubemaps, volume textures, manual mip chains.
- [ ] **No texture readback** — Can read the framebuffer via `readFramebuffer()`, but no way to read an arbitrary texture back to CPU. Needed for: screenshot of offscreen targets, GPU-computed data readback, picking.
- [ ] **No buffer readback (GL)** — `glMapNamedBufferRange` is declared in GlBindings but never called. VK and WebGPU have staging readback. Needed for: compute shader output, transform feedback, GPU-driven culling results.
- [ ] **No render pass load/store actions** — Clear always clears color+depth. No ability to specify: LOAD (preserve previous contents), DONT_CARE (discard for performance), or CLEAR per-attachment. VK and WebGPU render passes support this natively. Critical for: multi-pass rendering, tiled GPU optimization.
- [ ] **No dynamic viewport/scissor array** — Only one viewport/scissor rect supported. GL supports `glViewportArrayv`, VK supports multiple viewports. Needed for: VR stereo rendering, layered rendering, multi-view.
- [ ] **No tessellation shader support** — All three backends report TESSELLATION capability as true (GL/VK) but no pipeline creation or draw path supports tess control/evaluation shaders. ShaderStage only has VERTEX/FRAGMENT/GEOMETRY/COMPUTE — no TESS_CONTROL or TESS_EVALUATION.
- [ ] **No geometry shader integration** — GEOMETRY_SHADERS reports true but no pipeline path includes geometry shaders in the compilation/linking flow. GlRenderDevice.mapShaderStage() maps it but PipelineDescriptor only takes vertex+fragment.
- [ ] **No render target MSAA resolve** — Even if MSAA textures are created, no command exists to resolve multisampled to single-sampled. GL uses `glBlitNamedFramebuffer`, VK uses resolve attachments, WebGPU uses `resolveTarget` in render pass.
- [ ] **No depth bias / polygon offset** — Needed for shadow mapping to prevent shadow acne. GL has `glPolygonOffset`, VK has `vkCmdSetDepthBias`, WebGPU has `depthBias`/`depthBiasSlopeScale` in pipeline. Not in RenderState or any backend.
- [ ] **No color write mask** — Can't disable writing to individual color channels (R/G/B/A). GL has `glColorMask`, VK has `colorWriteMask` in blend attachment, WebGPU has `writeMask` in color target. Needed for: deferred rendering G-buffer, selective channel writes.
- [ ] **No multiple vertex buffer binding** — `BindVertexBuffer` binds exactly one VBO to binding point 0. No support for multiple vertex streams (e.g., position in buffer 0, normals in buffer 1, instance data in buffer 2). GL supports arbitrary binding points, VK/WebGPU support multiple vertex buffer slots.
- [ ] **No sub-allocation / memory aliasing** — Each buffer/texture gets its own allocation. No suballocator for batching small allocations into larger GPU pages. VK and WebGPU both benefit significantly from this for uniform/staging buffers.

### OpenGL-Specific Missing Features

- [ ] **No persistent mapped buffers** — `writeBuffer()` allocates a staging segment and copies via `glNamedBufferSubData` every time. GL 4.4+ supports persistent mapped buffers via `GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT` which avoids the copy entirely. Critical for streaming uniform/vertex data.
- [ ] **No buffer storage immutability** — Buffers are created with `glNamedBufferData` (mutable). Should use `glNamedBufferStorage` with `GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT` for buffers that won't be resized. Better driver optimization.
- [ ] **No conditional rendering** — `glBeginConditionalRender` / `glEndConditionalRender` with occlusion query results. Skips draw calls on GPU without CPU round-trip.
- [ ] **No transform feedback** — `glBeginTransformFeedback` for capturing vertex shader output to buffers. Useful for GPU particle systems, stream compaction.
- [ ] **No program pipeline objects (separable shaders)** — Could enable faster shader swapping by separating vertex and fragment stages into independent objects. GL 4.1+.
- [ ] **No debug labels/markers** — `glPushDebugGroup` / `glPopDebugGroup` / `glObjectLabel` for RenderDoc/NSight integration. Should be wrapped into RenderCommand or context helpers.

### Vulkan-Specific Missing Features

- [ ] **No VMA or sub-allocator** — Every `createBuffer`/`createImage` does a separate `vkAllocateMemory`. Vulkan has a hard limit on total allocations (~4096 on many drivers). A sub-allocator (or VMA-equivalent) is mandatory for non-trivial scenes.
- [x] **No pipeline cache** — Fixed: VkPipelineCache created at device init, passed to all graphics and compute pipeline creation, destroyed on close. Disk persistence not yet implemented.
- [ ] **No secondary command buffers** — Only one command buffer per frame. Secondary command buffers enable parallel recording from multiple threads — the whole point of Vulkan's explicit model.
- [ ] **No dynamic rendering (VK_KHR_dynamic_rendering)** — The engine uses VkRenderPass objects which require framebuffer/render-pass compatibility. Dynamic rendering eliminates render pass objects entirely, simplifying multi-pass and reducing state.
- [ ] **No descriptor set indexing / bindless descriptors** — Uses one descriptor set per draw. `VK_EXT_descriptor_indexing` enables thousands of textures/buffers bound at once. Essential for: asset streaming, virtual texturing, GPU-driven rendering.
- [ ] **No timeline semaphores** — Using binary semaphores only. Timeline semaphores (`VK_KHR_timeline_semaphore`, core in 1.2) simplify multi-queue synchronization.
- [ ] **No swapchain recreation on window resize** — `recreateSwapchain()` uses the old dimensions: `swapchain.create(swapchain.width(), swapchain.height())`. Should query the current surface extent or accept new dimensions. Resizing the window will not resize the render output.
- [ ] **VkFence stub** — `createFence()` returns a no-op stub that always returns "signaled". Real fences are needed for: async compute/transfer, frame pacing, readback synchronization.
- [ ] **No separate transfer queue** — All operations use the graphics queue. Using a dedicated transfer queue allows overlapping upload with rendering. Most discrete GPUs have dedicated async transfer queues.
- [ ] **Vulkan validation errors on shutdown are likely** — Destroying device-owned resources after `vkDeviceWaitIdle` but the order of `destroyAll` calls may still hit use-after-free in validation layers if render targets reference textures that were already destroyed in the same batch.
- [ ] **No push descriptor extension** — `VK_KHR_push_descriptor` avoids descriptor set allocation entirely for frequently updated bindings. Ideal for per-object UBOs.

### WebGPU-Specific Missing Features

- [ ] **No compute pipeline** — `createComputePipeline()` not implemented. WebGPU fully supports compute shaders via `GPUComputePipeline`.
- [ ] **No mipmap generation** — WebGPU has no built-in mipmap generation. Need compute shader or blit-based solution. Textures with mipmap samplers only use mip 0.
- [ ] **No indirect drawing** — `DrawIndirect`/`DrawIndexedIndirect` log warnings. WebGPU supports `drawIndirect` and `drawIndexedIndirect` on `GPURenderPassEncoder`.
- [ ] **No push constants emulation** — `PushConstants` command is ignored. WebGPU has no native push constants — should emulate via a dedicated UBO at a reserved binding (like GL does at binding 15).
- [ ] **No surface configuration for presentation** — Surface presentation is partially implemented but `presentToSurface` mode has no resize handling, no present mode selection (though FIFO/Mailbox are supported by wgpu-native).
- [ ] **No device limits queried** — Comment says `deviceGetLimits` is broken in jwebgpu 0.1.15. All limits are hardcoded (8192, 65536, etc.). Should be revisited when jwebgpu is updated.
- [ ] **No error handling / device lost callback** — WebGPU devices can be "lost" (driver crash, timeout). `GPUDevice.lost` promise should be handled to notify the engine and attempt recovery.
- [ ] **No render bundle support** — WebGPU's `GPURenderBundle` enables pre-recording draw calls for static geometry. Significant optimization for scenes with many static objects.

### Missing DeviceCapability Queries

- [ ] **Vulkan MAX_ANISOTROPY not queried** — Should query `VkPhysicalDeviceLimits.maxSamplerAnisotropy`.
- [ ] **Vulkan MAX_UNIFORM_BUFFER_SIZE not queried** — Should query `maxUniformBufferRange`.
- [ ] **Vulkan MAX_STORAGE_BUFFER_SIZE not queried** — Should query `maxStorageBufferRange`.
- [ ] **No MAX_COMPUTE_WORK_GROUP_SIZE query** — Needed for compute shader dispatch. All three APIs expose this.
- [ ] **No MAX_COLOR_ATTACHMENTS query** — GL: `GL_MAX_COLOR_ATTACHMENTS`. VK: `maxColorAttachments`. WebGPU: `maxColorAttachments`. Needed for MRT validation.
- [ ] **No VRAM usage / budget query** — VK has `VK_EXT_memory_budget`. GL has `NVX_gpu_memory_info` / `ATI_meminfo`. Useful for: streaming budget, LOD decisions, memory pressure detection.
- [ ] **No supported texture format query** — VK has `vkGetPhysicalDeviceFormatProperties`. WebGPU has feature flags for compressed formats. Currently all formats are assumed supported with fallback warnings.

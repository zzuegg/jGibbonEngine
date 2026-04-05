# Engine Code Review TODO

Full code review performed 2026-04-05 across all 467 source files.

## Critical Bugs

- [x] **Thread safety: Engine.run() race condition** — `Engine.java:152`. Render thread drains TransactionBuffer while logic thread writes concurrently. TransactionBuffer has no synchronization. Use TransactionBus (which has per-subscriber locks) or add synchronization.
- [x] **Profiler.lastFrame() returns currentFrame** — `Profiler.java:29`. Renamed to `currentFrame()`; `lastFrame()` now correctly returns the previous completed frame. Tests updated accordingly.
- [x] **GPU buffer leak: per-entity UBOs never cleaned up** — `UniformManager.java`. Per-entity UBO maps now keyed by `Handle<?>` (index + generation) instead of string. `removeEntity()` destroys the buffers when an entity is removed. `Renderer.renderFrame()` calls it for each `EntityRemoved` transaction.
- [x] **Stale shader binding after entity reuse** — `ShaderManager.java`. `removeEntityShader()` added; called by `Renderer.renderFrame()` on `EntityRemoved`.
- [x] **Engine.shutdown() doesn't shut down AssetManager** — `Engine.java`. Added `assets.shutdown()` to `shutdown()`.
- [x] **Renderer.close() doesn't clean up MeshManager** — `Renderer.java`. Added `meshManager.close()` to `close()`. Added `MeshManager.close()` that destroys all GPU buffers and vertex inputs.
- [x] **MeshRenderer leaks entity maps on EntityRemoved** — `MeshRenderer.java`. `meshDataAssignments`, `meshAssignments`, and `materialAssignments` were not cleared on `EntityRemoved`.
- [x] **Vec2 alignment missing from material UBO write loop** — `UniformManager.java`. Size calculation aligned Vec2 to 8 bytes, but the write offset loop did not. Produced a layout mismatch (GPU reads wrong bytes for any material with Vec2 properties after an unaligned predecessor). Fixed by adding `offset = align(offset, 8)` before writing Vec2.
- [x] **Remaining hardcoded `"Object"` string in `bindGlobals()`** — `UniformManager.java`. The `bindGlobals()` method still used the raw literal `"Object"` instead of `GlobalParamNames.OBJECT`. Now consistent with the rest of the class.
- [ ] **Material UBO silently wrong-sized on property change** — `UniformManager.uploadAndBindMaterial()`. The `materialUbos` map uses `computeIfAbsent` keyed by entity handle, allocating a buffer sized for the *first* set of material properties. If properties are subsequently changed to include new scalar keys (or different types), the existing buffer keeps its original size. The oversized-write silently goes into unallocated GPU memory. The fix requires either reallocating when the required size grows, or (better) deriving the UBO size from the shader reflection, not from the property count at runtime.

## Hardcoded Values (should be configurable/dynamic)

- [ ] **Primitive topology hardcoded to TRIANGLES** — All draw commands in Gl/Vk/Wgpu RenderDevice. Topology should be part of PipelineDescriptor or DrawCommand. Prevents drawing lines, points, triangle strips.
- [ ] **Clear color hardcoded (0.05, 0.05, 0.08)** — `Renderer.java:65`, `VkRenderDevice.java:60`. Duplicated magic constant. Default should come from config.
- [ ] **Push constant UBO size hardcoded to 128 bytes** — `GlRenderDevice.java:96`, `VkDescriptorManager.java:101`. Should be configurable via GraphicsConfig or DeviceCapability.
- [ ] **Texture/sampler array sizes: magic numbers** — GL: boundTextures[32], boundSamplers[32]. Vk: currentTextures[8], pendingUboBuffers[16], pendingSsboBuffers[8]. Should be queried from DeviceCapability or configurable.
- [ ] **MAX_FRAMES_IN_FLIGHT=2 hardcoded** — `VkRenderDevice.java:47`. Should be configurable through VulkanConfig.
- [ ] **MAX_SETS_PER_FRAME=256 hardcoded** — `VkDescriptorManager.java:17`. Could be insufficient for complex scenes. Should auto-grow or be configurable.
- [x] **Global param bindings hardcoded** — `Renderer.java:79-81`. Extracted to `GlobalParamNames` constants class (`ENGINE`, `CAMERA`, `OBJECT`). Used in `Renderer` and `UniformManager`.
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
- [ ] **TransactionBus never used** — Multi-consumer filtered bus implemented. Only the simpler TransactionBuffer is used. Bus is more appropriate for the architecture described in NOTES.md.
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
- [ ] **WeakCache.getOrCreate() linear scan** — `WeakCache.java:35`. Iterates entire map for identity lookup. O(n) per call. Use IdentityHashMap-based index.
- [ ] **HierarchicalScene.getWorldTransform() recursive, uncached** — Walks parent chain every call. O(depth) per entity per frame. Cache world transforms, invalidate on change.
- [ ] **AbstractScene.query() linear scan** — Iterates all entities per query. No component index. O(n) per query.
- [ ] **Entity HashMap for 2-4 components** — HashMap overhead for typical small component counts. Array-based map would be more efficient.

## Missing Renderer Features

- [ ] **No lighting system** — LightData/LightType exist in core but Renderer has zero light handling. No light buffer upload, no light culling. MeshRenderer ignores LightData components.
- [ ] **No shadow mapping** — Extensively designed in NOTES.md. No shadow pass, no shadow maps, no light-space matrices. LightData.CASTS_SHADOWS exists but nothing reads it.
- [x] **RenderStats never populated** — `RenderStats.java`. `Renderer.renderFrame()` now calls `recordDrawCall()`, `recordPipelineBind()`, `recordBufferBind()`, `recordTextureBind()`. Stats are reset at the top of each `renderFrame()` call. `Engine.renderStats()` now delegates to `renderer.renderStats()` (stats are owned by Renderer).

## Code Quality / API

- [ ] **BaseApplication Javadoc example uses deprecated API** — The `launch()` method Javadoc still shows `.graphicsBackend(OpenGlBackend.factory(glBindings))` which is the deprecated path. Should show the new `.graphics(new OpenGlConfig(...))` API.
- [ ] **`BaseApplication` sets viewport twice per frame** — `BaseApplication.java:124+129`. `WindowEvent.Resized` handler calls `setViewport(r.width, r.height)`. Then line 129 unconditionally calls `setViewport(window.width(), window.height())` every frame regardless. The second unconditional call makes the resize handler redundant. Either remove the event handler or remove the unconditional per-frame call.
- [ ] **`DrawCommand.materialData` raw unchecked generic** — `DrawCommand.java`. `PropertyMap materialData` is raw type (no `<MaterialData>` parameter). Should be `PropertyMap<MaterialData>` for type safety.

## Architectural Cleanup

- [ ] **Deprecated APIs to remove** — GraphicsBackendFactory, GraphicsConfigLegacy, EngineConfig.graphicsBackend field, GlfwWindowToolkit in graphics:opengl, deprecated SetDepthTest/SetBlending/SetCullFace/SetWireframe commands. New APIs (GraphicsConfig, SetRenderState) are in place.
- [x] **ResourceCleaner.register() package-private** — Made `public` so GPU resource owners outside `core.resource` can register cleanup actions.
- [ ] **NativeResource not used by GPU resources** — Interface exists but no GPU resource implements it. No Cleaner safety net for dropped handles.
- [ ] **GpuResourceManager deferred deletion delay doesn't match frames-in-flight** — `GpuResourceManager.java:175`. Double-buffer swap means N+2 deletion. If Vulkan uses 3 frames-in-flight, resources freed while still in use. Tie to actual fence/frame count.

## Previously Identified (from 2026-04-04 review)

- [ ] **WebGPU compute shader support** — `WgpuRenderDevice.java`. Push constants, compute pipeline, dispatch, storage images, texture copy/blit unimplemented.
- [ ] **Vulkan streaming buffer** — `VkRenderDevice.java:603-605`. Returns null. Needs persistent mapped buffer with ring buffer regions.
- [ ] **Vulkan storage image (bindImage)** — `VkRenderDevice.java:1006-1008`. Needs VK_DESCRIPTOR_TYPE_STORAGE_IMAGE support.
- [ ] **SDL3 window property stubs** — `Sdl3WindowToolkit.java`. get(), set(), sizeRef(), focusedRef(), swapBuffers() are stubs.
- [ ] **Async buffer mapping for TeaVM WebGPU** — Browser can't do synchronous mapping. Needs promise wrapper or skip readback on web.
- [ ] **Slang generic specialization parsing robustness** — `SlangCompilerNative.java:245-276`. Current regex works. Revisit when it breaks.
- [ ] **WGSL binding extraction regex** — `WgpuRenderDevice.java:623-625`. Works for current shaders. Revisit on Slang output changes.


## Hardcoded Values (should be configurable/dynamic)

- [ ] **Primitive topology hardcoded to TRIANGLES** — All draw commands in Gl/Vk/Wgpu RenderDevice. Topology should be part of PipelineDescriptor or DrawCommand. Prevents drawing lines, points, triangle strips.
- [ ] **Clear color hardcoded (0.05, 0.05, 0.08)** — `Renderer.java:65`, `VkRenderDevice.java:60`. Duplicated magic constant. Default should come from config.
- [ ] **Push constant UBO size hardcoded to 128 bytes** — `GlRenderDevice.java:96`, `VkDescriptorManager.java:101`. Should be configurable via GraphicsConfig or DeviceCapability.
- [ ] **Texture/sampler array sizes: magic numbers** — GL: boundTextures[32], boundSamplers[32]. Vk: currentTextures[8], pendingUboBuffers[16], pendingSsboBuffers[8]. Should be queried from DeviceCapability or configurable.
- [ ] **MAX_FRAMES_IN_FLIGHT=2 hardcoded** — `VkRenderDevice.java:47`. Should be configurable through VulkanConfig.
- [ ] **MAX_SETS_PER_FRAME=256 hardcoded** — `VkDescriptorManager.java:17`. Could be insufficient for complex scenes. Should auto-grow or be configurable.
- [x] **Global param bindings hardcoded** — `Renderer.java:79-81`. Extracted to `GlobalParamNames` constants class (`ENGINE`, `CAMERA`, `OBJECT`). Used in `Renderer` and `UniformManager`.
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
- [ ] **TransactionBus never used** — Multi-consumer filtered bus implemented. Only the simpler TransactionBuffer is used. Bus is more appropriate for the architecture described in NOTES.md.
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
- [ ] **WeakCache.getOrCreate() linear scan** — `WeakCache.java:35`. Iterates entire map for identity lookup. O(n) per call. Use IdentityHashMap-based index.
- [ ] **HierarchicalScene.getWorldTransform() recursive, uncached** — Walks parent chain every call. O(depth) per entity per frame. Cache world transforms, invalidate on change.
- [ ] **AbstractScene.query() linear scan** — Iterates all entities per query. No component index. O(n) per query.
- [ ] **Entity HashMap for 2-4 components** — HashMap overhead for typical small component counts. Array-based map would be more efficient.

## Missing Renderer Features

- [ ] **No lighting system** — LightData/LightType exist in core but Renderer has zero light handling. No light buffer upload, no light culling. MeshRenderer ignores LightData components.
- [ ] **No shadow mapping** — Extensively designed in NOTES.md. No shadow pass, no shadow maps, no light-space matrices. LightData.CASTS_SHADOWS exists but nothing reads it.
- [x] **RenderStats never populated** — `RenderStats.java`. `Renderer.renderFrame()` now calls `recordDrawCall()`, `recordPipelineBind()`, `recordBufferBind()`, `recordTextureBind()`. Stats are reset at the top of each `renderFrame()` call. `Engine.renderStats()` now delegates to `renderer.renderStats()` (stats are owned by Renderer).

## Architectural Cleanup

- [ ] **Deprecated APIs to remove** — GraphicsBackendFactory, GraphicsConfigLegacy, EngineConfig.graphicsBackend field, GlfwWindowToolkit in graphics:opengl, deprecated SetDepthTest/SetBlending/SetCullFace/SetWireframe commands. New APIs (GraphicsConfig, SetRenderState) are in place.
- [x] **ResourceCleaner.register() package-private** — Made `public` so GPU resource owners outside `core.resource` can register cleanup actions.
- [ ] **NativeResource not used by GPU resources** — Interface exists but no GPU resource implements it. No Cleaner safety net for dropped handles.
- [ ] **GpuResourceManager deferred deletion delay doesn't match frames-in-flight** — `GpuResourceManager.java:175`. Double-buffer swap means N+2 deletion. If Vulkan uses 3 frames-in-flight, resources freed while still in use. Tie to actual fence/frame count.

## Previously Identified (from 2026-04-04 review)

- [ ] **WebGPU compute shader support** — `WgpuRenderDevice.java`. Push constants, compute pipeline, dispatch, storage images, texture copy/blit unimplemented.
- [ ] **Vulkan streaming buffer** — `VkRenderDevice.java:603-605`. Returns null. Needs persistent mapped buffer with ring buffer regions.
- [ ] **Vulkan storage image (bindImage)** — `VkRenderDevice.java:1006-1008`. Needs VK_DESCRIPTOR_TYPE_STORAGE_IMAGE support.
- [ ] **SDL3 window property stubs** — `Sdl3WindowToolkit.java`. get(), set(), sizeRef(), focusedRef(), swapBuffers() are stubs.
- [ ] **Async buffer mapping for TeaVM WebGPU** — Browser can't do synchronous mapping. Needs promise wrapper or skip readback on web.
- [ ] **Slang generic specialization parsing robustness** — `SlangCompilerNative.java:245-276`. Current regex works. Revisit when it breaks.
- [ ] **WGSL binding extraction regex** — `WgpuRenderDevice.java:623-625`. Works for current shaders. Revisit on Slang output changes.

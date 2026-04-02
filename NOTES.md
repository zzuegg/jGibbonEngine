# Engine Design Notes

## Code Conventions

### Enums

Only use enums when the set of values is **guaranteed** to never be extended. If there is any chance a user or future module might need to add a variant, use an interface/sealed interface with static instances instead. Enums lock down extensibility — once a `switch` or pattern match exists, adding a variant is a breaking change everywhere.

### Modularity and Reuse

Code should be modular and reusable where possible. Prefer small, focused components with clear boundaries over monolithic classes. Each piece should be usable independently — if a utility or subsystem can only function inside the engine, it's too tightly coupled.

### Multithreading

Multithreading support is a hard requirement. Every component must be designed thread-safe from the start — retrofitting concurrency is not an option. Prefer immutable data, lock-free structures, and message passing over shared mutable state with locks.

### Minimal Dependencies

Add as few external libraries as possible. Prefer writing engine-owned implementations over pulling in dependencies. Exceptions only for things that are genuinely impractical to implement in-house (e.g. native bindings like LWJGL for OpenGL/Vulkan access, Slang compiler). SLF4J for logging is acceptable. Everything else — math, collections, utilities — is engine code.

### Math as Records (Valhalla-Ready)

All math types (`Vec2`, `Vec3`, `Vec4`, `Mat3`, `Mat4`, `Quat`, etc.) are implemented as Java records. This makes them:
- **Immutable** — thread-safe by default, no defensive copies needed.
- **Value semantics** — identity-free, equality by components.
- **Valhalla-ready** — when Project Valhalla ships value types, records are the natural migration path. Switching from `record` to `value record` should be a one-line change per type with zero API breakage.

Mutable operations return new instances. For hot paths where allocation pressure matters, provide static methods that write directly into `MemorySegment` / `float[]` buffers without creating intermediate objects.

### Native Resource Management

All native resources (GPU buffers, textures, shaders, native memory, file handles, etc.) must be tracked through a **Cleaner**-based mechanism (`java.lang.ref.Cleaner`). No reliance on `finalize()`. Every native allocation registers a cleaning action at creation time, guaranteeing cleanup even if the user forgets to close explicitly.

- Resources implement `AutoCloseable` for deterministic cleanup (try-with-resources).
- The Cleaner acts as a safety net — if a resource becomes unreachable without being closed, the cleaner reclaims it.
- Cleanup must happen on the correct thread/context (e.g. GL resources on the GL thread). The cleaner queues a release action to the owning context rather than freeing directly.
- Deferred destruction for frames-in-flight — resources are not freed immediately but after the GPU is done with them (fence-based or frame-count-based).

## Rendering Architecture

Triple-backend (OpenGL + Vulkan + WebGPU) behind a backend-agnostic abstraction.

### Key Abstractions

- **RenderDevice** — represents the GPU. Creates resources (buffers, textures, shaders, pipelines), queries capabilities and state.
- **RenderContext** — represents a frame in flight. Game records draw commands without knowing the backend.
- **Opaque handles** — game gets `BufferHandle`, `TextureHandle`, `PipelineHandle` etc. Backend maps them to GL names or Vulkan objects internally. Enables deferred destruction for Vulkan frames-in-flight.

### Design Decisions

- **Command-oriented, not state-machine** — design around the explicit model shared by Vulkan and WebGPU. OpenGL backend translates trivially. Reverse direction is painful.
- **Pipeline State Objects** — bundle rasterizer state, blend mode, depth test, shaders into one immutable object. Vulkan requires this; OpenGL emulates by diffing against current state.
- **Capability query system** — `RenderDevice.query(RenderCap)` returns normalized values regardless of backend (MAX_TEXTURE_SIZE, VRAM_USAGE, SUPPORTED_FORMATS, etc.).
- **Frame lifecycle** — `device.beginFrame()` → `RenderContext` → record commands → `device.endFrame()`.

### What NOT to Leak

- No descriptor sets, render passes, or command pools in the game-facing API.
- No attempt to expose every feature of both APIs — common denominator, extend per-backend where needed.

### Approach

- Get OpenGL working first, then factor out the interface when adding Vulkan and WebGPU.
- WebGPU is a natural fit alongside Vulkan — both are explicit, command-oriented APIs. The abstraction layer should map cleanly to all three.

## Buffer System

### Two Concepts

- **BufferHandle** — opaque, immutable GPU resource. Created with a descriptor specifying size, usage, and access pattern.
- **BufferWriter** — data transfer mechanism. Acquired from `device.writeBuffer(buffer)`. Wraps mapped memory or staging buffer transparently. Flushes/uploads on close (try-with-resources).

### Access Patterns (declared at creation, drive backend strategy)

- **STATIC** — upload once, use many. Device-local (Vulkan) / `GL_STATIC_DRAW` (OpenGL).
- **DYNAMIC** — updated frequently (per-frame uniforms). Persistent mapping or host-visible memory.
- **STREAM** — write once, use once, discard. Ring buffers or transient allocations.

### Backend Translation

| | OpenGL | Vulkan | WebGPU |
|---|---|---|---|
| STATIC | `glBufferData` or staging+copy | Staging buffer → `vkCmdCopyBuffer` to device-local | `createBuffer` with `mappedAtCreation` or `writeBuffer` |
| DYNAMIC | `glMapBufferRange` persistent | Persistently mapped host-visible memory or staging ring | `writeBuffer` per frame or staging via `mapAsync` |
| STREAM | Orphan + `glMapBufferRange` unsynchronized | Per-frame ring buffer from host-visible pool | `writeBuffer` (internally ring-buffered by implementation) |

### Partial Updates

`device.writeBuffer(buffer, offset, length)` — translates to `glBufferSubData`, mapped range offset, or staging copy with offset.

### MemorySegment (Java 26 FFM API)

BufferWriter hands out `MemorySegment` views of mapped/staging regions. Enables:
- Off-heap memory with deterministic lifetime
- Direct pointer access for mapped buffers
- Zero-copy interop with native libs
- Structured layouts for vertex formats

### Synchronization (hidden from game)

Backend internally tracks:
- Which frame last wrote to a buffer
- Whether a buffer is in use by the GPU
- Double/triple-buffering of dynamic resources per frame-in-flight

Game never sees fences, barriers, or ring buffer indices. The handle resolves to the correct backing memory for the current frame.

## Structured Layouts from Records

### Auto-generated StructLayouts

Java records are the single source of truth for data structures. `StructLayout` is derived at startup via reflection on record components, mapping types to `ValueLayout`s. No hand-written layouts.

### Challenges

- **Nested types** — records containing other records (e.g. `Vec3`, `Mat4`) resolved recursively. Works if all types in the chain are records.
- **GPU alignment (std140/std430)** — handled via annotations (e.g. `@Std140`) or layout strategy objects that insert padding automatically per target format.
- **Fast serialization** — `VarHandle`s and writers are built once at layout-creation time and cached per record type. Hot path is zero-reflection, just VarHandle sets.

### Usage Pattern

```
VertexFormat.of(Vertex.class)  →  reflects once, builds StructLayout + cached writers
format.write(segment, offset, vertex)  →  hot path, no reflection
```

### Slang Shader Struct Generation

StructLayouts derived from records can also emit Slang struct definitions. Single source of truth: the Java record defines both the CPU-side memory layout AND the GPU-side shader struct.

Flow: `Java Record → StructLayout → Slang struct source → injected into shader at compile time`

Example:
```
record PointLight(Vec3 position, float intensity, Vec3 color, float radius) {}
```
Generates:
```
struct PointLight {
    float3 position;
    float intensity;
    float3 color;
    float radius;
};
```

This eliminates layout mismatches between CPU and GPU — the record is the contract. Shader compilation can include generated structs via preprocessing/injection before passing to the Slang compiler.

## Shader System (Slang)

All shaders are written in **Slang** and compiled at runtime. No pre-compiled shader binaries, no offline SPIR-V compilation step.

### Why Slang

- Single shading language targeting all three backends (GLSL for OpenGL, SPIR-V for Vulkan, WGSL/SPIR-V for WebGPU).
- Module system, generics, interfaces — proper language features instead of preprocessor hacks.
- Automatic differentiation (future: ML/physics integration).
- Struct generation from Java records feeds directly into Slang source (see above).

### Runtime Compilation Pipeline

```
Slang source (.slang)
  + generated struct definitions (from Java records)
  + material property declarations (from property bag schema)
  → Slang compiler (via native FFM bindings)
  → target-specific output:
      OpenGL  → GLSL source → glShaderSource/glCompileShader
      Vulkan  → SPIR-V binary → VkShaderModule
      WebGPU  → WGSL source or SPIR-V → GPUShaderModule
```

### Compilation Caching

- Compiled shader variants are cached by a hash of (source + defines + target backend).
- Cache is persistent across sessions (disk cache) and in-memory for the current run.
- Hot-reloading: when a `.slang` file changes, recompile affected variants and swap them into live pipelines without restarting.

### Variant Generation

The renderer drives variant compilation based on material properties:
- Each unique property combination → a set of `#define`s or Slang specialization parameters.
- Variants are compiled on-demand (first use) and cached.
- A fallback/error shader is always available so missing variants never cause a black screen.

### Custom Shaders

Users write custom `.slang` files. The engine provides:
- A standard library of common functions (lighting, PBR, noise, etc.) importable via Slang modules.
- A contract interface — custom shaders implement a known entry point signature, receive material properties as parameters.
- The same hot-reload and caching infrastructure applies to custom shaders.

## Scene → Renderer Communication (Transactions)

The renderer is **completely independent** of the scene. It has no reference to scene objects, no knowledge of the scene graph, and never reads scene state directly. All communication flows one way: the scene produces transactions, the renderer consumes them.

This means:
- The renderer is a standalone system that maintains its own internal representation (GPU buffers, draw lists, spatial structures).
- The scene could be replaced, run on a different machine, or not exist at all — as long as something feeds valid transactions, the renderer works.
- Testing the renderer doesn't require a scene — just feed it transaction sequences.
- The scene never waits on the renderer and the renderer never reaches back into the scene.

### Transaction Types

Changes are categorized so the renderer can handle them with minimal work:

- **Added(entity)** — new object entered the scene. Renderer allocates GPU resources, picks a shader, inserts into spatial structures.
- **Removed(entity)** — object left the scene. Renderer schedules deferred destruction (respects frames-in-flight).
- **TransformChanged(entity, new transform)** — position/rotation/scale changed. Renderer updates the transform buffer, re-evaluates culling, may invalidate shadow maps. Does NOT触 trigger shader or material re-evaluation.
- **MaterialChanged(entity, property key, new value)** — a single material property changed (e.g. roughness, albedo color). Renderer updates the property in the material uniform/storage buffer. May trigger shader variant switch if the change affects the shader key (e.g. toggling transparency).
- **MaterialReplaced(entity, new material)** — entire material swapped. Renderer re-evaluates shader selection and re-binds.
- **MeshChanged(entity, new mesh)** — geometry swap. Renderer re-binds vertex/index buffers.
- **LightChanged(light, property, value)** — light parameter changed. Renderer updates light buffer, may invalidate shadow maps for that light.

### Why Fine-Grained

- A position change should never cause a material rebind or shader recompile.
- A material property tweak (e.g. color) should never trigger a full pipeline rebuild — only a uniform update.
- Batching by type lets the renderer process all transform updates in one pass, all material updates in another, etc.

### Transaction Buffer

Transactions accumulate during the logic tick into a per-frame transaction list. At the sync point (frame snapshot swap), the list is handed to the renderer as part of the snapshot. The renderer drains it, applies changes, then discards it.

## Materials as Property Bags

A material is a flat collection of typed properties (floats, vectors, textures, booleans). The renderer inspects these properties to select the appropriate shader/pipeline.

### Property-Driven Shader Selection

The renderer maintains a mapping from property combinations → shader variants. Examples:

| Properties present | Shader selected |
|---|---|
| `albedo`, `normal`, `roughness`, `metallic` | PBR standard |
| `albedo`, `normal`, `roughness`, `metallic`, `emissive` | PBR emissive |
| `albedo`, `opacity < 1.0` | Transparent forward |
| `albedo` only | Unlit |

The material never names a shader — the renderer figures it out from what properties are set.

### Custom Shaders

Users can bypass automatic selection by attaching a custom shader directly:

- `material.setShader(customShader)` — overrides automatic selection entirely. The renderer uses this shader as-is.
- Custom shaders still receive the material's properties as uniforms — the property bag is the data contract regardless of which shader consumes it.
- This allows full creative control while keeping the common case (90%+ of materials) zero-config.

### Property Change Granularity

When a single property changes, the transaction carries the property key and new value — not the entire material. The renderer can:
- Update just that slot in the material's GPU buffer.
- Check if the change crosses a shader variant boundary (e.g. `opacity` going from 1.0 to 0.5 triggers a switch from opaque to transparent pipeline).
- Skip entirely if the property doesn't affect the current render pass (e.g. emissive color change doesn't matter for shadow pass).

## Asset Manager

Central system for loading, caching, and managing all engine assets (textures, meshes, shaders, materials, sounds, etc.).

### Core Responsibilities

- **Caching** — assets are loaded once and shared via handles. Duplicate load requests return the cached instance. Reference counting or GC-based eviction for unused assets.
- **Async Loading** — asset loads happen off the main thread. Callers get a handle immediately; the handle resolves when the data is ready. Placeholder/fallback assets are used until loading completes.
- **Hot Reloading** — file watchers detect changes on disk and trigger reload. The asset handle stays the same — dependents see the updated data without rebinding. Essential for iteration during development.

### Asset Sources

Assets can come from multiple sources, abstracted behind a unified interface:

- **Filesystem** — local directory, the default during development.
- **Archive / Pack file** — bundled assets for distribution (e.g. zip, custom format).
- **Network** — remote asset server for streaming or editor workflows.
- **Generated** — procedurally created assets registered into the manager like any other.

Sources are composable — the manager searches them in priority order (e.g. local overrides → pack file → network fallback).

### Flexible Loader System

Asset loading is driven by pluggable **loaders** registered per asset type. Each loader knows how to read a specific format and produce the engine's internal representation.

- Register loaders at startup: `assetManager.registerLoader(Texture.class, new PngLoader(), new KtxLoader())`
- Multiple loaders per type — selected by file extension or content sniffing.
- Users can register custom loaders for proprietary or project-specific formats.
- Loaders are stateless — they transform bytes → asset data. The manager handles caching, lifecycle, and threading.

### Dependency Tracking

Assets can depend on other assets (e.g. a material references textures). The manager resolves dependencies transitively and ensures correct load order. Hot-reloading a dependency triggers re-evaluation of dependents.

## Object Versioning

### Core Mechanism

Every mutable object carries a monotonic version counter (int/long). Incremented on any mutation. Cheap to store, cheap to compare.

### Hierarchical Versioning

A scene node's effective version = `max(own version, parent version)`. Parent transform change makes all children implicitly dirty without touching them.

### Cache Invalidation

Caches (shadow maps, reflection probes, GI, culling results) store a version snapshot of everything they depend on. Next frame: compare versions, skip regeneration if all match.

For shadow maps specifically: track light transform/params version + combined version of all casters in that light's frustum.

### Static vs Dynamic (First-Class Distinction)

Objects are explicitly categorized:
- **STATIC** — never moves, never changes. Shadow maps for static-only geometry render once and persist indefinitely.
- **DYNAMIC** — transforms/properties change at runtime. Tracked per-frame via version counters.

This allows splitting render passes: static shadow casters into a persistent shadow map layer, dynamic casters into a layer that re-renders only when their versions change. Static scenes become essentially free.

### Open Question: Shader-Animated Objects

Objects that are visually dynamic but CPU-static (e.g. flags, foliage, water — animated purely in the vertex shader) are a grey area. The CPU version never changes, but the rendered output changes every frame. Need a strategy for this — possible approaches:
- A third category (e.g. `SHADER_DYNAMIC`) that the engine treats as always-dirty for relevant passes
- Let the material/shader declare whether it affects silhouette — if it does, shadow pass must re-render; if not (e.g. texture animation only), can be treated as static for shadows
- TBD — needs more thought

## Threading Model

### Double-Buffered Frame Pipeline

Logic and rendering run in parallel on separate threads:

```
Logic thread:  [update frame N+1] [update frame N+2] [update frame N+3] ...
Render thread: -------[render frame N] [render frame N+1] [render frame N+2] ...
```

Logic produces a frame snapshot, renderer consumes the previous one. One frame of latency, but logic and rendering overlap fully.

### Frame Snapshot

At the end of each logic tick, the game produces an immutable snapshot of everything the renderer needs (transforms, visibility, materials, lights, camera). The renderer reads only from this snapshot — no shared mutable state, no locks during rendering.

The versioning system feeds directly into this: the snapshot carries version numbers, so the renderer can diff against its caches.

### Synchronization

- One sync point per frame between logic and render threads (swap the snapshot).
- Logic thread must not outrun renderer by more than 1 frame — simple double-buffer swap with a fence/barrier.
- No fine-grained locking inside the frame — both threads operate on their own data.

### Multi-Window / Multi-Context Support

The engine supports rendering to multiple windows/surfaces simultaneously. Each window has its own:
- **RenderTarget / Surface** — represents the swapchain or framebuffer for that window
- **Viewport / Camera** — independent view into the scene

The renderer iterates over active surfaces and renders each. The scene data (snapshot) is shared read-only across all of them — only the camera/viewport differs.

Implementation:
- Each window gets its own dedicated render thread + its own graphics context. No context switching, no sharing.
- OpenGL: one GL context per thread per window. Clean and avoids shared-context driver bugs.
- Vulkan: single device, multiple swapchains. Command buffers per surface.
- WebGPU: single `GPUDevice`, multiple `GPUCanvasContext`s. One command encoder per surface per frame.
- The immutable frame snapshot is read-only — multiple render threads read it in parallel safely, zero coordination needed.
- Use cases: editor viewports (scene view, game view, asset preview), multi-monitor, split-screen, debug views.

### Window Toolkit Abstraction

The renderer does not create or manage windows directly. Window creation, event polling, and surface handling are delegated to a **WindowToolkit** abstraction, with backend implementations for different native toolkits.

#### Supported Toolkits

- **GLFW** — lightweight, widely used, good OpenGL/Vulkan support. Primary toolkit for development.
- **SDL3** — broader platform coverage, richer input/gamepad support, audio subsystem if needed.

#### Key Abstraction: WindowToolkit + WindowHandle

- **WindowToolkit** — creates windows, polls events, provides surfaces. One active toolkit per application (mixing GLFW and SDL windows is not supported — pick one at startup).
- **WindowHandle** — opaque handle to a native window. The renderer asks the toolkit for a drawable surface from this handle.

#### Toolkit ↔ Graphics Backend Interaction

The toolkit and graphics backend are independent choices that compose:

| | OpenGL | Vulkan | WebGPU |
|---|---|---|---|
| GLFW | `glfwCreateWindow` + GL context | `glfwCreateWindowSurface` → `VkSurfaceKHR` | `glfwCreateWindowSurface` → `wgpu::Surface` (via wgpu-native) |
| SDL3 | `SDL_GL_CreateContext` | `SDL_Vulkan_CreateSurface` → `VkSurfaceKHR` | `SDL_GetWindowWMInfo` → native handle → `wgpu::Surface` |

The toolkit provides the native surface/handle, the graphics backend wraps it into its own surface type. Neither knows about the other's internals.

#### Event Handling

The toolkit translates native events (resize, close, focus, input) into engine events. The renderer listens for resize events to recreate swapchains/framebuffers. Input events flow to the game logic, not the renderer.

#### Why Not Abstract Further

No attempt to support arbitrary toolkits via a plugin interface — GLFW and SDL3 cover all realistic desktop use cases. The abstraction exists to avoid hard-wiring one toolkit, not to support an open-ended ecosystem. Adding a new toolkit means implementing one interface, not designing a framework.


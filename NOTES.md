# Engine Design Notes

## Rendering Architecture

Dual-backend (OpenGL + Vulkan) behind a backend-agnostic abstraction.

### Key Abstractions

- **RenderDevice** — represents the GPU. Creates resources (buffers, textures, shaders, pipelines), queries capabilities and state.
- **RenderContext** — represents a frame in flight. Game records draw commands without knowing the backend.
- **Opaque handles** — game gets `BufferHandle`, `TextureHandle`, `PipelineHandle` etc. Backend maps them to GL names or Vulkan objects internally. Enables deferred destruction for Vulkan frames-in-flight.

### Design Decisions

- **Command-oriented, not state-machine** — design around Vulkan's explicit model. OpenGL backend translates trivially. Reverse direction is painful.
- **Pipeline State Objects** — bundle rasterizer state, blend mode, depth test, shaders into one immutable object. Vulkan requires this; OpenGL emulates by diffing against current state.
- **Capability query system** — `RenderDevice.query(RenderCap)` returns normalized values regardless of backend (MAX_TEXTURE_SIZE, VRAM_USAGE, SUPPORTED_FORMATS, etc.).
- **Frame lifecycle** — `device.beginFrame()` → `RenderContext` → record commands → `device.endFrame()`.

### What NOT to Leak

- No descriptor sets, render passes, or command pools in the game-facing API.
- No attempt to expose every feature of both APIs — common denominator, extend per-backend where needed.

### Approach

- Get OpenGL working first, then factor out the interface when adding Vulkan.

## Buffer System

### Two Concepts

- **BufferHandle** — opaque, immutable GPU resource. Created with a descriptor specifying size, usage, and access pattern.
- **BufferWriter** — data transfer mechanism. Acquired from `device.writeBuffer(buffer)`. Wraps mapped memory or staging buffer transparently. Flushes/uploads on close (try-with-resources).

### Access Patterns (declared at creation, drive backend strategy)

- **STATIC** — upload once, use many. Device-local (Vulkan) / `GL_STATIC_DRAW` (OpenGL).
- **DYNAMIC** — updated frequently (per-frame uniforms). Persistent mapping or host-visible memory.
- **STREAM** — write once, use once, discard. Ring buffers or transient allocations.

### Backend Translation

| | OpenGL | Vulkan |
|---|---|---|
| STATIC | `glBufferData` or staging+copy | Staging buffer → `vkCmdCopyBuffer` to device-local |
| DYNAMIC | `glMapBufferRange` persistent | Persistently mapped host-visible memory or staging ring |
| STREAM | Orphan + `glMapBufferRange` unsynchronized | Per-frame ring buffer from host-visible pool |

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
- The immutable frame snapshot is read-only — multiple render threads read it in parallel safely, zero coordination needed.
- Use cases: editor viewports (scene view, game view, asset preview), multi-monitor, split-screen, debug views.


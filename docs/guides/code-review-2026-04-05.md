# Code Review Findings (2026-04-05)

This document records the findings, decisions, and fixes from the full code review
performed in April 2026.  Items still open are tracked in [TODO.md](../../TODO.md).

---

## Bugs Fixed in This Review

### Profiler: `lastFrame()` / `currentFrame()` naming confusion

**Symptom:** `Profiler.lastFrame()` was documented as "the last completed frame" but
actually returned the *in-progress* current frame.  `previousFrame()` returned the
truly last completed frame.

**Fix:** Renamed the two methods to match their actual semantics:

| New name | Returns | Notes |
|---|---|---|
| `currentFrame()` | In-progress frame map | Write during the frame |
| `lastFrame()` | Most recently completed frame | Read by debug overlays / profiling |

Tests updated to use `currentFrame()` where they were reading in-frame data.

---

### GPU buffer leak: per-entity UBOs (`obj_N`, `mat_N`)

**Symptom:** `UniformManager` allocated a UBO per entity for the object transform and
for the material scalars.  The maps were keyed by `"obj_" + entity.index()` (an `int`),
which ignores the handle generation.  Buffers were never freed when entities were
destroyed.

**Consequences:**
1. Unbounded GPU memory growth for every entity that was ever created and destroyed.
2. After an entity slot was reused (same index, new generation) the new entity would
   silently reuse the old entity's GPU buffer without reallocating, leading to stale
   transform/material data for one frame.

**Fix:**
- Maps now keyed by `Handle<?>` (record with `index` + `generation`) so old entries are
  never reused by new entities at the same slot.
- Added `UniformManager.removeEntity(Handle<?> entity)` which destroys and removes the
  entity's object UBO and material UBO.
- `Renderer.renderFrame()` calls `removeEntity()` for every `EntityRemoved` transaction
  *before* passing transactions to `MeshRenderer`.

---

### Stale shader binding after entity slot reuse

**Symptom:** `ShaderManager.entityShaders` mapped `entity.index()` → `CompiledShader`.
After an entity was destroyed and its handle slot recycled, the new entity at the same
index inherited the old entity's compiled shader silently.

**Fix:** Added `ShaderManager.removeEntityShader(Handle<?> entity)` (clears by index).
`Renderer.renderFrame()` calls it for every `EntityRemoved` transaction.

---

### `Engine.shutdown()` did not stop the `AssetManager`

**Symptom:** If hot-reload was enabled (`AssetManager.enableHotReload()`), the
`FileWatcher` background thread kept running after `Engine.shutdown()`.

**Fix:** Added `assets.shutdown()` call in `Engine.shutdown()`.

---

### `Renderer.close()` did not clean up `MeshManager`

**Symptom:** `MeshManager` holds GPU vertex buffers, index buffers, and vertex-input
descriptors via `meshRegistry` (handle-based meshes) and `meshDataCache`
(data-keyed meshes).  Neither was freed at shutdown.

**Fix:** Added `MeshManager.close()` which destroys all buffers and vertex inputs in
both maps.  `Renderer.close()` now calls `meshManager.close()` first.

---

### `MeshRenderer` leaked entity maps on `EntityRemoved`

**Symptom:** `MeshRenderer.processTransaction` for `EntityRemoved` removed the entity
from `transforms`, `renderables`, `materials`, and `materialData`, but left behind
entries in `meshDataAssignments`, `meshAssignments`, and `materialAssignments`.

**Fix:** All seven maps are now cleared on `EntityRemoved`.

---

### `RenderStats` was never populated

**Symptom:** `RenderStats` tracks draw calls, vertex counts, pipeline binds, texture
binds, and buffer binds.  No code ever called any `record*` method.  All counters were
permanently zero.

**Fix:** `Renderer.renderFrame()` now calls:
- `renderStats.reset()` at the top of each frame
- `renderStats.recordPipelineBind()` on each `bindPipeline`
- `renderStats.recordDrawCall(vertices, indices)` on each `draw` / `drawIndexed`
- `renderStats.recordBufferBind()` on material UBO bind
- `renderStats.recordTextureBind()` on material texture bind

`Renderer.renderStats()` exposes the stats object.  `Engine.renderStats()` now
delegates to `renderer.renderStats()` (previously Engine owned a separate, unused
instance).

---

### `ResourceCleaner.register()` was package-private

**Symptom:** The `java.lang.ref.Cleaner` safety net for native resources could only
be used inside the `dev.engine.core.resource` package.

**Fix:** Made the method `public`.

---

### Global param name string literals scattered through code

**Symptom:** Strings `"Engine"`, `"Camera"`, `"Object"` appeared as raw literals in
`Renderer`, `UniformManager`, `ShaderManager`, and shader generation code.  A typo
would silently break param binding with no compile-time error.

**Fix:** Added `GlobalParamNames` constants class in `dev.engine.graphics.shader` with
`ENGINE`, `CAMERA`, and `OBJECT` constants.  `Renderer` and `UniformManager` updated
to use them.

---

## Items Still Open (see TODO.md for full list)

- Thread safety: `Engine.run()` race on `TransactionBuffer`
- Primitive topology hardcoded to TRIANGLES
- No frustum culling
- No lighting/shadow system
- RenderGraph/PostProcessChain/UploadStrategy implemented but unused
- Asset eviction / reference counting / async loading
- Disk shader cache
- Shader hot-reload not wired
- Error/fallback shader (currently returns null → entity disappears)

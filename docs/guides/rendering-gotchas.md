# Rendering Gotchas

## Depth Buffer Clear Value and Depth Functions

The Renderer's setup pass clears the depth buffer to **1.0** (the maximum depth value). This means:

- `CompareFunc.LESS` (default): works correctly. Closer fragments (depth < 1.0) pass.
- `CompareFunc.LEQUAL`: works correctly. Closer or equal fragments pass.
- `CompareFunc.GREATER`: **nothing renders**. No fragment can have depth > 1.0 since 1.0 is the maximum in the [0, 1] range.
- `CompareFunc.GEQUAL`: only fragments at exactly depth 1.0 (the far plane) pass.
- `CompareFunc.ALWAYS`: all fragments pass regardless of depth — useful for testing pipeline state changes.

To use reversed depth (GREATER/GEQUAL), you must also clear the depth buffer to **0.0** instead of 1.0. The engine does not yet support per-scene depth clear values.

## Texture Sampling Through Materials

The material/shader generation pipeline (`ShaderManager.getShaderWithMaterial()`) generates `IMaterialParams` structs with scalar fields (float, vec3, etc.) but does **not** yet support texture sampler declarations. This means:

- `MaterialData.ALBEDO_MAP` texture data is uploaded to the GPU (the upload path works)
- But the generated shader code has no sampler to read it
- The "textured" shader hint (`MaterialData.create("textured")`) uses explicit `register(b0)` bindings that conflict with the engine's generic param block layout (engine at b0, camera at b1, object at b2)

Until texture sampling is wired through the material interface generation, use the `unlit` or `pbr` shader hints with distinct colors, and attach `ALBEDO_MAP` only to exercise the upload path.

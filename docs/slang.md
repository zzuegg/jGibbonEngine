# Slang Integration Notes

Hard-won knowledge from integrating the Slang shader compiler. Read this before touching any shader code.

## Matrix Multiplication Convention

**Slang uses row-vector convention.** This is the single most important thing to know.

```slang
// CORRECT — row-vector * matrix
output.position = mul(float4(position, 1.0), mvp);

// WRONG — produces butterfly/bowtie artifacts, inverted geometry
output.position = mul(mvp, float4(position, 1.0));
```

When Slang compiles `mul(v, M)` to GLSL with `layout(row_major)`, it produces `M * v` — which is mathematically correct for our row-major Mat4 records.

When Slang compiles `mul(M, v)` to GLSL, it produces `v * M` — which is transposed and wrong.

**Rule: always put the vector FIRST in `mul()`.**

This applies to all matrix-vector multiplications including normal transforms:
```slang
// Correct
float3 worldNormal = mul(normal, (float3x3)modelMatrix);
float3 worldPos = mul(float4(position, 1.0), modelMatrix).xyz;
```

## GLSL Output Characteristics

Slang automatically adds `layout(row_major) uniform;` and `layout(row_major) buffer;` to all GLSL output. This matches our engine's convention of storing Mat4 in row-major order (the record component order: m00, m01, m02, m03, m10, ...).

**Do NOT transpose matrices before uploading to UBOs when using Slang-compiled shaders.** The `row_major` layout in the generated GLSL handles this.

## Attribute Locations

Slang assigns attribute locations based on semantic order in the `VertexInput` struct:
- First field → `layout(location = 0)`
- Second field → `layout(location = 1)`
- etc.

This matches our `VertexFormat` / `VertexAttribute` convention where location is explicit.

```slang
struct VertexInput {
    float3 position : POSITION;  // location = 0
    float3 color    : COLOR;     // location = 1
};
```

## Uniform Buffer Binding

`cbuffer` with `register(b0)` maps to `layout(binding = 0)` in GLSL. Our engine binds UBOs to binding points via `glBindBufferBase(GL_UNIFORM_BUFFER, binding, ubo)`.

```slang
cbuffer Matrices : register(b0) {   // binding = 0
    float4x4 mvp;
};
cbuffer MaterialData : register(b1) {  // binding = 1
    float3 albedoColor;
    float roughness;
};
```

## Slang Entry Points

Shaders use `[shader("vertex")]` and `[shader("fragment")]` attributes. The entry point name is passed to `slangc` via `-entry`:

```slang
[shader("vertex")]
VertexOutput vertexMain(VertexInput input) { ... }

[shader("fragment")]
float4 fragmentMain(VertexOutput input) : SV_Target { ... }
```

Compile with:
```
slangc shader.slang -target glsl -entry vertexMain -stage vertex
slangc shader.slang -target glsl -entry fragmentMain -stage fragment
```

Both entry points can live in the same `.slang` file.

## Slang Interfaces

Slang supports proper interfaces (like Java interfaces) for material abstraction:

```slang
interface IMaterial {
    float3 getAlbedo();
    float getRoughness();
};

struct PBRMaterial : IMaterial {
    float3 color;
    float roughness;
    float3 getAlbedo() { return color; }
    float getRoughness() { return roughness; }
};
```

These compile down to concrete structs in GLSL — no runtime dispatch overhead.

## Slang Generics

Slang supports generics with type constraints:

```slang
T myMax<T : IComparable>(T a, T b) {
    return a > b ? a : b;
}
```

Generics are monomorphized at compile time — zero runtime cost.

## Compilation Targets

| Target | Flag | Output |
|---|---|---|
| GLSL | `-target glsl` | `#version 450` GLSL source |
| SPIR-V | `-target spirv` | Binary SPIR-V blob |
| WGSL | `-target wgsl` | WebGPU shading language source |
| HLSL | `-target hlsl` | HLSL source |

For Vulkan, compile to SPIR-V. For OpenGL, compile to GLSL. For WebGPU, compile to WGSL.

## Runtime Compilation

We invoke `slangc` as a process at runtime (not at build time). Key considerations:

- **LD_LIBRARY_PATH** must include the directory containing `libslang-compiler.so` (the `tools/lib/` directory next to `tools/bin/slangc`)
- Compilation takes ~90ms per shader on first call, then results are cached by content hash
- Temp files are used for source input and output (slangc reads from files, not stdin)
- Always clean up temp files after compilation

## Search Paths

For multi-file shaders with `import`:
```slang
import "common";  // looks for common.slang in search paths
```

Add search paths via `-I` flag to slangc. Our `SlangCompiler.addSearchPath()` handles this.

## Texture Declarations (Sampler2D vs Texture2D + SamplerState)

**Always use `Sampler2D` (combined type) for cross-backend compatibility.**

```slang
// CORRECT — combined texture+sampler, works for both GLSL and SPIRV
Sampler2D albedoTexture;
float3 color = albedoTexture.Sample(uv).rgb;

// WRONG for OpenGL — separate texture and sampler
// Generates `uniform texture2D` + `uniform sampler` which Mesa's GLSL rejects
Texture2D albedoTexture;
SamplerState albedoSampler;
float3 color = albedoTexture.Sample(albedoSampler, uv).rgb;
```

`Texture2D` + `SamplerState` generates valid Vulkan SPIRV but invalid desktop OpenGL GLSL (`uniform texture2D` and `uniform sampler` are not supported by Mesa's GLSL compiler). Slang's `Sampler2D` compiles to `uniform sampler2D` in GLSL, which works everywhere.

## Texture Binding and [[vk::binding(N)]]

**`[[vk::binding(N)]]` affects GLSL output too, not just SPIRV.** The annotation overrides the `layout(binding = N)` in generated GLSL.

For cross-backend texture binding, use `[[vk::binding(16)]]` (matching `VkDescriptorManager.TEXTURE_BINDING_OFFSET`) so the Vulkan descriptor set layout matches. The same binding (16) is used for OpenGL's `glBindTextureUnit`.

```slang
[[vk::binding(16)]]
Sampler2D albedoTexture;
```

This generates:
- GLSL: `layout(binding = 16) uniform sampler2D albedoTexture_0;`
- SPIRV: descriptor binding 16

## Slang Reflection Limitations

Slang's reflection API (`bindingOffset()`) does not reliably return the correct binding index for texture parameters. The `DESCRIPTOR_TABLE_SLOT`, `CONSTANT_BUFFER`, `SHADER_RESOURCE`, and `SAMPLER_STATE` categories all return 0 for combined `Sampler2D` types.

**Workaround:** For GLSL targets, parse the generated GLSL for `layout(binding = N)` patterns to extract the actual binding index. For SPIRV targets, the binding comes from `[[vk::binding(N)]]` annotations. The `ShaderManager.parseGlslBindings()` method handles this.

## WGSL Target (WebGPU)

**The `SLANG_WGSL` compile target enum value is 28, NOT 29.** Value 29 is `SLANG_WGSL_SPIRV_ASM`, which requires the `slang-tint` pass-through library (not included in standard Slang releases).

Slang can emit WGSL directly (target 28) without any additional libraries. The generated WGSL uses `@vertex`/`@fragment` annotations, `@location(N)` for vertex inputs, and `@builtin(position)` for SV_Position.

The `ShaderManager` must select `SLANG_WGSL` when the backend is "WebGPU" (not fall through to GLSL). Passing GLSL source to `wgpuDeviceCreateShaderModule` causes a Rust panic in wgpu-native.

## Common Pitfalls

1. **mul order** — always `mul(vector, matrix)`, never `mul(matrix, vector)`
2. **Don't transpose** — Slang outputs `row_major`, our Mat4 is row-major, no transpose needed
3. **Entry point names must match** — the `-entry` flag must exactly match the function name
4. **Semantics are required** — Slang needs HLSL-style semantics (`: POSITION`, `: SV_Target`, etc.)
5. **slangc is not on PATH** — the engine searches `tools/bin/slangc` relative to the project root
6. **Cache invalidation** — if you change a .slang file, the cache key changes (content hash), so recompilation is automatic
7. **SPIR-V output is binary** — don't try to read it as text
8. **mix() vs lerp()** — Slang uses `lerp()` (HLSL convention), not `mix()` (GLSL convention)
9. **Sampler2D not Texture2D** — use combined `Sampler2D` type for OpenGL compatibility (see above)
10. **[[vk::binding]] affects GLSL** — the annotation is not Vulkan-only, it changes GLSL `layout(binding)` too
11. **SLANG_WGSL is 28** — the enum value for WGSL is 28 (not 29, which is WGSL_SPIRV_ASM and requires slang-tint)

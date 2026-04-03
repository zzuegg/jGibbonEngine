# Material & Shader System

## Overview

Materials are immutable typed property bags (`MaterialData`) that drive shader compilation and GPU data upload. The engine auto-generates Slang shader code from material properties and compiles shaders using the native Slang FFM bindings with generic specialization.

## MaterialData

Immutable wrapper over `PropertyMap`. Each property is a `PropertyKey<T>` with a name and type. Setting a property returns a new instance.

```java
var mat = MaterialData.pbr(new Vec3(0.9f, 0.2f, 0.2f), 0.3f, 0.8f);
var mat2 = mat.set(MaterialData.ROUGHNESS, 0.5f); // new instance
```

Factory methods: `MaterialData.pbr(albedo, roughness, metallic)`, `MaterialData.unlit(color)`, `MaterialData.create(shaderHint)`.

Standard keys: `ALBEDO_COLOR`, `ROUGHNESS`, `METALLIC`, `EMISSIVE`, `OPACITY`, `ALBEDO_MAP`, `NORMAL_MAP`, etc.

The `shaderHint()` (e.g., "PBR", "UNLIT") determines which `.slang` file is loaded.

## Shader Param Blocks — `SlangParamsBlock`

Generates Slang interface + struct + cbuffer + implementation from either:
- **Java records** (fixed params: camera, engine, object, user-defined)
- **PropertyKey sets** (dynamic params: materials)

```java
// From record — generates ICameraParams interface + UboCameraParams impl
SlangParamsBlock.fromRecord("Camera", CameraParams.class)
    .withBinding(1)       // → cbuffer CameraBuffer : register(b1)
    .generateUbo();

// From material keys — generates IMaterialParams + UboMaterialParams
SlangParamsBlock.fromKeys("Material", materialKeys)
    .generateUbo(false);  // no static global — uses generic specialization
```

Generated Slang output (example for camera):
```slang
interface ICameraParams {
    float4x4 viewProjection();
    float4x4 view();
    float4x4 projection();
    float3 position();
    float near();
    float far();
};

struct CameraParamsData {
    float4x4 viewProjection;
    float4x4 view;
    float4x4 projection;
    float3 position;
    float near;
    float far;
};

cbuffer CameraBuffer : register(b1) {
    CameraParamsData cameraData;
};

struct UboCameraParams : ICameraParams {
    float4x4 viewProjection() { return cameraData.viewProjection; }
    // ... etc
};
```

### `withBinding(int)` — explicit register annotations

Fixed binding slots use `register(bN)` for stable, predictable UBO layout:
- Engine: b0
- Camera: b1  
- Object: b2
- Material: no fixed binding (resolved via reflection)

## Global Params Registry

User-extensible registry for engine-wide and per-frame shader parameters. All registered params are prepended to every shader as Slang code.

```java
// Engine registers defaults
renderer.registerGlobalParams("Engine", EngineParams.class, 0);
renderer.registerGlobalParams("Camera", CameraParams.class, 1);
renderer.registerGlobalParams("Object", ObjectParams.class, 2);

// User registers custom params
renderer.registerGlobalParams("Light", LightParams.class);  // auto-assigns next binding

// Update per frame
renderer.updateGlobalParams("Light", new LightParams(dir, color, intensity));
```

### Built-in param records

| Record | Binding | Upload | Fields |
|--------|---------|--------|--------|
| `EngineParams` | b0 | per frame | time, deltaTime, resolution, frameCount |
| `CameraParams` | b1 | per camera | viewProjection, view, projection, position, near, far |
| `ObjectParams` | b2 | per draw | world |

### Shader access

Shaders access params through well-known globals (for non-generic params) or generic type parameters:

```slang
// camera/engine/object use static globals
static UboCameraParams camera;
camera.viewProjection();

// materials use generic specialization
[shader("fragment")]
float4 fragmentMain<M : IMaterialParams>(...) {
    M material;
    material.albedoColor();
}
```

## Generic Specialization

All shader params use Slang's generic specialization. Entry points declare their dependencies:

```slang
[shader("vertex")]
VertexOutput vertexMain<C : ICameraParams, O : IObjectParams>(VertexInput input) {
    C camera;
    O object;
    float4x4 mvp = mul(object.world(), camera.viewProjection());
    output.position = mul(float4(input.position, 1.0), mvp);
    output.worldNormal = mul(float4(input.normal, 0.0), object.world()).xyz;
    ...
}

[shader("fragment")]
float4 fragmentMain<C : ICameraParams, M : IMaterialParams>(VertexOutput input) : SV_Target {
    C camera;
    M material;
    ...
}
```

### Auto-specialization flow

1. `ShaderManager` prepends interfaces + structs + cbuffers + impls (no static globals)
2. `SlangCompilerNative.compileWithTypeMap()` composes the program
3. Parses shader source for generic declarations (`<C : ICameraParams, O : IObjectParams>`)
4. Matches each interface to a concrete type via registry map (`Camera → UboCameraParams`)
5. Calls `specialize("UboCameraParams", "UboObjectParams", "UboCameraParams", "UboMaterialParams")`
6. Slang monomorphizes — all interface calls become direct cbuffer reads in output GLSL

### Specialization args order

Args are positional, matching the order generics appear across entry points:
- VS `<C, O>` → param 0 = C, param 1 = O
- FS `<C, M>` → param 2 = C, param 3 = M

The `compileWithTypeMap()` method auto-discovers this by parsing generic declarations from source.

## Name-Based Binding Resolution

The `Renderable` stores a `Map<String, Integer>` of buffer name → binding slot, extracted from Slang reflection after compilation. The renderer binds UBOs by name:

```java
draw.bindUniformBuffer(r.bindingFor("CameraBuffer", 1), cameraUbo);
draw.bindUniformBuffer(r.bindingFor("EngineBuffer", 0), engineUbo);
```

Fallback values match the `register(bN)` conventions. Reflection provides the actual slots when available.

## BufferWriter / BufferReader

Single utility for all GPU buffer serialization. Used by `StructLayout`, material upload, and any mapped buffer.

```java
BufferWriter.write(segment, offset, 3.14f);         // float → 4 bytes
BufferWriter.write(segment, offset, new Vec3(1,2,3)); // Vec3 → 12 bytes
BufferWriter.write(segment, offset, myMat4);          // Mat4 → 64 bytes, column-major
BufferWriter.writeTextureHandle(segment, offset, h);  // uint64 → 8 bytes (bindless)
```

### Matrix convention

- `Mat4` in Java: row-major (m00, m01, m02, m03 is first row)
- GPU upload: column-major (`Mat4.writeGpu()` transposes)
- Slang source: row-major convention (`defaultMatrixLayoutMode = ROW_MAJOR`)
- GLSL output: Slang converts mul() order, emits `layout(column_major)`
- Slang handles the math conversion, `BufferWriter` handles the memory layout

## Compilation Pipeline

1. Entity has `MaterialData` with `shaderHint()` and property keys
2. Renderer calls `shaderManager.getShaderWithMaterial(hint, keys)` lazily on first render
3. ShaderManager loads `shaders/<hint>.slang`
4. `prependParamBlocks()` generates all global param blocks + material param block
5. `compileWithAutoSpecialize()` compiles via native Slang FFM:
   - Builds type map from registry + material
   - Parses generic declarations from source
   - Specializes with concrete types
   - Links and extracts GLSL + reflection
6. Creates pipeline, caches by `hint + sorted key names`

### Shader cache key

`"PBR_albedoColor_emissive_metallic_opacity_roughness"` — shader hint + sorted property key names. Different materials with same hint and keys share the same compiled pipeline.

## Techniques (Design — Not Yet Implemented)

Rendering strategies (fog, output mode, shading model) as typed material properties:

```java
// Technique interfaces
sealed interface FogTechnique {}
record LinearFog(float start, float end, Vec3 color) implements FogTechnique {}
record ExponentialFog(float density, Vec3 color) implements FogTechnique {}
record NoFog() implements FogTechnique {}

// Set on material via PropertyKey
static final PropertyKey<FogTechnique> FOG = PropertyKey.of("fog", FogTechnique.class);

var mat = MaterialData.pbr(...)
    .set(FOG, new LinearFog(10f, 100f, new Vec3(0.7f, 0.8f, 0.9f)));
```

The engine would:
1. See a record-typed property on the material
2. Generate Slang struct from the concrete record
3. Generate `typealias Fog = LinearFogImpl;`
4. Upload record fields to material UBO via `BufferWriter`

Shader uses the typealias:
```slang
Fog fog;
color = fog.apply(color, depth);
```

### Open design question

How to distinguish technique-typed properties from regular data:
- **Marker interface** (`implements Technique`) — explicit opt-in
- **Any record** — treat all non-primitive record-typed properties as techniques
- Both approaches work; marker is more explicit but adds classification burden

### Scope

Techniques can be:
- **Per-material**: different objects use different techniques (fog, shading model)
- **Global defaults**: `renderer.setTechnique("Fog", "NoFog")` — material can override

Different technique implementations can have different parameters (LinearFog has start/end, ExponentialFog has density). The concrete record type determines both the typealias and the data layout. Type safety is enforced by Java's type system through `PropertyKey<FogTechnique>`.

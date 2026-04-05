# Debug UI System

## Overview

The engine includes a built-in immediate-mode debug UI inspired by [Nuklear](https://github.com/Immediate-Mode-UI/Nuklear). It's a pure Java implementation in the `ui/` module — no native bindings, works on all backends (OpenGL, Vulkan, WebGPU, web via TeaVM).

## Architecture

- **`ui/`** — Pure Java UI core. No graphics dependencies, only depends on `core/`.
  - `NkContext` — Main API: windows, layout, widgets, draw command generation
  - `NkDrawList` — Converts draw commands → vertex/index buffers
  - `NkBuiltinFont` — Embedded 8x8 bitmap font atlas (ASCII 32-126)
  - `NkInputBridge` — Translates engine `InputEvent`s to UI input
  - `NkStyle` — Dark theme with full customization

- **`graphics/common/`** — Rendering integration
  - `DebugUiOverlay` — Compiles Slang shader, uploads buffers, submits draw calls
  - Hooks into `Renderer.addPostSceneCallback()` to render after the scene

## Shader

The UI shader is written in **Slang** (`shaders/debug_ui.slang`), compiled at runtime via the platform's `ShaderCompiler` to the correct target (GLSL for OpenGL, SPIRV for Vulkan, WGSL for WebGPU). This ensures cross-platform compatibility.

### Key points:
- Uses push constants for screen size (NDC conversion)
- Vertex format: `float2 position + float2 uv + byte4 color` (20 bytes)
- Uses `Sampler2D` combined texture+sampler for the font atlas
- Alpha blending, no depth test, no backface culling, scissor clipping

## Gotchas

- The shader uses `[[vk::push_constant]]` for the screen size uniform. This works on all Slang targets — Slang translates it to a uniform block for GLSL/WGSL.
- The vertex color attribute uses normalized bytes (`BYTE` with `normalized=true`). The Slang shader receives it as `float4 color : COLOR`.
- Font atlas uses NEAREST sampling for pixel-perfect rendering of the bitmap font.
- The `NkDrawList` uses unsigned shorts for indices, limiting to 65535 vertices per frame. This is sufficient for debug UI.

## Usage

```java
@Override
protected void update(float dt, List<InputEvent> events) {
    var ui = debugUi();
    if (ui.begin("Debug", 10, 10, 250, 300)) {
        ui.layoutRowDynamic(25, 1);
        ui.label("FPS: " + (int)(1/dt));
        myValue = ui.sliderFloat(0, myValue, 100, 1);
        if (ui.button("Reset")) myValue = 50;
    }
    ui.end();
}
```

Input events are fed automatically by `BaseApplication`. The overlay renders automatically after the scene pass.

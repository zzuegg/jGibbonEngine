<div align="center">
  <img src="docs/assets/img/mascot.svg" alt="jGibbonEngine mascot — Gibby" width="120" />

  # jGibbonEngine

  **A modern, multi-backend 3D engine for the JVM.**

  [![GitHub Pages](https://img.shields.io/badge/docs-GitHub%20Pages-orange?logo=github)](https://zzuegg.github.io/jGibbonEngine)
  [![License](https://img.shields.io/badge/license-BSD--3--Clause-blue)](LICENSE)
  [![Java](https://img.shields.io/badge/Java-25-007396?logo=openjdk)](https://openjdk.org)

  [Website](https://zzuegg.github.io/jGibbonEngine) ·
  [Tutorials](https://zzuegg.github.io/jGibbonEngine/tutorials/) ·
  [Examples](https://zzuegg.github.io/jGibbonEngine/examples/) ·
  [API Docs](https://zzuegg.github.io/jGibbonEngine/javadoc/)
</div>

---

## What is jGibbonEngine?

jGibbonEngine is a 3D engine for the JVM, written in Java 25. It runs on **OpenGL**, **Vulkan**, and **WebGPU** from the same codebase — same application code, different backend config.

## Quick Start

```java
public class MyGame extends BaseApplication {

    @Override
    protected void init() {
        camera().lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);

        var cube = scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.pbr(new Vec3(0.9f, 0.2f, 0.2f), 0.3f, 0.8f));
        cube.add(Transform.at(0, 0.5f, 0));
    }

    public static void main(String[] args) {
        var config = EngineConfig.builder()
                .windowTitle("My Game")
                .windowSize(1280, 720)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(new LwjglGlBindings()))
                .build();

        new MyGame().launch(config);
    }
}
```

## Features

- **Three GPU backends** — OpenGL 4.5, Vulkan, WebGPU (desktop + browser via TeaVM)
- **Slang shaders** — single shader source compiles to GLSL, SPIR-V, and WGSL
- **PBR materials** — physically-based rendering with per-texture sampler control
- **Entity-component scene** — entities with typed components (Transform, MeshData, MaterialData)
- **Manager architecture** — `GpuResourceManager` with deferred deletion, leak detection, and per-type managers (Mesh, Texture, Sampler, Pipeline, RenderTarget, Uniform, RenderState, Shader)
- **Transaction-based rendering** — scene and renderer are decoupled via a `TransactionBus` with subscriber filtering
- **Screenshot regression tests** — 28 scenes × 3 backends, auto-discovered, cross-backend comparison
- **Compilable tutorials** — tutorial source files are real Java that compile against the engine

## Architecture

```
core/                    — math, ECS, events, assets, transactions
graphics/
  api/                   — RenderDevice SPI, descriptors, commands
  common/                — Renderer, managers, engine orchestration
  opengl/                — OpenGL 4.5 DSA backend
  vulcan/                — Vulkan backend
  webgpu/                — WebGPU backend
providers/               — native bindings (LWJGL, jWebGPU, TeaVM, Slang, Assimp)
platforms/
  desktop/               — filesystem assets, native Slang
  web/                   — fetch assets, Slang WASM, TeaVM
samples/
  tutorials/             — compilable tutorial source → auto-generated website
  examples/              — runnable example applications
  tests/screenshot/      — visual regression tests across all backends
```

## Running

```bash
# Build
./gradlew build

# Run an example
./gradlew :examples:run -PmainClass=dev.engine.examples.MyGame

# Run screenshot tests (all 3 backends)
./gradlew :samples:tests:screenshot:test

# Generate screenshot report
./gradlew :samples:tests:screenshot:screenshotReport

# Generate tutorial docs
./gradlew :samples:tutorials:generateTutorials
```

Requires `JAVA_HOME` pointing to Java 25. Slang shader compiler in `tools/bin/slangc` ([download](https://github.com/shader-slang/slang/releases)).

## Documentation

| Resource | Link |
|----------|------|
| Website | https://zzuegg.github.io/jGibbonEngine |
| Tutorials | https://zzuegg.github.io/jGibbonEngine/tutorials/ |
| Examples | https://zzuegg.github.io/jGibbonEngine/examples/ |
| API Reference | https://zzuegg.github.io/jGibbonEngine/javadoc/ |

## License

BSD 3-Clause License. See [LICENSE](LICENSE).

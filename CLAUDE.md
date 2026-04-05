# Claude Instructions

## Documentation of Learnings

When discovering non-obvious behavior, gotchas, bugs, or integration quirks in ANY part of this project — whether it's Slang shaders, OpenGL, Vulkan, Java FFM, LWJGL, Gradle, or any other component — **automatically** create or update a documentation file in `docs/` capturing the finding.

This is not optional. Every time you:
- Fix a bug caused by a non-obvious convention (like Slang's mul order)
- Discover a platform-specific quirk (like LD_LIBRARY_PATH for slangc)
- Find that an API works differently than expected
- Encounter a compilation/runtime issue that took debugging to resolve
- Learn something about winding order, matrix conventions, memory layout, or similar

**Write it down in the relevant docs file immediately.** If no file exists for that topic, create one. Use `docs/<topic>.md` format.

Examples of files that should exist and be kept up to date:
- `docs/slang.md` — Slang compiler integration notes
- `docs/opengl.md` — OpenGL-specific gotchas (DSA, row_major, winding order, etc.)
- `docs/vulkan.md` — Vulkan integration notes
- `docs/matrix-conventions.md` — Row-major vs column-major, upload conventions, mul order
- `docs/gradle.md` — Build system quirks, dependency resolution, native libraries

The goal: a future developer (or Claude session) should never hit the same issue twice.

## Code Conventions

See `NOTES.md` for all engine code conventions. Key points:
- No enums for extensible types — use interfaces with static instances
- Everything flexible and extensible
- Math types are records (Valhalla-ready)
- All native resources tracked via Cleaner
- Thread-safe from the start
- Minimal external dependencies
- **No ServiceLoader** — all wiring is explicit. Platforms assemble providers directly, not via SPI discovery.
- No implicit magic — prefer explicit configuration and construction over classpath scanning
- Prefer abstract classes over interfaces when shared state/behavior exists (e.g., `GraphicsConfig`)
- Don't force records everywhere — use them for value types and immutable data, not for configuration objects that benefit from mutability or inheritance

## Project Structure

- `core/` — Backend-agnostic engine code (math, events, scene, assets, profiler)
- `core-processor/` — Annotation processor for core
- `graphics/` — Graphics abstraction layers
  - `api` — Low-level SPI: interfaces, descriptors, commands that backends implement
  - `common` — High-level engine: Renderer, Engine, ShaderManager, materials, meshes
  - `opengl` — OpenGL 4.5 DSA backend
  - `vulcan` — Vulkan backend
  - `webgpu` — WebGPU backend
- `providers/` — Concrete implementations (Gradle paths stay `providers:<name>`, dirs are categorized)
  - `graphics/desktop/` — lwjgl-gl, lwjgl-vk, jwebgpu
  - `graphics/web/` — teavm-webgpu
  - `windowing/desktop/` — lwjgl-glfw, sdl3
  - `windowing/web/` — teavm-windowing
  - `shader/` — slang
  - `assets/` — assimp
- `platforms/` — Opinionated assembly layers that wire providers with platform defaults
  - `desktop` — Filesystem asset sources, native slang, JVM deployment
  - `web` — Fetch asset sources, slang-wasm, TeaVM compilation, web deployment
- `examples/` — Example applications

## Running

```bash
./gradlew test                                                                    # all tests
./gradlew :examples:run -PmainClass=dev.engine.examples.SlangExample              # Slang scene
./gradlew :examples:run -PmainClass=dev.engine.examples.HighLevelSceneExample     # high-level scene
./gradlew :examples:run -PmainClass=dev.engine.examples.SpinningCubeExample       # spinning cube
./gradlew :examples:run                                                           # triangle
```

Requires `JAVA_HOME` pointing to Java 25 if not the default.
Slang examples require `tools/bin/slangc` — download from https://github.com/shader-slang/slang/releases

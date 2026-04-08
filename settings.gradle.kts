rootProject.name = "jGibbonEngine"

// ── Core ─────────────────────────────────────────────────────────────
include("core")
include("core-processor")

// ── UI ──────────────────────────────────────────────────────────────
include("ui")

// ── Graphics API & backends ──────────────────────────────────────────
include("graphics:api")
include("graphics:common")
include("graphics:opengl")
include("graphics:vulcan")
include("graphics:webgpu")

// ── Providers: graphics ──────────────────────────────────────────────
include("providers:lwjgl-gl")
project(":providers:lwjgl-gl").projectDir = file("providers/graphics/desktop/lwjgl-gl")

include("providers:lwjgl-vk")
project(":providers:lwjgl-vk").projectDir = file("providers/graphics/desktop/lwjgl-vk")

include("providers:jwebgpu")
project(":providers:jwebgpu").projectDir = file("providers/graphics/desktop/jwebgpu")

include("providers:wgpu-ffm")
project(":providers:wgpu-ffm").projectDir = file("providers/graphics/desktop/wgpu-ffm")

include("providers:teavm-webgpu")
project(":providers:teavm-webgpu").projectDir = file("providers/graphics/web/teavm-webgpu")

include("providers:graal-webgpu")
project(":providers:graal-webgpu").projectDir = file("providers/graphics/web/graal-webgpu")

// ── Providers: windowing ─────────────────────────────────────────────
include("providers:lwjgl-glfw")
project(":providers:lwjgl-glfw").projectDir = file("providers/windowing/desktop/lwjgl-glfw")

include("providers:sdl3")
project(":providers:sdl3").projectDir = file("providers/windowing/desktop/sdl3")

include("providers:teavm-windowing")
project(":providers:teavm-windowing").projectDir = file("providers/windowing/web/teavm-windowing")

include("providers:graal-windowing")
project(":providers:graal-windowing").projectDir = file("providers/windowing/web/graal-windowing")

// ── Providers: shader ────────────────────────────────────────────────
include("providers:slang")
project(":providers:slang").projectDir = file("providers/shader/slang")

include("providers:slang-wasm")
project(":providers:slang-wasm").projectDir = file("providers/shader/slang-wasm")

include("providers:graal-slang-wasm")
project(":providers:graal-slang-wasm").projectDir = file("providers/shader/graal-slang-wasm")

// ── Providers: assets ────────────────────────────────────────────────
include("providers:assimp")
project(":providers:assimp").projectDir = file("providers/assets/assimp")

// ── Platforms ────────────────────────────────────────────────────────
include("platforms:desktop")
include("platforms:web")
include("platforms:graalwasm")

// ── Tools ───────────────────────────────────────────────────────────
include("tools:site-generator")
project(":tools:site-generator").projectDir = file("tools/site-generator")

// ── Examples (legacy, to be migrated) ────────────────────────────────
include("examples")

// ── Samples ─────────────────────────────────────────────────────────
include("samples:tutorials")
include("samples:examples")

// ── Screenshot Tests ────────────────────────────────────────────────
include("samples:tests:screenshot:scenes")
project(":samples:tests:screenshot:scenes").projectDir = file("samples/tests/screenshot/scenes")

include("samples:tests:screenshot:runner")
project(":samples:tests:screenshot:runner").projectDir = file("samples/tests/screenshot/runner")

include("samples:tests:screenshot:desktop-runner")
project(":samples:tests:screenshot:desktop-runner").projectDir = file("samples/tests/screenshot/desktop")

include("samples:tests:screenshot:analysis")
project(":samples:tests:screenshot:analysis").projectDir = file("samples/tests/screenshot/analysis")

include("samples:tests:screenshot:web-runner")
project(":samples:tests:screenshot:web-runner").projectDir = file("samples/tests/screenshot/web")

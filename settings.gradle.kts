rootProject.name = "jGibbonEngine"

// ── Core ─────────────────────────────────────────────────────────────
include("core")
include("core-processor")

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

include("providers:teavm-webgpu")
project(":providers:teavm-webgpu").projectDir = file("providers/graphics/web/teavm-webgpu")

// ── Providers: windowing ─────────────────────────────────────────────
include("providers:lwjgl-glfw")
project(":providers:lwjgl-glfw").projectDir = file("providers/windowing/desktop/lwjgl-glfw")

include("providers:sdl3")
project(":providers:sdl3").projectDir = file("providers/windowing/desktop/sdl3")

include("providers:teavm-windowing")
project(":providers:teavm-windowing").projectDir = file("providers/windowing/web/teavm-windowing")

// ── Providers: shader ────────────────────────────────────────────────
include("providers:slang")
project(":providers:slang").projectDir = file("providers/shader/slang")

// ── Providers: assets ────────────────────────────────────────────────
include("providers:assimp")
project(":providers:assimp").projectDir = file("providers/assets/assimp")

// ── Platforms ────────────────────────────────────────────────────────
include("platforms:desktop")
include("platforms:web")

// ── Examples (legacy, to be migrated) ────────────────────────────────
include("examples")

// ── Samples ─────────────────────────────────────────────────────────
include("samples:tutorials")
include("samples:examples")
include("samples:tests:screenshot")
project(":samples:tests:screenshot").projectDir = file("samples/tests/screenshot")

@EngineModule(
    name = "WebGPU Backend",
    description = "WebGPU backend for browser and native — WGSL shaders, modern GPU API, runs on desktop and web.",
    category = "Graphics Backend",
    features = {"WebGPU API", "WGSL Shaders", "Browser Support", "Native via Dawn/wgpu"},
    icon = "🌐"
)
@EngineFeature(
    name = "Desktop + Web",
    description = "Same BaseApplication, same scene code — desktop via LWJGL (GLFW + OpenGL/Vulkan), browser via TeaVM + WebGPU.",
    icon = "🌍"
)
package dev.engine.graphics.webgpu;

import dev.engine.core.docs.EngineModule;
import dev.engine.core.docs.EngineFeature;

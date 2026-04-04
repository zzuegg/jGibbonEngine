@EngineModule(
    name = "Slang Shader Compiler",
    description = "Slang shader compilation provider — write shaders once, compile to GLSL, SPIR-V, or WGSL per backend.",
    category = "Provider",
    features = {"Cross-Backend Shaders", "GLSL Output", "SPIR-V Output", "WGSL Output", "WASM Support"},
    icon = "🔮"
)
@EngineFeature(
    name = "Slang Shaders",
    description = "Write shaders once in Slang. The engine compiles them to GLSL, SPIR-V, or WGSL depending on the active backend — including in the browser via WASM.",
    icon = "🔮"
)
package dev.engine.bindings.slang;

import dev.engine.core.docs.EngineModule;
import dev.engine.core.docs.EngineFeature;

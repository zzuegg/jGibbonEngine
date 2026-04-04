@EngineModule(
    name = "Graphics Common",
    description = "High-level rendering engine — Renderer, ShaderManager, material system, mesh handling, and render graph orchestration.",
    category = "Graphics",
    features = {"Renderer Orchestration", "Shader Manager", "PBR Materials", "Render Graph", "GPU Resource Manager"},
    icon = "🖥️"
)
@EngineFeature(
    name = "PBR Materials",
    description = "Physically-based rendering with albedo, roughness, metallic, and per-texture sampler control. Materials are type-safe property maps.",
    icon = "🎨"
)
@EngineFeature(
    name = "Render Graph",
    description = "Declarative render pass orchestration with automatic resource lifetime management and dependency resolution.",
    icon = "📊"
)
package dev.engine.graphics.common;

import dev.engine.core.docs.EngineModule;
import dev.engine.core.docs.EngineFeature;

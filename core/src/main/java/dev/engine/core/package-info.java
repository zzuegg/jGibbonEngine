@EngineModule(
    name = "Core",
    description = "Backend-agnostic engine foundation — math, ECS, events, assets, profiler, and resource management.",
    category = "Core",
    features = {"Math Types (Vec3, Mat4, Quat)", "Entity-Component System", "Event Bus", "Asset Management", "Profiler", "Resource Cleaner"},
    icon = "⚙️"
)
@EngineFeature(
    name = "Valhalla-Ready Math",
    description = "Math types are Java records, ready for Project Valhalla value types. Zero-allocation vector math.",
    icon = "🔢"
)
@EngineFeature(
    name = "Transaction System",
    description = "Atomic resource creation with automatic cleanup on failure. All GPU resource setup goes through transactions.",
    icon = "🛡️"
)
@EngineFeature(
    name = "Module System",
    description = "Dependency-aware module lifecycle with topological ordering, fixed/variable timestep, and state machine transitions.",
    icon = "🧩"
)
@EngineFeature(
    name = "Visual Regression Tests",
    description = "Screenshot test scenes run on all backends automatically. Cross-backend comparison catches rendering differences.",
    icon = "🧪"
)
@EngineFeature(
    name = "Compilable Tutorials",
    description = "Tutorials are real Java files that compile against the engine. Auto-generated into website docs — always up to date.",
    icon = "📖"
)
@EngineFeature(
    name = "Entity Components",
    description = "Scene entities with typed components — Transform, MeshData, MaterialData. Parent-child hierarchy for scene graph organisation.",
    icon = "🏔️"
)
@EngineFeature(
    name = "Resource Management",
    description = "GpuResourceManager tracks all GPU resources with deferred deletion and leak detection. WeakCache for automatic cleanup.",
    icon = "📦"
)
package dev.engine.core;

import dev.engine.core.docs.EngineModule;
import dev.engine.core.docs.EngineFeature;

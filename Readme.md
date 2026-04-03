<div align="center">
  <img src="docs/assets/img/mascot.svg" alt="jGibbonEngine mascot — Gibby" width="120" />

  # jGibbonEngine

  **The next-generation Java/Kotlin 3D game engine — spiritual successor of jMonkeyEngine.**

  [![GitHub Pages](https://img.shields.io/badge/docs-GitHub%20Pages-orange?logo=github)](https://zzuegg.github.io/jGibbonEngine)
  [![License](https://img.shields.io/badge/license-BSD--3--Clause-blue)](LICENSE)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)

  [🏠 Website](https://zzuegg.github.io/jGibbonEngine) ·
  [📖 Tutorials](https://zzuegg.github.io/jGibbonEngine/tutorials/) ·
  [💡 Examples](https://zzuegg.github.io/jGibbonEngine/examples/) ·
  [📚 API Docs](https://zzuegg.github.io/jGibbonEngine/javadoc/) ·
  [🖼️ Showcase](https://zzuegg.github.io/jGibbonEngine/showcase/)
</div>

---

## What is jGibbonEngine?

jGibbonEngine is a modern, high-performance 3D game engine for the JVM, written in Kotlin with full Java interoperability. It draws on the heritage and lessons of [jMonkeyEngine](https://jmonkeyengine.org/) while embracing modern JVM idioms, a clean ECS architecture, and a Kotlin-first API.

## Quick Start

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.zzuegg:jgibbonengine-core:0.1.0")
}
```

```kotlin
class MyGame : GibbonApplication() {
    override fun init() {
        rootNode.attachChild(Box(halfExtents = 1f))
        rootNode.addLight(DirectionalLight(direction = Vector3f(1f, -1f, -1f)))
    }
}

fun main() = MyGame().start()
```

→ [Full Getting Started tutorial](https://zzuegg.github.io/jGibbonEngine/tutorials/getting-started)

## Features

- **Modern Rendering** — PBR, HDR, dynamic shadows, OpenGL + Vulkan targets
- **ECS Architecture** — Cache-friendly entity-component-system for scalable game logic
- **Familiar Scene Graph** — jMonkeyEngine-style scene graph with Kotlin DSL sugar
- **Kotlin-first API** — Idiomatic Kotlin with full Java interop
- **Cross-platform** — Windows, macOS, Linux
- **Plugin System** — Swap or extend any subsystem

## Documentation

| Resource | Link |
|----------|------|
| 🏠 Project website | https://zzuegg.github.io/jGibbonEngine |
| 📖 Tutorials | https://zzuegg.github.io/jGibbonEngine/tutorials/ |
| 💡 Examples | https://zzuegg.github.io/jGibbonEngine/examples/ |
| 📚 API Reference (Javadoc) | https://zzuegg.github.io/jGibbonEngine/javadoc/ |
| 🖼️ Showcase | https://zzuegg.github.io/jGibbonEngine/showcase/ |

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) and open a pull request or [start a discussion](https://github.com/zzuegg/jGibbonEngine/discussions).

## License

jGibbonEngine is released under the [BSD 3-Clause License](LICENSE).

<div align="center">
  <img src="docs/assets/img/mascot.svg" alt="jGibbonEngine mascot — Gibby" width="120" />

  # jGibbonEngine

  **The next-generation Java 3D game engine for the JVM.**

  [![GitHub Pages](https://img.shields.io/badge/docs-GitHub%20Pages-orange?logo=github)](https://zzuegg.github.io/jGibbonEngine)
  [![License](https://img.shields.io/badge/license-BSD--3--Clause-blue)](LICENSE)
  [![Java](https://img.shields.io/badge/Java-17%2B-007396?logo=java)](https://openjdk.org)

  [🏠 Website](https://zzuegg.github.io/jGibbonEngine) ·
  [📖 Tutorials](https://zzuegg.github.io/jGibbonEngine/tutorials/) ·
  [💡 Examples](https://zzuegg.github.io/jGibbonEngine/examples/) ·
  [📚 API Docs](https://zzuegg.github.io/jGibbonEngine/javadoc/) ·
  [🖼️ Showcase](https://zzuegg.github.io/jGibbonEngine/showcase/)
</div>

---

## What is jGibbonEngine?

jGibbonEngine is a modern, high-performance 3D game engine for the JVM, written in Java. It embraces modern JVM idioms, a clean ECS architecture, and a straightforward Java API.

## Quick Start

```java
// build.gradle.kts
dependencies {
    implementation("io.github.zzuegg:jgibbonengine-core:0.1.0")
}
```

```java
public class MyGame extends GibbonApplication {
    @Override
    public void init() {
        getRootNode().attachChild(new Box(1f));
        getRootNode().addLight(new DirectionalLight(new float[]{1f, -1f, -1f}));
    }

    public static void main(String[] args) {
        new MyGame().start();
    }
}
```

→ [Full Getting Started tutorial](https://zzuegg.github.io/jGibbonEngine/tutorials/getting-started)

## Features

- **Modern Rendering** — PBR, HDR, dynamic shadows, OpenGL + Vulkan targets
- **ECS Architecture** — Cache-friendly entity-component-system for scalable game logic
- **Familiar Scene Graph** — hierarchical scene graph for intuitive 3D world organisation
- **Clean Java API** — straightforward, well-documented Java API
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

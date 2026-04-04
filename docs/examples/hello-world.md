---
layout: page
title: "Hello World"
description: "The simplest jGibbonEngine application — a single colored cube."
---

```java
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.platform.desktop.DesktopPlatform;

public class HelloWorld extends BaseApplication {

    @Override
    protected void init() {
        camera().lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);

        var cube = scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.2f, 0.6f, 1.0f)));
        cube.add(Transform.IDENTITY);
    }

    @Override
    protected void update(float dt) {
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
    }

    public static void main(String[] args) {
        var config = EngineConfig.builder()
                .windowTitle("Hello World")
                .windowSize(1280, 720)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();

        new HelloWorld().launch(config);
    }
}
```

## Architecture

All jGibbonEngine applications follow the same pattern:

1. **Configure** — `EngineConfig` bundles platform + backend + window settings
2. **Launch** — `launch(config)` creates the window, initializes the engine
3. **Init** — create entities with components (mesh, material, transform)
4. **Update** — game logic runs every frame
5. **Cleanup** — automatic resource management via `GpuResourceManager`

The same application code runs on **OpenGL**, **Vulkan**, and **WebGPU** — just swap the `GraphicsBackendFactory` in the config.

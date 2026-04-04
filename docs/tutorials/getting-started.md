---
layout: page
title: "Getting Started"
description: "Set up your first jGibbonEngine project and render a 3D scene."
---

Welcome to jGibbonEngine! In this tutorial you will:

- Create your first application class
- Display a 3D scene with PBR materials
- Understand the engine architecture

## Prerequisites

- JDK 25 or later
- Gradle 9.x
- Basic familiarity with Java

---

## Step 1 — Clone and build

```bash
git clone https://github.com/zzuegg/jGibbonEngine.git
cd jGibbonEngine
./gradlew build
```

---

## Step 2 — Create your Application class

Extend `BaseApplication` and override the lifecycle methods:

```java
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.platform.desktop.DesktopPlatform;

public class MyGame extends BaseApplication {

    @Override
    protected void init() {
        // Set up camera
        camera().lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);

        // Red metallic cube
        var cube = scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.pbr(new Vec3(0.9f, 0.2f, 0.2f), 0.3f, 0.8f));
        cube.add(Transform.at(0, 0.5f, 0));

        // Green rough sphere
        var sphere = scene().createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.pbr(new Vec3(0.2f, 0.9f, 0.2f), 0.9f, 0.1f));
        sphere.add(Transform.at(2, 0.5f, 0));
    }

    @Override
    protected void update(float dt) {
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
    }

    public static void main(String[] args) {
        var config = EngineConfig.builder()
                .windowTitle("My Game")
                .windowSize(1280, 720)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();

        new MyGame().launch(config);
    }
}
```

---

## Key Concepts

- **`BaseApplication`** — handles the main loop, window, and engine lifecycle
- **`EngineConfig`** — configures platform, graphics backend, and window settings
- **`Platform`** — provides asset loading and shader compilation (desktop, web)
- **`GraphicsBackendFactory`** — creates the rendering backend (OpenGL, Vulkan, WebGPU)
- **Scene entities** use components: `Transform`, `MeshData`, `MaterialData`
- **PBR materials** with `MaterialData.pbr(albedo, roughness, metallic)`

---

## Run it

```bash
./gradlew :examples:run -PmainClass=dev.engine.examples.MyGame
```

---

## What's next?

- **[Building Your First Scene]({{ site.baseurl }}/tutorials/first-scene)** — hierarchy, animation, modules
- **[Examples]({{ site.baseurl }}/examples/)** — runnable example applications
- **[API Reference]({{ site.baseurl }}/javadoc/)** — full Java API documentation

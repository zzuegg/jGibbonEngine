<!-- AUTO-GENERATED — do not edit, run ./gradlew :tools:site-generator:generateSite -->
---
layout: page
title: "Materials and Lighting"
description: "Add PBR materials to your scene — roughness, metallic, and how light interacts with surfaces."
---

# Materials and Lighting

In the previous tutorial we used `MaterialData.unlit()` — a flat color
with no lighting. Real 3D scenes need **PBR (Physically Based Rendering)**
materials that respond to light.

This tutorial shows:

- PBR materials with roughness and metallic properties
- Multiple entities in one scene
- How different material properties affect appearance
- Simple animation using the update loop

We store entity references as fields so update() can animate them.

```java
private Entity roughSphere;
    private Entity metallicSphere;
    private Entity ground;

    @Override
    protected void init() {
        camera().lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
```

## PBR Materials

`MaterialData.pbr(albedo, roughness, metallic)` creates a
physically-based material:

- **albedo** — the base color (Vec3, 0–1 per channel)
- **roughness** — 0.0 = mirror-smooth, 1.0 = completely rough
- **metallic** — 0.0 = dielectric (plastic/wood), 1.0 = metal

The engine's Slang shaders compute lighting automatically
using these properties.

A rough orange sphere — like clay or terracotta.
High roughness (0.9) means light scatters broadly.

```java
roughSphere = scene().createEntity();
        roughSphere.add(PrimitiveMeshes.sphere());
        roughSphere.add(MaterialData.pbr(
                new Vec3(0.8f, 0.4f, 0.1f),  // warm orange
                0.9f,                          // very rough
                0.0f                           // not metallic
        ));
        roughSphere.add(Transform.at(-2, 1, 0));
```

A shiny metallic sphere — like polished chrome.
Low roughness (0.1) means sharp reflections.
High metallic (0.9) means the color tints reflections.

```java
metallicSphere = scene().createEntity();
        metallicSphere.add(PrimitiveMeshes.sphere());
        metallicSphere.add(MaterialData.pbr(
                new Vec3(0.9f, 0.9f, 0.9f),  // silver-white
                0.1f,                          // very smooth
                0.9f                           // highly metallic
        ));
        metallicSphere.add(Transform.at(2, 1, 0));
```

## Ground plane

A large flat plane gives the scene a sense of space and
catches light/shadows. We use medium roughness for a
matte floor look.

```java
ground = scene().createEntity();
        ground.add(PrimitiveMeshes.plane(10, 10));
        ground.add(MaterialData.pbr(
                new Vec3(0.3f, 0.3f, 0.3f),  // dark grey
                0.8f,                          // fairly rough
                0.0f                           // non-metallic
        ));
        ground.add(Transform.at(0, 0, 0));
    }
```

## Animation

The `update()` method runs every frame. `deltaTime` is the time
since the last frame in seconds (typically ~0.016 for 60 FPS).

Here we make the spheres gently bob up and down using sine waves.
`time()` returns the total elapsed time since the app started.

```java
@Override
    protected void update(float deltaTime) {
        float t = (float) time();
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);
```

Animate the rough sphere: bob up and down

```java
roughSphere.update(Transform.class, tr ->
                tr.withPosition(-2, 1 + (float) Math.sin(t) * 0.3f, 0));
```

Animate the metallic sphere: bob with a phase offset

```java
metallicSphere.update(Transform.class, tr ->
                tr.withPosition(2, 1 + (float) Math.sin(t + Math.PI) * 0.3f, 0));
    }

    public static void main(String[] args) {
        var config = EngineConfig.builder()
                .windowTitle("Tutorial 02 — Materials and Lighting")
                .windowSize(1280, 720)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();

        new T02_MaterialsAndLighting().launch(config);
    }
```


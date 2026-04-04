---
layout: page
title: "Hello World Scene"
description: "The minimum code needed to open a window and render a lit 3D box with jGibbonEngine."
---

This example shows the absolute minimum you need to display a 3D scene with jGibbonEngine. It creates a single textured box illuminated by a directional light.

## Full source

```kotlin
package com.example

import io.github.zzuegg.engine.GibbonApplication
import io.github.zzuegg.engine.scene.Box
import io.github.zzuegg.engine.scene.DirectionalLight
import io.github.zzuegg.engine.math.ColorRGBA
import io.github.zzuegg.engine.math.Vector3f

class HelloWorld : GibbonApplication() {

    override fun init() {
        // --- Geometry ---
        val box = Box(halfExtents = 1f)
        box.setMaterial(assetManager.loadDefaultMaterial())
        box.localTranslation = Vector3f(0f, 0f, -3f)
        rootNode.attachChild(box)

        // --- Lighting ---
        val sun = DirectionalLight(
            direction = Vector3f(1f, -1f, -1f).normalize(),
            color     = ColorRGBA.White
        )
        rootNode.addLight(sun)
    }
}

fun main() {
    HelloWorld().apply {
        settings.title  = "jGibbonEngine — Hello World"
        settings.width  = 1280
        settings.height = 720
    }.start()
}
```

## `build.gradle.kts`

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
    application
}

application {
    mainClass.set("com.example.MainKt")
}

repositories {
    mavenCentral()
    mavenLocal() // needed during early access
}

dependencies {
    implementation("io.github.zzuegg:jgibbonengine-core:0.1.0")
}
```

## What's happening?

| Line | Explanation |
|------|-------------|
| `GibbonApplication` | Base class that owns the render loop, window, and asset manager. |
| `init()` | Called once before the first frame — set up your scene here. |
| `Box(halfExtents = 1f)` | Creates a cube mesh ±1 unit on each axis (2 units wide). |
| `assetManager.loadDefaultMaterial()` | A built-in flat grey PBR material — no texture files needed. |
| `localTranslation` | Positions the box 3 units in front of the default camera. |
| `DirectionalLight` | Simulates sunlight — infinite parallel rays from a direction. |
| `settings.start()` | Opens the window and starts the game loop. |

## Controls (default)

| Key / Input | Action |
|-------------|--------|
| `W A S D`   | Move camera |
| Mouse drag  | Look around |
| `Escape`    | Quit |

## Next steps

- Add more geometry: try replacing `Box` with `Sphere` or `Cylinder`
- Load a GLTF model with `assetManager.loadModel("path/to/model.gltf")`
- Read the [Building Your First Scene]({{ site.baseurl }}/tutorials/first-scene) tutorial for lights, materials, and cameras

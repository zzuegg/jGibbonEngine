---
layout: page
title: "Building Your First Scene"
description: "Learn the scene graph, add geometry, lights, cameras, and materials to bring your world to life."
---

In the previous tutorial you rendered a plain box. In this one you will build a proper scene with multiple objects, dynamic lighting, a skybox, and a controllable camera.

## What you will build

By the end of this tutorial you will have:

- A terrain-like floor plane
- A collection of PBR-lit objects
- A point light and a directional (sun) light
- An orbit camera controlled with the mouse

---

## Concepts: The Scene Graph

jGibbonEngine uses a **scene graph** — a tree of `Node` and `Geometry` objects:

```
rootNode  (Node)
├── terrain  (Geometry)
├── objects  (Node)
│   ├── box1  (Geometry)
│   └── sphere1  (Geometry)
└── lights  (Node — logical grouping)
```

- **Node** — a group / transform container. Has children but no mesh.
- **Geometry** — a leaf node that holds a mesh and a material.
- **rootNode** — the single root of the scene, provided by `GibbonApplication`.

---

## Step 1 — Add a floor

```kotlin
override fun init() {
    // A flat quad stretched to 20 × 20 units
    val floor = Geometry(
        mesh     = Quad(width = 20f, height = 20f),
        material = assetManager.loadMaterial("Materials/Terrain.jgmat")
    )
    floor.localRotation = Quaternion.fromAngleAxis(
        angle = -Math.PI.toFloat() / 2f,
        axis  = Vector3f.UNIT_X
    )
    rootNode.attachChild(floor)
}
```

<div class="callout callout-info">
<p>📌 Asset paths are resolved relative to your project's <code>assets/</code> classpath root by default.</p>
</div>

---

## Step 2 — Add geometry objects

```kotlin
// Reusable PBR material
val metalMat = assetManager.loadMaterial("Materials/Metal.jgmat")

// Add 5 boxes in a row
for (i in -2..2) {
    val box = Geometry(
        mesh     = Box(halfExtents = 0.5f),
        material = metalMat
    )
    box.localTranslation = Vector3f(i * 2f, 0.5f, 0f)
    rootNode.attachChild(box)
}

// Add a sphere in the center
val sphere = Geometry(
    mesh     = Sphere(radius = 0.75f, segments = 32),
    material = assetManager.loadMaterial("Materials/Emissive.jgmat")
)
sphere.localTranslation = Vector3f(0f, 1.5f, 0f)
rootNode.attachChild(sphere)
```

---

## Step 3 — Add lighting

```kotlin
// Sunlight (directional)
val sun = DirectionalLight(
    direction = Vector3f(1f, -2f, -1f).normalize(),
    color     = ColorRGBA(1f, 0.95f, 0.8f, 1f),
    intensity = 2.5f
)
rootNode.addLight(sun)

// Orange point light near the sphere
val pointLight = PointLight(
    position  = Vector3f(0f, 2f, 2f),
    color     = ColorRGBA(1f, 0.5f, 0.1f, 1f),
    radius    = 6f
)
rootNode.addLight(pointLight)

// Ambient fill
val ambient = AmbientLight(
    color = ColorRGBA(0.1f, 0.1f, 0.15f, 1f)
)
rootNode.addLight(ambient)
```

---

## Step 4 — Control the camera

The default `FlyCamControl` is attached automatically. To switch to an orbit camera:

```kotlin
override fun init() {
    // Disable the default fly cam
    flyCam.isEnabled = false

    // Attach an orbit camera control
    val orbitCam = OrbitCamControl(cam)
    orbitCam.target        = Vector3f.ZERO
    orbitCam.minDistance   = 2f
    orbitCam.maxDistance   = 30f
    orbitCam.initialPitch  = 30f
    orbitCam.initialYaw    = 45f

    inputManager.addControl(orbitCam)
}
```

Now you can orbit with the left mouse button, zoom with the scroll wheel, and pan with the middle mouse button.

---

## Step 5 — Animate a node

Use the `simpleUpdate` callback to animate objects each frame:

```kotlin
private var angle = 0f

override fun simpleUpdate(tpf: Float) {
    angle += tpf * 1.2f  // radians per second
    sphere.localRotation = Quaternion.fromAngleAxis(angle, Vector3f.UNIT_Y)
}
```

---

## Full listing

The complete source for this tutorial is available in the [Examples]({{ site.baseurl }}/examples/) section as the **Hello World Scene** example.

---

## What's next?

- [Examples →]({{ site.baseurl }}/examples/) — runnable projects with downloadable source
- [API Reference →]({{ site.baseurl }}/javadoc/) — detailed class and method documentation
- **Materials & Shaders** *(coming soon)* — go deeper into PBR and custom GLSL shaders

---

*Found an error or have a suggestion? [Open an issue](https://github.com/zzuegg/jGibbonEngine/issues) on GitHub.*

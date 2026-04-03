---
layout: page
title: "Getting Started"
description: "Set up your first jGibbonEngine project and render a 3D scene in under 10 minutes."
---

Welcome to jGibbonEngine! In this tutorial you will:

- Add jGibbonEngine to a Gradle project
- Create your first application class
- Display a simple 3D box on screen
- Understand the basic rendering loop

## Prerequisites

- JDK 17 or later
- Gradle 8.x
- Basic familiarity with Java

---

## Step 1 — Create a new Gradle project

Create a new directory and initialize a Gradle project:

```bash
mkdir my-gibbon-game && cd my-gibbon-game
gradle init --type java-application --dsl kotlin
```

Your directory will look like:

```
my-gibbon-game/
  app/
    src/main/java/…
    build.gradle.kts
  settings.gradle.kts
  gradle/
```

---

## Step 2 — Add the dependency

Open `app/build.gradle.kts` and add jGibbonEngine:

```kotlin
dependencies {
    implementation("io.github.zzuegg:jgibbonengine-core:0.1.0")
    // Optional — physics module
    // implementation("io.github.zzuegg:jgibbonengine-physics:0.1.0")
}
```

> **Note:** jGibbonEngine is not yet published to Maven Central during the early-access phase. Follow the [GitHub repository](https://github.com/zzuegg/jGibbonEngine) for release announcements, or build from source (see Step 6).

---

## Step 3 — Create your Application class

Create `app/src/main/java/com/example/MyGame.java`:

```java
package com.example;

import io.github.zzuegg.engine.GibbonApplication;
import io.github.zzuegg.engine.scene.Box;
import io.github.zzuegg.engine.scene.DirectionalLight;
import io.github.zzuegg.engine.math.ColorRGBA;

public class MyGame extends GibbonApplication {

    @Override
    public void init() {
        // Add a simple box to the scene
        Box box = new Box(1f);
        box.setMaterial(getAssetManager().loadDefaultMaterial());
        getRootNode().attachChild(box);

        // Add a directional light
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new float[]{0f, -1f, -1f});
        sun.setColor(ColorRGBA.White);
        getRootNode().addLight(sun);
    }
}
```

---

## Step 4 — Add the entry point

Create or edit `app/src/main/java/com/example/Main.java`:

```java
package com.example;

public class Main {
    public static void main(String[] args) {
        MyGame app = new MyGame();
        app.getSettings().setTitle("My First Gibbon Game");
        app.getSettings().setWidth(1280);
        app.getSettings().setHeight(720);
        app.getSettings().setVSync(true);
        app.start();
    }
}
```

---

## Step 5 — Run it

```bash
./gradlew :app:run
```

You should see a window appear with a grey-shaded 3D box illuminated by the directional light. Use the default fly-cam (`WASD` + mouse) to orbit the scene.

<div class="callout callout-tip">
<p>💡 <strong>Tip:</strong> If you see a black window, make sure you have a working OpenGL 3.3+ driver installed.</p>
</div>

---

## Step 6 — Build from source (early access)

Until the first stable release is on Maven Central, you can build jGibbonEngine locally:

```bash
git clone https://github.com/zzuegg/jGibbonEngine.git
cd jGibbonEngine
./gradlew publishToMavenLocal
```

Then add `mavenLocal()` to your `settings.gradle.kts` repository list.

---

## What's next?

Now that your first scene is running, continue with:

- **[Building Your First Scene]({{ site.baseurl }}/tutorials/first-scene)** — lights, cameras, and geometry
- **[Examples]({{ site.baseurl }}/examples/)** — copy-paste runnable snippets
- **[API Reference]({{ site.baseurl }}/javadoc/)** — full Java API documentation

---

*Have questions? Jump into [GitHub Discussions](https://github.com/zzuegg/jGibbonEngine/discussions).*

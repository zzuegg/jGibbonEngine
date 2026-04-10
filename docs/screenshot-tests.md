---
layout: page
title: "Screenshot Test Gotchas"
description: "Non-obvious rules for adding and debugging visual regression tests."
---

# Screenshot Test Gotchas

The screenshot test pipeline lives in `samples/tests/screenshot/` and runs
the same scene set against every supported backend (desktop OpenGL, desktop
Vulkan, desktop WebGPU, TeaVM/WebGPU, GraalWasm/WebGPU). Scenes are
authored once in `:samples:tests:screenshot:scenes` and executed by
per-backend runner projects.

## Adding a new scene: triple registration

> ⚠️ **A new scene is NOT discovered automatically on the web or graalwasm
> backends.** You must manually edit two registry files in addition to
> writing the scene itself.

The JVM runners (desktop OpenGL/Vulkan/WebGPU) use annotation-based
discovery: any class tagged `@Discoverable` in a sub-package of
`dev.engine.tests.screenshot.scenes` is picked up automatically by the
`core-processor` annotation processor, which generates
`GeneratedDiscoveryRegistry` entries at compile time. The runner scans
static-final `RenderTestScene` fields reflectively.

TeaVM and GraalWasm Native Image do **not** support the reflection needed
for discovery, so the web and graalwasm runners ship an **explicit static
registry**. Every scene must be added to both:

- `samples/tests/screenshot/web/src/main/java/dev/engine/tests/screenshot/web/WebTestSceneRegistry.java`
- `samples/tests/screenshot/graalwasm/src/main/java/dev/engine/tests/screenshot/graalwasm/GraalWasmTestSceneRegistry.java`

Forgetting this step produces the runtime error
`Scene 'your_scene' error: Unknown scene: your_scene` on the affected
backend(s), with a successful build — there is no compile-time check.

**Checklist for adding a new scene:**

1. Write the scene as a `public static final RenderTestScene` field in a
   `@Discoverable` class under `samples/tests/screenshot/scenes/.../<category>/`.
2. Add an import + `put(...)` call to `WebTestSceneRegistry#all()`.
3. Add an import + `put(...)` call to `GraalWasmTestSceneRegistry#all()`.
4. Run `./gradlew :samples:tests:screenshot:desktop-runner:runDesktop`
   to verify the scene renders on all three desktop backends.
5. Run `./gradlew :samples:tests:screenshot:web-runner:runWeb` and check
   the generated screenshot matches.
6. If GraalVM + Binaryen/WABT are installed: run
   `./gradlew :samples:tests:screenshot:graalwasm-runner:runGraalWasm`.

## Debugging: always check the manifest first, not the images

The runner writes a cumulative result JSON at
`build/screenshots/screenshot-report.json` with one entry per
scene × backend. Check it **before** opening PNGs — a scene can fail
for reasons unrelated to rendering (runtime exceptions, missing
registry entries, timeout) and you'll waste time staring at pixels
if you skip this.

```bash
# Show runs for a specific scene:
python3 -c "
import json
d = json.load(open('build/screenshots/screenshot-report.json'))
for r in d.get('runs', []):
    if r.get('scene') == 'YOUR_SCENE_NAME':
        print(r.get('backend'), '->', r.get('status'), '--', r.get('error'))
"
```

Each run entry has `scene`, `backend`, `status` (`success` / `exception`
/ `timeout`), `durationMs`, `screenshots` (list of
`{frame, path}`), and `error` (null on success).

## TeaVM reachability and dead-code elimination

TeaVM uses static reachability analysis from the `main` method of the
configured `mainClass` (`dev.engine.tests.screenshot.web.WebTestApp`).
Classes not reachable from that entry point are eliminated from the
generated JavaScript. This is why the explicit scene registry exists:
`put(map, "name", SomeScenes.FIELD)` gives TeaVM a direct reference
that keeps `SomeScenes` and its transitively reachable graph alive.

**Implication:** if you add a scene and it's silently missing from the
TeaVM output, first grep the generated `web-test.js` for the class
name — no hits means TeaVM never reached it, and the registry
entry is missing or typo'd.

## Incremental rebuilds

TeaVM's `generateJavaScript` task is Gradle-incremental and occasionally
fails to pick up changes from transitive dependencies. If you suspect
stale output, force it:

```bash
./gradlew :samples:tests:screenshot:web-runner:generateJavaScript --rerun-tasks
```

## Entity occlusion in test scenes

A common footgun when writing scenes: two entities placed at exactly the
same world position with overlapping bounds can fully occlude one
another, even if they're different sizes. When adding a "marker" or
"indicator" entity to verify a module ran, either:

- Offset it in Z (push it toward or away from the camera), or
- Offset it in Y / X beyond the bounds of the other entities, or
- Use a sufficiently different scale that its visible edges protrude.

The first failed iteration of `ModuleSystemScenes.PARALLEL_MODULES_OPERATIONAL`
placed a 0.35-unit white marker at `(0, 1.0, 0)` inside a 1.0-unit green
cube at the same position — the marker was 100% hidden. Fix: push the
marker forward to `z = 2.5`.

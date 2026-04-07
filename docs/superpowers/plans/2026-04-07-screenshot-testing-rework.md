# Screenshot Testing Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the tightly-coupled, crash-prone screenshot testing system with a multipass pipeline using process isolation per scene.

**Architecture:** Four Gradle modules under `samples/tests/screenshot/` (scenes, runner, desktop, analysis). Pipeline stages communicate via a single progressively-enriched `manifest.json`. Each scene+backend renders in its own JVM process, so crashes don't affect other tests.

**Tech Stack:** Java 25, Gradle, LWJGL, no JSON library (hand-rolled, matching existing project patterns)

---

### Task 1: Remove old screenshot module and set up new module structure

The existing `samples/tests/screenshot` is a single module. We need to convert it to a parent directory with four child modules. The old `src/` directory with all its test code will be removed after we've created the new modules (scenes get migrated in Task 2).

**Files:**
- Modify: `settings.gradle.kts` (root)
- Delete: `samples/tests/screenshot/build.gradle.kts`
- Create: `samples/tests/screenshot/scenes/build.gradle.kts`
- Create: `samples/tests/screenshot/runner/build.gradle.kts`
- Create: `samples/tests/screenshot/desktop/build.gradle.kts`
- Create: `samples/tests/screenshot/analysis/build.gradle.kts`

- [ ] **Step 1: Update settings.gradle.kts**

Replace the single screenshot module with four child modules:

```kotlin
// Replace:
// include("samples:tests:screenshot")
// project(":samples:tests:screenshot").projectDir = file("samples/tests/screenshot")

// With:
include("samples:tests:screenshot:scenes")
project(":samples:tests:screenshot:scenes").projectDir = file("samples/tests/screenshot/scenes")

include("samples:tests:screenshot:runner")
project(":samples:tests:screenshot:runner").projectDir = file("samples/tests/screenshot/runner")

include("samples:tests:screenshot:desktop")
project(":samples:tests:screenshot:desktop").projectDir = file("samples/tests/screenshot/desktop")

include("samples:tests:screenshot:analysis")
project(":samples:tests:screenshot:analysis").projectDir = file("samples/tests/screenshot/analysis")
```

- [ ] **Step 2: Create scenes/build.gradle.kts**

```kotlin
dependencies {
    api(project(":core"))
    api(project(":graphics:api"))
    api(project(":graphics:common"))
    api(project(":ui"))
}
```

- [ ] **Step 3: Create runner/build.gradle.kts**

```kotlin
dependencies {
    api(project(":samples:tests:screenshot:scenes"))
}
```

- [ ] **Step 4: Create desktop/build.gradle.kts**

```kotlin
val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name").startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "natives-macos-arm64" else "natives-macos"
    else -> "natives-linux"
}

dependencies {
    implementation(project(":samples:tests:screenshot:scenes"))
    implementation(project(":samples:tests:screenshot:runner"))
    implementation(project(":graphics:opengl"))
    implementation(project(":graphics:vulcan"))
    implementation(project(":graphics:webgpu"))
    implementation(project(":providers:lwjgl-gl"))
    implementation(project(":providers:lwjgl-vk"))
    implementation(project(":providers:jwebgpu"))
    implementation(project(":providers:slang"))
    implementation(project(":providers:lwjgl-glfw"))
    implementation(project(":platforms:desktop"))
    implementation(platform(libs.lwjgl.bom))
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.vulkan)
    implementation(libs.lwjgl.glfw)

    runtimeOnly(libs.lwjgl.core.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.opengl.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl.glfw.natives) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.slf4j.api)
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
}
```

- [ ] **Step 5: Create analysis/build.gradle.kts**

```kotlin
dependencies {
    implementation(project(":samples:tests:screenshot:scenes"))
}
```

- [ ] **Step 6: Delete old build.gradle.kts and old src directory**

Remove `samples/tests/screenshot/build.gradle.kts` and `samples/tests/screenshot/src/`.

- [ ] **Step 7: Verify Gradle resolves all modules**

Run: `./gradlew :samples:tests:screenshot:scenes:dependencies --configuration compileClasspath`
Expected: resolves without errors

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "refactor: scaffold 4-module screenshot test structure"
```

---

### Task 2: Scenes module — data types and scene interface

Core types that all modules depend on: `RenderTestScene`, `SceneConfig`, `Tolerance`, `ComparisonTest`, and the `Manifest` data model.

**Files:**
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/RenderTestScene.java`
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/SceneConfig.java`
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/Tolerance.java`
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/ComparisonTest.java`
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/manifest/Manifest.java`
- Test: `samples/tests/screenshot/scenes/src/test/java/dev/engine/tests/screenshot/scenes/manifest/ManifestTest.java`

- [ ] **Step 1: Create RenderTestScene interface**

```java
package dev.engine.tests.screenshot.scenes;

import dev.engine.core.input.InputEvent;
import dev.engine.graphics.common.engine.Engine;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface RenderTestScene {

    void setup(Engine engine);

    default SceneConfig config() {
        return SceneConfig.defaults();
    }

    default Map<Integer, List<InputEvent>> inputScript() {
        return Map.of();
    }
}
```

- [ ] **Step 2: Create SceneConfig record**

```java
package dev.engine.tests.screenshot.scenes;

import java.util.Map;
import java.util.Set;

public record SceneConfig(
        int width,
        int height,
        Set<Integer> captureFrames,
        Tolerance tolerance,
        boolean requiresShaderCompiler,
        Map<String, String> hints
) {
    public static SceneConfig defaults() {
        return new SceneConfig(256, 256, Set.of(3), Tolerance.loose(), false, Map.of());
    }

    public SceneConfig withTolerance(Tolerance tolerance) {
        return new SceneConfig(width, height, captureFrames, tolerance, requiresShaderCompiler, hints);
    }

    public SceneConfig withCaptureFrames(Set<Integer> captureFrames) {
        return new SceneConfig(width, height, captureFrames, tolerance, requiresShaderCompiler, hints);
    }

    public SceneConfig withShaderCompiler() {
        return new SceneConfig(width, height, captureFrames, tolerance, true, hints);
    }
}
```

- [ ] **Step 3: Create Tolerance record**

```java
package dev.engine.tests.screenshot.scenes;

public record Tolerance(int maxChannelDiff, double maxDiffPercent) {
    public static Tolerance exact() { return new Tolerance(0, 0.0); }
    public static Tolerance tight() { return new Tolerance(1, 0.001); }
    public static Tolerance loose() { return new Tolerance(2, 0.01); }
    public static Tolerance wide()  { return new Tolerance(3, 0.5); }
}
```

- [ ] **Step 4: Create ComparisonTest record**

```java
package dev.engine.tests.screenshot.scenes;

public record ComparisonTest(Tolerance tolerance, Variant... variants) {

    public record Variant(String name, RenderTestScene scene) {}

    public static ComparisonTest of(String nameA, RenderTestScene a, String nameB, RenderTestScene b) {
        return new ComparisonTest(Tolerance.tight(), new Variant(nameA, a), new Variant(nameB, b));
    }

    public static ComparisonTest of(String nameA, RenderTestScene a,
                                     String nameB, RenderTestScene b,
                                     String nameC, RenderTestScene c) {
        return new ComparisonTest(Tolerance.tight(),
                new Variant(nameA, a), new Variant(nameB, b), new Variant(nameC, c));
    }

    public ComparisonTest withTolerance(Tolerance tolerance) {
        return new ComparisonTest(tolerance, variants);
    }
}
```

- [ ] **Step 5: Write Manifest test (TDD)**

```java
package dev.engine.tests.screenshot.scenes.manifest;

import dev.engine.tests.screenshot.scenes.Tolerance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManifestTest {

    @Test
    void roundTripEmptyManifest(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.setBranch("main");
        manifest.setCommit("abc123");
        manifest.setTimestamp("2026-04-07T10:00:00Z");
        manifest.setProfile("ci");

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals("main", loaded.getBranch());
        assertEquals("abc123", loaded.getCommit());
        assertEquals("ci", loaded.getProfile());
        assertTrue(loaded.getScenes().isEmpty());
        assertTrue(loaded.getRuns().isEmpty());
        assertTrue(loaded.getComparisons().isEmpty());
    }

    @Test
    void roundTripWithSceneAndRun(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.setBranch("feature");
        manifest.setCommit("def456");
        manifest.setTimestamp("2026-04-07T10:00:00Z");
        manifest.setProfile("local");

        var scene = new Manifest.Scene();
        scene.name = "depth_test";
        scene.category = "basic";
        scene.className = "dev.engine.tests.screenshot.scenes.basic.BasicScenes";
        scene.fieldName = "DEPTH_TEST_CUBES";
        scene.captureFrames = Set.of(3);
        scene.tolerance = Tolerance.loose();
        manifest.getScenes().add(scene);

        var run = new Manifest.Run();
        run.scene = "depth_test";
        run.backend = "opengl";
        run.status = "success";
        run.durationMs = 1234;
        run.screenshots = List.of(new Manifest.Screenshot(3, "opengl/depth_test_f3.png"));
        manifest.getRuns().add(run);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals(1, loaded.getScenes().size());
        assertEquals("depth_test", loaded.getScenes().get(0).name);
        assertEquals(1, loaded.getRuns().size());
        assertEquals("opengl", loaded.getRuns().get(0).backend);
        assertEquals("success", loaded.getRuns().get(0).status);
        assertEquals(1234, loaded.getRuns().get(0).durationMs);
    }

    @Test
    void roundTripWithError(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.setBranch("main");
        manifest.setCommit("abc");
        manifest.setTimestamp("2026-04-07T10:00:00Z");
        manifest.setProfile("ci");

        var run = new Manifest.Run();
        run.scene = "crash_scene";
        run.backend = "vulkan";
        run.status = "crash";
        run.durationMs = 4500;
        run.error = new Manifest.RunError("crash", 139, "SIGSEGV", "stderr output", "stdout output");
        manifest.getRuns().add(run);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        var loadedRun = loaded.getRuns().get(0);
        assertEquals("crash", loadedRun.status);
        assertNotNull(loadedRun.error);
        assertEquals("crash", loadedRun.error.type());
        assertEquals(139, loadedRun.error.exitCode());
        assertEquals("SIGSEGV", loadedRun.error.message());
    }

    @Test
    void roundTripWithComparison(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.setBranch("main");
        manifest.setCommit("abc");
        manifest.setTimestamp("2026-04-07T10:00:00Z");
        manifest.setProfile("ci");

        var comp = new Manifest.Comparison();
        comp.scene = "depth_test";
        comp.frame = 3;
        comp.type = "reference";
        comp.backend = "opengl";
        comp.profile = "ci";
        comp.status = "pass";
        comp.diffPercent = 0.001;
        comp.tolerance = Tolerance.loose();
        manifest.getComparisons().add(comp);

        var crossComp = new Manifest.Comparison();
        crossComp.scene = "depth_test";
        crossComp.frame = 3;
        crossComp.type = "cross_backend";
        crossComp.backendA = "opengl";
        crossComp.backendB = "vulkan";
        crossComp.status = "fail";
        crossComp.diffPercent = 2.3;
        crossComp.tolerance = Tolerance.loose();
        crossComp.reason = "Diff 2.30% exceeds threshold 0.01%";
        manifest.getComparisons().add(crossComp);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals(2, loaded.getComparisons().size());
        var ref = loaded.getComparisons().get(0);
        assertEquals("reference", ref.type);
        assertEquals("opengl", ref.backend);
        assertEquals(0.001, ref.diffPercent, 0.0001);

        var cross = loaded.getComparisons().get(1);
        assertEquals("cross_backend", cross.type);
        assertEquals("opengl", cross.backendA);
        assertEquals("vulkan", cross.backendB);
        assertEquals("fail", cross.status);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew :samples:tests:screenshot:scenes:test`
Expected: FAIL — Manifest class does not exist

- [ ] **Step 7: Implement Manifest class**

Create `Manifest.java` with hand-rolled JSON serialization (matching project patterns — no external JSON lib). The class has:
- Top-level fields: branch, commit, buildVersion, timestamp, javaVersion, os, profile, viewport width/height
- Lists of: Scene, Run, Comparison
- Inner records/classes: Scene, Run, Screenshot, RunError, Comparison
- `writeTo(Path)` and `readFrom(Path)` static methods

The JSON serializer writes properly escaped strings and the parser reads them back. This is a focused, well-defined format — not a general-purpose JSON library.

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :samples:tests:screenshot:scenes:test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: scenes module — core types and manifest with JSON serialization"
```

---

### Task 3: Scenes module — SceneRegistry and scene migration

Move all existing scene definitions from the old module and migrate unique scenes from examples.

**Files:**
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/SceneRegistry.java`
- Move: all scene files from `samples/tests/screenshot/src/test/java/.../scenes/` to `samples/tests/screenshot/scenes/src/main/java/.../scenes/`
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/renderstate/StencilScenes.java` (migrated from examples)
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/renderstate/WireframeScenes.java` (migrated from examples)
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/textures/TextureApiScenes.java` (migrated from examples)
- Test: `samples/tests/screenshot/scenes/src/test/java/dev/engine/tests/screenshot/scenes/SceneRegistryTest.java`

- [ ] **Step 1: Write SceneRegistry test**

```java
package dev.engine.tests.screenshot.scenes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SceneRegistryTest {

    @Test
    void discoversScenes() {
        var registry = new SceneRegistry();
        var scenes = registry.scenes();
        assertFalse(scenes.isEmpty(), "Should discover at least one scene");
    }

    @Test
    void sceneNamesAreLowercase() {
        var registry = new SceneRegistry();
        for (var scene : registry.scenes()) {
            assertEquals(scene.name(), scene.name().toLowerCase(),
                    "Scene name should be lowercase: " + scene.name());
        }
    }

    @Test
    void scenesHaveCategories() {
        var registry = new SceneRegistry();
        for (var scene : registry.scenes()) {
            assertNotNull(scene.category(), "Scene should have category: " + scene.name());
            assertFalse(scene.category().isEmpty());
        }
    }

    @Test
    void scenesHaveConfig() {
        var registry = new SceneRegistry();
        for (var scene : registry.scenes()) {
            assertNotNull(scene.scene().config(), "Scene should have config: " + scene.name());
            assertTrue(scene.scene().config().width() > 0);
            assertTrue(scene.scene().config().height() > 0);
            assertFalse(scene.scene().config().captureFrames().isEmpty());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :samples:tests:screenshot:scenes:test`
Expected: FAIL — SceneRegistry does not exist

- [ ] **Step 3: Create SceneRegistry**

Adapt from existing `SceneDiscovery` but scan `dev.engine.tests.screenshot.scenes` package for `static final RenderTestScene` and `ComparisonTest` fields. Uses classpath scanning via reflection, same approach as existing code.

```java
package dev.engine.tests.screenshot.scenes;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class SceneRegistry {

    public record DiscoveredScene(String category, String name, RenderTestScene scene) {}
    public record DiscoveredComparison(String category, String name, ComparisonTest test) {}

    private final List<DiscoveredScene> scenes = new ArrayList<>();
    private final List<DiscoveredComparison> comparisons = new ArrayList<>();

    public SceneRegistry() { discover(); }

    public List<DiscoveredScene> scenes() { return scenes; }
    public List<DiscoveredComparison> comparisons() { return comparisons; }

    // ... (reflection-based scanning, adapted from SceneDiscovery)
}
```

- [ ] **Step 4: Move existing scene files**

Move all files from `samples/tests/screenshot/src/test/java/dev/engine/tests/screenshot/scenes/` to `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/`. Update package declarations from `dev.engine.tests.screenshot.scenes.basic` etc. — packages stay the same since the directory structure matches.

Scene files to move:
- `basic/BasicScenes.java`
- `basic/CameraScenes.java`
- `basic/HierarchyScenes.java`
- `materials/MaterialScenes.java`
- `materials/MixedMaterialScenes.java`
- `renderstate/RenderStateScenes.java`
- `renderstate/PerEntityRenderStateScenes.java`
- `textures/TextureScenes.java`
- `textures/SamplerScenes.java`
- `input/InputTestScenes.java`
- `ui/UiScenes.java`

Update imports: `dev.engine.tests.screenshot.RenderTestScene` → `dev.engine.tests.screenshot.scenes.RenderTestScene` (and same for Tolerance, ComparisonTest).

- [ ] **Step 5: Migrate unique example scenes**

Create `StencilScenes.java` (from examples `STENCIL_MASKING`), `WireframeScenes.java` (from `FORCED_WIREFRAME`), `TextureApiScenes.java` (from `TEXTURE_3D_CREATE`, `TEXTURE_ARRAY_CREATE`), and `DepthFuncScenes.java` (from `DEPTH_FUNC_GREATER`).

These need to be converted from the examples `(Renderer, Scene, w, h)` signature to the samples `(Engine)` signature — access renderer via `engine.renderer()` and scene via `engine.scene()`.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :samples:tests:screenshot:scenes:test`
Expected: PASS — SceneRegistry discovers scenes from the classpath

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: scene registry and migrate all scenes to scenes module"
```

---

### Task 4: Runner module — AbstractTestRunner

The abstract orchestrator that spawns child JVM processes and manages the manifest.

**Files:**
- Create: `samples/tests/screenshot/runner/src/main/java/dev/engine/tests/screenshot/runner/AbstractTestRunner.java`
- Create: `samples/tests/screenshot/runner/src/main/java/dev/engine/tests/screenshot/runner/RunnerConfig.java`
- Create: `samples/tests/screenshot/runner/src/main/java/dev/engine/tests/screenshot/runner/SceneResult.java`
- Test: `samples/tests/screenshot/runner/src/test/java/dev/engine/tests/screenshot/runner/AbstractTestRunnerTest.java`

- [ ] **Step 1: Create RunnerConfig**

```java
package dev.engine.tests.screenshot.runner;

import java.nio.file.Path;

public record RunnerConfig(
        long timeoutMs,
        String profile,
        Path outputDir,
        Path referencesDir
) {
    public static RunnerConfig defaults(Path outputDir, Path referencesDir) {
        return new RunnerConfig(30_000, "local", outputDir, referencesDir);
    }
}
```

- [ ] **Step 2: Create SceneResult sealed interface**

```java
package dev.engine.tests.screenshot.runner;

import java.util.List;
import java.util.Map;

public sealed interface SceneResult {
    record Success(Map<Integer, String> screenshotPaths) implements SceneResult {}
    record ExceptionResult(String message, String stackTrace) implements SceneResult {}
    record Crash(int exitCode, String stderr, String stdout) implements SceneResult {}
    record Timeout(String stderr, String stdout) implements SceneResult {}
}
```

- [ ] **Step 3: Write AbstractTestRunner test**

```java
package dev.engine.tests.screenshot.runner;

import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbstractTestRunnerTest {

    @Test
    void runPopulatesManifestWithSuccessfulRun(@TempDir Path tmp) throws Exception {
        var manifest = createMinimalManifest();
        var scene = new Manifest.Scene();
        scene.name = "test_scene";
        scene.category = "basic";
        scene.className = "com.example.TestScenes";
        scene.fieldName = "TEST_SCENE";
        scene.captureFrames = java.util.Set.of(3);
        scene.tolerance = new dev.engine.tests.screenshot.scenes.Tolerance(2, 0.01);
        manifest.getScenes().add(scene);

        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var outputDir = tmp.resolve("screenshots");
        var refsDir = tmp.resolve("references");
        var config = new RunnerConfig(5000, "local", outputDir, refsDir);

        // Use a fake runner that simulates success
        var runner = new FakeTestRunner(List.of("opengl"), SceneResult.Success::new);
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(1, loaded.getRuns().size());
        assertEquals("success", loaded.getRuns().get(0).status);
        assertEquals("opengl", loaded.getRuns().get(0).backend);
    }

    @Test
    void runPopulatesManifestWithCrash(@TempDir Path tmp) throws Exception {
        var manifest = createMinimalManifest();
        var scene = new Manifest.Scene();
        scene.name = "crash_scene";
        scene.category = "basic";
        scene.className = "com.example.TestScenes";
        scene.fieldName = "CRASH_SCENE";
        scene.captureFrames = java.util.Set.of(3);
        scene.tolerance = new dev.engine.tests.screenshot.scenes.Tolerance(2, 0.01);
        manifest.getScenes().add(scene);

        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var outputDir = tmp.resolve("screenshots");
        var refsDir = tmp.resolve("references");
        var config = new RunnerConfig(5000, "local", outputDir, refsDir);

        var runner = new FakeTestRunner(List.of("vulkan"),
                (frames) -> new SceneResult.Crash(139, "SIGSEGV", ""));
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(1, loaded.getRuns().size());
        assertEquals("crash", loaded.getRuns().get(0).status);
        assertEquals(139, loaded.getRuns().get(0).error.exitCode());
    }

    @Test
    void runContinuesAfterFailure(@TempDir Path tmp) throws Exception {
        var manifest = createMinimalManifest();
        for (String name : List.of("scene_a", "scene_b")) {
            var scene = new Manifest.Scene();
            scene.name = name;
            scene.category = "basic";
            scene.className = "com.example.TestScenes";
            scene.fieldName = name.toUpperCase();
            scene.captureFrames = java.util.Set.of(3);
            scene.tolerance = new dev.engine.tests.screenshot.scenes.Tolerance(2, 0.01);
            manifest.getScenes().add(scene);
        }

        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var outputDir = tmp.resolve("screenshots");
        var refsDir = tmp.resolve("references");
        var config = new RunnerConfig(5000, "local", outputDir, refsDir);

        // First scene crashes, second succeeds
        var callCount = new int[]{0};
        var runner = new FakeTestRunner(List.of("opengl"), (frames) -> {
            if (callCount[0]++ == 0) return new SceneResult.Crash(139, "SIGSEGV", "");
            return new SceneResult.Success(java.util.Map.of(3, "opengl/scene_b_f3.png"));
        });
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(2, loaded.getRuns().size());
        assertEquals("crash", loaded.getRuns().get(0).status);
        assertEquals("success", loaded.getRuns().get(1).status);
    }

    private Manifest createMinimalManifest() {
        var m = new Manifest();
        m.setBranch("test");
        m.setCommit("abc");
        m.setTimestamp("2026-04-07T10:00:00Z");
        m.setProfile("local");
        return m;
    }
}
```

Also create `FakeTestRunner` as a test helper:

```java
package dev.engine.tests.screenshot.runner;

import dev.engine.tests.screenshot.scenes.SceneConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class FakeTestRunner extends AbstractTestRunner {
    private final List<String> backends;
    private final Function<Set<Integer>, SceneResult> resultFactory;

    FakeTestRunner(List<String> backends, Function<Set<Integer>, SceneResult> resultFactory) {
        this.backends = backends;
        this.resultFactory = resultFactory;
    }

    @Override
    public List<String> backends() { return backends; }

    @Override
    protected SceneResult runScene(String className, String fieldName, String backend,
                                    java.nio.file.Path outputDir, SceneConfig config) {
        return resultFactory.apply(config.captureFrames());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :samples:tests:screenshot:runner:test`
Expected: FAIL — AbstractTestRunner does not exist

- [ ] **Step 5: Implement AbstractTestRunner**

The runner reads the manifest, iterates all scenes × backends, calls `runScene()` for each, converts `SceneResult` to `Manifest.Run`, and writes the updated manifest after each scene.

```java
package dev.engine.tests.screenshot.runner;

import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTestRunner {

    public abstract List<String> backends();

    protected abstract SceneResult runScene(String className, String fieldName,
                                             String backend, Path outputDir, SceneConfig config);

    public void run(Path manifestPath, RunnerConfig config) throws Exception {
        var manifest = Manifest.readFrom(manifestPath);

        for (var scene : manifest.getScenes()) {
            var sceneConfig = new SceneConfig(
                    /* reconstruct from manifest scene data */);

            for (var backend : backends()) {
                long start = System.currentTimeMillis();
                SceneResult result;
                try {
                    result = runScene(scene.className, scene.fieldName,
                            backend, config.outputDir(), sceneConfig);
                } catch (Exception e) {
                    result = new SceneResult.ExceptionResult(e.getMessage(),
                            stackTraceToString(e));
                }
                long duration = System.currentTimeMillis() - start;

                var run = toManifestRun(scene.name, backend, result, duration);
                manifest.getRuns().add(run);

                // Write after each scene so crashes don't lose prior results
                manifest.writeTo(manifestPath);
            }
        }
    }

    // ... conversion helpers
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :samples:tests:screenshot:runner:test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: abstract test runner with process orchestration and manifest updates"
```

---

### Task 5: Desktop module — DesktopRunner and DesktopRenderMain

The desktop-specific runner that spawns LWJGL child processes.

**Files:**
- Create: `samples/tests/screenshot/desktop/src/main/java/dev/engine/tests/screenshot/desktop/DesktopRunner.java`
- Create: `samples/tests/screenshot/desktop/src/main/java/dev/engine/tests/screenshot/desktop/DesktopRenderMain.java`
- Create: `samples/tests/screenshot/desktop/src/main/java/dev/engine/tests/screenshot/desktop/ChildResult.java`

- [ ] **Step 1: Create ChildResult — the JSON contract between child and runner**

```java
package dev.engine.tests.screenshot.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record ChildResult(String status, List<FrameCapture> screenshots,
                           String message, String stackTrace) {

    public record FrameCapture(int frame, String path) {}

    public static ChildResult success(List<FrameCapture> screenshots) {
        return new ChildResult("success", screenshots, null, null);
    }

    public static ChildResult exception(String message, String stackTrace) {
        return new ChildResult("exception", List.of(), message, stackTrace);
    }

    public void writeTo(Path file) throws IOException { /* hand-rolled JSON */ }
    public static ChildResult readFrom(Path file) throws IOException { /* parse JSON */ }
}
```

- [ ] **Step 2: Create DesktopRenderMain — child process entry point**

```java
package dev.engine.tests.screenshot.desktop;

import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.bindings.slang.SlangShaderCompiler;
import dev.engine.graphics.*;
import dev.engine.graphics.common.engine.*;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.platform.desktop.DesktopPlatform;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

/**
 * Child process main class. Receives scene class+field+backend+output path
 * as command-line args. Renders the scene, saves screenshots, writes result JSON.
 *
 * Args: <className> <fieldName> <backend> <outputDir> <resultFile>
 */
public class DesktopRenderMain {

    public static void main(String[] args) {
        String className = args[0];
        String fieldName = args[1];
        String backend = args[2];
        Path outputDir = Path.of(args[3]);
        Path resultFile = Path.of(args[4]);

        try {
            // Load scene via reflection
            var clazz = Class.forName(className);
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            var scene = (RenderTestScene) field.get(null);
            var config = scene.config();

            // Create backend
            var factory = createBackendFactory(backend);
            var windowDesc = WindowDescriptor.builder(backend + " Test")
                    .size(config.width(), config.height()).build();
            var gfxConfig = new GraphicsConfigLegacy(false);
            var gfxBackend = factory.create(windowDesc, gfxConfig);

            var platform = DesktopPlatform.builder()
                    .shaderCompiler(config.requiresShaderCompiler()
                            ? new SlangShaderCompiler() : null)
                    .build();
            var engineConfig = EngineConfig.builder()
                    .window(windowDesc)
                    .platform(platform)
                    .graphicsBackend(factory)
                    .maxFrames(0)
                    .build();
            var engine = new Engine(engineConfig, platform, gfxBackend.device());

            try {
                engine.renderer().setViewport(config.width(), config.height());
                scene.setup(engine);

                int maxFrame = config.captureFrames().stream()
                        .mapToInt(Integer::intValue).max().orElse(3);
                var inputScript = scene.inputScript();
                var captures = new ArrayList<ChildResult.FrameCapture>();

                for (int frame = 0; frame <= maxFrame; frame++) {
                    engine.setInputEvents(inputScript.getOrDefault(frame, List.of()));
                    engine.tick(1.0 / 60.0);

                    if (config.captureFrames().contains(frame)) {
                        byte[] pixels = gfxBackend.device().readFramebuffer(
                                config.width(), config.height());
                        var filename = fieldNameToSceneName(fieldName) + "_f" + frame + ".png";
                        var dir = outputDir.resolve(backend);
                        java.nio.file.Files.createDirectories(dir);
                        ScreenshotHelper.save(pixels, config.width(), config.height(),
                                dir.resolve(filename).toString());
                        captures.add(new ChildResult.FrameCapture(frame,
                                backend + "/" + filename));
                    }
                }

                ChildResult.success(captures).writeTo(resultFile);
            } finally {
                engine.shutdown();
                gfxBackend.toolkit().close();
            }
        } catch (Exception e) {
            try {
                ChildResult.exception(e.getMessage(), stackTraceToString(e))
                        .writeTo(resultFile);
            } catch (Exception ignored) {}
            System.exit(1);
        }
    }

    private static GraphicsBackendFactory createBackendFactory(String backend) {
        // ... switch on backend name to create GL/VK/WebGPU factory
    }

    private static String fieldNameToSceneName(String fieldName) {
        return fieldName.toLowerCase();
    }

    private static String stackTraceToString(Exception e) {
        var sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
```

- [ ] **Step 3: Create DesktopRunner**

```java
package dev.engine.tests.screenshot.desktop;

import dev.engine.tests.screenshot.runner.*;
import dev.engine.tests.screenshot.scenes.SceneConfig;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DesktopRunner extends AbstractTestRunner {

    @Override
    public List<String> backends() {
        return List.of("opengl", "vulkan", "webgpu");
    }

    @Override
    protected SceneResult runScene(String className, String fieldName,
                                    String backend, Path outputDir, SceneConfig config) {
        var resultFile = outputDir.resolve(backend + "_" + fieldName.toLowerCase() + "_result.json");
        try {
            var pb = buildProcess(className, fieldName, backend, outputDir, resultFile);
            var process = pb.start();
            // Capture stdout/stderr
            var stdout = new String(process.getInputStream().readAllBytes());
            var stderr = new String(process.getErrorStream().readAllBytes());
            boolean finished = process.waitFor(config.timeoutMs(), TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                return new SceneResult.Timeout(stderr, stdout);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0 && Files.exists(resultFile)) {
                var childResult = ChildResult.readFrom(resultFile);
                var paths = new HashMap<Integer, String>();
                for (var s : childResult.screenshots()) paths.put(s.frame(), s.path());
                return new SceneResult.Success(paths);
            } else if (Files.exists(resultFile)) {
                var childResult = ChildResult.readFrom(resultFile);
                return new SceneResult.ExceptionResult(childResult.message(), childResult.stackTrace());
            } else {
                return new SceneResult.Crash(exitCode, stderr, stdout);
            }
        } catch (Exception e) {
            return new SceneResult.ExceptionResult(e.getMessage(), stackTraceToString(e));
        }
    }

    private ProcessBuilder buildProcess(String className, String fieldName,
                                         String backend, Path outputDir, Path resultFile) {
        var javaHome = System.getProperty("java.home");
        var java = Path.of(javaHome, "bin", "java").toString();
        var classpath = System.getProperty("java.class.path");

        var args = new ArrayList<String>();
        args.add(java);
        args.add("--enable-native-access=ALL-UNNAMED");
        args.add("-cp");
        args.add(classpath);
        args.add(DesktopRenderMain.class.getName());
        args.add(className);
        args.add(fieldName);
        args.add(backend);
        args.add(outputDir.toAbsolutePath().toString());
        args.add(resultFile.toAbsolutePath().toString());

        var pb = new ProcessBuilder(args);
        // jemalloc
        var jemallocPaths = List.of(
                "/lib/x86_64-linux-gnu/libjemalloc.so.2",
                "/usr/lib/libjemalloc.so.2");
        for (var path : jemallocPaths) {
            if (new File(path).exists()) {
                pb.environment().put("LD_PRELOAD", path);
                break;
            }
        }
        return pb;
    }

    // ... helper methods
}
```

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: desktop runner with process isolation per scene"
```

---

### Task 6: Analysis module — ImageUtils and Comparator

**Files:**
- Create: `samples/tests/screenshot/analysis/src/main/java/dev/engine/tests/screenshot/analysis/ImageUtils.java`
- Create: `samples/tests/screenshot/analysis/src/main/java/dev/engine/tests/screenshot/analysis/Comparator.java`
- Test: `samples/tests/screenshot/analysis/src/test/java/dev/engine/tests/screenshot/analysis/ImageUtilsTest.java`
- Test: `samples/tests/screenshot/analysis/src/test/java/dev/engine/tests/screenshot/analysis/ComparatorTest.java`

- [ ] **Step 1: Write ImageUtils test**

```java
package dev.engine.tests.screenshot.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsTest {

    @Test
    void identicalImagesHaveZeroDiff() {
        byte[] a = new byte[]{(byte)255, 0, 0, (byte)255, 0, (byte)255, 0, (byte)255};
        byte[] b = new byte[]{(byte)255, 0, 0, (byte)255, 0, (byte)255, 0, (byte)255};
        assertEquals(0.0, ImageUtils.diffPercentage(a, b, 0));
    }

    @Test
    void completelyDifferentImagesReturn100() {
        byte[] a = new byte[]{(byte)255, 0, 0, (byte)255, 0, 0, 0, (byte)255};
        byte[] b = new byte[]{0, (byte)255, 0, (byte)255, (byte)255, 0, 0, (byte)255};
        assertEquals(100.0, ImageUtils.diffPercentage(a, b, 0));
    }

    @Test
    void withinThresholdCountsAsMatch() {
        byte[] a = new byte[]{100, 100, 100, (byte)255};
        byte[] b = new byte[]{102, 101, 100, (byte)255};
        assertEquals(0.0, ImageUtils.diffPercentage(a, b, 2));
    }

    @Test
    void saveAndLoadRoundTrips(@TempDir Path tmp) throws Exception {
        byte[] original = new byte[]{(byte)255, 0, 0, (byte)255, 0, (byte)255, 0, (byte)255};
        var file = tmp.resolve("test.png");
        ImageUtils.savePng(original, 2, 1, file);
        byte[] loaded = ImageUtils.loadPng(file, 2, 1);
        assertArrayEquals(original, loaded);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :samples:tests:screenshot:analysis:test`
Expected: FAIL

- [ ] **Step 3: Implement ImageUtils**

```java
package dev.engine.tests.screenshot.analysis;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

public final class ImageUtils {
    private ImageUtils() {}

    public static double diffPercentage(byte[] a, byte[] b, int threshold) {
        // Same logic as existing ScreenshotHelper.diffPercentage
    }

    public static void savePng(byte[] rgba, int width, int height, Path path) throws IOException {
        // Same logic as existing ScreenshotHelper.save
    }

    public static byte[] loadPng(Path path, int width, int height) throws IOException {
        // Load PNG and convert to RGBA byte array
    }
}
```

- [ ] **Step 4: Run ImageUtils test**

Run: `./gradlew :samples:tests:screenshot:analysis:test`
Expected: PASS

- [ ] **Step 5: Write Comparator test**

```java
package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.Tolerance;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ComparatorTest {

    @Test
    void matchingReferenceProducePassComparison(@TempDir Path tmp) throws Exception {
        // Create identical screenshot and reference PNGs
        byte[] pixels = new byte[4 * 4 * 4]; // 4x4 RGBA
        Arrays.fill(pixels, (byte) 128);
        var screenshotDir = tmp.resolve("screenshots/opengl");
        java.nio.file.Files.createDirectories(screenshotDir);
        ImageUtils.savePng(pixels, 4, 4, screenshotDir.resolve("test_scene_f3.png"));

        var refDir = tmp.resolve("references/local/opengl");
        java.nio.file.Files.createDirectories(refDir);
        ImageUtils.savePng(pixels, 4, 4, refDir.resolve("test_scene_f3.png"));

        var manifest = createManifestWithSuccessfulRun("test_scene", "opengl", 3,
                "opengl/test_scene_f3.png", 4, 4);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        Comparator.compare(manifestPath, tmp.resolve("screenshots"),
                tmp.resolve("references/local"));

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(1, loaded.getComparisons().size());
        assertEquals("pass", loaded.getComparisons().get(0).status);
    }

    @Test
    void differentReferenceProduceFailComparison(@TempDir Path tmp) throws Exception {
        byte[] screenshot = new byte[4 * 4 * 4];
        Arrays.fill(screenshot, (byte) 255);
        byte[] reference = new byte[4 * 4 * 4];
        Arrays.fill(reference, (byte) 0);

        var screenshotDir = tmp.resolve("screenshots/opengl");
        java.nio.file.Files.createDirectories(screenshotDir);
        ImageUtils.savePng(screenshot, 4, 4, screenshotDir.resolve("test_scene_f3.png"));

        var refDir = tmp.resolve("references/local/opengl");
        java.nio.file.Files.createDirectories(refDir);
        ImageUtils.savePng(reference, 4, 4, refDir.resolve("test_scene_f3.png"));

        var manifest = createManifestWithSuccessfulRun("test_scene", "opengl", 3,
                "opengl/test_scene_f3.png", 4, 4);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        Comparator.compare(manifestPath, tmp.resolve("screenshots"),
                tmp.resolve("references/local"));

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals("fail", loaded.getComparisons().get(0).status);
        assertTrue(loaded.getComparisons().get(0).diffPercent > 0);
    }

    @Test
    void missingReferenceProducesNoReferenceStatus(@TempDir Path tmp) throws Exception {
        byte[] pixels = new byte[4 * 4 * 4];
        var screenshotDir = tmp.resolve("screenshots/opengl");
        java.nio.file.Files.createDirectories(screenshotDir);
        ImageUtils.savePng(pixels, 4, 4, screenshotDir.resolve("test_scene_f3.png"));

        // No reference saved

        var manifest = createManifestWithSuccessfulRun("test_scene", "opengl", 3,
                "opengl/test_scene_f3.png", 4, 4);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        Comparator.compare(manifestPath, tmp.resolve("screenshots"),
                tmp.resolve("references/local"));

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals("no_reference", loaded.getComparisons().get(0).status);
    }

    @Test
    void crossBackendComparisonsGeneratedForAllPairs(@TempDir Path tmp) throws Exception {
        byte[] pixels = new byte[4 * 4 * 4];
        Arrays.fill(pixels, (byte) 128);

        // Create screenshots for 3 backends
        for (var backend : List.of("opengl", "vulkan", "webgpu")) {
            var dir = tmp.resolve("screenshots/" + backend);
            java.nio.file.Files.createDirectories(dir);
            ImageUtils.savePng(pixels, 4, 4, dir.resolve("test_scene_f3.png"));
        }

        var manifest = createMinimalManifest();
        var scene = new Manifest.Scene();
        scene.name = "test_scene";
        scene.category = "basic";
        scene.className = "Test";
        scene.fieldName = "TEST";
        scene.captureFrames = Set.of(3);
        scene.tolerance = Tolerance.loose();
        scene.width = 4;
        scene.height = 4;
        manifest.getScenes().add(scene);

        for (var backend : List.of("opengl", "vulkan", "webgpu")) {
            var run = new Manifest.Run();
            run.scene = "test_scene";
            run.backend = backend;
            run.status = "success";
            run.screenshots = List.of(new Manifest.Screenshot(3, backend + "/test_scene_f3.png"));
            manifest.getRuns().add(run);
        }

        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        Comparator.compare(manifestPath, tmp.resolve("screenshots"),
                tmp.resolve("references/local"));

        var loaded = Manifest.readFrom(manifestPath);
        // 3 reference comparisons (one per backend, all no_reference) + 3 cross-backend pairs
        long crossCount = loaded.getComparisons().stream()
                .filter(c -> "cross_backend".equals(c.type)).count();
        assertEquals(3, crossCount); // GL-VK, GL-WebGPU, VK-WebGPU
    }

    // ... helper methods
}
```

- [ ] **Step 6: Run Comparator test to verify it fails**

Run: `./gradlew :samples:tests:screenshot:analysis:test`
Expected: FAIL

- [ ] **Step 7: Implement Comparator**

```java
package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.*;
import java.util.*;

public final class Comparator {

    public static void compare(Path manifestPath, Path screenshotDir, Path referenceDir)
            throws Exception {
        var manifest = Manifest.readFrom(manifestPath);

        for (var scene : manifest.getScenes()) {
            var successfulRuns = manifest.getRuns().stream()
                    .filter(r -> r.scene.equals(scene.name) && "success".equals(r.status))
                    .toList();

            // Reference comparisons per backend per frame
            for (var run : successfulRuns) {
                for (var screenshot : run.screenshots) {
                    var refPath = referenceDir.resolve(run.backend)
                            .resolve(scene.name + "_f" + screenshot.frame() + ".png");
                    var screenshotPath = screenshotDir.resolve(screenshot.path());
                    // compare and add to manifest.comparisons
                }
            }

            // Cross-backend comparisons — full pair matrix
            var backends = successfulRuns.stream().map(r -> r.backend).sorted().toList();
            for (int i = 0; i < backends.size(); i++) {
                for (int j = i + 1; j < backends.size(); j++) {
                    // compare backends[i] vs backends[j] for each frame
                }
            }
        }

        // Also handle failed runs → skipped comparisons
        manifest.writeTo(manifestPath);
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew :samples:tests:screenshot:analysis:test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: analysis module — image comparison and cross-backend matrix"
```

---

### Task 7: Analysis module — ReportBuilder and RegressionChecker

**Files:**
- Create: `samples/tests/screenshot/analysis/src/main/java/dev/engine/tests/screenshot/analysis/ReportBuilder.java`
- Create: `samples/tests/screenshot/analysis/src/main/java/dev/engine/tests/screenshot/analysis/RegressionChecker.java`
- Test: `samples/tests/screenshot/analysis/src/test/java/dev/engine/tests/screenshot/analysis/RegressionCheckerTest.java`

- [ ] **Step 1: Write RegressionChecker test**

```java
package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.Tolerance;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegressionCheckerTest {

    @Test
    void returnsZeroWhenAllPass(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("pass", null);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(0, exitCode);
    }

    @Test
    void returnsOneWhenAnyFail(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("fail", "Diff 2.3% exceeds threshold");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(1, exitCode);
    }

    @Test
    void returnsZeroWithNoReferenceOnly(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("no_reference", "No reference for local");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        // No reference dir exists
        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(0, exitCode);
    }

    @Test
    void returnsZeroForSkipped(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("skipped", "Run crashed");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(0, exitCode);
    }

    // ... helper
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :samples:tests:screenshot:analysis:test`
Expected: FAIL

- [ ] **Step 3: Implement RegressionChecker**

```java
package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.*;

public final class RegressionChecker {

    public static int check(Path manifestPath, Path referenceDir) throws Exception {
        var manifest = Manifest.readFrom(manifestPath);

        boolean hasFailures = manifest.getComparisons().stream()
                .anyMatch(c -> "fail".equals(c.status));

        boolean allNoReference = manifest.getComparisons().stream()
                .allMatch(c -> "no_reference".equals(c.status) || "skipped".equals(c.status));

        if (hasFailures) {
            // Print failure details
            System.err.println("REGRESSION DETECTED:");
            for (var comp : manifest.getComparisons()) {
                if ("fail".equals(comp.status)) {
                    System.err.println("  FAIL: " + comp.scene + " [" +
                            (comp.backend != null ? comp.backend : comp.backendA + "↔" + comp.backendB)
                            + "] — " + comp.reason);
                }
            }
            return 1;
        }

        if (allNoReference && !manifest.getComparisons().isEmpty()) {
            boolean refDirExists = Files.exists(referenceDir)
                    && Files.list(referenceDir).findAny().isPresent();
            if (!refDirExists) {
                System.out.println("WARNING: No local reference images found for profile '" +
                        manifest.getProfile() + "'.");
                System.out.println("Screenshot tests ran but could not verify against references.");
                System.out.println();
                System.out.println("To generate local references, run:");
                System.out.println("  git stash");
                System.out.println("  git checkout main");
                System.out.println("  ./gradlew saveReferences -Pscreenshot.profile=local");
                System.out.println("  git checkout -");
                System.out.println("  git stash pop");
            }
        }

        return 0;
    }

    public static void main(String[] args) throws Exception {
        var manifestPath = Path.of(args[0]);
        var referenceDir = Path.of(args[1]);
        System.exit(check(manifestPath, referenceDir));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :samples:tests:screenshot:analysis:test`
Expected: PASS

- [ ] **Step 5: Implement ReportBuilder**

Adapt from existing `ScreenshotReportGenerator` but read from manifest instead of JUnit XML + diffs.json. Generates the same dark-themed HTML report with:
- Summary stats from manifest comparisons
- Per-scene cards grouped by category
- Backend badges with pass/fail/skip status and diff percentages
- Cross-backend comparison badges (full matrix)
- Error details for crashes/exceptions/timeouts
- Image thumbnails
- Filter buttons

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: report builder and regression checker with missing-reference warnings"
```

---

### Task 8: Gradle tasks and integration

Wire all pipeline stages as Gradle tasks and integrate with the root `test` task.

**Files:**
- Create: `samples/tests/screenshot/scenes/src/main/java/dev/engine/tests/screenshot/scenes/CollectScenes.java`
- Modify: `samples/tests/screenshot/desktop/build.gradle.kts`
- Modify: `samples/tests/screenshot/analysis/build.gradle.kts`
- Modify: root `build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Create CollectScenes main class**

```java
package dev.engine.tests.screenshot.scenes;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

/**
 * Pipeline Pass 1: Discovers all scenes and writes the initial manifest.
 * Args: <outputPath> <profile>
 */
public class CollectScenes {
    public static void main(String[] args) throws Exception {
        var outputPath = Path.of(args[0]);
        var profile = args.length > 1 ? args[1] : "local";

        var registry = new SceneRegistry();
        var manifest = new Manifest();

        // Populate metadata
        manifest.setTimestamp(Instant.now().toString());
        manifest.setProfile(profile);
        manifest.setJavaVersion(System.getProperty("java.version"));
        manifest.setOs(System.getProperty("os.name") + " " + System.getProperty("os.version"));

        // Git info
        try {
            manifest.setBranch(execGit("rev-parse", "--abbrev-ref", "HEAD"));
            manifest.setCommit(execGit("rev-parse", "--short", "HEAD"));
        } catch (Exception e) {
            manifest.setBranch("unknown");
            manifest.setCommit("unknown");
        }

        // Add scenes
        for (var discovered : registry.scenes()) {
            var scene = new Manifest.Scene();
            scene.name = discovered.name();
            scene.category = discovered.category();
            scene.className = /* derive from registry */;
            scene.fieldName = /* derive from registry */;
            var config = discovered.scene().config();
            scene.captureFrames = config.captureFrames();
            scene.tolerance = config.tolerance();
            scene.width = config.width();
            scene.height = config.height();
            manifest.getScenes().add(scene);
        }

        manifest.writeTo(outputPath);
        System.out.println("Collected " + manifest.getScenes().size() + " scenes → " + outputPath);
    }

    private static String execGit(String... args) throws Exception {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("git");
        cmd.addAll(java.util.Arrays.asList(args));
        var p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        var result = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return result;
    }
}
```

- [ ] **Step 2: Add Gradle tasks to desktop/build.gradle.kts**

```kotlin
// Add to existing desktop/build.gradle.kts:

val screenshotOutputDir = rootProject.layout.buildDirectory.dir(
    "screenshots").get().asFile.absolutePath
// Actually, use a shared output dir under samples/tests/screenshot/build/screenshots

val screenshotBuildDir = project.parent!!.layout.buildDirectory.dir("screenshots")
val referencesDir = project.parent!!.projectDir.resolve("references")

tasks.register<JavaExec>("collectScenes") {
    group = "verification"
    description = "Pass 1: Discover scenes and write initial manifest"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.scenes.CollectScenes"
    val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
    args = listOf(
        screenshotBuildDir.get().file("manifest.json").asFile.absolutePath,
        profile
    )
}

tasks.register<JavaExec>("runDesktop") {
    group = "verification"
    description = "Pass 2: Run all scenes on desktop backends"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.desktop.DesktopRunnerMain"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
    args = listOf(
        screenshotBuildDir.get().file("manifest.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath,
        profile
    )
    dependsOn("collectScenes")
}

tasks.register<JavaExec>("saveReferences") {
    group = "verification"
    description = "Render all scenes and save as reference screenshots"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.desktop.SaveReferencesMain"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
    args = listOf(referencesDir.resolve(profile).absolutePath, profile)
}
```

- [ ] **Step 3: Add Gradle tasks to analysis/build.gradle.kts**

```kotlin
val screenshotBuildDir = project.parent!!.layout.buildDirectory.dir("screenshots")
val referencesDir = project.parent!!.projectDir.resolve("references")

tasks.register<JavaExec>("compare") {
    group = "verification"
    description = "Pass 3: Compare screenshots against references and cross-backend"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.analysis.Comparator"
    val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
    args = listOf(
        screenshotBuildDir.get().file("manifest.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath
    )
    dependsOn(":samples:tests:screenshot:desktop:runDesktop")
}

tasks.register<JavaExec>("report") {
    group = "verification"
    description = "Pass 4: Generate HTML report from manifest"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.analysis.ReportBuilder"
    args = listOf(
        screenshotBuildDir.get().file("manifest.json").asFile.absolutePath,
        screenshotBuildDir.get().asFile.absolutePath,
        screenshotBuildDir.get().file("report.html").asFile.absolutePath
    )
    dependsOn("compare")
    doLast {
        val report = screenshotBuildDir.get().file("report.html").asFile
        println("Report: file://${report.absolutePath}")
    }
}

tasks.register<JavaExec>("failOnRegression") {
    group = "verification"
    description = "Fail build if any screenshot comparison shows regression"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.engine.tests.screenshot.analysis.RegressionChecker"
    val profile = project.findProperty("screenshot.profile")?.toString() ?: "local"
    args = listOf(
        screenshotBuildDir.get().file("manifest.json").asFile.absolutePath,
        referencesDir.resolve(profile).absolutePath
    )
    dependsOn("report")
}
```

- [ ] **Step 4: Wire root test task**

Add to root `build.gradle.kts`:

```kotlin
tasks.named("test") {
    dependsOn(":samples:tests:screenshot:analysis:failOnRegression")
}
```

- [ ] **Step 5: Add lifecycle task**

Create a `screenshotTest` task that chains everything:

```kotlin
// In analysis/build.gradle.kts or a parent convention
tasks.register("screenshotTest") {
    group = "verification"
    description = "Full screenshot test pipeline: collect → run → compare → report → check"
    dependsOn("failOnRegression")
}
```

- [ ] **Step 6: Test the pipeline**

Run: `./gradlew :samples:tests:screenshot:analysis:failOnRegression --dry-run`
Expected: shows task execution order: collectScenes → runDesktop → compare → report → failOnRegression

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: wire Gradle tasks for screenshot test pipeline"
```

---

### Task 9: Cleanup and gitignore

Remove old code and set up gitignore for local references.

**Files:**
- Delete: `samples/tests/screenshot/src/` (entire old test source tree)
- Delete: `examples/src/test/java/dev/engine/examples/RenderTestScene.java`
- Delete: `examples/src/test/java/dev/engine/examples/RenderTestHarness.java`
- Delete: `examples/src/test/java/dev/engine/examples/ScreenshotTestSuite.java`
- Delete: `examples/src/test/java/dev/engine/examples/CrossBackendScenes.java`
- Delete: `examples/src/test/java/dev/engine/examples/ScreenshotReportGenerator.java`
- Delete: `examples/src/test/java/dev/engine/examples/OpenGlRenderTest.java`
- Delete: `examples/src/test/java/dev/engine/examples/VulkanRenderTest.java`
- Delete: `examples/src/test/java/dev/engine/examples/WebGpuRenderTest.java`
- Delete: `examples/src/test/java/dev/engine/examples/CrossBackendTest.java`
- Modify: `examples/build.gradle.kts` (remove screenshot-related tasks)
- Create: `samples/tests/screenshot/.gitignore`
- Move: existing reference screenshots to `samples/tests/screenshot/references/ci/`

- [ ] **Step 1: Create .gitignore for local references**

```
# Local reference screenshots (real GPU, not committed)
references/local/

# JVM crash logs
hs_err_pid*.log
```

- [ ] **Step 2: Move existing CI references**

Move `samples/tests/screenshot/src/test/resources/reference-screenshots/` to `samples/tests/screenshot/references/ci/`.

- [ ] **Step 3: Delete old source tree**

Remove `samples/tests/screenshot/src/` entirely.

- [ ] **Step 4: Delete examples screenshot test files**

Remove all screenshot-related test files from examples module.

- [ ] **Step 5: Clean up examples/build.gradle.kts**

Remove `screenshotReport` and `generateReport` tasks.

- [ ] **Step 6: Verify build**

Run: `./gradlew build`
Expected: compiles successfully

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: remove old screenshot test infrastructure, set up reference profiles"
```

---

### Task 10: CI workflow update

Update GitHub Actions to use the new pipeline.

**Files:**
- Modify: `.github/workflows/screenshot-tests.yml`

- [ ] **Step 1: Update CI workflow**

```yaml
name: Screenshot Tests

on:
  pull_request:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  screenshot-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Install dependencies
        run: sudo apt-get install -y xvfb mesa-utils libjemalloc2

      - name: Run screenshot pipeline
        run: xvfb-run ./gradlew :samples:tests:screenshot:analysis:report -Pscreenshot.profile=ci
        continue-on-error: true

      - name: Upload report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: screenshot-report
          path: |
            samples/tests/screenshot/build/screenshots/report.html
            samples/tests/screenshot/build/screenshots/manifest.json
            samples/tests/screenshot/build/screenshots/**/*.png

      - name: Check for regressions
        run: ./gradlew :samples:tests:screenshot:analysis:failOnRegression -Pscreenshot.profile=ci
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "ci: update screenshot tests workflow for new pipeline"
```

# Screenshot Testing Rework — Design Spec

## Problem

The current screenshot testing system has several issues:

1. **Two parallel systems** (examples + samples) with duplicated infrastructure
2. **Tightly coupled** — test scenes, runner, comparison, and reporting are intertwined in single modules
3. **JVM crashes kill the entire suite** — a native crash (segfault in LWJGL/Vulkan) takes down the Gradle worker, failing all remaining tests
4. **Not portable** — adding a web backend or other platforms requires duplicating runner logic
5. **No structured pipeline** — test execution, comparison, and reporting happen in a single pass, making it hard to debug or re-run individual stages

## Solution

A multipass pipeline with four independent Gradle modules under `samples/tests/screenshot/`. The directory itself is no longer a module — it's a parent directory containing child modules.

## Module Structure

```
samples/tests/screenshot/              <- parent directory, NOT a module
├── references/
│   ├── ci/                            <- committed, Mesa/llvmpipe results
│   │   ├── opengl/<scene>_f<N>.png
│   │   └── vulkan/<scene>_f<N>.png
│   └── local/                         <- gitignored, real GPU results
│       ├── opengl/<scene>_f<N>.png
│       ├── vulkan/<scene>_f<N>.png
│       └── webgpu/<scene>_f<N>.png
├── scenes/                            <- module
├── runner/                            <- module
├── desktop/                           <- module
└── analysis/                          <- module
```

### Module: `scenes`

Scene definitions, manifest schema, and discovery. No platform dependencies.

**Package:** `dev.engine.tests.screenshot.scenes`

**Key classes:**

- `RenderTestScene` — interface that scene definitions implement
- `SceneConfig` — record: width, height, captureFrames, tolerance, requiresShaderCompiler, hints
- `Tolerance` — record: channelThreshold, percentThreshold (presets: exact, tight, loose, wide)
- `ComparisonTest` — record for A/B variant comparisons
- `SceneRegistry` — discovers all scenes via reflection on static final fields in `scenes.*` packages
- `Manifest` — data model + JSON serialization/deserialization for the manifest file

**Scene interface:**

```java
public interface RenderTestScene {
    default SceneConfig config() {
        return SceneConfig.defaults();  // 256x256, capture frame 3, loose tolerance
    }
    void setup(Engine engine);
    default Map<Integer, List<InputEvent>> inputScript() {
        return Map.of();
    }
}
```

**Scene categories** (under `scenes/` source, package `dev.engine.tests.screenshot.scenes.<category>`):

- `basic/` — depth test, primitives, two cubes unlit
- `materials/` — PBR, shader switching, roughness gradient, mixed materials, batching
- `renderstate/` — blend modes, cull modes, depth write, stencil masking, front face CW, per-entity states
- `textures/` — textured quads, material textures, texture switching, samplers (nearest/linear, repeat/clamp), 3D textures, array textures
- `input/` — key press color change (multi-frame)
- `ui/` — debug UI window

**Dependencies:** `graphics-common` (for Engine, entities, materials — no backend imports)

### Module: `runner`

Abstract test orchestrator. Handles process spawning, manifest updates, timeout/crash handling. No backend or platform dependencies.

**Package:** `dev.engine.tests.screenshot.runner`

**Key classes:**

- `AbstractTestRunner` — iterates scenes from manifest, calls `buildProcess()` per scene+backend, captures results
- `RunnerConfig` — record: timeout (default 30s), viewport override, profile name
- `SceneResult` — sealed interface with variants: Success, Exception, Crash, Timeout

**Abstract contract:**

```java
public abstract class AbstractTestRunner {
    abstract List<String> backends();
    abstract ProcessBuilder buildProcess(
        String sceneClass, String sceneField, String backend, 
        Path outputDir, SceneConfig config
    );
    public void run(Path manifestPath, RunnerConfig config) { ... }
}
```

The runner reads the manifest, iterates all scenes x backends, spawns a child JVM per combination via `ProcessBuilder`, and updates the manifest after each scene completes. A crash or timeout in one scene has zero impact on subsequent scenes.

**Dependencies:** `scenes` (for manifest schema and scene metadata)

### Module: `desktop`

Desktop-specific runner implementation and child process entry point.

**Package:** `dev.engine.tests.screenshot.desktop`

**Key classes:**

- `DesktopRunner` — extends `AbstractTestRunner`, provides LWJGL classpath, JVM args (`--enable-native-access`), environment (jemalloc), and available backends (opengl, vulkan, webgpu)
- `DesktopRenderMain` — child process `main()` that receives scene class+field+backend+output path as command-line args, instantiates the scene, reads its `config()`, builds `EngineConfig` with the platform-specific backend, renders frames, saves screenshots, writes a result JSON, and exits

**Child process contract:**

The child process writes a result JSON before exiting:

```json
// Success:
{
  "status": "success",
  "screenshots": [
    { "frame": 3, "path": "build/screenshots/opengl/depth_test_cubes_f3.png" }
  ]
}

// Exception:
{
  "status": "exception",
  "message": "java.lang.IllegalStateException: Shader compilation failed",
  "stackTrace": "full.stack.trace.here..."
}
```

If the child crashes (segfault), no result JSON is written. The runner detects this via non-zero exit code + missing result file, captures stderr/stdout, and records a crash entry in the manifest.

**Dependencies:** `runner`, `scenes`, `platform-desktop`, LWJGL, all backend providers

### Module: `analysis`

Comparator and report builder. Pure Java, no native dependencies.

**Package:** `dev.engine.tests.screenshot.analysis`

**Key classes:**

- `Comparator` — loads PNGs from successful runs, compares against reference images (per profile) and cross-backend (full matrix of all backend pairs), updates manifest comparisons section
- `ReportBuilder` — reads final manifest, generates interactive HTML report with summary stats, per-scene cards, backend badges, diff percentages, error details, thumbnails, crash/exception info
- `ImageUtils` — PNG load/save, per-pixel channel diff, diff percentage calculation
- `RegressionChecker` — reads manifest, exits non-zero if any comparison has status "fail". Warns with actionable instructions if no local references exist.

**Dependencies:** `scenes` (for manifest schema only)

## Manifest Format

A single JSON file progressively enriched by each pipeline pass.

```json
{
  "branch": "main",
  "commit": "7a50976",
  "buildVersion": "0.1.0-SNAPSHOT",
  "timestamp": "2026-04-07T14:30:00Z",
  "javaVersion": "25",
  "os": "Linux 6.17.0",
  "profile": "ci",
  "viewport": { "width": 256, "height": 256 },

  "scenes": [
    {
      "name": "depth_test_cubes",
      "category": "basic",
      "className": "dev.engine.tests.screenshot.scenes.basic.BasicScenes",
      "fieldName": "DEPTH_TEST_CUBES",
      "captureFrames": [3],
      "tolerance": { "channelThreshold": 2, "percentThreshold": 1.0 }
    }
  ],

  "runs": [
    {
      "scene": "depth_test_cubes",
      "backend": "opengl",
      "status": "success",
      "durationMs": 1230,
      "screenshots": [
        { "frame": 3, "path": "build/screenshots/opengl/depth_test_cubes_f3.png" }
      ],
      "error": null
    },
    {
      "scene": "stencil_masking",
      "backend": "vulkan",
      "status": "crash",
      "durationMs": 4500,
      "screenshots": [],
      "error": {
        "type": "crash",
        "exitCode": 139,
        "message": "Process killed by signal 11 (SIGSEGV)",
        "stderr": "...last 50 lines...",
        "stdout": "...last 50 lines..."
      }
    },
    {
      "scene": "textured_quad",
      "backend": "opengl",
      "status": "exception",
      "durationMs": 800,
      "screenshots": [],
      "error": {
        "type": "exception",
        "exitCode": 1,
        "message": "java.lang.IllegalStateException: Shader compilation failed",
        "stderr": "...full stack trace...",
        "stdout": ""
      }
    },
    {
      "scene": "ui_window",
      "backend": "webgpu",
      "status": "timeout",
      "durationMs": 30000,
      "screenshots": [],
      "error": {
        "type": "timeout",
        "exitCode": -1,
        "message": "Process did not complete within 30000ms",
        "stderr": "...last output before kill...",
        "stdout": ""
      }
    }
  ],

  "comparisons": [
    {
      "scene": "depth_test_cubes",
      "frame": 3,
      "type": "reference",
      "backend": "opengl",
      "profile": "ci",
      "status": "pass",
      "diffPercent": 0.0,
      "tolerance": { "channelThreshold": 2, "percentThreshold": 1.0 },
      "reason": null
    },
    {
      "scene": "depth_test_cubes",
      "frame": 3,
      "type": "cross_backend",
      "backendA": "opengl",
      "backendB": "vulkan",
      "status": "fail",
      "diffPercent": 2.3,
      "tolerance": { "channelThreshold": 2, "percentThreshold": 1.0 },
      "reason": "Diff 2.3% exceeds threshold 1.0%"
    },
    {
      "scene": "stencil_masking",
      "frame": 3,
      "type": "reference",
      "backend": "vulkan",
      "status": "skipped",
      "reason": "Run failed: crash (exit code 139)"
    },
    {
      "scene": "some_new_scene",
      "frame": 3,
      "type": "reference",
      "backend": "opengl",
      "status": "no_reference",
      "reason": "No reference image for profile 'local'"
    }
  ]
}
```

## Pipeline Flow

```
Pass 1: COLLECT           Pass 2: RUN               Pass 3: COMPARE           Pass 4: REPORT
────────────────          ─────────────              ───────────────           ──────────────
SceneRegistry             AbstractTestRunner         Comparator                ReportBuilder
scans scene classes       reads manifest             reads manifest            reads manifest
      │                         │                          │                         │
      v                         v                          v                         v
manifest.json             For each scene+backend:    For each successful       HTML report with:
{                         spawn child JVM            run, per capture frame:   - summary stats
  scenes: [...],            │                        - load screenshot         - per-scene cards
  runs: [],                 ├─ success -> save PNGs  - load reference          - backend badges
  comparisons: []           ├─ exception -> capture  - compute diff            - diff percentages
}                           │   stacktrace           - record comparison       - error details
                            ├─ crash -> capture                                - thumbnails
                            │   exit code + stderr   Cross-backend:            - crash/exception
                            └─ timeout -> kill +     - full pair matrix          info
                                capture stderr         from successful runs
                                    │                        │                       │
                                    v                        v                       v
                            manifest.json            manifest.json             report.html
                            (runs populated)          (comparisons populated)
```

## Comparison Strategy

### Reference comparisons

Each successful run is compared against the reference image for the current profile (`ci` or `local`). Per capture frame, per backend.

Possible statuses:
- `pass` — diff within tolerance
- `fail` — diff exceeds tolerance, reason includes actual vs threshold percentages
- `no_reference` — no reference image exists for this profile
- `skipped` — run failed (crash/exception/timeout), reason includes run failure info

### Cross-backend comparisons

For each scene and capture frame, the comparator generates all unique pairs from backends that produced successful screenshots. For 3 backends: GL-VK, GL-WebGPU, VK-WebGPU. For 2: one pair. For 1: none.

Uses the scene's tolerance (or a cross-backend-specific tolerance if the scene provides one).

## Error Handling & Isolation

### Child process isolation

Each scene+backend combination runs in its own JVM process. The runner (orchestrator) never loads native libraries — it's a lightweight process manager.

| Failure mode | Detection | Manifest entry |
|---|---|---|
| Success | Exit 0, result JSON present | `status: "success"`, screenshots from result JSON |
| Exception | Exit non-zero, result JSON present | `status: "exception"`, message + stacktrace |
| Crash | Exit non-zero, no result JSON | `status: "crash"`, exit code + stderr/stdout |
| Timeout | Process exceeds deadline | Runner kills process, `status: "timeout"` + captured output |

The runner always continues to the next scene after any failure.

### No JUnit in the pipeline

The pipeline stages are standalone `main()` methods invoked via Gradle JavaExec tasks, not JUnit tests. This avoids Gradle's test executor getting confused by native crashes.

## Gradle Tasks

| Task | Module | Description |
|---|---|---|
| `collectScenes` | `scenes` | Runs SceneRegistry, writes initial manifest.json |
| `runDesktop` | `desktop` | Runs DesktopRunner, spawns child processes, populates runs |
| `compare` | `analysis` | Runs Comparator, populates comparisons |
| `report` | `analysis` | Runs ReportBuilder, generates report.html |
| `screenshotTest` | lifecycle | Chains: collectScenes -> runDesktop -> compare -> report |
| `saveReferences` | `desktop` | Renders all scenes, copies PNGs to references/<profile>/ |
| `failOnRegression` | `analysis` | Exits non-zero if any comparison status is "fail" |

**Task dependency chain:**

```
collectScenes -> runDesktop -> compare -> report -> failOnRegression
```

**Integration with root `test` task:**

The root project's `test` task depends on `screenshotTest` and `failOnRegression`, so `./gradlew test` runs the full screenshot pipeline and fails on regressions.

## Reference Image Profiles

Two profiles for reference images:

- **`ci`** — committed to git, generated with Mesa/llvmpipe software rendering. Used on CI.
- **`local`** — gitignored, generated with real GPU hardware. Used for local development.

Selected via `-Pscreenshot.profile=ci|local` (defaults to `local`).

### Missing local references

When `failOnRegression` detects no local references exist, it warns with actionable instructions:

```
WARNING: No local reference images found for profile 'local'.
Screenshot tests ran but could not verify against references.

To generate local references, run:
  git stash
  git checkout main
  ./gradlew saveReferences -Pscreenshot.profile=local
  git checkout -
  git stash pop

This captures baseline screenshots from main so your changes
can be compared against them.
```

This exits with code 0 (not a failure). Only actual `"fail"` comparison statuses cause a non-zero exit.

## CI Integration

GitHub Actions workflow (`.github/workflows/screenshot-tests.yml`):

```yaml
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
      run: xvfb-run ./gradlew screenshotTest -Pscreenshot.profile=ci
      continue-on-error: true

    - name: Upload report
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: screenshot-report
        path: samples/tests/screenshot/build/screenshots/

    - name: Check for regressions
      run: ./gradlew failOnRegression -Pscreenshot.profile=ci
```

With branch protection rules requiring this job to pass, PRs with visual regressions cannot be merged.

## Migration Plan

1. Create the four new modules under `samples/tests/screenshot/`
2. Move existing sample scenes into the `scenes` module (already organized by category)
3. Migrate unique scenes from `examples/` that don't exist in samples:
   - `RENDER_TO_TEXTURE`, `FORCED_WIREFRAME`, `TEXTURE_3D_CREATE`, `TEXTURE_ARRAY_CREATE`, `STENCIL_MASKING`, `DEPTH_FUNC_GREATER`
4. Build the runner, desktop, and analysis modules
5. Remove old test infrastructure from both `examples/` and the current `samples/tests/screenshot/` module
6. Move CI reference images to `references/ci/`
7. Update `.gitignore` for `references/local/`
8. Update GitHub Actions workflow
9. Update root `test` task dependency

## Output Directory

All build artifacts:

```
samples/tests/screenshot/build/screenshots/
├── manifest.json
├── report.html
├── opengl/
│   ├── depth_test_cubes_f3.png
│   └── ...
├── vulkan/
│   └── ...
└── webgpu/
    └── ...
```

# Screenshot Test Architecture

## Module Structure

The screenshot tests live under `samples/tests/screenshot/` as three Gradle submodules:

```
samples/tests/screenshot/
├── scenes/    — Shared scene definitions, discovery, tolerances
├── desktop/   — Desktop runner (OpenGL, Vulkan, WebGPU-native via GLFW)
└── web/       — Browser runner (WebGPU via headless Chrome + CDP)
```

### `scenes` module
Contains `RenderTestScene` interface, `Tolerance`, `ComparisonTest`, `SceneDiscovery`,
and all scene classes organized by category (basic, materials, renderstate, textures, input, ui).

Scene fields must be `public static final` so the web module can reference them directly
(TeaVM cannot do classpath scanning).

### `desktop` module
JUnit 5 dynamic tests via `@TestFactory`. Creates real Engine instances with GLFW windows
and native GPU backends. Uses `RenderDevice.readFramebuffer()` for pixel capture.

Run: `./gradlew :samples:tests:screenshot:desktop:test`

### `web` module
Two source sets:
- **main** — TeaVM-compiled `WebTestApp` that renders scenes in the browser
- **test** — JUnit runner with `ChromeDevTools` (CDP client) that orchestrates capture

Run: `./gradlew :samples:tests:screenshot:web:test`

## Web Test Architecture

### Zero External Dependencies
The browser automation uses Chrome DevTools Protocol (CDP) over JDK's built-in
`java.net.http.WebSocket`. No Playwright, Selenium, or Node.js required.

### Communication Protocol
1. `WebTestApp` initializes WebGPU and sets `window._testStatus = "ready"`
2. Test runner sends `window._startRendering = true` when ready to capture
3. App renders each scene sequentially, yielding to browser event loop between frames
4. After the capture frame, sets `window._captureReady = sceneName`
5. CDP client polls for readiness, then captures canvas pixels via 2D context copy
6. Sets `window._captureAck = true` so the app proceeds to the next scene
7. If a scene crashes, app signals `window._captureReady = "ERROR:sceneName"`
8. `window._testsDone = true` signals completion

### Canvas Pixel Readback
Browser WebGPU doesn't support synchronous buffer mapping. Instead, pixels are
captured by drawing the WebGPU canvas onto a 2D OffscreenCanvas and reading
`getImageData()` — all done via CDP `Runtime.evaluate`.

### TeaVM Compatibility
Several engine classes required changes for TeaVM compatibility:
- `GpuResourceManager`: replaced `ConcurrentLinkedQueue` with synchronized `ArrayDeque`
- `ModuleManager`: replaced `CountDownLatch` with synchronized counter
- `Engine`: debug UI initialization gracefully disabled when resources unavailable
- `StructLayout`: generated `_NativeStruct` classes must be explicitly loaded via `init()`
- `WebTestApp` uses `TestWebPlatform` instead of `platforms:web`'s `WebPlatform` to avoid
  circular Gradle dependency (both modules use the TeaVM plugin)

### Chrome Requirements
- Chrome 113+ with `--enable-unsafe-webgpu` and `--enable-features=Vulkan`
- Set `CHROME_BIN` environment variable to override the binary path
- GitHub Actions: use `browser-actions/setup-chrome@v1`

## Running

```bash
# Build the TeaVM web app
./gradlew :samples:tests:screenshot:web:assembleWebTest

# Run screenshot tests (requires Chrome)
./gradlew :samples:tests:screenshot:web:test

# Generate/update reference screenshots
./gradlew :samples:tests:screenshot:web:saveReferences

# Desktop tests
./gradlew :samples:tests:screenshot:desktop:test
./gradlew :samples:tests:screenshot:desktop:saveReferences
```

## Adding New Scenes

1. Add a `public static final RenderTestScene` field to a class in `scenes/src/main/java/.../scenes/`
2. Desktop tests discover it automatically via `SceneDiscovery` (reflection-based)
3. **Also register it in `web/src/main/java/.../web/WebSceneRegistry.java`** (explicit, for TeaVM)
4. Generate reference images:
   - Desktop: `./gradlew :samples:tests:screenshot:desktop:saveReferences`
   - Web: `./gradlew :samples:tests:screenshot:web:saveReferences`

## CI

GitHub Actions runs web screenshot tests on every push and PR via
`.github/workflows/screenshot-tests.yml`. Tests gracefully skip if WebGPU is not
available in headless Chrome on the CI runner. Screenshots and test results are
uploaded as artifacts.

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
1. `WebTestApp` renders each scene sequentially in the browser
2. After the capture frame, sets `window._captureReady = sceneName`
3. CDP client polls for readiness, then captures canvas pixels via 2D context copy
4. Sets `window._captureAck = true` so the app proceeds to the next scene
5. `window._testsDone = true` signals completion

### Canvas Pixel Readback
Browser WebGPU doesn't support synchronous buffer mapping. Instead, pixels are
captured by drawing the WebGPU canvas onto a 2D OffscreenCanvas and reading
`getImageData()` — all done via CDP `Runtime.evaluate`.

### Chrome Requirements
- Chrome 113+ with `--enable-unsafe-webgpu` and `--enable-features=Vulkan`
- Set `CHROME_BIN` environment variable to override the binary path
- GitHub Actions: use `browser-actions/setup-chrome@v1`

## Adding New Scenes

1. Add a `public static final RenderTestScene` field to a class in `scenes/src/main/java/.../scenes/`
2. Desktop tests discover it automatically via `SceneDiscovery` (reflection-based)
3. **Also register it in `web/src/main/java/.../web/WebSceneRegistry.java`** (explicit, for TeaVM)
4. Generate reference images: `./gradlew :samples:tests:screenshot:desktop:saveReferences`

## Cross-Backend Comparison

The desktop module compares GL vs VK vs WebGPU-native with tight tolerances.
The web module captures WebGPU-browser screenshots separately. Future work:
cross-compare `webgpu-native` vs `webgpu-browser` with wider tolerance to catch
browser-specific WebGPU implementation differences.

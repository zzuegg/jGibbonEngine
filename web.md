# Web Platform - TeaVM Compilation Progress

## Status: Render loop runs, no errors — rendering output pending verification

The engine now compiles to JavaScript via TeaVM 0.13.1. The web build produces
output at `platforms/web/build/web/` and can be served via any HTTP server.

## Changes Made

### TeaVM Classlib Shims (`providers/windowing/web/teavm-windowing/`)

Three shims added following TeaVM's `T`-prefixed naming convention in
`org.teavm.classlib.*`:

| Shim | JDK Class | Behavior |
|------|-----------|----------|
| `TCleaner` | `java.lang.ref.Cleaner` | Manual `clean()` only, no GC auto-trigger |
| `TConcurrentLinkedQueue` | `java.util.concurrent.ConcurrentLinkedQueue` | Delegates to `ArrayDeque` (single-threaded) |
| `TCountDownLatch` | `java.util.concurrent.CountDownLatch` | Simple counter, `await()` is no-op |

Added `teavm-classlib:0.13.1` as `compileOnly` dependency so shims can extend
TeaVM base classes like `TAbstractQueue`.

### Core Fixes

- **`ResourceStats.java`** — Cast `ConcurrentHashMap` to `Map` before calling
  `keySet()` to avoid TeaVM's missing `KeySetView` return type.
- **`StructLayout.java`** — Broadened exception catch for `ReflectiveLayoutBuilder`
  fallback (TeaVM has no `MethodHandles`, so it throws instead of
  `ClassNotFoundException`).

### NativeStruct Initialization (`WebMain.java`)

TeaVM's dead code elimination removes classes only reachable via `Class.forName()`
with dynamic strings. Added explicit `init()` calls for all `_NativeStruct` classes:

```java
CameraParams_NativeStruct.init();
EngineParams_NativeStruct.init();
ObjectParams_NativeStruct.init();
```

Any new `@NativeStruct` records must be added here for the web platform.

## Bugs Fixed

### 1. NullPointerException on `debugUi().input()` (BaseApplication.java)

`Engine.debugUi()` returns `null` when `debugOverlay=false`. The call
`NkInputBridge.feedEvents(engine.debugUi().input(), inputEvents)` at
BaseApplication:134 caused an NPE on the first frame, which was caught by the
try/finally and silently shut down the engine. Fixed with a null check.

### 2. `requestAnimationFrame` never fires on hidden tabs (CanvasWindowToolkit.java)

`CanvasWindowToolkit.pollEvents()` used `requestAnimationFrame` exclusively.
Browsers do not fire RAF callbacks for hidden/background tabs, so the TeaVM
async continuation never resumed and the render loop froze permanently.

Fixed by falling back to `setTimeout(callback, 16)` when `document.hidden` is
true. This also means the engine survives tab switches (though at reduced FPS
due to browser throttling of background timers to ~1 Hz).

### 3. Debug UI not web-compatible (multiple files)

Several changes were needed to make the debug overlay work on web:

- **`DebugUiOverlay.java`** — Used `ByteBuffer.allocateDirect()` for font atlas
  upload; replaced with `ByteBuffer.wrap()`. Used `getResourceAsStream()` which
  doesn't work in TeaVM; switched to `ShaderManager.loadResource()` which tries
  the asset system first.
- **`ShaderManager.java`** — Added public `loadResource()` method (asset system
  first, classpath fallback) for use by DebugUiOverlay.
- **`WgpuRenderDevice.java`** — `writeBuffer()` and `uploadTexture()` used
  `ByteBuffer.allocateDirect()` which TeaVM can't handle; switched to heap
  buffers since both desktop (FFM `MemorySegment.ofBuffer`) and web bindings
  handle them correctly.
- **`TeaVmWgpuBindings.java`** — `queueWriteBuffer()` interpreted all data as
  floats; fixed to use raw `byte[]` + `Uint8Array` for correct mixed-type data.
  Implemented `queueWriteTexture()` (was `throw unsupported`).
- **Binding fallback** — Slang WASM compiler doesn't produce reflection data
  for standalone `cbuffer`/`Sampler2D` bindings. DebugUiOverlay now falls back
  to default indices (0) when reflection returns -1.

## Known Issues / Next Steps

- **Rendering verification**: The render loop runs error-free with debug overlay
  enabled. Visual output needs verification with a visible tab.
- **WebGPU init slow on hidden tabs**: `navigator.gpu.requestAdapter()` and
  `requestDevice()` can take 30+ seconds on hidden tabs due to Chrome's GPU
  throttling. No workaround — users should keep the tab visible during load.
- **Cleaner semantics**: GPU resources won't be automatically cleaned up by GC
  in the browser. Explicit `close()` / `destroy()` calls are required.
- **SlangParamsBlock reflection**: Uses `Method.invoke()` which has limited
  support in TeaVM. May need shims or explicit wiring.
- **New @NativeStruct records**: Must be manually registered in `WebMain.main()`
  via `init()` calls until a better discovery mechanism is added.

## Building & Running

```bash
./gradlew :platforms:web:assembleWeb
cd platforms/web/build/web
python3 -m http.server 8080
# Open http://localhost:8080 in Chrome/Edge (WebGPU required)
```

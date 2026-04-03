# TeaVM Integration Notes

## Gradle Plugin Compatibility

- TeaVM 0.11.0 is **not compatible** with Gradle 9.x — it calls the removed
  `LenientConfiguration.getFiles()` API.
- TeaVM **0.13.1** works with Gradle 9.4.1 (deprecation warnings present but
  functional).

## Build Pipeline

The TeaVM Gradle plugin (`org.teavm`) is applied only to the `web/` module.
Provider modules (`providers/teavm-webgpu`, `providers/teavm-windowing`) use
plain `java-library` and declare TeaVM JSO dependencies as `compileOnly` so
they compile with standard javac but are processed by TeaVM when pulled into
the `web` module.

```bash
./gradlew :web:generateJavaScript
# Output: web/build/generated/js/teavm/js/web.js
```

## Output File Naming

TeaVM 0.13.1 names the output JS file after the Gradle module name (e.g.,
`web.js` for the `:web` module), **not** `classes.js` as older versions did.
The `index.html` must reference the correct filename.

## TeaVM Limitations

Code compiled by TeaVM cannot use:

- **FFM** (Foreign Function & Memory API) — no `MemorySegment`, `Linker`, etc.
- **`java.lang.ref.Cleaner`** — no destructor callbacks
- **Complex reflection** — `Class.forName`, `Method.invoke` etc. are limited
- **Most `java.nio`** — limited `ByteBuffer` support
- **Threads** — `Thread.start()` is not supported; browser is single-threaded
- **`System.loadLibrary`** — no JNI/native libraries

Provider modules for TeaVM must avoid these APIs. The `WgpuBindings` interface
is TeaVM-safe because it uses only primitive types, `String`, `ByteBuffer`,
arrays, and records.

## Async WebGPU Calls

Browser WebGPU's `requestAdapter()` and `requestDevice()` return Promises.
TeaVM handles this via the `@Async` / `AsyncCallback` pattern:

```java
@Async
private static native long requestAdapterAsync();

private static void requestAdapterAsync(AsyncCallback<Long> callback) {
    requestAdapterJS(callback);
}

@JSBody(params = {"callback"}, script = """
    navigator.gpu.requestAdapter().then(function(adapter) {
        // store adapter, call back
        callback(complete(1));
    });
""")
private static native void requestAdapterJS(AsyncCallback<Long> callback);
```

This converts Promise-based async into synchronous-looking Java code that
TeaVM compiles to continuation-passing style JS.

## Handle System for Browser WebGPU

Browser WebGPU objects are JavaScript objects, not native pointers. The
`WgpuBindings` interface uses `long` handles. The bridge uses a JavaScript-side
registry (`window._wgpu`) mapping integer IDs to JS objects:

```javascript
window._wgpu = {};
window._wgpuNextId = 1;
// Register: id = window._wgpuNextId++; window._wgpu[id] = obj;
// Lookup:   obj = window._wgpu[id];
// Release:  delete window._wgpu[id];
```

TeaVM `@JSBody` methods return `int` (not `long`) for JS numbers. The Java
binding layer casts `int <-> long` at the interface boundary.

## Canvas Surface vs Desktop Surface

Browser WebGPU does **not** use `wgpuInstanceCreateSurface`. Instead:

```javascript
var ctx = canvas.getContext('webgpu');
ctx.configure({ device: device, format: navigator.gpu.getPreferredCanvasFormat() });
// Per frame:
var textureView = ctx.getCurrentTexture().createView();
// No explicit present — browser handles it via requestAnimationFrame
```

The `TeaVmWgpuBindings` class provides extra static methods not in the
`WgpuBindings` interface for canvas surface management:
- `configureCanvasContext(canvasId, deviceId)` — sets up the WebGPU context
- `getCurrentTextureView(contextId)` — gets the per-frame render target
- `getPreferredCanvasFormat()` — returns the browser's preferred format string

## @JSBody String Enum Conversion

Browser WebGPU uses string enums (`"triangle-list"`, `"bgra8unorm"`) while
the engine uses integer constants. Each `@JSBody` call converts via helper
methods like `wgpuTextureFormatString(int)` on the Java side before passing
to JavaScript.

## Serving for Testing

WebGPU requires a secure context (HTTPS or localhost). To test locally:

```bash
./gradlew :web:generateJavaScript
# Serve the output directory
cd web/build/generated/js/teavm/js
python3 -m http.server 8080
# Open http://localhost:8080 in Chrome 113+ or Edge 113+
```

## Shaders in Browser

The Slang compiler is not available in the browser. For the web target,
shaders must be pre-compiled to WGSL or embedded as WGSL string literals
in the Java source. The `WebMain` class uses hard-coded WGSL for the
initial triangle demo.

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

## SLF4J Shim

SLF4J's `LoggerFactory.getLogger()` cannot run under TeaVM because it uses:
- `SecurityManager.getClassContext()` for caller detection
- `LinkedBlockingQueue` in `SubstituteLoggerFactory`
- `ClassLoader.getResources()` / `ServiceLoader` for provider discovery

**Solution:** The `web/` module excludes `slf4j-api` from its transitive
dependencies and provides its own minimal `org.slf4j.*` classes in
`web/src/main/java/org/slf4j/`. This works because the project does not use
JPMS (no `module-info.java`), so there is no split-package conflict.

The shim classes:
- `LoggerFactory` — returns `ConsoleLogger` instances from a `HashMap` cache
- `ConsoleLogger` — formats `{}` placeholders and writes to `System.out` / `System.err`
  (which TeaVM maps to `console.log` / `console.error`)
- `Logger`, `ILoggerFactory`, `Marker` — interfaces matching the SLF4J API surface
- `Level`, `LoggingEventBuilder` — minimal types for the fluent API default methods
- `NOPLoggingEventBuilder` — no-op for the `atInfo()` / `atDebug()` etc. fluent API

Other modules continue to compile and run against the real `slf4j-api` jar.
The exclusion only affects the `web/` module's classpath.

## TeaVM Limitations

Code compiled by TeaVM cannot use:

- **FFM** (Foreign Function & Memory API) — no `MemorySegment`, `Linker`, etc.
- **Complex reflection** — `Class.forName`, `Method.invoke` etc. are limited
- **Most `java.nio`** — limited `ByteBuffer` support
- **Threads** — `Thread.start()` is not supported; browser is single-threaded
- **`System.loadLibrary`** — no JNI/native libraries
- **`SecurityManager`** — removed from recent JDKs, not in TeaVM classlib
- **`ServiceLoader`** — relies on `ClassLoader.getResources()` which is not available
- **`LinkedBlockingQueue`** — not implemented in TeaVM's `java.util.concurrent`

Provider modules for TeaVM must avoid these APIs. The `WgpuBindings` interface
is TeaVM-safe because it uses only primitive types, `String`, `ByteBuffer`,
arrays, and records.

## JDK Classlib Shims

TeaVM's classlib is missing several JDK classes used by the engine's core
modules. Shims are provided in `providers/windowing/web/teavm-windowing/`
following TeaVM's `T`-prefixed naming convention
(`org.teavm.classlib.java.lang.ref.TCleaner` → maps to `java.lang.ref.Cleaner`).

**Provided shims:**

| JDK Class | Shim | Behavior |
|-----------|------|----------|
| `java.lang.ref.Cleaner` | `TCleaner` | Manual `clean()` only — no GC-based auto-trigger in the browser |
| `java.lang.ref.Cleaner.Cleanable` | `TCleaner.Cleanable` | Runs cleanup action once on `clean()`, then disarms |
| `java.util.concurrent.ConcurrentLinkedQueue` | `TConcurrentLinkedQueue` | Delegates to `ArrayDeque` (single-threaded browser) |
| `java.util.concurrent.CountDownLatch` | `TCountDownLatch` | Simple counter; `await()` is a no-op (executor runs inline) |

**Adding new shims:** Create `T`-prefixed classes in
`org.teavm.classlib.<java.package>` within the `teavm-windowing` module.
Inner classes keep their original name (only the outer class gets the `T`
prefix). The `teavm-classlib` dependency is `compileOnly` so shims can extend
TeaVM base classes like `TAbstractQueue`.

**ConcurrentHashMap.keySet() quirk:** TeaVM's `TConcurrentHashMap.keySet()`
returns `TSet`, not `ConcurrentHashMap.KeySetView`. Code calling `keySet()`
on a `ConcurrentHashMap` must cast to `Map` first to avoid the JDK-specific
return type: `((Map<K,V>) map).keySet()`.

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

The Slang WASM compiler **is** available in the browser (see `docs/slang.md`
for setup). `TeaVmSlangCompiler` wraps the Slang WASM module to compile
`.slang` source to WGSL at runtime.

### Self-Contained Shaders

The WASM compiler cannot resolve `import` statements. Shaders compiled in
the browser must inline all dependencies. Use `ParameterBlock<T>` for
uniform blocks (maps to `@group(N) @binding(0)` in WGSL) rather than
generic interfaces (`ICameraParams`, etc.) which require desktop-only
Slang features.

### WebRenderer Pipeline

`WebRenderer` replaces the hardcoded triangle with the full engine pipeline:
- Uses engine math types (Mat4, Vec3, Transform) for camera and scene setup
- Uses `MaterialData` for material properties (COLOR for unlit)
- Compiles the unlit shader via `TeaVmSlangCompiler` at runtime
- Falls back to hand-written WGSL if Slang WASM is not loaded
- Manages WebGPU resources (buffers, bind groups, depth texture) directly
  through `WgpuBindings`, bypassing the desktop `Renderer`/`ShaderManager`
  which depend on FFM and reflection

### Matrix Upload Convention (WGSL)

WGSL `mat4x4f` uses column-major storage. The engine's `Mat4` stores data
in row-major order (`m00, m01, m02, m03` is row 0). When uploading to
WebGPU uniform buffers, matrices must be transposed to column-major:

```java
// Column-major upload (matches Mat4.writeGpu() on desktop)
float[] data = {
    m.m00(), m.m10(), m.m20(), m.m30(), // column 0
    m.m01(), m.m11(), m.m21(), m.m31(), // column 1
    ...
};
```

In hand-written WGSL, use `vec4f(pos, 1.0) * mvp` (row-vector convention)
to match the Slang-generated multiplication order. This works because
multiplying a row vector by the transposed matrix produces the same result
as the standard `M * v` with the original matrix.

### Bind Group Layout

The unlit shader uses 3 bind groups:
- Group 0, binding 0: Camera UBO (mat4x4f viewProjection, 64 bytes)
- Group 1, binding 0: Object UBO (mat4x4f world, 64 bytes)
- Group 2, binding 0: Material UBO (vec3f color, padded to 16 bytes)

Each group has its own `BindGroupLayout`. Bind groups for object and
material are recreated per draw call (WebGPU bind groups are cheap to
create and cannot be mutated).

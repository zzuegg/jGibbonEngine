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

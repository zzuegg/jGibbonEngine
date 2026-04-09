# GraalWasm Platform Integration

## Architecture

The GraalWasm platform provides a complete WebGPU backend running on the JVM
via GraalJS + GraalWasm. It mirrors the TeaVM web platform structurally but
uses GraalVM polyglot interop instead of TeaVM JSO.

### Modules

| Module | Purpose |
|--------|---------|
| `providers:slang-wasm` | Shared `SlangWasmBridge` interface + `SlangWasmCompiler` |
| `providers:graal-slang-wasm` | Slang WASM compiler via GraalJS + GraalWasm |
| `providers:graal-webgpu` | `WgpuBindings` impl calling browser WebGPU via GraalJS |
| `providers:graal-windowing` | Canvas `WindowToolkit` via GraalJS DOM calls |
| `platforms:graalwasm` | Platform assembly wiring everything together |

### Shared GraalJS Context

All providers share a single GraalJS `Context` — same as how TeaVM providers
share the browser's global JS scope implicitly. The `GraalWasmPlatform` creates
and owns this context, passing it to all providers.

### Usage

```java
var platform = GraalWasmPlatform.builder()
    .slangWasmDir(Path.of("tools/slang-wasm"))
    .assetBaseUrl("assets/")
    .build();

int deviceId = platform.initWebGpu();

var config = EngineConfig.builder()
    .window(WindowDescriptor.builder("Engine - GraalWasm").size(1280, 720).build())
    .platform(platform)
    .graphics(platform.graphicsConfig())
    .build();

new MyApp().launch(config);
```

## Slang WASM Compiler

The same `slang-wasm.wasm` Emscripten binary used by TeaVM runs on the JVM
via GraalJS + GraalWasm:

1. Java reads `slang-wasm.wasm` bytes and passes as `wasmBinary` to Emscripten init
2. The `.mjs` glue is loaded via ES module `import()`
3. GraalWasm executes the WASM binary natively on the JVM
4. The embind API is called through `Value` interop

## WebGPU Bindings

The WebGPU API is a browser JavaScript API (`navigator.gpu`), not a WASM export.
There is no `.wasm` file for WebGPU — it lives in the browser's JS environment.
Therefore, the GraalJS bridge through JS is necessary for WebGPU calls.

The JS bridge is thin — just a handle registry + direct WebGPU API calls, bundled
as a single JS object for efficient polyglot method dispatch.

## Key Gotchas

- **WebGPU requires browser context**: `navigator.gpu` must exist. This works when
  GraalJS runs in a browser-like environment or with a WebGPU polyfill.

- **Thread safety**: GraalVM polyglot `Context` is single-threaded. The entire
  render loop runs on one thread — same as in a browser.

- **ES module for async**: Top-level `await` (used for `requestAnimationFrame`,
  `fetch`, WebGPU init) requires source evaluated with MIME type
  `application/javascript+module`.

- **Java byte[] → JS**: Java bytes are signed. When passing to `Uint8Array`,
  the JS bridge masks with `& 0xFF`.

- **Emscripten environment**: The `.mjs` file checks for `process` (Node) and
  `window` (browser). Pass `wasmBinary` to avoid file-loading code paths.

## Sharing with TeaVM

The `SlangWasmBridge` interface can be implemented by TeaVM to share the
`compileWithTypeMap` source-specialization logic:

```java
public class TeaVmSlangBridge implements SlangWasmBridge {
    public boolean isAvailable() { return TeaVmSlangCompiler.isAvailable(); }
    public String[] compile(String s, String v, String f) { return TeaVmSlangCompiler.compile(s, v, f); }
    public String compileCompute(String s, String e) { return TeaVmSlangCompiler.compileCompute(s, e); }
}

// Replace TeaVmShaderCompiler with:
var compiler = new SlangWasmCompiler(new TeaVmSlangBridge());
```

# GraalWasm Slang Compiler Integration

## Architecture

The Slang WASM compiler integration is split into two modules:

- **`providers:slang-wasm`** — Shared `SlangWasmBridge` interface + `SlangWasmCompiler`
- **`providers:graal-slang-wasm`** — GraalVM polyglot bridge implementation

### How it works

The same `slang-wasm.wasm` Emscripten binary used by the TeaVM web platform runs
on the JVM via GraalJS + GraalWasm. The flow:

1. Java reads `slang-wasm.wasm` bytes and passes them to the GraalJS context
2. The bytes are converted to a JS `ArrayBuffer` and passed as `wasmBinary` to
   the Emscripten module init (avoids needing browser `fetch` or Node `fs`)
3. The Emscripten `.mjs` glue is loaded via ES module `import()`
4. GraalWasm executes the WASM binary natively on the JVM
5. The embind API (createGlobalSession, loadModuleFromSource, etc.) is called
   through GraalVM's polyglot `Value` interop

### Key gotchas

- **Emscripten environment detection**: The `.mjs` file checks for `process` (Node)
  and `window` (browser). GraalJS is neither — the Emscripten module may need the
  `wasmBinary` option to avoid file-loading code paths that assume Node or browser APIs.

- **Thread safety**: The GraalVM polyglot `Context` is single-threaded. All
  compilation calls are `synchronized`. Don't share a bridge across threads without
  external synchronization.

- **ES module loading**: The init script must be evaluated with MIME type
  `application/javascript+module` to support top-level `await`. The
  `js.esm-eval-returns-exports` option must be enabled.

- **Java byte[] → ArrayBuffer**: Java byte values are signed (-128..127). When
  copying to `Uint8Array`, mask with `& 0xFF` to get unsigned values.

### Sharing with TeaVM

The `SlangWasmBridge` interface can also be implemented by TeaVM:

```java
// TeaVM bridge (delegates to existing @JSBody calls)
public class TeaVmSlangBridge implements SlangWasmBridge {
    public boolean isAvailable() { return TeaVmSlangCompiler.isAvailable(); }
    public String[] compile(String s, String v, String f) { return TeaVmSlangCompiler.compile(s, v, f); }
    public String compileCompute(String s, String e) { return TeaVmSlangCompiler.compileCompute(s, e); }
}

// Then replace TeaVmShaderCompiler with:
var compiler = new SlangWasmCompiler(new TeaVmSlangBridge());
```

This shares the `compileWithTypeMap` source-specialization logic across both platforms.

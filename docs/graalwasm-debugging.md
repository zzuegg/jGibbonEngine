# GraalVM Web Image (GraalWasm) Debugging Notes

## Critical: Java `String` vs `JSString` in `@JS` methods

In GraalVM Web Image (`native-image --tool:svm-wasm`), Java `String` parameters
passed to `@JS` native methods become **opaque WASM GC proxy objects** in JavaScript
context. They are NOT automatically coerced to JavaScript strings.

### Symptoms
- `typeof javaString` returns `"function"` in JavaScript
- `'' + javaString` produces `"[Java Proxy: java.lang.String]"` (NOT the string content)
- String-dependent APIs (XHR, Slang WASM, etc.) silently fail or produce garbage

### Fix
Always convert `String` to `JSString.of(...)` before passing to `@JS` methods:

```java
// WRONG - Java String becomes WASM GC proxy in JS
@JS(args = "url", value = "xhr.open('GET', url, false);")
private static native JSString fetchSync(String url);

// Call site:
fetchSync(myUrl); // url is a WASM GC proxy, not a JS string

// CORRECT - JSString is a proper JS string
@JS(args = "url", value = "xhr.open('GET', url, false);")
private static native JSString fetchSync(JSString url);

// Call site:
fetchSync(JSString.of(myUrl)); // url is now a proper JS string
```

This applies to ALL `@JS` method parameters: `String`, `int`, `boolean`, `double`.
Use `JSString.of()`, `JSNumber.of()`, `JSBoolean.of()` respectively.

The WebGPU bindings (`GraalWgpuBindings`) already follow this pattern correctly
by serializing everything to JSON strings via `JSString.of()`.

## Slang WASM API

The Slang WASM module (Emscripten/embind) API differs from what might be expected:

| Incorrect (used initially) | Correct (matches TeaVM) |
|---|---|
| `globalSession.getCompileTargetCount()` | `slang.getCompileTargets()` (module-level) |
| `globalSession.getCompileTargetInfo(i)` | `targets[i].name / targets[i].value` |
| `globalSession.createSession(index)` | `globalSession.createSession(targetValue)` |
| `session.loadModuleFromSource(src)` | `session.loadModuleFromSource(src, name, path)` |
| Separate composites per entry point | One composite with all entry points |

Always refer to `TeaVmSlangCompiler.java` as the reference implementation.

## `requestAnimationFrame` in headless/background tabs

`requestAnimationFrame` does NOT fire in:
- Hidden/background tabs
- Headless Chrome (without `--headless=new` + proper flags)
- Tabs in non-focused tab groups

For test harnesses that drive frames synchronously, use `setTimeout(cb, 0)` instead.

## Silent error swallowing

The engine's `ShaderManager.resolveForEntity()` catches all exceptions and logs
them via SLF4J. In GraalWasm, there is no SLF4J provider, so the NOP logger
silently discards ALL error messages. This makes shader compilation failures
completely invisible.

If rendering produces no draw calls but no errors, check:
1. Are `createShaderModule` calls appearing in the bridge command log?
2. Is the Slang compilation `@JS` method being called at all?
3. Are Java `String` parameters being properly converted to `JSString`?

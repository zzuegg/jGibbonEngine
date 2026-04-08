/**
 * GraalVM polyglot implementation of {@link dev.engine.providers.slang.wasm.SlangWasmBridge}.
 *
 * <p>Loads the Slang WASM compiler ({@code slang-wasm.wasm}) via GraalJS + GraalWasm
 * and calls the Emscripten embind API through GraalVM's polyglot interop.
 * The WASM execution runs natively in GraalWasm on the JVM — no browser required.
 *
 * <p>Requires GraalVM polyglot SDK + JS and WASM community languages on the classpath.
 */
package dev.engine.providers.slang.graalwasm;

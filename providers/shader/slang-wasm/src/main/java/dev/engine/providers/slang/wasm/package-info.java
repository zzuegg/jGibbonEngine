/**
 * Shared WASM-based Slang shader compiler.
 *
 * <p>Provides a {@link dev.engine.providers.slang.wasm.SlangWasmBridge} interface
 * that abstracts how the slang-wasm.wasm module is called, and a shared
 * {@link dev.engine.providers.slang.wasm.SlangWasmCompiler} that implements
 * {@link dev.engine.graphics.shader.ShaderCompiler} on top of it.
 *
 * <p>Bridge implementations:
 * <ul>
 *   <li>GraalVM polyglot — {@code providers:graal-slang-wasm}</li>
 *   <li>TeaVM JSO — {@code providers:teavm-webgpu} (can be refactored to use this bridge)</li>
 * </ul>
 */
package dev.engine.providers.slang.wasm;

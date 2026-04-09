/**
 * GraalWasm web platform — runs the engine on the JVM with GraalJS + GraalWasm
 * providing browser API access (WebGPU, DOM, canvas).
 *
 * <p>All GraalWasm providers share a single GraalJS {@link org.graalvm.polyglot.Context}
 * since they operate in the same JavaScript environment. The platform creates and
 * owns this context.
 *
 * <p>Mirrors the TeaVM web platform structurally:
 * <ul>
 *   <li>WebGPU via GraalJS polyglot instead of TeaVM JSO</li>
 *   <li>Canvas windowing via GraalJS instead of TeaVM DOM bindings</li>
 *   <li>Slang WASM shader compilation via GraalWasm instead of browser WASM</li>
 * </ul>
 */
package dev.engine.platform.graalwasm;

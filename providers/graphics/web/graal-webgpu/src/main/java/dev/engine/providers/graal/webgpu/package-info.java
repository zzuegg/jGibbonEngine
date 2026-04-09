/**
 * GraalJS-based WebGPU bindings.
 *
 * <p>Implements {@link dev.engine.graphics.webgpu.WgpuBindings} by calling the
 * browser WebGPU API through GraalVM's polyglot {@link org.graalvm.polyglot.Value}
 * interop. Mirrors the TeaVM {@code @JSBody}-based implementation but runs
 * on the JVM via GraalJS + GraalWasm.
 */
package dev.engine.providers.graal.webgpu;

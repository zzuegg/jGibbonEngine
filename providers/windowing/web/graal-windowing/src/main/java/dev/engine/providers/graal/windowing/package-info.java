/**
 * GraalJS-based canvas windowing toolkit.
 *
 * <p>Implements {@link dev.engine.graphics.window.WindowToolkit} by calling
 * browser DOM APIs through GraalVM polyglot interop. The web equivalent of
 * GLFW — canvas dimensions, title, and {@code requestAnimationFrame}-based
 * event yielding.
 */
package dev.engine.providers.graal.windowing;

package dev.engine.graphics;

import dev.engine.graphics.window.WindowDescriptor;

/**
 * Factory for creating backend-specific graphics infrastructure.
 *
 * <p>Each graphics backend (OpenGL, Vulkan, WebGPU) provides a factory
 * that creates the window toolkit, window, and render device.
 *
 * <pre>{@code
 * var factory = OpenGlBackend.factory(glBindings);
 * var backend = factory.create(new WindowDescriptor("My Game", 1280, 720));
 * }</pre>
 */
@FunctionalInterface
public interface GraphicsBackendFactory {

    GraphicsBackend create(WindowDescriptor window);
}

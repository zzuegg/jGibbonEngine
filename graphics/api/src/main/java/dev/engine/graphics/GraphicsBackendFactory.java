package dev.engine.graphics;

import dev.engine.graphics.window.WindowDescriptor;

/**
 * Factory for creating backend-specific graphics infrastructure.
 * @deprecated Use {@link GraphicsConfig} implementations directly instead.
 */
@Deprecated
@FunctionalInterface
public interface GraphicsBackendFactory {

    GraphicsBackend create(WindowDescriptor window, GraphicsConfig config);
}

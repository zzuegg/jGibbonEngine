package dev.engine.graphics.opengl;

import dev.engine.graphics.GraphicsConfig;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * OpenGL graphics configuration.
 *
 * <pre>{@code
 * var gfx = new OpenGlConfig(toolkit, glBindings);
 * }</pre>
 */
public final class OpenGlConfig extends GraphicsConfig {

    private final GlBindings gl;

    public OpenGlConfig(WindowToolkit toolkit, GlBindings gl) {
        super(toolkit);
        this.gl = gl;
    }

    @Override
    protected RenderDevice createDevice(WindowHandle window) {
        return new GlRenderDevice(window, gl, this);
    }
}

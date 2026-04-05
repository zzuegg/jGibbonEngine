package dev.engine.graphics.webgpu;

import dev.engine.graphics.GraphicsConfig;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * WebGPU graphics configuration.
 *
 * <pre>{@code
 * var gfx = new WebGpuConfig(toolkit, wgpuBindings);
 * }</pre>
 */
public final class WebGpuConfig extends GraphicsConfig {

    private final WgpuBindings gpu;

    public WebGpuConfig(WindowToolkit toolkit, WgpuBindings gpu) {
        super(toolkit);
        this.gpu = gpu;
    }

    @Override
    protected RenderDevice createDevice(WindowHandle window) {
        return new WgpuRenderDevice(window, gpu, !headless());
    }
}

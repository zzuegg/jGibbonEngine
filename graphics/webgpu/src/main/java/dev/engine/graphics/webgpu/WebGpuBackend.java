package dev.engine.graphics.webgpu;

import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.window.WindowToolkit;

/**
 * WebGPU backend factory.
 * Takes an externally provided window toolkit and creates a WebGPU render device.
 * The toolkit must create windows without an API-specific context (NO_API).
 */
public final class WebGpuBackend {

    private WebGpuBackend() {}

    public static GraphicsBackendFactory factory(WindowToolkit toolkit, WgpuBindings gpu) {
        return (windowDesc, config) -> {
            var window = toolkit.createWindow(windowDesc);
            var device = new WgpuRenderDevice(window, gpu, !config.headless());
            return new GraphicsBackend(toolkit, window, device);
        };
    }
}

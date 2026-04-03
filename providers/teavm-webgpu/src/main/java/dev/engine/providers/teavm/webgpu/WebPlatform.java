package dev.engine.providers.teavm.webgpu;

import dev.engine.core.memory.NativeMemory;
import dev.engine.platform.Platform;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;

/**
 * Web platform using TeaVM + WebGPU.
 *
 * <p>Runs in the browser via TeaVM transpilation. Uses a canvas-based
 * window toolkit and JavaScript WebGPU bindings.
 */
public class WebPlatform implements Platform {

    @Override
    public String name() {
        return "Web WebGPU";
    }

    @Override
    public WindowToolkit createWindowToolkit() {
        return new CanvasWindowToolkit();
    }

    @Override
    public RenderDevice createRenderDevice(WindowHandle window, int width, int height) {
        return new WgpuRenderDevice(window, new TeaVmWgpuBindings());
    }

    @Override
    public NativeMemory allocateMemory(long size) {
        return new ByteBufferNativeMemory((int) size);
    }
}

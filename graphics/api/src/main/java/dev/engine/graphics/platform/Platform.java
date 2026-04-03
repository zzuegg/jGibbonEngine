package dev.engine.graphics.platform;

import dev.engine.core.gpu.GpuMemory;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Bundles platform-specific providers into a single configuration.
 *
 * <p>Users pick a platform, the platform provides everything needed:
 * window toolkit, render device, and memory allocation strategy.
 *
 * <pre>{@code
 * var platform = new OpenGlPlatform();
 * var toolkit = platform.createWindowToolkit();
 * var window = toolkit.createWindow(new WindowDescriptor("My App", 800, 600));
 * var device = platform.createRenderDevice(window, 800, 600);
 * }</pre>
 */
public interface Platform {

    /** Human-readable platform name (e.g., "Desktop OpenGL", "Web WebGPU"). */
    String name();

    /** Creates the window toolkit for this platform. */
    WindowToolkit createWindowToolkit();

    /** Creates a render device for the given window. */
    RenderDevice createRenderDevice(WindowHandle window, int width, int height);

    /** Creates a {@link GpuMemory} instance of the given size. */
    GpuMemory allocateMemory(long size);
}

package dev.engine.platform;

import dev.engine.core.memory.NativeMemory;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Bundles platform-specific providers into a single configuration.
 *
 * <p>Users pick a platform, the platform provides everything needed:
 * window toolkit, render device, shader compiler, and memory allocation strategy.
 *
 * <pre>{@code
 * var platform = new OpenGlPlatform();
 * var toolkit = platform.createWindowToolkit();
 * var window = toolkit.createWindow(new WindowDescriptor("My App", 800, 600));
 * var device = platform.createRenderDevice(window, 800, 600);
 * var compiler = platform.createShaderCompiler();
 * }</pre>
 */
public interface Platform {

    /** Human-readable platform name (e.g., "Desktop OpenGL", "Web WebGPU"). */
    String name();

    /** Creates the window toolkit for this platform. */
    WindowToolkit createWindowToolkit();

    /** Creates a render device for the given window. */
    RenderDevice createRenderDevice(WindowHandle window, int width, int height);

    /** Creates the shader compiler for this platform. */
    ShaderCompiler createShaderCompiler();

    /** Creates a {@link NativeMemory} instance of the given size. */
    NativeMemory allocateMemory(long size);
}

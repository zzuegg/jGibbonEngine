package dev.engine.windowing.glfw;

import dev.engine.core.input.InputProvider;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowProperty;
import dev.engine.graphics.window.WindowToolkit;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GLFW-based window toolkit. The graphics backend provides window hints
 * via a callback to configure the correct context type (OpenGL, Vulkan, none).
 *
 * <pre>{@code
 * // OpenGL backend
 * var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
 *
 * // Vulkan backend
 * var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
 * }</pre>
 */
public class GlfwWindowToolkit implements WindowToolkit {

    private static final Logger log = LoggerFactory.getLogger(GlfwWindowToolkit.class);

    /** OpenGL 4.5 Core Profile hints (default). */
    public static final Consumer<Void> OPENGL_HINTS = openGlHints(4, 5);

    /** Creates OpenGL Core Profile hints for a specific version. */
    public static Consumer<Void> openGlHints(int major, int minor) {
        return v -> {
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        };
    }

    /** No API — for Vulkan or other non-GL backends. */
    public static final Consumer<Void> NO_API_HINTS = v -> {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
    };

    // GLFW must only be initialized once per process — LWJGL's callback registry
    // is invalidated by glfwTerminate and cannot be re-initialized in the same JVM.
    private static int refCount = 0;
    private static GLFWErrorCallback sharedErrorCallback;

    private final Consumer<Void> windowHints;
    private final List<GlfwWindowHandle> windows = new ArrayList<>();
    private boolean initialized = false;

    public GlfwWindowToolkit(Consumer<Void> windowHints) {
        this.windowHints = windowHints;
        if (refCount == 0) {
            sharedErrorCallback = GLFWErrorCallback.createPrint(System.err).set();
            if (!GLFW.glfwInit()) {
                throw new RuntimeException("Failed to initialize GLFW");
            }
            log.info("GLFW initialized");
        }
        refCount++;
        initialized = true;
    }

    /** Creates a toolkit with OpenGL 4.5 hints (default). */
    public GlfwWindowToolkit() {
        this(OPENGL_HINTS);
    }

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        windowHints.accept(null);

        long window = GLFW.glfwCreateWindow(
                descriptor.width(), descriptor.height(),
                descriptor.title(), 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        var handle = new GlfwWindowHandle(window, descriptor);
        windows.add(handle);
        log.info("Created window '{}' ({}x{})", descriptor.title(), descriptor.width(), descriptor.height());
        return handle;
    }

    @Override
    public void pollEvents() {
        GLFW.glfwPollEvents();
    }

    @Override
    public InputProvider createInputProvider(WindowHandle window) {
        if (window instanceof GlfwWindowHandle glfwWindow) {
            return new GlfwInputProvider(glfwWindow);
        }
        return null;
    }

    @Override
    public void close() {
        for (var window : windows) {
            window.close();
        }
        windows.clear();
        if (initialized) {
            initialized = false;
            refCount--;
            // Never call glfwTerminate — LWJGL's callback registry is invalidated
            // and cannot be re-initialized. JVM process exit cleans up natively.
        }
    }

    /**
     * Creates a Vulkan surface for a GLFW window.
     * Returns the VkSurfaceKHR handle. Intended for use with
     * {@code VkRenderDevice.SurfaceFactory}.
     */
    public static long createVulkanSurface(org.lwjgl.vulkan.VkInstance instance, long windowHandle) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var pSurface = stack.mallocLong(1);
            int result = org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, pSurface);
            if (result != 0) {
                throw new RuntimeException("Failed to create Vulkan surface: " + result);
            }
            return pSurface.get(0);
        }
    }

    /**
     * Creates a Vulkan surface from a raw VkInstance handle (long) and a GLFW window handle.
     * This overload does not require the LWJGL VkInstance wrapper — it creates one internally.
     */
    public static long createVulkanSurfaceFromHandle(long instanceHandle, long windowHandle) {
        // Reconstruct a minimal VkInstance from the raw handle for GLFW's benefit
        var instance = new org.lwjgl.vulkan.VkInstance(instanceHandle,
                org.lwjgl.vulkan.VkInstanceCreateInfo.calloc().sType$Default());
        return createVulkanSurface(instance, windowHandle);
    }

    /**
     * Returns the required Vulkan instance extensions as a String array.
     */
    public static String[] getRequiredVulkanExtensions() {
        var buf = org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions();
        if (buf == null) return new String[0];
        String[] result = new String[buf.remaining()];
        for (int i = 0; i < result.length; i++) {
            result[i] = org.lwjgl.system.MemoryUtil.memUTF8(buf.get(buf.position() + i));
        }
        return result;
    }

    public static class GlfwWindowHandle implements WindowHandle {
        private long handle;
        private final WindowDescriptor descriptor;
        private final MutablePropertyMap<WindowHandle> properties = new MutablePropertyMap<>();
        private final dev.engine.core.versioned.Versioned<int[]> size;
        private final dev.engine.core.versioned.Versioned<Boolean> focused;
        // Saved windowed-mode geometry for fullscreen toggle
        private int savedX, savedY, savedWidth, savedHeight;

        GlfwWindowHandle(long handle, WindowDescriptor descriptor) {
            this.handle = handle;
            this.descriptor = descriptor;
            this.size = new dev.engine.core.versioned.Versioned<>(new int[]{descriptor.width(), descriptor.height()});
            this.focused = new dev.engine.core.versioned.Versioned<>(false);
            properties.set(WindowProperty.TITLE, descriptor.title());
            properties.set(WindowProperty.VISIBLE, false);
            properties.set(WindowProperty.VSYNC, false);
            properties.set(WindowProperty.RESIZABLE, true);
            properties.set(WindowProperty.DECORATED, true);
            properties.set(WindowProperty.FULLSCREEN, false);
        }

        @Override
        public long nativeHandle() { return handle; }

        @Override
        public boolean isOpen() {
            return handle != 0 && !GLFW.glfwWindowShouldClose(handle);
        }

        @Override
        public int width() {
            if (handle == 0) return 0;
            int[] w = new int[1];
            GLFW.glfwGetWindowSize(handle, w, new int[1]);
            return w[0];
        }

        @Override
        public int height() {
            if (handle == 0) return 0;
            int[] h = new int[1];
            GLFW.glfwGetWindowSize(handle, new int[1], h);
            return h[0];
        }

        @Override
        public String title() {
            String t = properties.get(WindowProperty.TITLE);
            return t != null ? t : descriptor.title();
        }

        @Override
        public void show() {
            if (handle != 0) {
                GLFW.glfwShowWindow(handle);
                properties.set(WindowProperty.VISIBLE, true);
            }
        }

        @Override
        public <T> T get(PropertyKey<WindowHandle, T> key) {
            return properties.get(key);
        }

        @Override
        public <T> void set(PropertyKey<WindowHandle, T> key, T value) {
            properties.set(key, value);
            if (handle == 0) return;

            if (key == WindowProperty.TITLE) {
                GLFW.glfwSetWindowTitle(handle, (String) value);
            } else if (key == WindowProperty.VSYNC) {
                GLFW.glfwSwapInterval((Boolean) value ? 1 : 0);
            } else if (key == WindowProperty.RESIZABLE) {
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_RESIZABLE, (Boolean) value ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            } else if (key == WindowProperty.DECORATED) {
                GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, (Boolean) value ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
            } else if (key == WindowProperty.VISIBLE) {
                if ((Boolean) value) GLFW.glfwShowWindow(handle);
                else GLFW.glfwHideWindow(handle);
            } else if (key == WindowProperty.FULLSCREEN) {
                boolean fullscreen = (Boolean) value;
                if (fullscreen) {
                    // Save windowed position and size
                    int[] xBuf = new int[1], yBuf = new int[1];
                    GLFW.glfwGetWindowPos(handle, xBuf, yBuf);
                    savedX = xBuf[0];
                    savedY = yBuf[0];
                    savedWidth = width();
                    savedHeight = height();
                    // Switch to fullscreen on primary monitor
                    long monitor = GLFW.glfwGetPrimaryMonitor();
                    var mode = GLFW.glfwGetVideoMode(monitor);
                    if (mode != null) {
                        GLFW.glfwSetWindowMonitor(handle, monitor, 0, 0,
                                mode.width(), mode.height(), mode.refreshRate());
                    }
                } else {
                    // Restore windowed mode
                    GLFW.glfwSetWindowMonitor(handle, 0L, savedX, savedY,
                            savedWidth > 0 ? savedWidth : 1280,
                            savedHeight > 0 ? savedHeight : 720, GLFW.GLFW_DONT_CARE);
                }
            } else if (key == WindowProperty.SWAP_INTERVAL) {
                GLFW.glfwSwapInterval((Integer) value);
            }
        }

        @Override
        public dev.engine.core.versioned.Reference<int[]> sizeRef() {
            return size.createReference();
        }

        @Override
        public dev.engine.core.versioned.Reference<Boolean> focusedRef() {
            return focused.createReference();
        }

        @Override
        public void swapBuffers() {
            if (handle != 0) GLFW.glfwSwapBuffers(handle);
        }

        @Override
        public SurfaceInfo surfaceInfo() {
            if (handle == 0) return null;
            try {
                // Try Wayland first
                long waylandDisplay = org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay();
                long waylandWindow = org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow(handle);
                if (waylandDisplay != 0 && waylandWindow != 0) {
                    return new SurfaceInfo(SurfaceInfo.SurfaceType.WAYLAND, waylandDisplay, waylandWindow);
                }
            } catch (Throwable ignored) {}
            try {
                // Fall back to X11
                long x11Display = org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display();
                long x11Window = org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle);
                if (x11Display != 0 && x11Window != 0) {
                    return new SurfaceInfo(SurfaceInfo.SurfaceType.X11, x11Display, x11Window);
                }
            } catch (Throwable ignored) {}
            try {
                // Windows
                long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(handle);
                if (hwnd != 0) {
                    return new SurfaceInfo(SurfaceInfo.SurfaceType.WINDOWS, 0, hwnd);
                }
            } catch (Throwable ignored) {}
            try {
                // macOS
                long cocoa = org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow(handle);
                if (cocoa != 0) {
                    return new SurfaceInfo(SurfaceInfo.SurfaceType.COCOA, 0, cocoa);
                }
            } catch (Throwable ignored) {}
            return null;
        }

        /** Called from GLFW resize callback. */
        void updateSize(int width, int height) {
            size.set(new int[]{width, height});
        }

        /** Called from GLFW focus callback. */
        void updateFocused(boolean isFocused) {
            focused.set(isFocused);
        }

        @Override
        public void close() {
            if (handle != 0) {
                GLFW.glfwDestroyWindow(handle);
                handle = 0;
            }
        }
    }
}

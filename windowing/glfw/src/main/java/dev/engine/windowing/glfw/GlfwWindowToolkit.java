package dev.engine.windowing.glfw;

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

    /** OpenGL 4.5 Core Profile hints. */
    public static final Consumer<Void> OPENGL_HINTS = v -> {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
    };

    /** No API — for Vulkan or other non-GL backends. */
    public static final Consumer<Void> NO_API_HINTS = v -> {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
    };

    private final Consumer<Void> windowHints;
    private final List<GlfwWindowHandle> windows = new ArrayList<>();
    private boolean initialized = false;

    public GlfwWindowToolkit(Consumer<Void> windowHints) {
        this.windowHints = windowHints;
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        initialized = true;
        log.info("GLFW initialized");
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
    public void close() {
        for (var window : windows) {
            window.close();
        }
        windows.clear();
        if (initialized) {
            GLFW.glfwTerminate();
            initialized = false;
            log.info("GLFW terminated");
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

    public static class GlfwWindowHandle implements WindowHandle {
        private long handle;
        private final WindowDescriptor descriptor;
        private final MutablePropertyMap properties = new MutablePropertyMap();

        GlfwWindowHandle(long handle, WindowDescriptor descriptor) {
            this.handle = handle;
            this.descriptor = descriptor;
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
        @SuppressWarnings("unchecked")
        public <T> T get(PropertyKey<T> key) {
            return properties.get(key);
        }

        @Override
        public <T> void set(PropertyKey<T> key, T value) {
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
                // Fullscreen toggle is complex — store but don't apply for now
                // Would need monitor info, video mode, etc.
            } else if (key == WindowProperty.SWAP_INTERVAL) {
                GLFW.glfwSwapInterval((Integer) value);
            }
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

package dev.engine.graphics.opengl;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class GlfwWindowToolkit implements WindowToolkit {

    private static final Logger log = LoggerFactory.getLogger(GlfwWindowToolkit.class);

    private final List<GlfwWindowHandle> windows = new ArrayList<>();
    private boolean initialized = false;

    public GlfwWindowToolkit() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        initialized = true;
        log.info("GLFW initialized");
    }

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 5);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

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

    static class GlfwWindowHandle implements WindowHandle {
        private long handle;
        private final WindowDescriptor descriptor;

        GlfwWindowHandle(long handle, WindowDescriptor descriptor) {
            this.handle = handle;
            this.descriptor = descriptor;
        }

        long glfwHandle() { return handle; }

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
        public String title() { return descriptor.title(); }

        @Override
        public void close() {
            if (handle != 0) {
                GLFW.glfwDestroyWindow(handle);
                handle = 0;
            }
        }
    }
}

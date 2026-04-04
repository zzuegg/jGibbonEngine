package dev.engine.bindings.sdl3;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;

public class Sdl3WindowToolkit implements WindowToolkit {

    private static final Logger log = LoggerFactory.getLogger(Sdl3WindowToolkit.class);

    private final List<Sdl3WindowHandle> windows = new ArrayList<>();
    private final boolean opengl;
    private boolean initialized;
    private Sdl3InputProvider inputProvider;

    /** Creates a plain SDL3 toolkit (no graphics API). */
    public Sdl3WindowToolkit() {
        this(false);
    }

    /**
     * Creates an SDL3 toolkit.
     * @param opengl if true, sets up OpenGL 4.5 core context on created windows
     */
    public Sdl3WindowToolkit(boolean opengl) {
        this.opengl = opengl;
        if (!SDL_Init(SDL_INIT_VIDEO)) {
            throw new RuntimeException("Failed to initialize SDL3");
        }
        if (opengl) {
            SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 4);
            SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 5);
            SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);
            SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 1);
        }
        initialized = true;
        log.info("SDL3 initialized (opengl={})", opengl);
    }

    /** Register an input provider to receive events during pollEvents(). */
    public void setInputProvider(Sdl3InputProvider provider) {
        this.inputProvider = provider;
    }

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        long flags = SDL_WINDOW_RESIZABLE | SDL_WINDOW_HIDDEN;
        if (opengl) flags |= SDL_WINDOW_OPENGL;

        long ptr = SDL_CreateWindow(descriptor.title(), descriptor.width(), descriptor.height(), flags);
        if (ptr == 0) {
            throw new RuntimeException("Failed to create SDL3 window");
        }

        long glContext = 0;
        if (opengl) {
            glContext = SDL_GL_CreateContext(ptr);
            if (glContext == 0) {
                SDL_DestroyWindow(ptr);
                throw new RuntimeException("Failed to create SDL3 OpenGL context");
            }
            if (!SDL_GL_MakeCurrent(ptr, glContext)) {
                throw new RuntimeException("Failed to make SDL3 OpenGL context current");
            }
            org.lwjgl.opengl.GL.createCapabilities();
            log.info("SDL3 OpenGL context created (4.5 core)");
        }

        var handle = new Sdl3WindowHandle(ptr, glContext, descriptor);
        windows.add(handle);
        log.info("Created SDL3 window '{}' ({}x{})", descriptor.title(), descriptor.width(), descriptor.height());
        return handle;
    }

    @Override
    public void pollEvents() {
        var event = SDL_Event.calloc();
        try {
            while (SDLEvents.SDL_PollEvent(event)) {
                int type = event.type();
                if (type == SDLEvents.SDL_EVENT_QUIT || type == SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED) {
                    for (var w : windows) w.requestClose();
                }
                if (inputProvider != null) {
                    inputProvider.processEvent(event);
                }
            }
        } finally {
            event.free();
        }
    }

    @Override
    public void close() {
        for (var w : windows) w.close();
        windows.clear();
        if (initialized) {
            SDLInit.SDL_Quit();
            initialized = false;
            log.info("SDL3 terminated");
        }
    }

    public static class Sdl3WindowHandle implements WindowHandle {
        private long ptr;
        private long glContext;
        private final WindowDescriptor descriptor;
        private boolean closeRequested;

        Sdl3WindowHandle(long ptr, long glContext, WindowDescriptor descriptor) {
            this.ptr = ptr;
            this.glContext = glContext;
            this.descriptor = descriptor;
        }

        public long nativePtr() { return ptr; }

        @Override public long nativeHandle() { return ptr; }

        @Override
        public SurfaceInfo surfaceInfo() {
            if (ptr == 0) return null;
            int props = SDL_GetWindowProperties(ptr);
            if (props == 0) return null;

            // Try Wayland
            long wlSurface = SDLProperties.SDL_GetPointerProperty(props,
                    SDLVideo.SDL_PROP_WINDOW_WAYLAND_SURFACE_POINTER, 0);
            long wlDisplay = SDLProperties.SDL_GetPointerProperty(props,
                    SDLVideo.SDL_PROP_WINDOW_WAYLAND_DISPLAY_POINTER, 0);
            if (wlSurface != 0 && wlDisplay != 0) {
                return new SurfaceInfo(SurfaceInfo.SurfaceType.WAYLAND, wlDisplay, wlSurface);
            }

            // Try X11
            long x11Display = SDLProperties.SDL_GetPointerProperty(props,
                    SDLVideo.SDL_PROP_WINDOW_X11_DISPLAY_POINTER, 0);
            long x11Window = SDLProperties.SDL_GetNumberProperty(props,
                    SDLVideo.SDL_PROP_WINDOW_X11_WINDOW_NUMBER, 0);
            if (x11Display != 0 && x11Window != 0) {
                return new SurfaceInfo(SurfaceInfo.SurfaceType.X11, x11Display, x11Window);
            }

            // Try Windows
            long hwnd = SDLProperties.SDL_GetPointerProperty(props,
                    SDLVideo.SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0);
            if (hwnd != 0) {
                return new SurfaceInfo(SurfaceInfo.SurfaceType.WINDOWS, 0, hwnd);
            }

            // Try macOS
            long cocoa = SDLProperties.SDL_GetPointerProperty(props,
                    SDLVideo.SDL_PROP_WINDOW_COCOA_WINDOW_POINTER, 0);
            if (cocoa != 0) {
                return new SurfaceInfo(SurfaceInfo.SurfaceType.COCOA, 0, cocoa);
            }

            return null;
        }

        void requestClose() { closeRequested = true; }

        @Override public boolean isOpen() { return ptr != 0 && !closeRequested; }

        @Override
        public int width() {
            if (ptr == 0) return 0;
            var w = MemoryUtil.memAllocInt(1);
            var h = MemoryUtil.memAllocInt(1);
            try {
                SDL_GetWindowSize(ptr, w, h);
                return w.get(0);
            } finally {
                MemoryUtil.memFree(w);
                MemoryUtil.memFree(h);
            }
        }

        @Override
        public int height() {
            if (ptr == 0) return 0;
            var w = MemoryUtil.memAllocInt(1);
            var h = MemoryUtil.memAllocInt(1);
            try {
                SDL_GetWindowSize(ptr, w, h);
                return h.get(0);
            } finally {
                MemoryUtil.memFree(w);
                MemoryUtil.memFree(h);
            }
        }

        @Override public String title() { return descriptor.title(); }

        @Override
        public void show() {
            if (ptr != 0) SDL_ShowWindow(ptr);
        }

        @Override
        public void swapBuffers() {
            if (ptr != 0 && glContext != 0) {
                SDL_GL_SwapWindow(ptr);
            }
        }

        @Override
        public void close() {
            if (glContext != 0) {
                SDL_GL_DestroyContext(glContext);
                glContext = 0;
            }
            if (ptr != 0) {
                SDL_DestroyWindow(ptr);
                ptr = 0;
            }
        }
    }
}

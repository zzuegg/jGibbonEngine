package dev.engine.bindings.sdl3;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.sdl.SDLInit.*;
import static org.lwjgl.sdl.SDLVideo.*;

public class Sdl3WindowToolkit implements WindowToolkit {

    private static final Logger log = LoggerFactory.getLogger(Sdl3WindowToolkit.class);

    private final List<Sdl3WindowHandle> windows = new ArrayList<>();
    private boolean initialized;

    public Sdl3WindowToolkit() {
        if (!SDL_Init(SDL_INIT_VIDEO)) {
            throw new RuntimeException("Failed to initialize SDL3");
        }
        initialized = true;
        log.info("SDL3 initialized (via LWJGL)");
    }

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        long ptr = SDL_CreateWindow(descriptor.title(), descriptor.width(), descriptor.height(),
                SDL_WINDOW_RESIZABLE | SDL_WINDOW_HIDDEN);
        if (ptr == 0) {
            throw new RuntimeException("Failed to create SDL3 window");
        }
        var handle = new Sdl3WindowHandle(ptr, descriptor);
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
        private final WindowDescriptor descriptor;
        private boolean closeRequested;

        Sdl3WindowHandle(long ptr, WindowDescriptor descriptor) {
            this.ptr = ptr;
            this.descriptor = descriptor;
        }

        public long nativePtr() { return ptr; }

        void requestClose() { closeRequested = true; }

        @Override public boolean isOpen() { return ptr != 0 && !closeRequested; }

        @Override
        public int width() {
            if (ptr == 0) return 0;
            var w = org.lwjgl.system.MemoryUtil.memAllocInt(1);
            var h = org.lwjgl.system.MemoryUtil.memAllocInt(1);
            try {
                SDL_GetWindowSize(ptr, w, h);
                return w.get(0);
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(w);
                org.lwjgl.system.MemoryUtil.memFree(h);
            }
        }

        @Override
        public int height() {
            if (ptr == 0) return 0;
            var w = org.lwjgl.system.MemoryUtil.memAllocInt(1);
            var h = org.lwjgl.system.MemoryUtil.memAllocInt(1);
            try {
                SDL_GetWindowSize(ptr, w, h);
                return h.get(0);
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(w);
                org.lwjgl.system.MemoryUtil.memFree(h);
            }
        }

        @Override public String title() { return descriptor.title(); }

        @Override
        public void show() {
            if (ptr != 0) SDL_ShowWindow(ptr);
        }

        @Override
        public void close() {
            if (ptr != 0) {
                SDL_DestroyWindow(ptr);
                ptr = 0;
            }
        }
    }
}

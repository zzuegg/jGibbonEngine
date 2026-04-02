package dev.engine.bindings.sdl3;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

public class Sdl3WindowToolkit implements WindowToolkit {

    private static final Logger log = LoggerFactory.getLogger(Sdl3WindowToolkit.class);
    private static final int SDL_EVENT_SIZE = 128;

    private final Arena eventArena = Arena.ofAuto();
    private final MemorySegment eventBuffer = eventArena.allocate(SDL_EVENT_SIZE);
    private final List<Sdl3WindowHandle> windows = new ArrayList<>();
    private boolean initialized;

    public Sdl3WindowToolkit() {
        if (!SDL3.init(SDL3.SDL_INIT_VIDEO)) {
            throw new RuntimeException("Failed to initialize SDL3: " + SDL3.getError());
        }
        initialized = true;
        log.info("SDL3 initialized");
    }

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        int flags = SDL3.SDL_WINDOW_RESIZABLE | SDL3.SDL_WINDOW_HIDDEN;
        var ptr = SDL3.createWindow(descriptor.title(), descriptor.width(), descriptor.height(), flags);
        if (ptr.equals(MemorySegment.NULL)) {
            throw new RuntimeException("Failed to create SDL3 window: " + SDL3.getError());
        }
        var handle = new Sdl3WindowHandle(ptr, descriptor);
        windows.add(handle);
        log.info("Created SDL3 window '{}' ({}x{})", descriptor.title(), descriptor.width(), descriptor.height());
        return handle;
    }

    @Override
    public void pollEvents() {
        while (SDL3.pollEvent(eventBuffer)) {
            int type = eventBuffer.get(ValueLayout.JAVA_INT, 0);
            if (type == SDL3.SDL_EVENT_QUIT || type == SDL3.SDL_EVENT_WINDOW_CLOSE_REQUESTED) {
                for (var w : windows) w.requestClose();
            }
        }
    }

    @Override
    public void close() {
        for (var w : windows) w.close();
        windows.clear();
        if (initialized) {
            SDL3.quit();
            initialized = false;
            log.info("SDL3 terminated");
        }
    }

    public static class Sdl3WindowHandle implements WindowHandle {
        private MemorySegment ptr;
        private final WindowDescriptor descriptor;
        private boolean closeRequested;

        Sdl3WindowHandle(MemorySegment ptr, WindowDescriptor descriptor) {
            this.ptr = ptr;
            this.descriptor = descriptor;
        }

        public MemorySegment nativePtr() { return ptr; }

        void requestClose() { closeRequested = true; }

        @Override public boolean isOpen() { return ptr != null && !ptr.equals(MemorySegment.NULL) && !closeRequested; }
        @Override public int width() { return ptr == null ? 0 : SDL3.getWindowSize(ptr)[0]; }
        @Override public int height() { return ptr == null ? 0 : SDL3.getWindowSize(ptr)[1]; }
        @Override public String title() { return descriptor.title(); }
        @Override public void show() { if (ptr != null) SDL3.showWindow(ptr); }

        @Override
        public void close() {
            if (ptr != null && !ptr.equals(MemorySegment.NULL)) {
                SDL3.destroyWindow(ptr);
                ptr = null;
            }
        }
    }
}

package dev.engine.bindings.sdl3;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal FFM bindings to SDL3 for window management.
 */
public final class SDL3 {

    public static final int SDL_INIT_VIDEO = 0x00000020;
    public static final int SDL_WINDOW_VULKAN = 0x10000000;
    public static final int SDL_WINDOW_OPENGL = 0x00000002;
    public static final int SDL_WINDOW_RESIZABLE = 0x00000020;
    public static final int SDL_WINDOW_HIDDEN = 0x00000008;

    public static final int SDL_EVENT_QUIT = 0x100;
    public static final int SDL_EVENT_WINDOW_CLOSE_REQUESTED = 0x202;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SDL;

    private static final MethodHandle sdl_Init;
    private static final MethodHandle sdl_Quit;
    private static final MethodHandle sdl_CreateWindow;
    private static final MethodHandle sdl_DestroyWindow;
    private static final MethodHandle sdl_ShowWindow;
    private static final MethodHandle sdl_GetWindowSize;
    private static final MethodHandle sdl_PollEvent;
    private static final MethodHandle sdl_GetError;

    static {
        SDL = SymbolLookup.libraryLookup("libSDL3.so.0", Arena.global());

        sdl_Init = downcall("SDL_Init",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        sdl_Quit = downcall("SDL_Quit",
                FunctionDescriptor.ofVoid());
        sdl_CreateWindow = downcall("SDL_CreateWindow",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        sdl_DestroyWindow = downcall("SDL_DestroyWindow",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        sdl_ShowWindow = downcall("SDL_ShowWindow",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        sdl_GetWindowSize = downcall("SDL_GetWindowSize",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        sdl_PollEvent = downcall("SDL_PollEvent",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        sdl_GetError = downcall("SDL_GetError",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
    }

    private SDL3() {}

    public static boolean init(int flags) {
        try {
            return (int) sdl_Init.invokeExact(flags) != 0;
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void quit() {
        try {
            sdl_Quit.invokeExact();
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static MemorySegment createWindow(String title, int width, int height, int flags) {
        try (var arena = Arena.ofConfined()) {
            var titleSeg = arena.allocateFrom(title);
            return (MemorySegment) sdl_CreateWindow.invokeExact(titleSeg, width, height, flags);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void destroyWindow(MemorySegment window) {
        try {
            sdl_DestroyWindow.invokeExact(window);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void showWindow(MemorySegment window) {
        try {
            int ignored = (int) sdl_ShowWindow.invokeExact(window);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static int[] getWindowSize(MemorySegment window) {
        try (var arena = Arena.ofConfined()) {
            var w = arena.allocate(ValueLayout.JAVA_INT);
            var h = arena.allocate(ValueLayout.JAVA_INT);
            int ignored = (int) sdl_GetWindowSize.invokeExact(window, w, h);
            return new int[]{w.get(ValueLayout.JAVA_INT, 0), h.get(ValueLayout.JAVA_INT, 0)};
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static boolean pollEvent(MemorySegment eventBuffer) {
        try {
            return (int) sdl_PollEvent.invokeExact(eventBuffer) != 0;
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static String getError() {
        try {
            var ptr = (MemorySegment) sdl_GetError.invokeExact();
            if (ptr.equals(MemorySegment.NULL)) return "";
            return ptr.reinterpret(256).getString(0);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor desc) {
        var symbol = SDL.find(name).orElseThrow(() ->
                new UnsatisfiedLinkError("SDL3 symbol not found: " + name));
        return LINKER.downcallHandle(symbol, desc);
    }
}

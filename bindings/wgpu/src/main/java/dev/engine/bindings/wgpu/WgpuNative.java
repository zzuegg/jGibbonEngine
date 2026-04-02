package dev.engine.bindings.wgpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * FFM bindings to wgpu-native (webgpu.h C API).
 *
 * <p>Requires libwgpu_native.so to be installed on the system.
 * If not available, {@link #isAvailable()} returns false and
 * all operations throw {@link UnsatisfiedLinkError}.
 */
public final class WgpuNative {

    private static final boolean AVAILABLE;
    private static final SymbolLookup WGPU;

    static {
        SymbolLookup lookup = null;
        try {
            lookup = SymbolLookup.libraryLookup("libwgpu_native.so", Arena.global());
        } catch (IllegalArgumentException | UnsatisfiedLinkError e) {
            // wgpu-native not installed
        }
        WGPU = lookup;
        AVAILABLE = lookup != null;
    }

    private WgpuNative() {}

    public static boolean isAvailable() { return AVAILABLE; }

    public static MemorySegment createInstance() {
        requireAvailable();
        var handle = downcall("wgpuCreateInstance",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        try {
            return (MemorySegment) handle.invokeExact(MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    public static void instanceRelease(MemorySegment instance) {
        requireAvailable();
        var handle = downcall("wgpuInstanceRelease",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        try {
            handle.invokeExact(instance);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static void requireAvailable() {
        if (!AVAILABLE) throw new UnsatisfiedLinkError("wgpu-native (libwgpu_native.so) is not installed");
    }

    private static MethodHandle downcall(String name, FunctionDescriptor desc) {
        var symbol = WGPU.find(name).orElseThrow(() ->
                new UnsatisfiedLinkError("wgpu symbol not found: " + name));
        return Linker.nativeLinker().downcallHandle(symbol, desc);
    }
}

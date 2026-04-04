package dev.engine.bindings.slang;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a COM interface pointer and provides access to vtable methods.
 *
 * <p>A COM interface pointer points to an object whose first field is a pointer
 * to a vtable (array of function pointers). Method N is at vtable offset
 * {@code N * ADDRESS_SIZE}.
 *
 * <p>Every vtable method receives the object pointer as its first argument ({@code this}).
 */
public class ComPtr implements AutoCloseable {

    private static final long ADDRESS_SIZE = ValueLayout.ADDRESS.byteSize();

    /** Cache of downcall handles keyed by (vtable address, method index, descriptor). */
    private static final Map<Long, Map<Integer, MethodHandle>> handleCache = new ConcurrentHashMap<>();

    private MemorySegment ptr;

    public ComPtr(MemorySegment ptr) {
        this.ptr = ptr;
    }

    /** Returns the raw interface pointer. */
    public MemorySegment ptr() {
        return ptr;
    }

    /** Returns true if this pointer is null or NULL. */
    public boolean isNull() {
        return ptr == null || ptr.equals(MemorySegment.NULL);
    }

    /**
     * Reads the vtable pointer from offset 0 of the object.
     */
    public MemorySegment vtable() {
        return ptr.reinterpret(ADDRESS_SIZE)
                .get(ValueLayout.ADDRESS, 0)
                .reinterpret(256 * ADDRESS_SIZE); // generous size for vtable
    }

    /**
     * Gets the function pointer at the given vtable index.
     */
    public MemorySegment functionPointer(int methodIndex) {
        return vtable().get(ValueLayout.ADDRESS, (long) methodIndex * ADDRESS_SIZE);
    }

    /**
     * Creates a downcall handle for the vtable method at the given index.
     *
     * @param methodIndex the zero-based index into the vtable
     * @param descriptor  the function descriptor (including {@code this} pointer as first arg)
     * @return a MethodHandle that can be invoked
     */
    public MethodHandle methodHandle(int methodIndex, FunctionDescriptor descriptor) {
        var fnPtr = functionPointer(methodIndex);
        return Linker.nativeLinker().downcallHandle(fnPtr, descriptor);
    }

    /**
     * Calls {@code addRef()} — vtable index 1 for ISlangUnknown.
     */
    public int addRef() {
        try {
            var handle = methodHandle(1, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            return (int) handle.invoke(ptr);
        } catch (Throwable t) {
            throw new SlangException("addRef failed", t);
        }
    }

    /**
     * Calls {@code release()} — vtable index 2 for ISlangUnknown.
     */
    public int release() {
        try {
            var handle = methodHandle(2, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            return (int) handle.invoke(ptr);
        } catch (Throwable t) {
            throw new SlangException("release failed", t);
        }
    }

    @Override
    public void close() {
        if (!isNull()) {
            try {
                release();
            } catch (Throwable t) {
                // Ignore release errors during cleanup
            }
            ptr = MemorySegment.NULL;
        }
    }
}

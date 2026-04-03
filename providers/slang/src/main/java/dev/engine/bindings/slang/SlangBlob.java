package dev.engine.bindings.slang;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * Wraps an ISlangBlob (IBlob) COM interface pointer.
 *
 * <p>ISlangBlob extends ISlangUnknown:
 * <ul>
 *   <li>[0] queryInterface</li>
 *   <li>[1] addRef</li>
 *   <li>[2] release</li>
 *   <li>[3] getBufferPointer() -> void*</li>
 *   <li>[4] getBufferSize() -> size_t</li>
 * </ul>
 */
public class SlangBlob implements AutoCloseable {

    private final ComPtr com;

    public SlangBlob(ComPtr com) {
        this.com = com;
    }

    public SlangBlob(MemorySegment ptr) {
        this(new ComPtr(ptr));
    }

    public boolean isNull() {
        return com.isNull();
    }

    /**
     * Returns the raw data pointer from getBufferPointer().
     */
    public MemorySegment bufferPointer() {
        try {
            var handle = com.methodHandle(3, FunctionDescriptor.of(
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            return (MemorySegment) handle.invoke(com.ptr());
        } catch (Throwable t) {
            throw new SlangException("getBufferPointer failed", t);
        }
    }

    /**
     * Returns the buffer size from getBufferSize().
     */
    public long bufferSize() {
        try {
            var handle = com.methodHandle(4, FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            return (long) handle.invoke(com.ptr());
        } catch (Throwable t) {
            throw new SlangException("getBufferSize failed", t);
        }
    }

    /**
     * Reads the blob contents as a byte array.
     */
    public byte[] data() {
        var ptr = bufferPointer();
        var size = bufferSize();
        if (ptr.equals(MemorySegment.NULL) || size <= 0) return new byte[0];
        return ptr.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
    }

    /**
     * Reads the blob contents as a UTF-8 string.
     */
    public String string() {
        var bytes = data();
        // Strip trailing null bytes
        int len = bytes.length;
        while (len > 0 && bytes[len - 1] == 0) len--;
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        com.close();
    }
}

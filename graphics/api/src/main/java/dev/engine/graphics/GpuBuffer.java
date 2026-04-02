package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.shader.SlangStructGenerator;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;

/**
 * A typed GPU buffer backed by a Java record type.
 *
 * <p>Combines a GPU buffer handle with a {@link StructLayout} for automatic
 * serialization, and a {@link SlangStructGenerator} for shader code generation.
 * Single source of truth: the record defines CPU layout, GPU layout, and shader struct.
 *
 * @param <T> the record type
 */
public class GpuBuffer<T> {

    private final RenderDevice device;
    private final Handle<BufferResource> handle;
    private final Class<T> type;
    private final StructLayout layout;
    private final int capacity;

    private GpuBuffer(RenderDevice device, Handle<BufferResource> handle, Class<T> type, StructLayout layout, int capacity) {
        this.device = device;
        this.handle = handle;
        this.type = type;
        this.layout = layout;
        this.capacity = capacity;
    }

    /**
     * Creates a single-element GPU buffer from a record type.
     */
    public static <T> GpuBuffer<T> create(RenderDevice device, Class<T> type, BufferUsage usage, AccessPattern access) {
        var layout = StructLayout.of(type);
        var handle = device.createBuffer(new BufferDescriptor(layout.size(), usage, access));
        return new GpuBuffer<>(device, handle, type, layout, 1);
    }

    /**
     * Creates an array GPU buffer that can hold multiple elements.
     */
    public static <T> GpuBuffer<T> createArray(RenderDevice device, Class<T> type, int capacity, BufferUsage usage, AccessPattern access) {
        var layout = StructLayout.of(type);
        long size = (long) layout.size() * capacity;
        var handle = device.createBuffer(new BufferDescriptor(size, usage, access));
        return new GpuBuffer<>(device, handle, type, layout, capacity);
    }

    /** Writes a single record to the buffer (for single-element buffers). */
    public void write(T value) {
        write(0, value);
    }

    /** Writes a record at the given index (for array buffers). */
    public void write(int index, T value) {
        long offset = (long) index * layout.size();
        try (var writer = device.writeBuffer(handle, offset, layout.size())) {
            layout.write(writer.segment(), 0, value);
        }
    }

    /** GPU buffer handle for binding. */
    public Handle<BufferResource> handle() { return handle; }

    /** The record type this buffer holds. */
    public Class<T> type() { return type; }

    /** The struct layout used for serialization. */
    public StructLayout layout() { return layout; }

    /** Maximum number of elements. */
    public int capacity() { return capacity; }

    /** Size of a single element in bytes. */
    public int elementSize() { return layout.size(); }

    /** Total buffer size in bytes. */
    public long totalSize() { return (long) layout.size() * capacity; }

    // --- Slang code generation ---

    /** Generates the Slang struct definition for this buffer's type. */
    public String slangStructSource() {
        return SlangStructGenerator.generate(type);
    }

    /** Generates a Slang cbuffer declaration for this buffer. */
    public String slangCbuffer(String name, int binding) {
        return SlangStructGenerator.generateCbuffer(name, type, binding);
    }

    /** Generates Slang source for this type and all dependencies. */
    public String slangFullSource() {
        return SlangStructGenerator.generateWithDependencies(type);
    }

    /** Destroys the GPU buffer. */
    public void destroy() {
        device.destroyBuffer(handle);
    }
}

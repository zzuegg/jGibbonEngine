package dev.engine.core.layout;

import dev.engine.core.gpu.BufferWriter;
import dev.engine.core.gpu.GpuMemory;
import dev.engine.core.math.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StructLayout {

    public record Field(String name, Class<?> type, int offset, int size, int alignment) {}

    private static final Map<String, StructLayout> CACHE = new java.util.HashMap<>();

    private final List<Field> fields;
    private final int size;
    private final List<FieldWriter> writers;
    private final Class<?> recordType;
    private final LayoutMode mode;

    private StructLayout(Class<?> recordType, LayoutMode mode, List<Field> fields, int size, List<FieldWriter> writers) {
        this.recordType = recordType;
        this.mode = mode;
        this.fields = Collections.unmodifiableList(fields);
        this.size = size;
        this.writers = writers;
    }

    /** Packed layout (vertex buffers, CPU-side). */
    public static synchronized StructLayout of(Class<?> recordType) {
        return of(recordType, LayoutMode.PACKED);
    }

    /** Layout with specific mode (PACKED, STD140, STD430). */
    public static synchronized StructLayout of(Class<?> recordType, LayoutMode mode) {
        var key = recordType.getName() + ":" + mode.name();
        var cached = CACHE.get(key);
        if (cached != null) return cached;
        var layout = build(recordType, mode);
        CACHE.put(key, layout);
        return layout;
    }

    public List<Field> fields() { return fields; }
    public int size() { return size; }
    public LayoutMode mode() { return mode; }

    public void write(GpuMemory memory, long offset, Object record) {
        for (var writer : writers) {
            writer.write(memory, offset, record);
        }
    }

    // --- Build ---

    private static StructLayout build(Class<?> type, LayoutMode mode) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }

        var components = type.getRecordComponents();
        var fields = new ArrayList<Field>();
        var writers = new ArrayList<FieldWriter>();
        int currentOffset = 0;
        int maxAlignment = 1;

        for (var comp : components) {
            var compType = comp.getType();
            MethodHandle accessor;
            try {
                var method = comp.getAccessor();
                method.setAccessible(true);
                accessor = MethodHandles.lookup().unreflect(method);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access component " + comp.getName(), e);
            }

            if (compType.isRecord() && !isKnownType(compType)) {
                // Nested record — expand recursively
                var nested = StructLayout.of(compType, mode);
                int nestedAlign = nested.mode == LayoutMode.PACKED ? 1 : 16; // struct alignment in std140/430
                if (mode != LayoutMode.PACKED) {
                    currentOffset = align(currentOffset, nestedAlign);
                }
                int nestedBaseOffset = currentOffset;
                var outerAccessor = accessor;
                for (var nestedWriter : nested.writers) {
                    var capturedWriter = nestedWriter;
                    var capturedOffset = nestedBaseOffset;
                    writers.add((mem, off, rec) -> {
                        try {
                            var nestedRec = outerAccessor.invoke(rec);
                            capturedWriter.write(mem, off + capturedOffset, nestedRec);
                        } catch (Throwable t) { throw new RuntimeException(t); }
                    });
                }
                for (var nestedField : nested.fields) {
                    fields.add(new Field(comp.getName() + "." + nestedField.name,
                            nestedField.type, currentOffset + nestedField.offset, nestedField.size, nestedField.alignment));
                }
                currentOffset += nested.size;
                maxAlignment = Math.max(maxAlignment, nestedAlign);
            } else {
                int fieldSize = sizeOf(compType);
                int fieldAlign = alignmentOf(compType, mode);
                currentOffset = align(currentOffset, fieldAlign);
                int fieldOffset = currentOffset;
                maxAlignment = Math.max(maxAlignment, fieldAlign);

                fields.add(new Field(comp.getName(), compType, fieldOffset, fieldSize, fieldAlign));
                writers.add(createPrimitiveWriter(accessor, compType, fieldOffset));
                currentOffset += fieldSize;
            }
        }

        // Round up total size to largest alignment
        if (mode != LayoutMode.PACKED) {
            currentOffset = align(currentOffset, maxAlignment);
        }

        return new StructLayout(type, mode, fields, currentOffset, writers);
    }

    // --- Alignment rules ---

    private static int alignmentOf(Class<?> type, LayoutMode mode) {
        if (mode == LayoutMode.PACKED) return 1; // no alignment in packed mode

        // std140 / std430 alignment rules
        if (type == float.class || type == Float.class) return 4;
        if (type == int.class || type == Integer.class) return 4;
        if (type == boolean.class || type == Boolean.class) return 4;
        if (type == Vec2.class) return 8;
        if (type == Vec3.class) return 16; // vec3 aligns to 16 in both std140 and std430
        if (type == Vec4.class) return 16;
        if (type == Mat4.class) return 16;
        if (type == double.class || type == Double.class) return 8;
        if (type == long.class || type == Long.class) return 8;
        if (type == short.class || type == Short.class) return 2;
        if (type == byte.class || type == Byte.class) return 1;
        return 4; // default
    }

    private static boolean isKnownType(Class<?> type) {
        return type == Vec2.class || type == Vec3.class || type == Vec4.class || type == Mat4.class;
    }

    private static int align(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }

    // --- Size ---

    private static int sizeOf(Class<?> type) {
        if (type == float.class || type == Float.class) return 4;
        if (type == int.class || type == Integer.class) return 4;
        if (type == boolean.class || type == Boolean.class) return 4;
        if (type == Vec2.class) return 8;
        if (type == Vec3.class) return 12;
        if (type == Vec4.class) return 16;
        if (type == Mat4.class) return 64;
        if (type == double.class || type == Double.class) return 8;
        if (type == long.class || type == Long.class) return 8;
        if (type == short.class || type == Short.class) return 2;
        if (type == byte.class || type == Byte.class) return 1;
        throw new IllegalArgumentException("Unsupported: " + type);
    }

    // --- Writers ---

    private static FieldWriter createPrimitiveWriter(MethodHandle accessor, Class<?> type, int offset) {
        // Delegate to BufferWriter for types it supports
        if (BufferWriter.supports(type)) {
            return (mem, base, rec) -> {
                try {
                    BufferWriter.write(mem, base + offset, accessor.invoke(rec));
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        }
        // Fallback for types BufferWriter doesn't handle (double, long, short, byte)
        if (type == double.class || type == Double.class) {
            return (mem, base, rec) -> { try { mem.putDouble(base + offset, (double) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        } else if (type == long.class || type == Long.class) {
            return (mem, base, rec) -> { try { mem.putLong(base + offset, (long) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        } else if (type == short.class || type == Short.class) {
            return (mem, base, rec) -> { try { mem.putShort(base + offset, (short) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        } else if (type == byte.class || type == Byte.class) {
            return (mem, base, rec) -> { try { mem.putByte(base + offset, (byte) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        }
        throw new IllegalArgumentException("Unsupported: " + type);
    }

    @FunctionalInterface
    private interface FieldWriter {
        void write(GpuMemory memory, long baseOffset, Object record);
    }
}

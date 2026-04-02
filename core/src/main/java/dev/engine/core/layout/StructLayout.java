package dev.engine.core.layout;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StructLayout {

    public record Field(String name, Class<?> type, int offset, int size) {}

    private static final Map<Class<?>, StructLayout> CACHE = new java.util.HashMap<>();

    private final List<Field> fields;
    private final int size;
    private final List<FieldWriter> writers;
    private final Class<?> recordType;

    private StructLayout(Class<?> recordType, List<Field> fields, int size, List<FieldWriter> writers) {
        this.recordType = recordType;
        this.fields = Collections.unmodifiableList(fields);
        this.size = size;
        this.writers = writers;
    }

    public static synchronized StructLayout of(Class<?> recordType) {
        var cached = CACHE.get(recordType);
        if (cached != null) return cached;
        var layout = build(recordType);
        CACHE.put(recordType, layout);
        return layout;
    }

    public List<Field> fields() { return fields; }
    public int size() { return size; }

    public void write(MemorySegment segment, long offset, Object record) {
        for (var writer : writers) {
            writer.write(segment, offset, record);
        }
    }

    private static StructLayout build(Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }

        var components = type.getRecordComponents();
        var fields = new ArrayList<Field>();
        var writers = new ArrayList<FieldWriter>();
        int currentOffset = 0;

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

            if (compType.isRecord()) {
                // Nested record — expand recursively
                var nested = StructLayout.of(compType);
                int nestedBaseOffset = currentOffset;
                var outerAccessor = accessor;
                for (var nestedWriter : nested.writers) {
                    var capturedWriter = nestedWriter;
                    var capturedOffset = nestedBaseOffset;
                    writers.add((seg, off, rec) -> {
                        try {
                            var nestedRec = outerAccessor.invoke(rec);
                            capturedWriter.write(seg, off + capturedOffset, nestedRec);
                        } catch (Throwable t) {
                            throw new RuntimeException(t);
                        }
                    });
                }
                for (var nestedField : nested.fields) {
                    fields.add(new Field(
                            comp.getName() + "." + nestedField.name,
                            nestedField.type,
                            currentOffset + nestedField.offset,
                            nestedField.size
                    ));
                }
                currentOffset += nested.size;
            } else {
                int fieldSize = sizeOf(compType);
                int fieldOffset = currentOffset;
                fields.add(new Field(comp.getName(), compType, fieldOffset, fieldSize));
                writers.add(createPrimitiveWriter(accessor, compType, fieldOffset));
                currentOffset += fieldSize;
            }
        }

        return new StructLayout(type, fields, currentOffset, writers);
    }

    private static FieldWriter createPrimitiveWriter(MethodHandle accessor, Class<?> type, int offset) {
        if (type == float.class || type == Float.class) {
            return (seg, baseOffset, rec) -> {
                try {
                    float val = (float) accessor.invoke(rec);
                    seg.set(ValueLayout.JAVA_FLOAT, baseOffset + offset, val);
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        } else if (type == int.class || type == Integer.class) {
            return (seg, baseOffset, rec) -> {
                try {
                    int val = (int) accessor.invoke(rec);
                    seg.set(ValueLayout.JAVA_INT, baseOffset + offset, val);
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        } else if (type == double.class || type == Double.class) {
            return (seg, baseOffset, rec) -> {
                try {
                    double val = (double) accessor.invoke(rec);
                    seg.set(ValueLayout.JAVA_DOUBLE, baseOffset + offset, val);
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        } else if (type == long.class || type == Long.class) {
            return (seg, baseOffset, rec) -> {
                try {
                    long val = (long) accessor.invoke(rec);
                    seg.set(ValueLayout.JAVA_LONG, baseOffset + offset, val);
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        } else if (type == short.class || type == Short.class) {
            return (seg, baseOffset, rec) -> {
                try {
                    short val = (short) accessor.invoke(rec);
                    seg.set(ValueLayout.JAVA_SHORT, baseOffset + offset, val);
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        } else if (type == byte.class || type == Byte.class) {
            return (seg, baseOffset, rec) -> {
                try {
                    byte val = (byte) accessor.invoke(rec);
                    seg.set(ValueLayout.JAVA_BYTE, baseOffset + offset, val);
                } catch (Throwable t) { throw new RuntimeException(t); }
            };
        }
        throw new IllegalArgumentException("Unsupported field type: " + type);
    }

    private static int sizeOf(Class<?> type) {
        if (type == float.class || type == Float.class) return Float.BYTES;
        if (type == int.class || type == Integer.class) return Integer.BYTES;
        if (type == double.class || type == Double.class) return Double.BYTES;
        if (type == long.class || type == Long.class) return Long.BYTES;
        if (type == short.class || type == Short.class) return Short.BYTES;
        if (type == byte.class || type == Byte.class) return Byte.BYTES;
        throw new IllegalArgumentException("Unsupported field type: " + type);
    }

    @FunctionalInterface
    private interface FieldWriter {
        void write(MemorySegment segment, long baseOffset, Object record);
    }
}

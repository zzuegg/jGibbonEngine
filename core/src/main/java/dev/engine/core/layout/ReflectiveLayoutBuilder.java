package dev.engine.core.layout;

import dev.engine.core.gpu.BufferWriter;
import dev.engine.core.memory.NativeMemory;
import dev.engine.core.math.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds StructLayouts via reflection at runtime.
 * This class uses Class.isRecord() and Class.getRecordComponents() which
 * are NOT available on TeaVM. It is loaded dynamically by StructLayout.of()
 * only on platforms that support reflection.
 *
 * <p>On TeaVM, this class is never loaded because StructLayout.of() checks
 * for generated _NativeStruct classes first, and the reflection fallback uses
 * Class.forName() to load this class — which fails on TeaVM since this
 * class is not on the web classpath.
 */
public final class ReflectiveLayoutBuilder {

    private ReflectiveLayoutBuilder() {}

    public static StructLayout build(Class<?> type, LayoutMode mode) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type.getName() + " is not a record");
        }

        var components = type.getRecordComponents();
        var fields = new ArrayList<StructLayout.Field>();
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
                var nested = StructLayout.of(compType, mode);
                int nestedAlign = mode == LayoutMode.PACKED ? 1 : 16;
                if (mode != LayoutMode.PACKED) {
                    currentOffset = align(currentOffset, nestedAlign);
                }
                int nestedBaseOffset = currentOffset;
                var outerAccessor = accessor;
                for (var nestedField : nested.fields()) {
                    fields.add(new StructLayout.Field(comp.getName() + "." + nestedField.name(),
                            nestedField.type(), currentOffset + nestedField.offset(), nestedField.size(), nestedField.alignment()));
                }
                final int capturedOffset = nestedBaseOffset;
                final var capturedNested = nested;
                writers.add((mem, off, rec) -> {
                    try {
                        var nestedRec = outerAccessor.invoke(rec);
                        capturedNested.write(mem, off + capturedOffset, nestedRec);
                    } catch (Throwable t) { throw new RuntimeException(t); }
                });
                currentOffset += nested.size();
                maxAlignment = Math.max(maxAlignment, nestedAlign);
            } else {
                int fieldSize = sizeOf(compType);
                int fieldAlign = alignmentOf(compType, mode);
                currentOffset = align(currentOffset, fieldAlign);
                int fieldOffset = currentOffset;
                maxAlignment = Math.max(maxAlignment, fieldAlign);

                fields.add(new StructLayout.Field(comp.getName(), compType, fieldOffset, fieldSize, fieldAlign));
                writers.add(createPrimitiveWriter(accessor, compType, fieldOffset));
                currentOffset += fieldSize;
            }
        }

        if (mode != LayoutMode.PACKED) {
            currentOffset = align(currentOffset, maxAlignment);
        }

        int finalSize = currentOffset;
        var finalWriters = List.copyOf(writers);
        return StructLayout.manual(type, mode, fields, finalSize,
                (mem, off, record) -> {
                    for (var w : finalWriters) w.write(mem, off, record);
                });
    }

    private static int alignmentOf(Class<?> type, LayoutMode mode) {
        if (mode == LayoutMode.PACKED) return 1;
        if (type == float.class || type == Float.class) return 4;
        if (type == int.class || type == Integer.class) return 4;
        if (type == boolean.class || type == Boolean.class) return 4;
        if (type == Vec2.class) return 8;
        if (type == Vec3.class) return 16;
        if (type == Vec4.class) return 16;
        if (type == Mat4.class) return 16;
        if (type == double.class || type == Double.class) return 8;
        if (type == long.class || type == Long.class) return 8;
        if (type == short.class || type == Short.class) return 2;
        if (type == byte.class || type == Byte.class) return 1;
        return 4;
    }

    private static boolean isKnownType(Class<?> type) {
        return type == Vec2.class || type == Vec3.class || type == Vec4.class || type == Mat4.class;
    }

    private static int align(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }

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

    private static FieldWriter createPrimitiveWriter(MethodHandle accessor, Class<?> type, int offset) {
        if (BufferWriter.supports(type)) {
            return (mem, base, rec) -> {
                try { BufferWriter.write(mem, base + offset, accessor.invoke(rec)); }
                catch (Throwable t) { throw new RuntimeException(t); }
            };
        }
        if (type == double.class || type == Double.class)
            return (mem, base, rec) -> { try { mem.putDouble(base + offset, (double) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        if (type == long.class || type == Long.class)
            return (mem, base, rec) -> { try { mem.putLong(base + offset, (long) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        if (type == short.class || type == Short.class)
            return (mem, base, rec) -> { try { mem.putShort(base + offset, (short) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        if (type == byte.class || type == Byte.class)
            return (mem, base, rec) -> { try { mem.putByte(base + offset, (byte) accessor.invoke(rec)); } catch (Throwable t) { throw new RuntimeException(t); } };
        throw new IllegalArgumentException("Unsupported: " + type);
    }

    @FunctionalInterface
    private interface FieldWriter {
        void write(NativeMemory memory, long baseOffset, Object record);
    }
}

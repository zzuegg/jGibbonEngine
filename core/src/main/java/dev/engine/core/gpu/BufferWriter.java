package dev.engine.core.gpu;

import dev.engine.core.memory.NativeMemory;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;

import java.util.Map;

/**
 * Writes typed values to a {@link NativeMemory} for GPU buffer upload.
 * Works with any buffer: UBOs, SSBOs, mapped streaming buffers, staging buffers.
 *
 * <p>Matrices are written in column-major order (GPU convention).
 * Booleans are written as 4-byte ints (GLSL bool layout).
 * Texture handles are written as uint64 (bindless).
 */
public final class BufferWriter {

    public static final int TEXTURE_HANDLE_SIZE = 8;

    private static final Map<Class<?>, Integer> SIZES = Map.ofEntries(
            Map.entry(float.class, 4), Map.entry(Float.class, 4),
            Map.entry(int.class, 4), Map.entry(Integer.class, 4),
            Map.entry(boolean.class, 4), Map.entry(Boolean.class, 4),
            Map.entry(Vec2.class, 8),
            Map.entry(Vec3.class, 12),
            Map.entry(Vec4.class, 16),
            Map.entry(Mat4.class, 64)
    );

    private BufferWriter() {}

    // --- Typed writes ---

    public static void write(NativeMemory mem, long offset, float value) {
        mem.putFloat(offset, value);
    }

    public static void write(NativeMemory mem, long offset, int value) {
        mem.putInt(offset, value);
    }

    public static void write(NativeMemory mem, long offset, boolean value) {
        mem.putInt(offset, value ? 1 : 0);
    }

    public static void write(NativeMemory mem, long offset, Vec2 v) {
        mem.putFloat(offset, v.x());
        mem.putFloat(offset + 4, v.y());
    }

    public static void write(NativeMemory mem, long offset, Vec3 v) {
        mem.putFloat(offset, v.x());
        mem.putFloat(offset + 4, v.y());
        mem.putFloat(offset + 8, v.z());
    }

    public static void write(NativeMemory mem, long offset, Vec4 v) {
        mem.putFloat(offset, v.x());
        mem.putFloat(offset + 4, v.y());
        mem.putFloat(offset + 8, v.z());
        mem.putFloat(offset + 12, v.w());
    }

    public static void write(NativeMemory mem, long offset, Mat4 m) {
        m.writeGpu(mem, offset);
    }

    public static void writeTextureHandle(NativeMemory mem, long offset, long handle) {
        mem.putLong(offset, handle);
    }

    // --- Dynamic dispatch by Object type ---

    public static void write(NativeMemory mem, long offset, Object value) {
        switch (value) {
            case Float f -> write(mem, offset, f.floatValue());
            case Integer i -> write(mem, offset, i.intValue());
            case Boolean b -> write(mem, offset, b.booleanValue());
            case Vec2 v -> write(mem, offset, v);
            case Vec3 v -> write(mem, offset, v);
            case Vec4 v -> write(mem, offset, v);
            case Mat4 m -> write(mem, offset, m);
            default -> throw new IllegalArgumentException("Unsupported type: " + value.getClass().getName());
        }
    }

    // --- Size queries ---

    /** Returns the GPU byte size for a given type. */
    public static int sizeOf(Class<?> type) {
        var size = SIZES.get(type);
        if (size == null) throw new IllegalArgumentException("Unsupported type: " + type.getName());
        return size;
    }

    /** Returns true if the type is supported by BufferWriter. */
    public static boolean supports(Class<?> type) {
        return SIZES.containsKey(type);
    }
}

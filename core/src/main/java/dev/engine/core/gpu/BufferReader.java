package dev.engine.core.gpu;

import dev.engine.core.memory.NativeMemory;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;

/**
 * Reads typed values from a {@link NativeMemory} (GPU buffer readback).
 * Works with any buffer: UBOs, SSBOs, mapped buffers, staging buffers.
 *
 * <p>Matrices are read from column-major layout (GPU convention)
 * and returned as row-major Mat4.
 */
public final class BufferReader {

    private BufferReader() {}

    public static float readFloat(NativeMemory mem, long offset) {
        return mem.getFloat(offset);
    }

    public static int readInt(NativeMemory mem, long offset) {
        return mem.getInt(offset);
    }

    public static boolean readBoolean(NativeMemory mem, long offset) {
        return mem.getInt(offset) != 0;
    }

    public static Vec2 readVec2(NativeMemory mem, long offset) {
        return new Vec2(
                mem.getFloat(offset),
                mem.getFloat(offset + 4));
    }

    public static Vec3 readVec3(NativeMemory mem, long offset) {
        return new Vec3(
                mem.getFloat(offset),
                mem.getFloat(offset + 4),
                mem.getFloat(offset + 8));
    }

    public static Vec4 readVec4(NativeMemory mem, long offset) {
        return new Vec4(
                mem.getFloat(offset),
                mem.getFloat(offset + 4),
                mem.getFloat(offset + 8),
                mem.getFloat(offset + 12));
    }

    /**
     * Reads a Mat4 from column-major GPU layout back into row-major Mat4.
     */
    public static Mat4 readMat4(NativeMemory mem, long offset) {
        // Column-major: col0(m00,m10,m20,m30), col1(m01,m11,m21,m31), ...
        float c00 = mem.getFloat(offset);
        float c01 = mem.getFloat(offset + 4);
        float c02 = mem.getFloat(offset + 8);
        float c03 = mem.getFloat(offset + 12);
        float c10 = mem.getFloat(offset + 16);
        float c11 = mem.getFloat(offset + 20);
        float c12 = mem.getFloat(offset + 24);
        float c13 = mem.getFloat(offset + 28);
        float c20 = mem.getFloat(offset + 32);
        float c21 = mem.getFloat(offset + 36);
        float c22 = mem.getFloat(offset + 40);
        float c23 = mem.getFloat(offset + 44);
        float c30 = mem.getFloat(offset + 48);
        float c31 = mem.getFloat(offset + 52);
        float c32 = mem.getFloat(offset + 56);
        float c33 = mem.getFloat(offset + 60);
        // Transpose back to row-major
        return new Mat4(
                c00, c10, c20, c30,
                c01, c11, c21, c31,
                c02, c12, c22, c32,
                c03, c13, c23, c33);
    }

    public static long readTextureHandle(NativeMemory mem, long offset) {
        return mem.getLong(offset);
    }
}

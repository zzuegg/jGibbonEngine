package dev.engine.core.gpu;

import dev.engine.core.memory.NativeMemory;
import dev.engine.core.memory.SegmentNativeMemory;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

class BufferWriterTest {

    private Arena arena;
    private NativeMemory memory;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        memory = new SegmentNativeMemory(arena.allocate(256, 16));
    }

    @Nested
    class Primitives {
        @Test void writeFloat() {
            BufferWriter.write(memory, 0, 3.14f);
            assertEquals(3.14f, memory.getFloat(0));
            assertEquals(4, BufferWriter.sizeOf(float.class));
        }

        @Test void writeInt() {
            BufferWriter.write(memory, 0, 42);
            assertEquals(42, memory.getInt(0));
            assertEquals(4, BufferWriter.sizeOf(int.class));
        }

        @Test void writeBoolean() {
            BufferWriter.write(memory, 0, true);
            assertEquals(1, memory.getInt(0));
            BufferWriter.write(memory, 0, false);
            assertEquals(0, memory.getInt(0));
            assertEquals(4, BufferWriter.sizeOf(boolean.class));
        }
    }

    @Nested
    class Vectors {
        @Test void writeVec2() {
            BufferWriter.write(memory, 0, new Vec2(1.5f, 2.5f));
            assertEquals(1.5f, memory.getFloat(0));
            assertEquals(2.5f, memory.getFloat(4));
            assertEquals(8, BufferWriter.sizeOf(Vec2.class));
        }

        @Test void writeVec3() {
            BufferWriter.write(memory, 0, new Vec3(1, 2, 3));
            assertEquals(1f, memory.getFloat(0));
            assertEquals(2f, memory.getFloat(4));
            assertEquals(3f, memory.getFloat(8));
            assertEquals(12, BufferWriter.sizeOf(Vec3.class));
        }

        @Test void writeVec4() {
            BufferWriter.write(memory, 0, new Vec4(1, 2, 3, 4));
            assertEquals(1f, memory.getFloat(0));
            assertEquals(2f, memory.getFloat(4));
            assertEquals(3f, memory.getFloat(8));
            assertEquals(4f, memory.getFloat(12));
            assertEquals(16, BufferWriter.sizeOf(Vec4.class));
        }
    }

    @Nested
    class Matrices {
        @Test void writeMat4ColumnMajor() {
            BufferWriter.write(memory, 0, Mat4.IDENTITY);
            assertEquals(1f, memory.getFloat(0));
            assertEquals(0f, memory.getFloat(4));
            assertEquals(64, BufferWriter.sizeOf(Mat4.class));
        }

        @Test void translationWrittenColumnMajor() {
            var m = Mat4.translation(5, 6, 7);
            BufferWriter.write(memory, 0, m);
            // Column 3 holds translation: (tx, ty, tz, 1)
            assertEquals(5f, memory.getFloat(48));
            assertEquals(6f, memory.getFloat(52));
            assertEquals(7f, memory.getFloat(56));
            assertEquals(1f, memory.getFloat(60));
        }
    }

    @Nested
    class TextureHandles {
        @Test void writeUint64Handle() {
            long handle = 0xDEADBEEFCAFEL;
            BufferWriter.writeTextureHandle(memory, 0, handle);
            assertEquals(handle, memory.getLong(0));
            assertEquals(8, BufferWriter.TEXTURE_HANDLE_SIZE);
        }
    }

    @Nested
    class TypeDispatch {
        @Test void writeByObjectType() {
            BufferWriter.write(memory, 0, (Object) 3.14f);
            assertEquals(3.14f, memory.getFloat(0));

            BufferWriter.write(memory, 0, (Object) new Vec3(1, 2, 3));
            assertEquals(1f, memory.getFloat(0));

            BufferWriter.write(memory, 0, (Object) Mat4.IDENTITY);
            assertEquals(1f, memory.getFloat(0));
        }

        @Test void sizeOfBoxedAndPrimitive() {
            assertEquals(4, BufferWriter.sizeOf(Float.class));
            assertEquals(4, BufferWriter.sizeOf(float.class));
            assertEquals(4, BufferWriter.sizeOf(Integer.class));
            assertEquals(4, BufferWriter.sizeOf(int.class));
            assertEquals(4, BufferWriter.sizeOf(Boolean.class));
            assertEquals(4, BufferWriter.sizeOf(boolean.class));
        }

        @Test void unsupportedTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> BufferWriter.write(memory, 0, (Object) "bad"));
            assertThrows(IllegalArgumentException.class,
                    () -> BufferWriter.sizeOf(String.class));
        }
    }
}

package dev.engine.core.gpu;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class BufferWriterTest {

    private Arena arena;
    private MemorySegment segment;

    @BeforeEach
    void setUp() {
        arena = Arena.ofConfined();
        segment = arena.allocate(256, 16);
    }

    @Nested
    class Primitives {
        @Test void writeFloat() {
            BufferWriter.write(segment, 0, 3.14f);
            assertEquals(3.14f, segment.get(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(4, BufferWriter.sizeOf(float.class));
        }

        @Test void writeInt() {
            BufferWriter.write(segment, 0, 42);
            assertEquals(42, segment.get(ValueLayout.JAVA_INT, 0));
            assertEquals(4, BufferWriter.sizeOf(int.class));
        }

        @Test void writeBoolean() {
            BufferWriter.write(segment, 0, true);
            assertEquals(1, segment.get(ValueLayout.JAVA_INT, 0));
            BufferWriter.write(segment, 0, false);
            assertEquals(0, segment.get(ValueLayout.JAVA_INT, 0));
            assertEquals(4, BufferWriter.sizeOf(boolean.class));
        }
    }

    @Nested
    class Vectors {
        @Test void writeVec2() {
            BufferWriter.write(segment, 0, new Vec2(1.5f, 2.5f));
            assertEquals(1.5f, segment.get(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(2.5f, segment.get(ValueLayout.JAVA_FLOAT, 4));
            assertEquals(8, BufferWriter.sizeOf(Vec2.class));
        }

        @Test void writeVec3() {
            BufferWriter.write(segment, 0, new Vec3(1, 2, 3));
            assertEquals(1f, segment.get(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(2f, segment.get(ValueLayout.JAVA_FLOAT, 4));
            assertEquals(3f, segment.get(ValueLayout.JAVA_FLOAT, 8));
            assertEquals(12, BufferWriter.sizeOf(Vec3.class));
        }

        @Test void writeVec4() {
            BufferWriter.write(segment, 0, new Vec4(1, 2, 3, 4));
            assertEquals(1f, segment.get(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(2f, segment.get(ValueLayout.JAVA_FLOAT, 4));
            assertEquals(3f, segment.get(ValueLayout.JAVA_FLOAT, 8));
            assertEquals(4f, segment.get(ValueLayout.JAVA_FLOAT, 12));
            assertEquals(16, BufferWriter.sizeOf(Vec4.class));
        }
    }

    @Nested
    class Matrices {
        @Test void writeMat4ColumnMajor() {
            BufferWriter.write(segment, 0, Mat4.IDENTITY);
            assertEquals(1f, segment.get(ValueLayout.JAVA_FLOAT, 0));
            assertEquals(0f, segment.get(ValueLayout.JAVA_FLOAT, 4));
            assertEquals(64, BufferWriter.sizeOf(Mat4.class));
        }

        @Test void translationWrittenColumnMajor() {
            var m = Mat4.translation(5, 6, 7);
            BufferWriter.write(segment, 0, m);
            // Column 3 holds translation: (tx, ty, tz, 1)
            assertEquals(5f, segment.get(ValueLayout.JAVA_FLOAT, 48));
            assertEquals(6f, segment.get(ValueLayout.JAVA_FLOAT, 52));
            assertEquals(7f, segment.get(ValueLayout.JAVA_FLOAT, 56));
            assertEquals(1f, segment.get(ValueLayout.JAVA_FLOAT, 60));
        }
    }

    @Nested
    class TextureHandles {
        @Test void writeUint64Handle() {
            long handle = 0xDEADBEEFCAFEL;
            BufferWriter.writeTextureHandle(segment, 0, handle);
            assertEquals(handle, segment.get(ValueLayout.JAVA_LONG, 0));
            assertEquals(8, BufferWriter.TEXTURE_HANDLE_SIZE);
        }
    }

    @Nested
    class TypeDispatch {
        @Test void writeByObjectType() {
            BufferWriter.write(segment, 0, (Object) 3.14f);
            assertEquals(3.14f, segment.get(ValueLayout.JAVA_FLOAT, 0));

            BufferWriter.write(segment, 0, (Object) new Vec3(1, 2, 3));
            assertEquals(1f, segment.get(ValueLayout.JAVA_FLOAT, 0));

            BufferWriter.write(segment, 0, (Object) Mat4.IDENTITY);
            assertEquals(1f, segment.get(ValueLayout.JAVA_FLOAT, 0));
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
                    () -> BufferWriter.write(segment, 0, (Object) "bad"));
            assertThrows(IllegalArgumentException.class,
                    () -> BufferWriter.sizeOf(String.class));
        }
    }

    @Nested
    class Reading {
        @Test void readFloat() {
            segment.set(ValueLayout.JAVA_FLOAT, 0, 3.14f);
            assertEquals(3.14f, BufferReader.readFloat(segment, 0));
        }

        @Test void readInt() {
            segment.set(ValueLayout.JAVA_INT, 0, 42);
            assertEquals(42, BufferReader.readInt(segment, 0));
        }

        @Test void readVec3() {
            segment.set(ValueLayout.JAVA_FLOAT, 0, 1f);
            segment.set(ValueLayout.JAVA_FLOAT, 4, 2f);
            segment.set(ValueLayout.JAVA_FLOAT, 8, 3f);
            assertEquals(new Vec3(1, 2, 3), BufferReader.readVec3(segment, 0));
        }

        @Test void readMat4ColumnMajor() {
            BufferWriter.write(segment, 0, Mat4.translation(5, 6, 7));
            var m = BufferReader.readMat4(segment, 0);
            assertEquals(5f, m.m03()); // translation x
            assertEquals(6f, m.m13()); // translation y
            assertEquals(7f, m.m23()); // translation z
        }

        @Test void readTextureHandle() {
            long handle = 0xDEADBEEFCAFEL;
            BufferWriter.writeTextureHandle(segment, 0, handle);
            assertEquals(handle, BufferReader.readTextureHandle(segment, 0));
        }
    }
}

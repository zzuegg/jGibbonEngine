package dev.engine.core.layout;

import dev.engine.core.memory.NativeMemory;
import dev.engine.core.memory.SegmentNativeMemory;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

class StructLayoutTest {

    record SimpleVertex(float x, float y, float z) {}
    record ColorVertex(float x, float y, float z, float r, float g, float b, float a) {}
    record MixedVertex(float x, float y, float z, int id) {}

    private NativeMemory allocate(long size) {
        return new SegmentNativeMemory(Arena.ofAuto().allocate(size, 16));
    }

    @Nested
    class LayoutDerivation {
        @Test void simpleRecordHasCorrectSize() {
            var layout = StructLayout.of(SimpleVertex.class);
            assertEquals(3 * Float.BYTES, layout.size());
        }

        @Test void simpleRecordHasCorrectFieldCount() {
            var layout = StructLayout.of(SimpleVertex.class);
            assertEquals(3, layout.fields().size());
        }

        @Test void fieldNamesMatchRecordComponents() {
            var layout = StructLayout.of(SimpleVertex.class);
            var names = layout.fields().stream().map(StructLayout.Field::name).toList();
            assertEquals(java.util.List.of("x", "y", "z"), names);
        }

        @Test void mixedTypesHaveCorrectSize() {
            var layout = StructLayout.of(MixedVertex.class);
            // 3 floats + 1 int = 16 bytes
            assertEquals(3 * Float.BYTES + Integer.BYTES, layout.size());
        }

        @Test void cachedAcrossMultipleCalls() {
            var a = StructLayout.of(SimpleVertex.class);
            var b = StructLayout.of(SimpleVertex.class);
            assertSame(a, b);
        }
    }

    @Nested
    class Writing {
        @Test void writeAndReadSimpleVertex() {
            var layout = StructLayout.of(SimpleVertex.class);
            var memory = allocate(layout.size());
            layout.write(memory, 0, new SimpleVertex(1f, 2f, 3f));
            assertEquals(1f, memory.getFloat(0));
            assertEquals(2f, memory.getFloat(4));
            assertEquals(3f, memory.getFloat(8));
        }

        @Test void writeAtOffset() {
            var layout = StructLayout.of(SimpleVertex.class);
            var memory = allocate(layout.size() * 2);
            layout.write(memory, 0, new SimpleVertex(1f, 2f, 3f));
            layout.write(memory, layout.size(), new SimpleVertex(4f, 5f, 6f));
            assertEquals(4f, memory.getFloat(12));
            assertEquals(5f, memory.getFloat(16));
            assertEquals(6f, memory.getFloat(20));
        }

        @Test void writeMixedTypes() {
            var layout = StructLayout.of(MixedVertex.class);
            var memory = allocate(layout.size());
            layout.write(memory, 0, new MixedVertex(1f, 2f, 3f, 42));
            assertEquals(1f, memory.getFloat(0));
            // int at byte offset 12
            assertEquals(42, memory.getInt(12));
        }
    }

    @Nested
    class NestedRecords {
        record VertexWithNormal(Vec3 position, Vec3 normal) {}

        @Test void nestedVec3ExpandsToFloats() {
            var layout = StructLayout.of(VertexWithNormal.class);
            // Vec3 has 3 floats, 2 Vec3s = 24 bytes
            assertEquals(6 * Float.BYTES, layout.size());
        }

        @Test void writeNestedRecord() {
            var layout = StructLayout.of(VertexWithNormal.class);
            var memory = allocate(layout.size());
            layout.write(memory, 0, new VertexWithNormal(
                    new Vec3(1f, 2f, 3f), new Vec3(0f, 1f, 0f)));
            assertEquals(1f, memory.getFloat(0));
            assertEquals(2f, memory.getFloat(4));
            assertEquals(3f, memory.getFloat(8));
            assertEquals(0f, memory.getFloat(12));
            assertEquals(1f, memory.getFloat(16));
            assertEquals(0f, memory.getFloat(20));
        }
    }
}

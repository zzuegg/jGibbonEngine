package dev.engine.graphics.opengl;

import dev.engine.windowing.glfw.GlfwWindowToolkit;

import dev.engine.core.layout.StructLayout;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL45;

import java.lang.foreign.ValueLayout;

import static org.junit.jupiter.api.Assertions.*;

class GlBufferUploadTest {

    record Vertex(float x, float y, float z) {}

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice(window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Nested
    class RawDataUpload {
        @Test
        void writeFloatsToBuffer() {
            var desc = new BufferDescriptor(4 * Float.BYTES, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);

            try (var writer = device.writeBuffer(handle)) {
                var seg = writer.segment();
                seg.setAtIndex(ValueLayout.JAVA_FLOAT, 0, 1.0f);
                seg.setAtIndex(ValueLayout.JAVA_FLOAT, 1, 2.0f);
                seg.setAtIndex(ValueLayout.JAVA_FLOAT, 2, 3.0f);
                seg.setAtIndex(ValueLayout.JAVA_FLOAT, 3, 4.0f);
            }

            // Readback via GL to verify data reached the GPU
            float[] readback = new float[4];
            int glName = device.getGlBufferName(handle);
            GL45.glGetNamedBufferSubData(glName, 0, readback);
            assertEquals(1.0f, readback[0]);
            assertEquals(2.0f, readback[1]);
            assertEquals(3.0f, readback[2]);
            assertEquals(4.0f, readback[3]);

            device.destroyBuffer(handle);
        }
    }

    @Nested
    class StructLayoutUpload {
        @Test
        void writeVerticesViaStructLayout() {
            var layout = StructLayout.of(Vertex.class);
            int vertexCount = 3;
            long bufferSize = (long) layout.size() * vertexCount;
            var desc = new BufferDescriptor(bufferSize, BufferUsage.VERTEX, AccessPattern.STATIC);
            var handle = device.createBuffer(desc);

            try (var writer = device.writeBuffer(handle)) {
                var seg = writer.segment();
                layout.write(seg, 0, new Vertex(0f, 0f, 0f));
                layout.write(seg, layout.size(), new Vertex(1f, 0f, 0f));
                layout.write(seg, layout.size() * 2L, new Vertex(0.5f, 1f, 0f));
            }

            // Readback
            float[] readback = new float[9];
            GL45.glGetNamedBufferSubData(device.getGlBufferName(handle), 0, readback);
            // First vertex
            assertEquals(0f, readback[0]); assertEquals(0f, readback[1]); assertEquals(0f, readback[2]);
            // Second vertex
            assertEquals(1f, readback[3]); assertEquals(0f, readback[4]); assertEquals(0f, readback[5]);
            // Third vertex
            assertEquals(0.5f, readback[6]); assertEquals(1f, readback[7]); assertEquals(0f, readback[8]);

            device.destroyBuffer(handle);
        }
    }
}

package dev.engine.graphics;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.common.HeadlessRenderDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GpuBufferTest {

    record PBRMaterial(Vec3 albedoColor, float roughness, float metallic, Vec3 emissive) {}
    record TransformData(Mat4 mvp, Mat4 model) {}

    private RenderDevice device;

    @BeforeEach
    void setUp() {
        device = new dev.engine.graphics.common.HeadlessRenderDevice();
    }

    @Nested
    class SingleElement {
        @Test void writeAndReadType() {
            var buf = GpuBuffer.create(device, PBRMaterial.class, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
            assertNotNull(buf);
            assertNotNull(buf.handle());
            assertEquals(PBRMaterial.class, buf.type());
        }

        @Test void writeRecord() {
            var buf = GpuBuffer.create(device, PBRMaterial.class, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
            assertDoesNotThrow(() -> buf.write(new PBRMaterial(new Vec3(1, 0, 0), 0.5f, 0.2f, Vec3.ZERO)));
        }

        @Test void writeTransformData() {
            var buf = GpuBuffer.create(device, TransformData.class, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
            buf.write(new TransformData(Mat4.IDENTITY, Mat4.IDENTITY));
            // No exception = success
        }
    }

    @Nested
    class ArrayBuffer {
        @Test void writeMultipleElements() {
            var buf = GpuBuffer.createArray(device, PBRMaterial.class, 10, BufferUsage.STORAGE, AccessPattern.DYNAMIC);
            assertNotNull(buf);
            buf.write(0, new PBRMaterial(new Vec3(1, 0, 0), 0.5f, 0.2f, Vec3.ZERO));
            buf.write(5, new PBRMaterial(new Vec3(0, 1, 0), 0.8f, 0.0f, Vec3.ONE));
        }
    }

    @Nested
    class SlangIntegration {
        @Test void generatesSlangStruct() {
            var buf = GpuBuffer.create(device, PBRMaterial.class, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
            var slang = buf.slangStructSource();
            assertTrue(slang.contains("struct PBRMaterial"));
            assertTrue(slang.contains("float3 albedoColor;"));
            assertTrue(slang.contains("float roughness;"));
        }

        @Test void generatesCbufferDeclaration() {
            var buf = GpuBuffer.create(device, PBRMaterial.class, BufferUsage.UNIFORM, AccessPattern.DYNAMIC);
            var slang = buf.slangCbuffer("MaterialData", 1);
            assertTrue(slang.contains("cbuffer MaterialData"));
        }
    }
}

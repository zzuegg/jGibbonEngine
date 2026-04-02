package dev.engine.core.shader;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlangStructGeneratorTest {

    record SimpleColor(float r, float g, float b) {}
    record PointLight(Vec3 position, float intensity, Vec3 color, float radius) {}
    record PBRMaterial(Vec3 albedoColor, float roughness, float metallic, Vec3 emissive) {}
    record TransformData(Mat4 mvp, Mat4 model) {}
    record FullVertex(Vec3 position, Vec3 normal, Vec2 texCoord, Vec4 tangent) {}
    record WithInt(int id, float value) {}
    record Nested(Vec3 offset, SimpleColor tint) {}

    @org.junit.jupiter.api.Nested
    class BasicGeneration {
        @Test void simpleFloatRecord() {
            var slang = SlangStructGenerator.generate(SimpleColor.class);
            assertTrue(slang.contains("struct SimpleColor"));
            assertTrue(slang.contains("float r;"));
            assertTrue(slang.contains("float g;"));
            assertTrue(slang.contains("float b;"));
            assertTrue(slang.contains("};"));
        }

        @Test void vec3Fields() {
            var slang = SlangStructGenerator.generate(PointLight.class);
            assertTrue(slang.contains("float3 position;"));
            assertTrue(slang.contains("float intensity;"));
            assertTrue(slang.contains("float3 color;"));
            assertTrue(slang.contains("float radius;"));
        }

        @Test void mat4Fields() {
            var slang = SlangStructGenerator.generate(TransformData.class);
            assertTrue(slang.contains("float4x4 mvp;"));
            assertTrue(slang.contains("float4x4 model;"));
        }

        @Test void allVectorTypes() {
            var slang = SlangStructGenerator.generate(FullVertex.class);
            assertTrue(slang.contains("float3 position;"));
            assertTrue(slang.contains("float3 normal;"));
            assertTrue(slang.contains("float2 texCoord;"));
            assertTrue(slang.contains("float4 tangent;"));
        }

        @Test void intFields() {
            var slang = SlangStructGenerator.generate(WithInt.class);
            assertTrue(slang.contains("int id;"));
            assertTrue(slang.contains("float value;"));
        }
    }

    @org.junit.jupiter.api.Nested
    class NestedRecords {
        @Test void nestedRecordExpandsInline() {
            var slang = SlangStructGenerator.generate(Nested.class);
            // Nested records should be referenced by name
            assertTrue(slang.contains("SimpleColor tint;"));
        }

        @Test void generateDependencies() {
            var all = SlangStructGenerator.generateWithDependencies(Nested.class);
            // Should contain both Nested and SimpleColor structs
            assertTrue(all.contains("struct SimpleColor"));
            assertTrue(all.contains("struct Nested"));
            // SimpleColor should come before Nested (dependency order)
            assertTrue(all.indexOf("struct SimpleColor") < all.indexOf("struct Nested"));
        }
    }

    @org.junit.jupiter.api.Nested
    class CbufferGeneration {
        @Test void generateCbuffer() {
            var slang = SlangStructGenerator.generateCbuffer("MaterialData", PBRMaterial.class, 1);
            assertTrue(slang.contains("cbuffer MaterialData : register(b1)"));
            assertTrue(slang.contains("float3 albedoColor;"));
            assertTrue(slang.contains("float roughness;"));
        }
    }
}

package dev.engine.graphics.common;

import dev.engine.core.math.Vec3;
import dev.engine.core.shader.SlangCompiler;
import dev.engine.core.material.Material;
import dev.engine.core.material.MaterialType;
import dev.engine.graphics.common.material.MaterialCompiler;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

class MaterialShaderTest {

    @Nested
    class ShaderKeyDerivation {
        @Test void unlitMaterialKeyWithNoProperties() {
            var mat = Material.create(MaterialType.UNLIT);
            var key = MaterialCompiler.shaderKey(mat);
            assertEquals("UNLIT", key);
        }

        @Test void unlitMaterialKeyWithColor() {
            var mat = Material.create(MaterialType.UNLIT);
            mat.set(Material.COLOR, new Vec3(1, 0, 0));
            var key = MaterialCompiler.shaderKey(mat);
            assertEquals("UNLIT_color", key);
        }

        @Test void pbrMaterialKey() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ALBEDO_COLOR, new Vec3(1, 0, 0));
            mat.set(Material.ROUGHNESS, 0.5f);
            mat.set(Material.METALLIC, 0.2f);
            var key = MaterialCompiler.shaderKey(mat);
            assertTrue(key.startsWith("PBR"));
        }

        @Test void differentPropertiesProduceDifferentKeys() {
            var mat1 = Material.create(MaterialType.PBR);
            mat1.set(Material.ALBEDO_COLOR, Vec3.ONE);
            mat1.set(Material.ROUGHNESS, 0.5f);

            var mat2 = Material.create(MaterialType.PBR);
            mat2.set(Material.ALBEDO_COLOR, Vec3.ONE);
            mat2.set(Material.ROUGHNESS, 0.5f);
            mat2.set(Material.EMISSIVE, Vec3.ONE);

            assertNotEquals(MaterialCompiler.shaderKey(mat1), MaterialCompiler.shaderKey(mat2));
        }
    }

    @Nested
    class MaterialDataGeneration {
        @Test void generatesMaterialStruct() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ALBEDO_COLOR, new Vec3(1, 0, 0));
            mat.set(Material.ROUGHNESS, 0.5f);
            mat.set(Material.METALLIC, 0.2f);

            var slang = MaterialCompiler.generateMaterialStruct(mat);
            assertTrue(slang.contains("struct MaterialData"));
            assertTrue(slang.contains("float3 albedoColor;"));
            assertTrue(slang.contains("float roughness;"));
            assertTrue(slang.contains("float metallic;"));
        }

        @Test void generatesCbuffer() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ROUGHNESS, 0.5f);

            var slang = MaterialCompiler.generateMaterialCbuffer(mat, 1);
            assertTrue(slang.contains("cbuffer MaterialData : register(b1)"));
            assertTrue(slang.contains("float roughness;"));
        }
    }

    @Nested
    class MaterialDataUpload {
        @Test void serializesToBytes() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ALBEDO_COLOR, new Vec3(1, 0, 0));
            mat.set(Material.ROUGHNESS, 0.5f);
            mat.set(Material.METALLIC, 0.2f);

            var bytes = MaterialCompiler.serializeMaterialData(mat);
            assertNotNull(bytes);
            assertTrue(bytes.remaining() > 0);
            // First 3 floats should be albedo (1, 0, 0)
            assertEquals(1f, bytes.getFloat(0));
            assertEquals(0f, bytes.getFloat(4));
            assertEquals(0f, bytes.getFloat(8));
        }
    }

    @Nested
    class ShaderInjection {
        @Test void injectStructIntoShaderSource() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ALBEDO_COLOR, Vec3.ONE);
            mat.set(Material.ROUGHNESS, 0.5f);

            var source = """
                    // __MATERIAL_DATA__
                    [shader("fragment")]
                    float4 fragmentMain() : SV_Target {
                        return float4(albedoColor, 1.0);
                    }
                    """;

            var injected = MaterialCompiler.injectMaterialData(source, mat, 1);
            assertTrue(injected.contains("cbuffer MaterialData : register(b1)"));
            assertTrue(injected.contains("float3 albedoColor;"));
            assertFalse(injected.contains("__MATERIAL_DATA__"));
        }
    }
}

package dev.engine.graphics.common;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.common.material.MaterialCompiler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialShaderTest {

    @Nested
    class ShaderKeyDerivation {
        @Test void unlitMaterialKeyWithNoExtraProperties() {
            var mat = MaterialData.unlit(new Vec3(1, 0, 0));
            var key = MaterialCompiler.shaderKey(mat);
            assertTrue(key.startsWith("UNLIT"));
        }

        @Test void pbrMaterialKey() {
            var mat = MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.2f);
            var key = MaterialCompiler.shaderKey(mat);
            assertTrue(key.startsWith("PBR"));
        }

        @Test void differentPropertiesProduceDifferentKeys() {
            var mat1 = MaterialData.create("PBR")
                    .set(MaterialData.ALBEDO_COLOR, Vec3.ONE)
                    .set(MaterialData.ROUGHNESS, 0.5f);

            var mat2 = MaterialData.create("PBR")
                    .set(MaterialData.ALBEDO_COLOR, Vec3.ONE)
                    .set(MaterialData.ROUGHNESS, 0.5f)
                    .set(MaterialData.EMISSIVE, Vec3.ONE);

            assertNotEquals(MaterialCompiler.shaderKey(mat1), MaterialCompiler.shaderKey(mat2));
        }
    }

    @Nested
    class MaterialDataGeneration {
        @Test void generatesMaterialStruct() {
            var mat = MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.2f);

            var slang = MaterialCompiler.generateMaterialStruct(mat);
            assertTrue(slang.contains("struct MaterialData"));
            assertTrue(slang.contains("float3 albedoColor;"));
            assertTrue(slang.contains("float roughness;"));
            assertTrue(slang.contains("float metallic;"));
        }

        @Test void generatesCbuffer() {
            var mat = MaterialData.create("PBR")
                    .set(MaterialData.ROUGHNESS, 0.5f);

            var slang = MaterialCompiler.generateMaterialCbuffer(mat, 1);
            assertTrue(slang.contains("cbuffer MaterialData : register(b1)"));
            assertTrue(slang.contains("float roughness;"));
        }
    }

    @Nested
    class MaterialDataUpload {
        @Test void serializesToBytes() {
            var mat = MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.2f);

            var bytes = MaterialCompiler.serializeMaterialData(mat);
            assertNotNull(bytes);
            assertTrue(bytes.remaining() > 0);
        }
    }

    @Nested
    class ShaderInjection {
        @Test void injectStructIntoShaderSource() {
            var mat = MaterialData.create("PBR")
                    .set(MaterialData.ALBEDO_COLOR, Vec3.ONE)
                    .set(MaterialData.ROUGHNESS, 0.5f);

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

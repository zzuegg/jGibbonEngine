package dev.engine.core.shader;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SlangMaterialGeneratorTest {

    @Nested
    class InterfaceGeneration {
        @Test void generatesInterfaceFromKeys() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS, MaterialData.METALLIC);
            var code = SlangMaterialGenerator.generateInterface(keys);
            assertTrue(code.contains("interface IMaterialParams"));
            assertTrue(code.contains("float3 albedoColor()"));
            assertTrue(code.contains("float roughness()"));
            assertTrue(code.contains("float metallic()"));
        }

        @Test void excludesTextureKeys() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ALBEDO_MAP, MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generateInterface(keys);
            assertTrue(code.contains("albedoColor"));
            assertTrue(code.contains("roughness"));
            assertFalse(code.contains("albedoMap")); // texture excluded
        }

        @Test void emptyKeysProducesEmptyInterface() {
            var code = SlangMaterialGenerator.generateInterface(Set.of());
            assertTrue(code.contains("interface IMaterialParams"));
            // No method declarations in empty interface
            assertFalse(code.contains("float "));
        }
    }

    @Nested
    class UboImplementation {
        @Test void generatesDataStruct() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generateUboImplementation(keys);
            assertTrue(code.contains("struct MaterialParamsData"));
            assertTrue(code.contains("float3 albedoColor"));
            assertTrue(code.contains("float roughness"));
        }

        @Test void generatesCbuffer() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generateUboImplementation(keys);
            assertTrue(code.contains("cbuffer MaterialBuffer"));
            assertTrue(code.contains("MaterialParamsData materialData"));
        }

        @Test void generatesAccessors() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generateUboImplementation(keys);
            assertTrue(code.contains("struct UboMaterialParams : IMaterialParams"));
            assertTrue(code.contains("return materialData.albedoColor"));
            assertTrue(code.contains("return materialData.roughness"));
        }
    }

    @Nested
    class SsboImplementation {
        @Test void generatesStructuredBuffer() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generateSsboImplementation(keys);
            assertTrue(code.contains("StructuredBuffer<MaterialParamsData> materialArray"));
            assertTrue(code.contains("materialArray"));
        }

        @Test void generatesInstanceIdAccess() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generateSsboImplementation(keys);
            assertTrue(code.contains("struct SsboMaterialParams : IMaterialParams"));
            assertTrue(code.contains("uint instanceId"));
            assertTrue(code.contains("materialArray[instanceId].roughness"));
        }
    }

    @Nested
    class FullGeneration {
        @Test void uboMode() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS, MaterialData.METALLIC);
            var code = SlangMaterialGenerator.generate(keys, SlangMaterialGenerator.UploadMode.UBO);
            assertTrue(code.contains("interface IMaterialParams"));
            assertTrue(code.contains("cbuffer MaterialBuffer"));
            assertTrue(code.contains("UboMaterialParams : IMaterialParams"));
        }

        @Test void ssboMode() {
            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS);
            var code = SlangMaterialGenerator.generate(keys, SlangMaterialGenerator.UploadMode.SSBO);
            assertTrue(code.contains("interface IMaterialParams"));
            assertTrue(code.contains("StructuredBuffer<MaterialParamsData> materialArray"));
            assertTrue(code.contains("SsboMaterialParams : IMaterialParams"));
        }
    }

    @Nested
    class SlangCompilation {
        @Test void generatedUboCodeCompiles() {
            var compiler = SlangCompiler.find();
            assumeTrue(compiler.isAvailable(), "slangc not found");

            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS, MaterialData.METALLIC);
            var materialCode = SlangMaterialGenerator.generate(keys, SlangMaterialGenerator.UploadMode.UBO);

            var shader = materialCode + """

                struct VertexInput { float3 position : POSITION; float3 normal : NORMAL; float2 uv : TEXCOORD; };
                struct VertexOutput { float4 position : SV_Position; float3 normal; };
                cbuffer Matrices { float4x4 mvp; };

                [shader("vertex")]
                VertexOutput vertexMain(VertexInput input) {
                    VertexOutput output;
                    output.position = mul(float4(input.position, 1.0), mvp);
                    output.normal = input.normal;
                    return output;
                }

                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    UboMaterialParams material;
                    float3 color = material.albedoColor() * material.roughness();
                    return float4(color, 1.0);
                }
                """;

            var vs = compiler.compileToGlsl(shader, "vertexMain", ShaderStageType.VERTEX);
            assertTrue(vs.success(), "VS failed: " + vs.error());

            var fs = compiler.compileToGlsl(shader, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(fs.success(), "FS failed: " + fs.error());
        }

        @Test void generatedSsboCodeCompiles() {
            var compiler = SlangCompiler.find();
            assumeTrue(compiler.isAvailable(), "slangc not found");

            var keys = Set.<PropertyKey<?>>of(MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS);
            var materialCode = SlangMaterialGenerator.generate(keys, SlangMaterialGenerator.UploadMode.SSBO);

            var shader = materialCode + """

                struct VertexOutput { float4 position : SV_Position; };
                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    SsboMaterialParams material;
                    material.instanceId = 0;
                    return float4(material.albedoColor(), 1.0);
                }
                """;

            var fs = compiler.compileToGlsl(shader, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(fs.success(), "FS failed: " + fs.error());
        }
    }
}

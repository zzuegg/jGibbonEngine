package dev.engine.graphics.shader;

import dev.engine.core.material.MaterialData;
import dev.engine.graphics.texture.TextureKeys;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SlangParamsBlockTest {

    // --- From PropertyKeys (dynamic, for materials — uses generic specialization) ---

    @Nested
    class FromPropertyKeys {
        @Test void generatesInterfaceWithGetters() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(
                    MaterialData.ALBEDO_COLOR, MaterialData.ROUGHNESS));
            var code = block.generateUbo();
            assertTrue(code.contains("interface IMaterialParams"));
            assertTrue(code.contains("float3 albedoColor()"));
            assertTrue(code.contains("float roughness()"));
        }

        @Test void generatesConcreteType() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(MaterialData.ROUGHNESS));
            var code = block.generateUbo();
            assertTrue(code.contains("struct UboMaterialParams : IMaterialParams"));
            assertTrue(code.contains("cbuffer MaterialBuffer"));
        }

        @Test void withBindingAddsRegister() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(MaterialData.ROUGHNESS))
                    .withBinding(2);
            var code = block.generateUbo();
            assertTrue(code.contains("cbuffer MaterialBuffer : register(b2)"),
                    "Expected register(b2), got:\n" + code);
        }

        @Test void withoutBindingNoRegister() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(MaterialData.ROUGHNESS));
            var code = block.generateUbo();
            assertFalse(code.contains("register("),
                    "Should not contain register without withBinding");
        }

        @Test void generatesGlobalInstance() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(MaterialData.ROUGHNESS));
            var code = block.generateUbo();
            assertTrue(code.contains("static UboMaterialParams material"),
                    "Expected global 'material' instance, got:\n" + code);
        }

        @Test void generatesSsboWithInstanceId() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(MaterialData.ROUGHNESS));
            var code = block.generateSsbo();
            assertTrue(code.contains("StructuredBuffer<MaterialParamsData>"));
            assertTrue(code.contains("SsboMaterialParams : IMaterialParams"));
        }

        @Test void excludesTextureKeys() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of(
                    MaterialData.ALBEDO_COLOR, TextureKeys.ALBEDO_TEXTURE));
            var code = block.generateUbo();
            assertTrue(code.contains("albedoColor"));
            assertFalse(code.contains("albedoTexture"));
        }

        @Test void emptyKeysProducesEmptyInterface() {
            var block = SlangParamsBlock.fromKeys("Material", Set.of());
            var code = block.generateUbo();
            assertTrue(code.contains("interface IMaterialParams"));
            // Interface should be empty — no method declarations
            var interfaceBlock = code.substring(code.indexOf("interface"), code.indexOf("};") + 2);
            assertFalse(interfaceBlock.contains("()"), "Empty keys should produce no methods");
        }
    }

    // --- From Java records (static, for camera/engine — uses global instance) ---

    record TestCameraParams(Mat4 viewProjection, Mat4 view, Vec3 position, float near, float far) {}

    record TestEngineParams(float time, float deltaTime, Vec2 resolution, int frameCount) {}

    @Nested
    class FromRecord {
        @Test void generatesInterfaceFromRecord() {
            var block = SlangParamsBlock.fromRecord("Camera", TestCameraParams.class);
            var code = block.generateUbo();
            assertTrue(code.contains("interface ICameraParams"));
            assertTrue(code.contains("float4x4 viewProjection()"));
            assertTrue(code.contains("float4x4 view()"));
            assertTrue(code.contains("float3 position()"));
            assertTrue(code.contains("float near()"));
            assertTrue(code.contains("float far()"));
        }

        @Test void generatesGlobalInstanceFromRecord() {
            var block = SlangParamsBlock.fromRecord("Camera", TestCameraParams.class);
            var code = block.generateUbo();
            assertTrue(code.contains("static UboCameraParams camera"),
                    "Expected global 'camera' instance, got:\n" + code);
        }

        @Test void generatesEngineParams() {
            var block = SlangParamsBlock.fromRecord("Engine", TestEngineParams.class);
            var code = block.generateUbo();
            assertTrue(code.contains("interface IEngineParams"));
            assertTrue(code.contains("float time()"));
            assertTrue(code.contains("float deltaTime()"));
            assertTrue(code.contains("float2 resolution()"));
            assertTrue(code.contains("int frameCount()"));
            assertTrue(code.contains("static UboEngineParams engine"));
        }

        @Test void withBindingOnRecord() {
            var block = SlangParamsBlock.fromRecord("Camera", TestCameraParams.class)
                    .withBinding(0);
            var code = block.generateUbo();
            assertTrue(code.contains("cbuffer CameraBuffer : register(b0)"),
                    "Expected register(b0), got:\n" + code);
        }

        @Test void nonRecordThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> SlangParamsBlock.fromRecord("Bad", String.class));
        }
    }

}

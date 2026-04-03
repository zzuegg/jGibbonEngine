package dev.engine.core.shader;

import dev.engine.core.shader.params.CameraParams;
import dev.engine.core.shader.params.EngineParams;
import dev.engine.core.math.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ShaderParamsTest {

    @Nested
    class CameraParamsTest {
        @Test void isRecord() {
            assertTrue(CameraParams.class.isRecord());
        }

        @Test void hasExpectedFields() {
            var params = new CameraParams(
                    Mat4.IDENTITY, Mat4.IDENTITY, Mat4.IDENTITY,
                    Vec3.ZERO, 0.1f, 100f);
            assertEquals(Mat4.IDENTITY, params.viewProjection());
            assertEquals(0.1f, params.near());
            assertEquals(100f, params.far());
        }

        @Test void generatesSlangBlock() {
            var block = SlangParamsBlock.fromRecord("Camera", CameraParams.class);
            var code = block.generateUbo();
            assertTrue(code.contains("interface ICameraParams"));
            assertTrue(code.contains("float4x4 viewProjection()"));
            assertTrue(code.contains("float4x4 view()"));
            assertTrue(code.contains("float4x4 projection()"));
            assertTrue(code.contains("float3 position()"));
            assertTrue(code.contains("float near()"));
            assertTrue(code.contains("float far()"));
            assertTrue(code.contains("static UboCameraParams camera"));
        }

        @Test void compilesInSlang() {
            var compiler = SlangCompiler.find();
            assumeTrue(compiler.isAvailable(), "slangc not found");

            var block = SlangParamsBlock.fromRecord("Camera", CameraParams.class);
            var shader = block.generateUbo() + """

                struct VertexInput { float3 position : POSITION; };
                struct VertexOutput { float4 position : SV_Position; };

                [shader("vertex")]
                VertexOutput vertexMain(VertexInput input) {
                    VertexOutput output;
                    output.position = mul(float4(input.position, 1.0), camera.viewProjection());
                    return output;
                }

                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    return float4(camera.position(), camera.near());
                }
                """;

            var vs = compiler.compileToGlsl(shader, "vertexMain", ShaderStageType.VERTEX);
            assertTrue(vs.success(), "VS failed: " + vs.error());
            var fs = compiler.compileToGlsl(shader, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(fs.success(), "FS failed: " + fs.error());
        }
    }

    @Nested
    class EngineParamsTest {
        @Test void isRecord() {
            assertTrue(EngineParams.class.isRecord());
        }

        @Test void hasExpectedFields() {
            var params = new EngineParams(1.5f, 0.016f, new Vec2(1920, 1080), 42);
            assertEquals(1.5f, params.time());
            assertEquals(0.016f, params.deltaTime());
            assertEquals(42, params.frameCount());
        }

        @Test void generatesSlangBlock() {
            var block = SlangParamsBlock.fromRecord("Engine", EngineParams.class);
            var code = block.generateUbo();
            assertTrue(code.contains("interface IEngineParams"));
            assertTrue(code.contains("float time()"));
            assertTrue(code.contains("float deltaTime()"));
            assertTrue(code.contains("float2 resolution()"));
            assertTrue(code.contains("int frameCount()"));
            assertTrue(code.contains("static UboEngineParams engine"));
        }

        @Test void compilesInSlang() {
            var compiler = SlangCompiler.find();
            assumeTrue(compiler.isAvailable(), "slangc not found");

            var block = SlangParamsBlock.fromRecord("Engine", EngineParams.class);
            var shader = block.generateUbo() + """

                struct VertexOutput { float4 position : SV_Position; };

                [shader("fragment")]
                float4 fragmentMain(VertexOutput input) : SV_Target {
                    float t = engine.time();
                    float dt = engine.deltaTime();
                    return float4(t, dt, 0, 1);
                }
                """;

            var fs = compiler.compileToGlsl(shader, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(fs.success(), "FS failed: " + fs.error());
        }
    }
}

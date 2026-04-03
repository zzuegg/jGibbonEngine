package dev.engine.core.shader;

import dev.engine.core.math.Mat4;
import dev.engine.core.shader.params.ObjectParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ObjectParamsTest {

    @Test
    void isRecord() {
        assertTrue(ObjectParams.class.isRecord());
    }

    @Test
    void hasExpectedFields() {
        var params = new ObjectParams(Mat4.IDENTITY);
        assertEquals(Mat4.IDENTITY, params.world());
    }

    @Test
    void generatesSlangBlock() {
        var block = SlangParamsBlock.fromRecord("Object", ObjectParams.class);
        var code = block.generateUbo();
        assertTrue(code.contains("interface IObjectParams"));
        assertTrue(code.contains("float4x4 world()"));
        assertTrue(code.contains("static UboObjectParams object"));
    }

    @Test
    void compilesWithCameraAndObject() {
        var compiler = SlangCompiler.find();
        assumeTrue(compiler.isAvailable(), "slangc not found");

        var registry = new GlobalParamsRegistry();
        registry.register("Engine", dev.engine.core.shader.params.EngineParams.class, 0);
        registry.register("Camera", dev.engine.core.shader.params.CameraParams.class, 1);
        registry.register("Object", ObjectParams.class, 2);

        var shader = registry.generateSlang() + """

            struct VertexInput { float3 position : POSITION; float3 normal : NORMAL; };
            struct VertexOutput { float4 position : SV_Position; float3 normal; };

            [shader("vertex")]
            VertexOutput vertexMain(VertexInput input) {
                VertexOutput output;
                float4x4 mvp = mul(object.world(), camera.viewProjection());
                output.position = mul(float4(input.position, 1.0), mvp);
                output.normal = mul(float4(input.normal, 0.0), object.world()).xyz;
                return output;
            }

            [shader("fragment")]
            float4 fragmentMain(VertexOutput input) : SV_Target {
                return float4(normalize(input.normal), 1.0);
            }
            """;

        var vs = compiler.compileToGlsl(shader, "vertexMain", ShaderStageType.VERTEX);
        assertTrue(vs.success(), "VS failed: " + vs.error());

        var fs = compiler.compileToGlsl(shader, "fragmentMain", ShaderStageType.FRAGMENT);
        assertTrue(fs.success(), "FS failed: " + fs.error());
    }
}

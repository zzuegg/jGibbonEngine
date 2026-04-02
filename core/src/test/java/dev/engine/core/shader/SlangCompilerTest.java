package dev.engine.core.shader;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SlangCompilerTest {

    static SlangCompiler compiler;

    @BeforeAll
    static void setUp() {
        compiler = SlangCompiler.find();
        assumeTrue(compiler.isAvailable(), "slangc not found, skipping");
    }

    @Nested
    class CompileToGlsl {
        @Test void simpleVertexShader() {
            var source = """
                    struct VertexOutput {
                        float4 position : SV_Position;
                    };
                    cbuffer Matrices { float4x4 mvp; };
                    [shader("vertex")]
                    VertexOutput vertexMain(float3 position : POSITION) {
                        VertexOutput output;
                        output.position = mul(mvp, float4(position, 1.0));
                        return output;
                    }
                    """;
            var result = compiler.compileToGlsl(source, "vertexMain", ShaderStageType.VERTEX);
            assertTrue(result.success(), "Compilation failed: " + result.error());
            assertTrue(result.glsl().contains("#version 450"));
            assertTrue(result.glsl().contains("row_major"));
        }

        @Test void simpleFragmentShader() {
            var source = """
                    [shader("fragment")]
                    float4 fragmentMain() : SV_Target {
                        return float4(1.0, 0.0, 0.0, 1.0);
                    }
                    """;
            var result = compiler.compileToGlsl(source, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(result.success(), "Compilation failed: " + result.error());
            assertTrue(result.glsl().contains("vec4"));
        }

        @Test void invalidShaderReportsError() {
            var source = "this is not valid slang code }{";
            var result = compiler.compileToGlsl(source, "main", ShaderStageType.VERTEX);
            assertFalse(result.success());
            assertNotNull(result.error());
            assertFalse(result.error().isEmpty());
        }
    }

    @Nested
    class SlangFeatures {
        @Test void interfacesCompile() {
            var source = """
                    interface IColor {
                        float3 getColor();
                    };
                    struct Red : IColor {
                        float3 getColor() { return float3(1, 0, 0); }
                    };
                    cbuffer Data { Red material; };
                    [shader("fragment")]
                    float4 fragmentMain() : SV_Target {
                        return float4(material.getColor(), 1.0);
                    }
                    """;
            var result = compiler.compileToGlsl(source, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(result.success(), "Failed: " + result.error());
        }

        @Test void genericsCompile() {
            var source = """
                    T myMax<T : IComparable>(T a, T b) {
                        return a > b ? a : b;
                    }
                    [shader("fragment")]
                    float4 fragmentMain() : SV_Target {
                        float val = myMax<float>(0.3, 0.7);
                        return float4(val, val, val, 1.0);
                    }
                    """;
            var result = compiler.compileToGlsl(source, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(result.success(), "Failed: " + result.error());
        }

        @Test void compileToSpirv() {
            var source = """
                    [shader("fragment")]
                    float4 fragmentMain() : SV_Target {
                        return float4(0, 1, 0, 1);
                    }
                    """;
            var result = compiler.compileToSpirv(source, "fragmentMain", ShaderStageType.FRAGMENT);
            assertTrue(result.success(), "Failed: " + result.error());
            assertNotNull(result.binary());
            assertTrue(result.binary().length > 0);
        }
    }
}

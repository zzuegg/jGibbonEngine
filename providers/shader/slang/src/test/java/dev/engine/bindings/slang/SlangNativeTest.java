package dev.engine.bindings.slang;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the Slang FFM bindings.
 *
 * <p>These tests require the Slang native library to be available.
 * They are skipped (via {@code assumeTrue}) if the library is not found.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SlangNativeTest {

    private static final String SIMPLE_SHADER = """
            struct VertexInput {
                float3 position;
                float2 uv;
            };

            struct VertexOutput {
                float4 position : SV_Position;
                float2 uv;
            };

            struct FragmentOutput {
                float4 color : SV_Target;
            };

            uniform float4x4 modelViewProjection;
            uniform Sampler2D diffuseTexture;

            [shader("vertex")]
            VertexOutput vertexMain(VertexInput input) {
                VertexOutput output;
                output.position = mul(modelViewProjection, float4(input.position, 1.0));
                output.uv = input.uv;
                return output;
            }

            [shader("fragment")]
            FragmentOutput fragmentMain(VertexOutput input) {
                FragmentOutput output;
                output.color = diffuseTexture.Sample(input.uv);
                return output;
            }
            """;

    @BeforeAll
    static void checkSlangAvailable() {
        assumeTrue(SlangNative.isAvailable(), "Slang native library not available");
    }

    @Test
    @Order(1)
    void createGlobalSession() {
        var session = SlangNative.createGlobalSession();
        assertNotNull(session);
        assertFalse(session.com().isNull());
        session.close();
    }

    @Test
    @Order(2)
    void createCompileSession() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        assertNotNull(session);
        assertFalse(session.com().isNull());
        session.close();
        globalSession.close();
    }

    @Test
    @Order(3)
    void loadModuleFromSourceString() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        var module = session.loadModuleFromSourceString("test", SIMPLE_SHADER);
        assertNotNull(module);
        assertFalse(module.com().isNull());
        module.close();
        session.close();
        globalSession.close();
    }

    @Test
    @Order(4)
    void findEntryPointByName() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        var module = session.loadModuleFromSourceString("test", SIMPLE_SHADER);

        var vertexEp = module.findEntryPointByName("vertexMain");
        assertNotNull(vertexEp);
        assertFalse(vertexEp.com().isNull());

        var fragmentEp = module.findEntryPointByName("fragmentMain");
        assertNotNull(fragmentEp);
        assertFalse(fragmentEp.com().isNull());

        fragmentEp.close();
        vertexEp.close();
        module.close();
        session.close();
        globalSession.close();
    }

    @Test
    @Order(5)
    void compileToGlsl() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        var module = session.loadModuleFromSourceString("test", SIMPLE_SHADER);
        var vertexEp = module.findEntryPointByName("vertexMain");
        var fragmentEp = module.findEntryPointByName("fragmentMain");

        var composite = session.createCompositeComponentType(
                module.com(), vertexEp.com(), fragmentEp.com());
        var linked = composite.link();

        // Get vertex shader GLSL
        var vertexCode = linked.getEntryPointCode(0, 0);
        assertNotNull(vertexCode);
        String glsl = vertexCode.string();
        assertNotNull(glsl);
        assertFalse(glsl.isBlank(), "Vertex GLSL should not be blank");
        assertTrue(glsl.contains("main"), "GLSL should contain 'main'");
        System.out.println("--- Vertex GLSL ---");
        System.out.println(glsl.substring(0, Math.min(500, glsl.length())));
        vertexCode.close();

        // Get fragment shader GLSL
        var fragmentCode = linked.getEntryPointCode(1, 0);
        assertNotNull(fragmentCode);
        String fGlsl = fragmentCode.string();
        assertNotNull(fGlsl);
        assertFalse(fGlsl.isBlank(), "Fragment GLSL should not be blank");
        System.out.println("--- Fragment GLSL ---");
        System.out.println(fGlsl.substring(0, Math.min(500, fGlsl.length())));
        fragmentCode.close();

        linked.close();
        composite.close();
        fragmentEp.close();
        vertexEp.close();
        module.close();
        session.close();
        globalSession.close();
    }

    @Test
    @Order(6)
    void reflectionData() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        var module = session.loadModuleFromSourceString("test", SIMPLE_SHADER);
        var vertexEp = module.findEntryPointByName("vertexMain");
        var fragmentEp = module.findEntryPointByName("fragmentMain");

        var composite = session.createCompositeComponentType(
                module.com(), vertexEp.com(), fragmentEp.com());
        var linked = composite.link();

        var reflection = linked.getLayout(0);
        assertNotNull(reflection);

        int paramCount = reflection.getParameterCount();
        assertTrue(paramCount > 0, "Should have at least one parameter");
        System.out.println("Parameter count: " + paramCount);

        var params = reflection.getParameters();
        assertFalse(params.isEmpty(), "Should have parameters");

        for (var param : params) {
            String name = param.name();
            assertNotNull(name, "Parameter name should not be null");
            assertFalse(name.isBlank(), "Parameter name should not be blank");
            System.out.println("  " + param);
        }

        linked.close();
        composite.close();
        fragmentEp.close();
        vertexEp.close();
        module.close();
        session.close();
        globalSession.close();
    }

    private static final String GENERIC_SHADER = """
            interface IMaterialParams {
                float3 albedoColor();
                float roughness();
            };

            struct MaterialParamsData {
                float3 albedoColor;
                float roughness;
            };

            cbuffer MaterialBuffer {
                MaterialParamsData materialData;
            };

            struct UboMaterialParams : IMaterialParams {
                float3 albedoColor() { return materialData.albedoColor; }
                float roughness() { return materialData.roughness; }
            };

            struct VertexInput { float3 position : POSITION; };
            struct VertexOutput { float4 position : SV_Position; };

            cbuffer Matrices { float4x4 mvp; };

            [shader("vertex")]
            VertexOutput vertexMain(VertexInput input) {
                VertexOutput output;
                output.position = mul(float4(input.position, 1.0), mvp);
                return output;
            }

            [shader("fragment")]
            float4 fragmentMain<M : IMaterialParams>(VertexOutput input) : SV_Target {
                M material;
                float3 color = material.albedoColor() * material.roughness();
                return float4(color, 1.0);
            }
            """;

    @Test
    @Order(7)
    void specializeGenericEntryPoint() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        var module = session.loadModuleFromSourceString("test", GENERIC_SHADER);

        var vertexEp = module.findAndCheckEntryPoint("vertexMain", SlangNative.SLANG_STAGE_VERTEX);
        var fragmentEp = module.findAndCheckEntryPoint("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT);

        var composite = session.createCompositeComponentType(
                module.com(), vertexEp.com(), fragmentEp.com());

        // Check that there are specialization params
        int specCount = composite.getSpecializationParamCount();
        System.out.println("Specialization param count: " + specCount);
        assertTrue(specCount > 0, "Should have specialization parameters for generic entry point");

        // Specialize with concrete type
        var specialized = composite.specialize("UboMaterialParams");
        assertNotNull(specialized);

        var linked = specialized.link();

        // Get fragment GLSL — should compile without "no matching IR symbol" error
        var fragmentCode = linked.getEntryPointCode(1, 0);
        String fGlsl = fragmentCode.string();
        assertNotNull(fGlsl);
        assertFalse(fGlsl.isBlank(), "Specialized fragment GLSL should not be blank");
        System.out.println("--- Specialized Fragment GLSL ---");
        System.out.println(fGlsl.substring(0, Math.min(500, fGlsl.length())));

        fragmentCode.close();
        linked.close();
        specialized.close();
        composite.close();
        fragmentEp.close();
        vertexEp.close();
        module.close();
        session.close();
        globalSession.close();
    }

    private static final String MULTI_GENERIC_SHADER = """
            interface ICameraParams {
                float4x4 viewProjection();
                float3 position();
            };
            interface IObjectParams {
                float4x4 world();
            };
            interface IMaterialParams {
                float3 albedoColor();
                float roughness();
            };

            struct CameraData { float4x4 viewProjection; float3 position; };
            cbuffer CameraBuffer { CameraData cameraData; };
            struct UboCameraParams : ICameraParams {
                float4x4 viewProjection() { return cameraData.viewProjection; }
                float3 position() { return cameraData.position; }
            };

            struct ObjectData { float4x4 world; };
            cbuffer ObjectBuffer { ObjectData objectData; };
            struct UboObjectParams : IObjectParams {
                float4x4 world() { return objectData.world; }
            };

            struct MaterialData { float3 albedoColor; float roughness; };
            cbuffer MaterialBuffer { MaterialData materialData; };
            struct UboMaterialParams : IMaterialParams {
                float3 albedoColor() { return materialData.albedoColor; }
                float roughness() { return materialData.roughness; }
            };

            struct VertexInput { float3 position : POSITION; float3 normal : NORMAL; };
            struct VertexOutput { float4 position : SV_Position; float3 normal; };

            [shader("vertex")]
            VertexOutput vertexMain<C : ICameraParams, O : IObjectParams>(VertexInput input) {
                C camera;
                O object;
                VertexOutput output;
                float4x4 mvp = mul(object.world(), camera.viewProjection());
                output.position = mul(float4(input.position, 1.0), mvp);
                output.normal = mul(float4(input.normal, 0.0), object.world()).xyz;
                return output;
            }

            [shader("fragment")]
            float4 fragmentMain<C : ICameraParams, M : IMaterialParams>(VertexOutput input) : SV_Target {
                C camera;
                M material;
                float3 N = normalize(input.normal);
                float3 V = normalize(camera.position());
                float3 color = material.albedoColor() * material.roughness();
                return float4(color, 1.0);
            }
            """;

    @Test
    @Order(8)
    void multiGenericSpecialization() {
        var globalSession = SlangNative.createGlobalSession();
        var session = globalSession.createSession(SlangNative.SLANG_GLSL);
        var module = session.loadModuleFromSourceString("test", MULTI_GENERIC_SHADER);

        var vertexEp = module.findAndCheckEntryPoint("vertexMain", SlangNative.SLANG_STAGE_VERTEX);
        var fragmentEp = module.findAndCheckEntryPoint("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT);

        var composite = session.createCompositeComponentType(
                module.com(), vertexEp.com(), fragmentEp.com());

        int specCount = composite.getSpecializationParamCount();
        System.out.println("Multi-generic specialization param count: " + specCount);
        assertTrue(specCount >= 4, "Expected at least 4 specialization params (C,O for VS + C,M for FS), got: " + specCount);

        // Specialize: VS gets (C=UboCameraParams, O=UboObjectParams), FS gets (C=UboCameraParams, M=UboMaterialParams)
        var specialized = composite.specialize(
                "UboCameraParams", "UboObjectParams",    // vertexMain<C, O>
                "UboCameraParams", "UboMaterialParams"); // fragmentMain<C, M>

        var linked = specialized.link();

        var vsCode = linked.getEntryPointCode(0, 0);
        assertFalse(vsCode.string().isBlank(), "VS should compile");
        System.out.println("--- Multi-generic VS ---\n" + vsCode.string());

        var fsCode = linked.getEntryPointCode(1, 0);
        assertFalse(fsCode.string().isBlank(), "FS should compile");
        System.out.println("--- Multi-generic FS ---\n" + fsCode.string().substring(0, Math.min(300, fsCode.string().length())));

        vsCode.close();
        fsCode.close();
        linked.close();
        specialized.close();
        composite.close();
        fragmentEp.close();
        vertexEp.close();
        module.close();
        session.close();
        globalSession.close();
    }

    @Test
    @Order(9)
    void highLevelSpecializedCompile() {
        var compiler = SlangCompilerNative.create();

        var result = compiler.compileSpecialized(GENERIC_SHADER,
                java.util.List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)
                ),
                SlangNative.SLANG_GLSL,
                "UboMaterialParams");

        assertNotNull(result);
        assertEquals(2, result.entryPointCount());

        String fragmentGlsl = result.code(1);
        assertNotNull(fragmentGlsl);
        assertFalse(fragmentGlsl.isBlank());
        // Should contain actual material data access (inlined from the concrete type)
        assertTrue(fragmentGlsl.contains("materialData") || fragmentGlsl.contains("MaterialBuffer"),
                "Specialized GLSL should reference material buffer data");

        result.close();
        compiler.close();
    }

    @Test
    @Order(10)
    void compileToWgsl() {
        var compiler = SlangCompilerNative.create();

        String source = """
                struct VertexInput { float3 position : POSITION; };
                struct VertexOutput { float4 position : SV_Position; };

                [shader("vertex")]
                VertexOutput vertexMain(VertexInput input) {
                    VertexOutput output;
                    output.position = float4(input.position, 1.0);
                    return output;
                }

                [shader("fragment")]
                float4 fragmentMain() : SV_Target {
                    return float4(1.0, 0.0, 0.0, 1.0);
                }
                """;

        var entryPoints = List.of(
                new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT));

        try (var result = compiler.compile(source, entryPoints, SlangNative.SLANG_WGSL)) {
            String vertexWgsl = result.code(0);
            String fragmentWgsl = result.code(1);
            System.out.println("=== VERTEX WGSL ===");
            System.out.println(vertexWgsl);
            System.out.println("=== FRAGMENT WGSL ===");
            System.out.println(fragmentWgsl);
            assertNotNull(vertexWgsl);
            assertNotNull(fragmentWgsl);
            assertTrue(vertexWgsl.contains("@vertex"), "Should contain @vertex annotation");
        }

        compiler.close();
    }

    @Test
    @Order(9)
    void highLevelCompilerApi() {
        var compiler = SlangCompilerNative.create();

        var result = compiler.compile(SIMPLE_SHADER,
                java.util.List.of(
                        new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                        new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT)
                ),
                SlangNative.SLANG_GLSL);

        assertNotNull(result);
        assertEquals(2, result.entryPointCount());

        // Check vertex code
        String vertexGlsl = result.code(0);
        assertNotNull(vertexGlsl);
        assertFalse(vertexGlsl.isBlank());
        System.out.println("--- High-level vertex GLSL ---");
        System.out.println(vertexGlsl.substring(0, Math.min(300, vertexGlsl.length())));

        // Check fragment code
        String fragmentGlsl = result.code(1);
        assertNotNull(fragmentGlsl);
        assertFalse(fragmentGlsl.isBlank());

        // Check reflection
        var reflection = result.reflection();
        assertNotNull(reflection);
        assertTrue(reflection.getParameterCount() > 0);

        result.close();
        compiler.close();
    }
}

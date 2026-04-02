package dev.engine.bindings.slang;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

    @Test
    @Order(7)
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

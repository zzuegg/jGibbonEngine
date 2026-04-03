package dev.engine.examples;

import org.junit.jupiter.api.Test;
import java.io.IOException;

/** OpenGL visual regression tests. Runs in its own JVM (forkEvery=1). */
class OpenGlRenderTest {
    private final RenderTestHarness harness = new RenderTestHarness(256, 256);

    @Test void twoCubesUnlit() throws IOException {
        harness.assertOpenGlMatchesReference(CrossBackendScenes.TWO_CUBES_UNLIT, "two_cubes_unlit");
    }

    @Test void singleSpherePbr() throws IOException {
        harness.assertOpenGlMatchesReference(CrossBackendScenes.SINGLE_SPHERE_PBR, "single_sphere_pbr");
    }

    @Test void depthTestCubes() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.DEPTH_TEST_CUBES, "depth_test_cubes");
    }

    @Test void primitiveMeshes() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.PRIMITIVE_MESHES, "primitive_meshes");
    }

    @Test void pbrMaterials() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.PBR_MATERIALS, "pbr_materials");
    }

    @Test void renderToTexture() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.RENDER_TO_TEXTURE, "render_to_texture");
    }

    @Test void mixedRenderStates() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.MIXED_RENDER_STATES, "mixed_render_states");
    }

    @Test void forcedWireframe() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.FORCED_WIREFRAME, "forced_wireframe");
    }

    @Test void blendAdditive() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.BLEND_ADDITIVE, "blend_additive");
    }

    @Test void depthWriteOff() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.DEPTH_WRITE_OFF, "depth_write_off");
    }

    @Test void texturedQuad() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.TEXTURED_QUAD, "textured_quad");
    }

    @Test void allBlendModes() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.ALL_BLEND_MODES, "all_blend_modes");
    }

    @Test void depthFuncGreater() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.DEPTH_FUNC_GREATER, "depth_func_greater");
    }

    @Test void frontFaceCw() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.FRONT_FACE_CW, "front_face_cw");
    }

    @Test void stencilMasking() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.STENCIL_MASKING, "stencil_masking");
    }

    @Test void shaderSwitching() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.SHADER_SWITCHING, "shader_switching");
    }

    @Test void materialTexture() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.MATERIAL_TEXTURE, "material_texture");
    }

    @Test void textureSwitching() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.TEXTURE_SWITCHING, "texture_switching");
    }

    @Test void texture3dCreate() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.TEXTURE_3D_CREATE, "texture_3d_create");
    }

    @Test void textureArrayCreate() throws IOException {
        harness.assertOpenGlMatchesReference(ScreenshotTestSuite.TEXTURE_ARRAY_CREATE, "texture_array_create");
    }
}

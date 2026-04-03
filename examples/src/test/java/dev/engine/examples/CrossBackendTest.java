package dev.engine.examples;

import org.junit.jupiter.api.Test;

/** Cross-backend comparison tests — renders on both GL and Vulkan and asserts similarity. */
class CrossBackendTest {
    private final RenderTestHarness harness = new RenderTestHarness(256, 256);

    @Test void twoCubesUnlit() {
        harness.assertCrossBackend(CrossBackendScenes.TWO_CUBES_UNLIT, "two_cubes_unlit",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void singleSpherePbr() {
        harness.assertCrossBackend(CrossBackendScenes.SINGLE_SPHERE_PBR, "single_sphere_pbr",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void depthTestCubes() {
        harness.assertCrossBackend(ScreenshotTestSuite.DEPTH_TEST_CUBES, "depth_test_cubes",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void primitiveMeshes() {
        harness.assertCrossBackend(ScreenshotTestSuite.PRIMITIVE_MESHES, "primitive_meshes",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void pbrMaterials() {
        harness.assertCrossBackend(ScreenshotTestSuite.PBR_MATERIALS, "pbr_materials",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void renderToTexture() {
        harness.assertCrossBackend(ScreenshotTestSuite.RENDER_TO_TEXTURE, "render_to_texture",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void mixedRenderStates() {
        harness.assertCrossBackend(ScreenshotTestSuite.MIXED_RENDER_STATES, "mixed_render_states",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void blendAdditive() {
        harness.assertCrossBackend(ScreenshotTestSuite.BLEND_ADDITIVE, "blend_additive",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void depthWriteOff() {
        harness.assertCrossBackend(ScreenshotTestSuite.DEPTH_WRITE_OFF, "depth_write_off",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void texturedQuad() {
        harness.assertCrossBackend(ScreenshotTestSuite.TEXTURED_QUAD, "textured_quad",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void allBlendModes() {
        harness.assertCrossBackend(ScreenshotTestSuite.ALL_BLEND_MODES, "all_blend_modes",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void frontFaceCw() {
        harness.assertCrossBackend(ScreenshotTestSuite.FRONT_FACE_CW, "front_face_cw",
            RenderTestHarness.Tolerance.loose());
    }

    @Test void stencilMasking() {
        harness.assertCrossBackend(ScreenshotTestSuite.STENCIL_MASKING, "stencil_masking",
            RenderTestHarness.Tolerance.loose());
    }

    // Wireframe is not cross-backend comparable — Vulkan doesn't support dynamic polygon mode.
    // Per-backend wireframe tests exist in OpenGlRenderTest and VulkanRenderTest.

    // DEPTH_FUNC_GREATER is not cross-backend — Vulkan doesn't support dynamic depth compare op.
    // Per-backend test exists in OpenGlRenderTest.
}

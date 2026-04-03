package dev.engine.examples;

import org.junit.jupiter.api.Test;
import java.io.IOException;

/** Vulkan visual regression tests. Runs in its own JVM (forkEvery=1). */
class VulkanRenderTest {
    private final RenderTestHarness harness = new RenderTestHarness(256, 256);

    @Test void twoCubesUnlit() throws IOException {
        harness.assertVulkanMatchesReference(CrossBackendScenes.TWO_CUBES_UNLIT, "two_cubes_unlit");
    }

    @Test void singleSpherePbr() throws IOException {
        harness.assertVulkanMatchesReference(CrossBackendScenes.SINGLE_SPHERE_PBR, "single_sphere_pbr");
    }

    @Test void depthTestCubes() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.DEPTH_TEST_CUBES, "depth_test_cubes");
    }

    @Test void primitiveMeshes() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.PRIMITIVE_MESHES, "primitive_meshes");
    }

    @Test void pbrMaterials() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.PBR_MATERIALS, "pbr_materials");
    }

    @Test void renderToTexture() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.RENDER_TO_TEXTURE, "render_to_texture");
    }

    @Test void mixedRenderStates() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.MIXED_RENDER_STATES, "mixed_render_states");
    }

    @Test void forcedWireframe() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.FORCED_WIREFRAME, "forced_wireframe");
    }

    @Test void blendAdditive() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.BLEND_ADDITIVE, "blend_additive");
    }

    @Test void depthWriteOff() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.DEPTH_WRITE_OFF, "depth_write_off");
    }

    @Test void texturedQuad() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.TEXTURED_QUAD, "textured_quad");
    }

    @Test void allBlendModes() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.ALL_BLEND_MODES, "all_blend_modes");
    }

    @Test void frontFaceCw() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.FRONT_FACE_CW, "front_face_cw");
    }

    @Test void stencilMasking() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.STENCIL_MASKING, "stencil_masking");
    }

    @Test void depthFuncGreater() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.DEPTH_FUNC_GREATER, "depth_func_greater");
    }

    @Test void shaderSwitching() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.SHADER_SWITCHING, "shader_switching");
    }

    @Test void materialTexture() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.MATERIAL_TEXTURE, "material_texture");
    }

    @Test void textureSwitching() throws IOException {
        harness.assertVulkanMatchesReference(ScreenshotTestSuite.TEXTURE_SWITCHING, "texture_switching");
    }
}

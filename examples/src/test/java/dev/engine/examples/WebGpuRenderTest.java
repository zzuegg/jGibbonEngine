package dev.engine.examples;

import dev.engine.graphics.webgpu.WgpuRenderDevice;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** WebGPU visual regression tests. Skips entirely if jWebGPU is not available. */
class WebGpuRenderTest {
    private final RenderTestHarness harness = new RenderTestHarness(256, 256);

    @BeforeAll static void checkAvailable() {
        assumeTrue(WgpuRenderDevice.isAvailable(), "jWebGPU not available");
    }

    @Test void twoCubesUnlit() throws IOException {
        harness.assertWebGpuMatchesReference(CrossBackendScenes.TWO_CUBES_UNLIT, "two_cubes_unlit");
    }

    @Test void singleSpherePbr() throws IOException {
        harness.assertWebGpuMatchesReference(CrossBackendScenes.SINGLE_SPHERE_PBR, "single_sphere_pbr");
    }

    @Test void depthTestCubes() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.DEPTH_TEST_CUBES, "depth_test_cubes");
    }

    @Test void primitiveMeshes() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.PRIMITIVE_MESHES, "primitive_meshes");
    }

    @Test void pbrMaterials() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.PBR_MATERIALS, "pbr_materials");
    }

    @Test void renderToTexture() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.RENDER_TO_TEXTURE, "render_to_texture");
    }

    @Test void mixedRenderStates() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.MIXED_RENDER_STATES, "mixed_render_states");
    }

    @Test void forcedWireframe() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.FORCED_WIREFRAME, "forced_wireframe");
    }

    @Test void blendAdditive() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.BLEND_ADDITIVE, "blend_additive");
    }

    @Test void depthWriteOff() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.DEPTH_WRITE_OFF, "depth_write_off");
    }

    @Test void texturedQuad() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.TEXTURED_QUAD, "textured_quad");
    }

    @Test void allBlendModes() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.ALL_BLEND_MODES, "all_blend_modes");
    }

    @Test void depthFuncGreater() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.DEPTH_FUNC_GREATER, "depth_func_greater");
    }

    @Test void frontFaceCw() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.FRONT_FACE_CW, "front_face_cw");
    }

    @Test void stencilMasking() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.STENCIL_MASKING, "stencil_masking");
    }

    @Test void shaderSwitching() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.SHADER_SWITCHING, "shader_switching");
    }

    @Test void materialTexture() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.MATERIAL_TEXTURE, "material_texture");
    }

    @Test void textureSwitching() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.TEXTURE_SWITCHING, "texture_switching");
    }

    @Test void texture3dCreate() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.TEXTURE_3D_CREATE, "texture_3d_create");
    }

    @Test void textureArrayCreate() throws IOException {
        harness.assertWebGpuMatchesReference(ScreenshotTestSuite.TEXTURE_ARRAY_CREATE, "texture_array_create");
    }
}

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
}

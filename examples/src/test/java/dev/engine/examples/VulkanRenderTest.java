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
}

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
}

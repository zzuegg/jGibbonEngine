package dev.engine.tests.screenshot.desktop;

import dev.engine.graphics.ScreenshotHelper;
import dev.engine.tests.screenshot.ComparisonTest;
import dev.engine.tests.screenshot.SceneDiscovery;
import dev.engine.tests.screenshot.TestResults;
import dev.engine.tests.screenshot.Tolerance;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Auto-discovers all {@link RenderTestScene} and {@link ComparisonTest} fields
 * in the {@code scenes} package and generates JUnit tests dynamically.
 *
 * <p>Each scene produces:
 * <ul>
 *   <li>One per-backend test per capture frame (GL, VK, WebGPU)</li>
 *   <li>One cross-backend comparison test per capture frame</li>
 * </ul>
 */
class ScreenshotTest {

    private final ScreenshotTestHarness harness = new ScreenshotTestHarness(256, 256);
    private final SceneDiscovery discovery = new SceneDiscovery();

    @TestFactory
    Collection<DynamicNode> screenshotTests() {
        var categories = new LinkedHashMap<String, List<DynamicNode>>();

        // Scene tests
        for (var discovered : discovery.scenes()) {
            categories.computeIfAbsent(discovered.category(), k -> new ArrayList<>())
                    .add(generateSceneTests(discovered));
        }

        // Comparison tests
        for (var discovered : discovery.comparisons()) {
            categories.computeIfAbsent(discovered.category(), k -> new ArrayList<>())
                    .add(generateComparisonTests(discovered));
        }

        // Wrap categories as DynamicContainers
        return categories.entrySet().stream()
                .map(e -> DynamicContainer.dynamicContainer(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.toList());
    }

    private DynamicContainer generateSceneTests(SceneDiscovery.DiscoveredScene discovered) {
        var tests = new ArrayList<DynamicNode>();
        int[] frames = discovered.scene().captureFrames();

        for (int frame : frames) {
            String frameSuffix = frames.length > 1 ? "_f" + frame : "";

            // Per-backend tests (render + reference comparison)
            for (var backend : Backend.values()) {
                var testName = backend.name().toLowerCase() + frameSuffix;
                tests.add(DynamicTest.dynamicTest(testName, () -> {
                    assumeTrue(backend.isAvailable(), backend.name() + " not available");
                    var captures = harness.render(discovered.scene(), backend);
                    var pixels = captures.get(frame);
                    var backendName = backend.name().toLowerCase();
                    var sceneName = discovered.name() + frameSuffix;
                    harness.saveScreenshot(pixels, backendName, sceneName);

                    // Compare against reference if one exists
                    var reference = harness.loadReference(backendName, sceneName);
                    if (reference != null) {
                        var tolerance = discovered.tolerance();
                        double diff = ScreenshotTestHarness.diffPercent(pixels, reference, tolerance.maxChannelDiff());
                        TestResults.instance().recordDiff(discovered.name(), backendName + "_ref", diff);
                        assertTrue(diff < tolerance.maxDiffPercent(),
                                backendName.toUpperCase() + " '" + sceneName
                                        + "' regressed: " + String.format("%.2f%%", diff)
                                        + " diff from reference (max " + tolerance.maxDiffPercent() + "%)."
                                        + " Screenshot: build/screenshots/" + backendName + "/" + sceneName + ".png");
                    }
                }));
            }

            // Cross-backend comparison
            tests.add(DynamicTest.dynamicTest("cross" + frameSuffix, () -> {
                byte[] glPixels = null, vkPixels = null, wgpuPixels = null;

                if (Backend.OPENGL.isAvailable()) {
                    glPixels = harness.render(discovered.scene(), Backend.OPENGL).get(frame);
                    harness.saveScreenshot(glPixels, "opengl", discovered.name() + frameSuffix);
                }
                if (Backend.VULKAN.isAvailable()) {
                    vkPixels = harness.render(discovered.scene(), Backend.VULKAN).get(frame);
                    harness.saveScreenshot(vkPixels, "vulkan", discovered.name() + frameSuffix);
                }
                if (Backend.WEBGPU.isAvailable()) {
                    wgpuPixels = harness.render(discovered.scene(), Backend.WEBGPU).get(frame);
                    harness.saveScreenshot(wgpuPixels, "webgpu", discovered.name() + frameSuffix);
                }

                var tolerance = discovered.tolerance();

                // GL vs VK
                if (glPixels != null && vkPixels != null) {
                    double diff = ScreenshotTestHarness.diffPercent(glPixels, vkPixels, tolerance.maxChannelDiff());
                    TestResults.instance().recordDiff(discovered.name(), "gl_vs_vk", diff);
                    assertTrue(diff < tolerance.maxDiffPercent(),
                            "GL/VK differ by " + String.format("%.2f%%", diff)
                                    + " (max " + tolerance.maxDiffPercent() + "%)");
                }

                // GL vs WebGPU (wider tolerance)
                if (glPixels != null && wgpuPixels != null) {
                    var wideTolerance = Tolerance.wide();
                    double diff = ScreenshotTestHarness.diffPercent(glPixels, wgpuPixels, wideTolerance.maxChannelDiff());
                    TestResults.instance().recordDiff(discovered.name(), "gl_vs_webgpu", diff);
                    assertTrue(diff < wideTolerance.maxDiffPercent(),
                            "GL/WebGPU differ by " + String.format("%.2f%%", diff)
                                    + " (max " + wideTolerance.maxDiffPercent() + "%)");
                }

                // Write diff results for report
                TestResults.instance().writeToFile("build/screenshots");
            }));
        }

        return DynamicContainer.dynamicContainer(discovered.name(), tests);
    }

    private DynamicContainer generateComparisonTests(SceneDiscovery.DiscoveredComparison discovered) {
        var tests = new ArrayList<DynamicNode>();
        var compTest = discovered.test();

        for (var backend : Backend.values()) {
            tests.add(DynamicTest.dynamicTest(backend.name().toLowerCase(), () -> {
                assumeTrue(backend.isAvailable(), backend.name() + " not available");

                byte[][] variantPixels = new byte[compTest.variants().length][];
                for (int i = 0; i < compTest.variants().length; i++) {
                    var variant = compTest.variants()[i];
                    variantPixels[i] = harness.renderSingle(variant.scene(), backend);
                    harness.saveScreenshot(variantPixels[i], backend.name().toLowerCase(),
                            discovered.name() + "_" + variant.name());
                }

                // Compare all pairs
                var tolerance = compTest.tolerance();
                for (int i = 0; i < variantPixels.length; i++) {
                    for (int j = i + 1; j < variantPixels.length; j++) {
                        double diff = ScreenshotTestHarness.diffPercent(
                                variantPixels[i], variantPixels[j], tolerance.maxChannelDiff());
                        assertTrue(diff < tolerance.maxDiffPercent(),
                                compTest.variants()[i].name() + " vs " + compTest.variants()[j].name()
                                        + " differ by " + String.format("%.2f%%", diff)
                                        + " on " + backend.name());
                    }
                }
            }));
        }

        return DynamicContainer.dynamicContainer(discovered.name() + " (comparison)", tests);
    }
}

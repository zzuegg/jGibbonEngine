package dev.engine.tests.screenshot.desktop;

import dev.engine.tests.screenshot.SceneDiscovery;

import java.io.IOException;

/**
 * Renders all discovered scenes on all available backends and saves
 * the output as reference screenshots in src/test/resources/reference-screenshots/.
 *
 * <p>Run via: {@code ./gradlew :samples:tests:screenshot:saveReferences}
 */
public class SaveReferences {

    public static void main(String[] args) throws IOException {
        var harness = new ScreenshotTestHarness(256, 256);
        var discovery = new SceneDiscovery();
        int saved = 0;

        for (var scene : discovery.scenes()) {
            int[] frames = scene.scene().captureFrames();
            for (var backend : Backend.values()) {
                if (!backend.isAvailable()) {
                    System.out.println("  SKIP " + backend.name() + " (not available)");
                    continue;
                }
                try {
                    var captures = harness.render(scene.scene(), backend);
                    for (int frame : frames) {
                        var pixels = captures.get(frame);
                        if (pixels == null) continue;
                        var frameSuffix = frames.length > 1 ? "_f" + frame : "";
                        var name = scene.name() + frameSuffix;
                        harness.saveReference(pixels, backend.name().toLowerCase(), name);
                        System.out.println("  SAVED " + backend.name().toLowerCase() + "/" + name + ".png");
                        saved++;
                    }
                } catch (Exception e) {
                    System.err.println("  ERROR " + backend.name() + "/" + scene.name() + ": " + e.getMessage());
                }
            }
        }

        System.out.println("\nSaved " + saved + " reference screenshots.");
        System.out.println("Commit them to track regressions:");
        System.out.println("  git add samples/tests/screenshot/src/test/resources/reference-screenshots/");
    }
}

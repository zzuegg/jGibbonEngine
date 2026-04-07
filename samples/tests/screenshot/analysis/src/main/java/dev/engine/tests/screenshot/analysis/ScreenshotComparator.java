package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pipeline Pass 3: Compares screenshots against reference images and
 * cross-backend. Populates the manifest's comparisons section.
 */
public final class ScreenshotComparator {

    private ScreenshotComparator() {}

    /**
     * Runs all comparisons and updates the manifest.
     *
     * @param manifestPath path to the manifest JSON
     * @param screenshotDir directory containing backend subdirectories with PNGs
     * @param referenceDir  directory containing reference PNGs (profile-specific)
     */
    public static void compare(Path manifestPath, Path screenshotDir, Path referenceDir)
            throws Exception {
        var manifest = Manifest.readFrom(manifestPath);

        for (var scene : manifest.scenes) {
            var successfulRuns = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name) && "success".equals(r.status))
                    .toList();
            var failedRuns = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name) && !"success".equals(r.status))
                    .toList();

            // Reference comparisons for successful runs
            for (var run : successfulRuns) {
                for (var screenshot : run.screenshots) {
                    var refFilename = scene.name + "_f" + screenshot.frame() + ".png";
                    var refPath = referenceDir.resolve(run.backend).resolve(refFilename);
                    var screenshotPath = screenshotDir.resolve(screenshot.path());

                    var comp = new Manifest.Comparison();
                    comp.scene = scene.name;
                    comp.frame = screenshot.frame();
                    comp.type = "reference";
                    comp.backend = run.backend;
                    comp.profile = manifest.profile;
                    comp.tolerance = scene.tolerance;

                    if (!Files.exists(refPath)) {
                        comp.status = "no_reference";
                        comp.reason = "No reference image for profile '" + manifest.profile + "'";
                    } else {
                        byte[] actual = ImageUtils.loadPng(screenshotPath, scene.width, scene.height);
                        byte[] expected = ImageUtils.loadPng(refPath, scene.width, scene.height);
                        double diff = ImageUtils.diffPercentage(actual, expected,
                                scene.tolerance.maxChannelDiff());
                        comp.diffPercent = diff;
                        if (diff <= scene.tolerance.maxDiffPercent()) {
                            comp.status = "pass";
                        } else {
                            comp.status = "fail";
                            comp.reason = String.format("Diff %.2f%% exceeds threshold %.2f%%",
                                    diff, scene.tolerance.maxDiffPercent());
                        }
                    }
                    manifest.comparisons.add(comp);
                }
            }

            // Skipped comparisons for failed runs
            for (var run : failedRuns) {
                for (int frame : scene.captureFrames) {
                    var comp = new Manifest.Comparison();
                    comp.scene = scene.name;
                    comp.frame = frame;
                    comp.type = "reference";
                    comp.backend = run.backend;
                    comp.status = "skipped";
                    comp.reason = "Run failed: " + run.status
                            + (run.error != null ? " (" + run.error.message() + ")" : "");
                    manifest.comparisons.add(comp);
                }
            }

            // Cross-backend comparisons — full pair matrix from successful runs
            var backends = successfulRuns.stream()
                    .map(r -> r.backend).distinct().sorted().toList();
            for (int i = 0; i < backends.size(); i++) {
                for (int j = i + 1; j < backends.size(); j++) {
                    var backendA = backends.get(i);
                    var backendB = backends.get(j);
                    var runsA = successfulRuns.stream()
                            .filter(r -> r.backend.equals(backendA)).findFirst().orElse(null);
                    var runsB = successfulRuns.stream()
                            .filter(r -> r.backend.equals(backendB)).findFirst().orElse(null);
                    if (runsA == null || runsB == null) continue;

                    for (int frame : scene.captureFrames) {
                        var screenshotA = runsA.screenshots.stream()
                                .filter(s -> s.frame() == frame).findFirst().orElse(null);
                        var screenshotB = runsB.screenshots.stream()
                                .filter(s -> s.frame() == frame).findFirst().orElse(null);
                        if (screenshotA == null || screenshotB == null) continue;

                        byte[] pixelsA = ImageUtils.loadPng(
                                screenshotDir.resolve(screenshotA.path()),
                                scene.width, scene.height);
                        byte[] pixelsB = ImageUtils.loadPng(
                                screenshotDir.resolve(screenshotB.path()),
                                scene.width, scene.height);
                        double diff = ImageUtils.diffPercentage(pixelsA, pixelsB,
                                scene.tolerance.maxChannelDiff());

                        var comp = new Manifest.Comparison();
                        comp.scene = scene.name;
                        comp.frame = frame;
                        comp.type = "cross_backend";
                        comp.backendA = backendA;
                        comp.backendB = backendB;
                        comp.tolerance = scene.tolerance;
                        comp.diffPercent = diff;
                        if (diff <= scene.tolerance.maxDiffPercent()) {
                            comp.status = "pass";
                        } else {
                            // Check if this failure matches a known limitation
                            var limitation = scene.knownLimitations.stream()
                                    .filter(kl -> kl.backend().equals(backendA)
                                            || kl.backend().equals(backendB))
                                    .findFirst().orElse(null);
                            if (limitation != null) {
                                comp.status = "known_limitation";
                                comp.reason = limitation.reason()
                                        + String.format(" (diff %.2f%%)", diff);
                            } else {
                                comp.status = "fail";
                                comp.reason = String.format("Diff %.2f%% exceeds threshold %.2f%%",
                                        diff, scene.tolerance.maxDiffPercent());
                            }
                        }
                        manifest.comparisons.add(comp);
                    }
                }
            }
        }

        manifest.writeTo(manifestPath);
    }

    /** Entry point for Gradle JavaExec task. Args: manifestPath screenshotDir referenceDir */
    public static void main(String[] args) throws Exception {
        compare(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("Comparison complete.");
    }
}

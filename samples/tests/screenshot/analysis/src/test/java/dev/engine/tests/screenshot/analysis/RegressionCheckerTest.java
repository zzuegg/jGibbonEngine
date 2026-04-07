package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.Tolerance;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegressionCheckerTest {

    @Test
    void returnsZeroWhenAllPass(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("pass", null);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(0, exitCode);
    }

    @Test
    void returnsOneWhenAnyFail(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("fail", "Diff 2.3% exceeds threshold");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(1, exitCode);
    }

    @Test
    void returnsZeroWithNoReferenceOnly(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("no_reference", "No reference for local");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(0, exitCode);
    }

    @Test
    void returnsZeroForSkipped(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithComparison("skipped", "Run crashed");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(0, exitCode);
    }

    @Test
    void mixedPassAndFailReturnsOne(@TempDir Path tmp) throws Exception {
        var manifest = createMinimalManifest();
        addComparison(manifest, "pass", null);
        addComparison(manifest, "fail", "Diff too high");

        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        int exitCode = RegressionChecker.check(manifestPath, tmp.resolve("references"));
        assertEquals(1, exitCode);
    }

    private Manifest createManifestWithComparison(String status, String reason) {
        var m = createMinimalManifest();
        addComparison(m, status, reason);
        return m;
    }

    private void addComparison(Manifest m, String status, String reason) {
        var comp = new Manifest.Comparison();
        comp.scene = "test_scene";
        comp.frame = 3;
        comp.type = "reference";
        comp.backend = "opengl";
        comp.status = status;
        comp.reason = reason;
        comp.tolerance = Tolerance.loose();
        m.comparisons.add(comp);
    }

    private Manifest createMinimalManifest() {
        var m = new Manifest();
        m.branch = "test";
        m.commit = "abc";
        m.timestamp = "2026-04-07T10:00:00Z";
        m.profile = "local";
        return m;
    }
}

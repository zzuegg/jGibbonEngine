package dev.engine.tests.screenshot.scenes.manifest;

import dev.engine.tests.screenshot.scenes.Tolerance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManifestTest {

    @Test
    void roundTripEmptyManifest(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.branch = "main";
        manifest.commit = "abc123";
        manifest.timestamp = "2026-04-07T10:00:00Z";
        manifest.profile = "ci";

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals("main", loaded.branch);
        assertEquals("abc123", loaded.commit);
        assertEquals("ci", loaded.profile);
        assertTrue(loaded.scenes.isEmpty());
        assertTrue(loaded.runs.isEmpty());
        assertTrue(loaded.comparisons.isEmpty());
    }

    @Test
    void roundTripWithSceneAndRun(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.branch = "feature";
        manifest.commit = "def456";
        manifest.timestamp = "2026-04-07T10:00:00Z";
        manifest.profile = "local";

        var scene = new Manifest.Scene();
        scene.name = "depth_test";
        scene.category = "basic";
        scene.className = "dev.engine.tests.screenshot.scenes.basic.BasicScenes";
        scene.fieldName = "DEPTH_TEST_CUBES";
        scene.captureFrames = Set.of(3);
        scene.tolerance = Tolerance.loose();
        scene.width = 256;
        scene.height = 256;
        manifest.scenes.add(scene);

        var run = new Manifest.Run();
        run.scene = "depth_test";
        run.backend = "opengl";
        run.status = "success";
        run.durationMs = 1234;
        run.screenshots = List.of(new Manifest.Screenshot(3, "opengl/depth_test_f3.png"));
        manifest.runs.add(run);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals(1, loaded.scenes.size());
        assertEquals("depth_test", loaded.scenes.get(0).name);
        assertEquals("basic", loaded.scenes.get(0).category);
        assertEquals(256, loaded.scenes.get(0).width);
        assertTrue(loaded.scenes.get(0).captureFrames.contains(3));
        assertEquals(1, loaded.runs.size());
        assertEquals("opengl", loaded.runs.get(0).backend);
        assertEquals("success", loaded.runs.get(0).status);
        assertEquals(1234, loaded.runs.get(0).durationMs);
        assertEquals(1, loaded.runs.get(0).screenshots.size());
        assertEquals(3, loaded.runs.get(0).screenshots.get(0).frame());
    }

    @Test
    void roundTripWithError(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.branch = "main";
        manifest.commit = "abc";
        manifest.timestamp = "2026-04-07T10:00:00Z";
        manifest.profile = "ci";

        var run = new Manifest.Run();
        run.scene = "crash_scene";
        run.backend = "vulkan";
        run.status = "crash";
        run.durationMs = 4500;
        run.error = new Manifest.RunError("crash", 139, "SIGSEGV", "stderr output", "stdout output");
        manifest.runs.add(run);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        var loadedRun = loaded.runs.get(0);
        assertEquals("crash", loadedRun.status);
        assertNotNull(loadedRun.error);
        assertEquals("crash", loadedRun.error.type());
        assertEquals(139, loadedRun.error.exitCode());
        assertEquals("SIGSEGV", loadedRun.error.message());
        assertEquals("stderr output", loadedRun.error.stderr());
    }

    @Test
    void roundTripWithComparison(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.branch = "main";
        manifest.commit = "abc";
        manifest.timestamp = "2026-04-07T10:00:00Z";
        manifest.profile = "ci";

        var comp = new Manifest.Comparison();
        comp.scene = "depth_test";
        comp.frame = 3;
        comp.type = "reference";
        comp.backend = "opengl";
        comp.profile = "ci";
        comp.status = "pass";
        comp.diffPercent = 0.001;
        comp.tolerance = Tolerance.loose();
        manifest.comparisons.add(comp);

        var crossComp = new Manifest.Comparison();
        crossComp.scene = "depth_test";
        crossComp.frame = 3;
        crossComp.type = "cross_backend";
        crossComp.backendA = "opengl";
        crossComp.backendB = "vulkan";
        crossComp.status = "fail";
        crossComp.diffPercent = 2.3;
        crossComp.tolerance = Tolerance.loose();
        crossComp.reason = "Diff 2.30% exceeds threshold 0.01%";
        manifest.comparisons.add(crossComp);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals(2, loaded.comparisons.size());
        var ref = loaded.comparisons.get(0);
        assertEquals("reference", ref.type);
        assertEquals("opengl", ref.backend);
        assertEquals(0.001, ref.diffPercent, 0.0001);

        var cross = loaded.comparisons.get(1);
        assertEquals("cross_backend", cross.type);
        assertEquals("opengl", cross.backendA);
        assertEquals("vulkan", cross.backendB);
        assertEquals("fail", cross.status);
        assertEquals("Diff 2.30% exceeds threshold 0.01%", cross.reason);
    }

    @Test
    void roundTripWithSpecialCharactersInStrings(@TempDir Path tmp) throws Exception {
        var manifest = new Manifest();
        manifest.branch = "feature/test-\"quotes\"";
        manifest.commit = "abc";
        manifest.timestamp = "2026-04-07T10:00:00Z";
        manifest.profile = "local";

        var run = new Manifest.Run();
        run.scene = "test";
        run.backend = "opengl";
        run.status = "exception";
        run.durationMs = 100;
        run.error = new Manifest.RunError("exception", 1,
                "Error: \"bad thing\"\nnewline", "line1\nline2\ttab", "");
        manifest.runs.add(run);

        var file = tmp.resolve("manifest.json");
        manifest.writeTo(file);
        var loaded = Manifest.readFrom(file);

        assertEquals("feature/test-\"quotes\"", loaded.branch);
        assertEquals("Error: \"bad thing\"\nnewline", loaded.runs.get(0).error.message());
        assertEquals("line1\nline2\ttab", loaded.runs.get(0).error.stderr());
    }
}

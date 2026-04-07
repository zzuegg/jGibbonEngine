package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.Tolerance;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ComparatorTest {

    @Test
    void matchingReferenceProducesPassComparison(@TempDir Path tmp) throws Exception {
        byte[] pixels = new byte[4 * 4 * 4];
        Arrays.fill(pixels, (byte) 128);
        writeScreenshot(tmp, "opengl", "test_scene_f3.png", pixels, 4, 4);
        writeReference(tmp, "opengl", "test_scene_f3.png", pixels, 4, 4);

        var manifest = createManifestWithRun("test_scene", "opengl", 3,
                "opengl/test_scene_f3.png", 4, 4);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        ScreenshotComparator.compare(manifestPath, tmp.resolve("screenshots"), tmp.resolve("references"));

        var loaded = Manifest.readFrom(manifestPath);
        var refs = loaded.comparisons.stream().filter(c -> "reference".equals(c.type)).toList();
        assertEquals(1, refs.size());
        assertEquals("pass", refs.get(0).status);
    }

    @Test
    void differentReferenceProducesFailComparison(@TempDir Path tmp) throws Exception {
        byte[] screenshot = new byte[4 * 4 * 4];
        Arrays.fill(screenshot, (byte) 255);
        byte[] reference = new byte[4 * 4 * 4];
        Arrays.fill(reference, (byte) 0);
        writeScreenshot(tmp, "opengl", "test_scene_f3.png", screenshot, 4, 4);
        writeReference(tmp, "opengl", "test_scene_f3.png", reference, 4, 4);

        var manifest = createManifestWithRun("test_scene", "opengl", 3,
                "opengl/test_scene_f3.png", 4, 4);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        ScreenshotComparator.compare(manifestPath, tmp.resolve("screenshots"), tmp.resolve("references"));

        var loaded = Manifest.readFrom(manifestPath);
        var refs = loaded.comparisons.stream().filter(c -> "reference".equals(c.type)).toList();
        assertEquals("fail", refs.get(0).status);
        assertTrue(refs.get(0).diffPercent > 0);
    }

    @Test
    void missingReferenceProducesNoReferenceStatus(@TempDir Path tmp) throws Exception {
        byte[] pixels = new byte[4 * 4 * 4];
        writeScreenshot(tmp, "opengl", "test_scene_f3.png", pixels, 4, 4);

        var manifest = createManifestWithRun("test_scene", "opengl", 3,
                "opengl/test_scene_f3.png", 4, 4);
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        ScreenshotComparator.compare(manifestPath, tmp.resolve("screenshots"), tmp.resolve("references"));

        var loaded = Manifest.readFrom(manifestPath);
        var refs = loaded.comparisons.stream().filter(c -> "reference".equals(c.type)).toList();
        assertEquals("no_reference", refs.get(0).status);
    }

    @Test
    void crossBackendComparisonsGeneratedForAllPairs(@TempDir Path tmp) throws Exception {
        byte[] pixels = new byte[4 * 4 * 4];
        Arrays.fill(pixels, (byte) 128);
        for (var backend : List.of("opengl", "vulkan", "webgpu")) {
            writeScreenshot(tmp, backend, "test_scene_f3.png", pixels, 4, 4);
        }

        var manifest = createMinimalManifest();
        addScene(manifest, "test_scene", 4, 4);
        for (var backend : List.of("opengl", "vulkan", "webgpu")) {
            addSuccessfulRun(manifest, "test_scene", backend, 3,
                    backend + "/test_scene_f3.png");
        }
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        ScreenshotComparator.compare(manifestPath, tmp.resolve("screenshots"), tmp.resolve("references"));

        var loaded = Manifest.readFrom(manifestPath);
        long crossCount = loaded.comparisons.stream()
                .filter(c -> "cross_backend".equals(c.type)).count();
        assertEquals(3, crossCount); // GL-VK, GL-WebGPU, VK-WebGPU
    }

    @Test
    void failedRunProducesSkippedComparison(@TempDir Path tmp) throws Exception {
        var manifest = createMinimalManifest();
        addScene(manifest, "crash_scene", 4, 4);
        var run = new Manifest.Run();
        run.scene = "crash_scene";
        run.backend = "vulkan";
        run.status = "crash";
        run.durationMs = 100;
        run.error = new Manifest.RunError("crash", 139, "SIGSEGV", "", "");
        manifest.runs.add(run);

        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        ScreenshotComparator.compare(manifestPath, tmp.resolve("screenshots"), tmp.resolve("references"));

        var loaded = Manifest.readFrom(manifestPath);
        var refs = loaded.comparisons.stream().filter(c -> "reference".equals(c.type)).toList();
        assertEquals(1, refs.size());
        assertEquals("skipped", refs.get(0).status);
        assertTrue(refs.get(0).reason.contains("crash"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void writeScreenshot(Path tmp, String backend, String filename,
                                  byte[] pixels, int w, int h) throws Exception {
        var dir = tmp.resolve("screenshots/" + backend);
        java.nio.file.Files.createDirectories(dir);
        ImageUtils.savePng(pixels, w, h, dir.resolve(filename));
    }

    private void writeReference(Path tmp, String backend, String filename,
                                 byte[] pixels, int w, int h) throws Exception {
        var dir = tmp.resolve("references/" + backend);
        java.nio.file.Files.createDirectories(dir);
        ImageUtils.savePng(pixels, w, h, dir.resolve(filename));
    }

    private Manifest createManifestWithRun(String name, String backend, int frame,
                                            String path, int w, int h) {
        var m = createMinimalManifest();
        addScene(m, name, w, h);
        addSuccessfulRun(m, name, backend, frame, path);
        return m;
    }

    private void addScene(Manifest m, String name, int w, int h) {
        var scene = new Manifest.Scene();
        scene.name = name;
        scene.category = "basic";
        scene.className = "Test";
        scene.fieldName = name.toUpperCase();
        scene.captureFrames = Set.of(3);
        scene.tolerance = Tolerance.loose();
        scene.width = w;
        scene.height = h;
        m.scenes.add(scene);
    }

    private void addSuccessfulRun(Manifest m, String scene, String backend,
                                   int frame, String path) {
        var run = new Manifest.Run();
        run.scene = scene;
        run.backend = backend;
        run.status = "success";
        run.durationMs = 100;
        run.screenshots = List.of(new Manifest.Screenshot(frame, path));
        m.runs.add(run);
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

package dev.engine.tests.screenshot.runner;

import dev.engine.tests.screenshot.scenes.Tolerance;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AbstractTestRunnerTest {

    @Test
    void runPopulatesManifestWithSuccessfulRun(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithScene("test_scene", "basic");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var config = new RunnerConfig(5000, "local", tmp.resolve("screenshots"), tmp.resolve("refs"), null);
        var runner = new FakeTestRunner(List.of("opengl"),
                (cls, field, backend, out) ->
                        new SceneResult.Success(Map.of(3, "opengl/test_scene_f3.png")));
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(1, loaded.runs.size());
        assertEquals("success", loaded.runs.get(0).status);
        assertEquals("opengl", loaded.runs.get(0).backend);
        assertEquals(1, loaded.runs.get(0).screenshots.size());
    }

    @Test
    void runPopulatesManifestWithCrash(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithScene("crash_scene", "basic");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var config = new RunnerConfig(5000, "local", tmp.resolve("screenshots"), tmp.resolve("refs"), null);
        var runner = new FakeTestRunner(List.of("vulkan"),
                (cls, field, backend, out) ->
                        new SceneResult.Crash(139, "SIGSEGV", ""));
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(1, loaded.runs.size());
        assertEquals("crash", loaded.runs.get(0).status);
        assertNotNull(loaded.runs.get(0).error);
        assertEquals(139, loaded.runs.get(0).error.exitCode());
    }

    @Test
    void runContinuesAfterFailure(@TempDir Path tmp) throws Exception {
        var manifest = createMinimalManifest();
        addScene(manifest, "scene_a", "basic");
        addScene(manifest, "scene_b", "basic");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var config = new RunnerConfig(5000, "local", tmp.resolve("screenshots"), tmp.resolve("refs"), null);
        var callCount = new int[]{0};
        var runner = new FakeTestRunner(List.of("opengl"),
                (cls, field, backend, out) -> {
                    if (callCount[0]++ == 0)
                        return new SceneResult.Crash(139, "SIGSEGV", "");
                    return new SceneResult.Success(Map.of(3, "opengl/scene_b_f3.png"));
                });
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(2, loaded.runs.size());
        assertEquals("crash", loaded.runs.get(0).status);
        assertEquals("success", loaded.runs.get(1).status);
    }

    @Test
    void runIteratesAllBackends(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithScene("test_scene", "basic");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var config = new RunnerConfig(5000, "local", tmp.resolve("screenshots"), tmp.resolve("refs"), null);
        var runner = new FakeTestRunner(List.of("opengl", "vulkan"),
                (cls, field, backend, out) ->
                        new SceneResult.Success(Map.of(3, backend + "/test_scene_f3.png")));
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals(2, loaded.runs.size());
        assertEquals("opengl", loaded.runs.get(0).backend);
        assertEquals("vulkan", loaded.runs.get(1).backend);
    }

    @Test
    void exceptionResultPopulatesError(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithScene("exc_scene", "basic");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var config = new RunnerConfig(5000, "local", tmp.resolve("screenshots"), tmp.resolve("refs"), null);
        var runner = new FakeTestRunner(List.of("opengl"),
                (cls, field, backend, out) ->
                        new SceneResult.ExceptionResult("Shader failed", "at Foo.bar(Foo.java:42)"));
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals("exception", loaded.runs.get(0).status);
        assertEquals("exception", loaded.runs.get(0).error.type());
        assertEquals("Shader failed", loaded.runs.get(0).error.message());
    }

    @Test
    void timeoutResultPopulatesError(@TempDir Path tmp) throws Exception {
        var manifest = createManifestWithScene("timeout_scene", "basic");
        var manifestPath = tmp.resolve("manifest.json");
        manifest.writeTo(manifestPath);

        var config = new RunnerConfig(5000, "local", tmp.resolve("screenshots"), tmp.resolve("refs"), null);
        var runner = new FakeTestRunner(List.of("webgpu"),
                (cls, field, backend, out) ->
                        new SceneResult.Timeout("last stderr", "last stdout"));
        runner.run(manifestPath, config);

        var loaded = Manifest.readFrom(manifestPath);
        assertEquals("timeout", loaded.runs.get(0).status);
        assertEquals("timeout", loaded.runs.get(0).error.type());
    }

    private Manifest createManifestWithScene(String name, String category) {
        var m = createMinimalManifest();
        addScene(m, name, category);
        return m;
    }

    private void addScene(Manifest m, String name, String category) {
        var scene = new Manifest.Scene();
        scene.name = name;
        scene.category = category;
        scene.className = "com.example.TestScenes";
        scene.fieldName = name.toUpperCase();
        scene.captureFrames = Set.of(3);
        scene.tolerance = Tolerance.loose();
        scene.width = 256;
        scene.height = 256;
        m.scenes.add(scene);
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

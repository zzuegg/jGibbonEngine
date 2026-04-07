package dev.engine.tests.screenshot.runner;

import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Abstract test runner that orchestrates scene rendering across backends.
 * Reads the manifest, iterates all scenes × backends, delegates rendering
 * to {@link #runScene}, and updates the manifest after each scene.
 *
 * <p>Concrete implementations provide the backend list and the actual
 * rendering logic (typically spawning a child JVM process).
 */
public abstract class AbstractTestRunner {

    /** Returns the list of backend names this runner supports. */
    public abstract List<String> backends();

    /**
     * Renders a single scene on a single backend.
     *
     * @param className fully qualified class containing the scene field
     * @param fieldName the static final field name
     * @param backend   the backend name (e.g. "opengl", "vulkan")
     * @param outputDir directory to write screenshot PNGs
     * @param config    the scene's configuration
     * @return the result of the render attempt
     */
    protected abstract SceneResult runScene(String className, String fieldName,
                                             String backend, Path outputDir, SceneConfig config);

    /**
     * Runs all scenes from the manifest on all backends.
     * Updates the manifest with run results after each scene+backend combination.
     */
    public void run(Path manifestPath, RunnerConfig config) throws Exception {
        var manifest = Manifest.readFrom(manifestPath);

        for (var scene : manifest.scenes) {
            var sceneConfig = new SceneConfig(
                    scene.width, scene.height,
                    scene.captureFrames, scene.tolerance,
                    false, Map.of());

            for (var backend : backends()) {
                long start = System.currentTimeMillis();
                SceneResult result;
                try {
                    result = runScene(scene.className, scene.fieldName,
                            backend, config.outputDir(), sceneConfig);
                } catch (Exception e) {
                    result = new SceneResult.ExceptionResult(e.getMessage(), stackTraceToString(e));
                }
                long duration = System.currentTimeMillis() - start;

                var run = toManifestRun(scene.name, backend, result, duration);
                manifest.runs.add(run);

                // Write after each scene so crashes don't lose prior results
                manifest.writeTo(manifestPath);
            }
        }
    }

    private Manifest.Run toManifestRun(String sceneName, String backend,
                                        SceneResult result, long durationMs) {
        var run = new Manifest.Run();
        run.scene = sceneName;
        run.backend = backend;
        run.durationMs = durationMs;

        switch (result) {
            case SceneResult.Success success -> {
                run.status = "success";
                var screenshots = new ArrayList<Manifest.Screenshot>();
                for (var entry : new TreeSet<>(success.screenshotPaths().keySet())) {
                    screenshots.add(new Manifest.Screenshot(entry, success.screenshotPaths().get(entry)));
                }
                run.screenshots = screenshots;
            }
            case SceneResult.ExceptionResult exc -> {
                run.status = "exception";
                run.error = new Manifest.RunError("exception", 1, exc.message(), exc.stackTrace(), "");
            }
            case SceneResult.Crash crash -> {
                run.status = "crash";
                run.error = new Manifest.RunError("crash", crash.exitCode(), crash.stderr(), crash.stderr(), crash.stdout());
            }
            case SceneResult.Timeout timeout -> {
                run.status = "timeout";
                run.error = new Manifest.RunError("timeout", -1,
                        "Process did not complete within timeout", timeout.stderr(), timeout.stdout());
            }
        }

        return run;
    }

    static String stackTraceToString(Exception e) {
        var sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}

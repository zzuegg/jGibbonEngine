package dev.engine.tests.screenshot.desktop;

import dev.engine.tests.screenshot.runner.AbstractTestRunner;
import dev.engine.tests.screenshot.runner.SceneResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Desktop test runner that spawns a separate JVM process for each scene+backend.
 * This provides complete isolation — a native crash in one scene cannot affect others.
 */
public class DesktopRunner extends AbstractTestRunner {

    private final long timeoutMs;

    public DesktopRunner(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public List<String> backends() {
        // All desktop backends — individual scenes may skip unavailable ones
        return List.of("opengl", "vulkan", "webgpu");
    }

    @Override
    protected SceneResult runScene(String className, String fieldName,
                                    String backend, Path outputDir) {
        var resultFile = outputDir.resolve(
                backend + "_" + fieldName.toLowerCase() + "_result.json");
        try {
            Files.createDirectories(outputDir);
            var pb = buildProcess(className, fieldName, backend, outputDir, resultFile);
            var process = pb.start();

            // Capture stdout/stderr in background threads to avoid blocking
            var stdoutReader = new StreamCapture(process.getInputStream());
            var stderrReader = new StreamCapture(process.getErrorStream());
            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                stdoutReader.join(1000);
                stderrReader.join(1000);
                return new SceneResult.Timeout(
                        truncate(stderrReader.captured(), 5000),
                        truncate(stdoutReader.captured(), 5000));
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);

            int exitCode = process.exitValue();
            if (exitCode == 0 && Files.exists(resultFile)) {
                var childResult = ChildResult.readFrom(resultFile);
                if ("success".equals(childResult.status())) {
                    var paths = new HashMap<Integer, String>();
                    for (var s : childResult.screenshots()) paths.put(s.frame(), s.path());
                    return new SceneResult.Success(paths);
                } else {
                    return new SceneResult.ExceptionResult(
                            childResult.message(), childResult.stackTrace());
                }
            } else if (Files.exists(resultFile)) {
                var childResult = ChildResult.readFrom(resultFile);
                return new SceneResult.ExceptionResult(
                        childResult.message(), childResult.stackTrace());
            } else {
                return new SceneResult.Crash(exitCode,
                        truncate(stderrReader.captured(), 5000),
                        truncate(stdoutReader.captured(), 5000));
            }
        } catch (Exception e) {
            return new SceneResult.ExceptionResult(e.getMessage(), stackTraceToString(e));
        } finally {
            // Clean up result file
            try { Files.deleteIfExists(resultFile); } catch (Exception ignored) {}
        }
    }

    private ProcessBuilder buildProcess(String className, String fieldName,
                                         String backend, Path outputDir, Path resultFile) {
        var javaHome = System.getProperty("java.home");
        var java = Path.of(javaHome, "bin", "java").toString();
        var classpath = System.getProperty("java.class.path");

        var args = new ArrayList<String>();
        args.add(java);
        args.add("--enable-native-access=ALL-UNNAMED");
        args.add("-cp");
        args.add(classpath);
        args.add(DesktopRenderMain.class.getName());
        args.add(className);
        args.add(fieldName);
        args.add(backend);
        args.add(outputDir.toAbsolutePath().toString());
        args.add(resultFile.toAbsolutePath().toString());

        var pb = new ProcessBuilder(args);
        pb.redirectErrorStream(false);
        // Inherit working directory so Slang native library can be found at tools/lib/
        pb.directory(new File(System.getProperty("user.dir")));

        // jemalloc preload to avoid glibc heap corruption with Slang COM objects
        var jemallocPaths = List.of(
                "/lib/x86_64-linux-gnu/libjemalloc.so.2",
                "/usr/lib/libjemalloc.so.2",
                "/usr/lib64/libjemalloc.so.2",
                "/opt/homebrew/lib/libjemalloc.dylib");
        for (var path : jemallocPaths) {
            if (new File(path).exists()) {
                pb.environment().put("LD_PRELOAD", path);
                break;
            }
        }

        return pb;
    }

    private static String stackTraceToString(Exception e) {
        var sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? "..." + s.substring(s.length() - maxLen) : s;
    }

    /**
     * Captures an InputStream into a String in a background thread.
     */
    private static class StreamCapture extends Thread {
        private final java.io.InputStream stream;
        private String result = "";

        StreamCapture(java.io.InputStream stream) {
            this.stream = stream;
            setDaemon(true);
        }

        @Override
        public void run() {
            try { result = new String(stream.readAllBytes()); } catch (Exception ignored) {}
        }

        String captured() { return result; }
    }
}

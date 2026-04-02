package dev.engine.core.shader;

import dev.engine.core.native_.NativeLibraryLoader;
import dev.engine.core.native_.NativeLibraryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime Slang shader compiler. Invokes slangc to compile .slang source
 * to GLSL, SPIR-V, or WGSL. Results are cached by content hash.
 */
public class SlangCompiler {

    private static final Logger log = LoggerFactory.getLogger(SlangCompiler.class);

    private final Path slangcPath;
    private final List<Path> searchPaths = new ArrayList<>();
    private final Map<String, GlslCompileResult> glslCache = new ConcurrentHashMap<>();
    private final Map<String, SpirvCompileResult> spirvCache = new ConcurrentHashMap<>();

    private SlangCompiler(Path slangcPath) {
        this.slangcPath = slangcPath;
    }

    /**
     * Finds slangc on the system. Checks tools/bin/ in the project, then PATH.
     */
    public static SlangCompiler find() {
        // Search common locations
        var candidates = new String[]{
                "tools/bin/slangc",
                System.getProperty("user.dir") + "/tools/bin/slangc",
        };

        // Also walk up from working directory to find project root
        var dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            var candidate = dir.resolve("tools/bin/slangc");
            if (Files.isExecutable(candidate)) {
                log.info("Found slangc at {}", candidate);
                return new SlangCompiler(candidate);
            }
            dir = dir.getParent();
        }

        for (var c : candidates) {
            var p = Path.of(c);
            if (Files.isExecutable(p)) {
                log.info("Found slangc at {}", p.toAbsolutePath());
                return new SlangCompiler(p.toAbsolutePath());
            }
        }

        // Check PATH
        try {
            var proc = new ProcessBuilder("which", "slangc")
                    .redirectErrorStream(true).start();
            var output = new String(proc.getInputStream().readAllBytes()).trim();
            if (proc.waitFor() == 0 && !output.isEmpty()) {
                log.info("Found slangc at {}", output);
                return new SlangCompiler(Path.of(output));
            }
        } catch (Exception ignored) {}

        // Auto-download via NativeLibraryLoader
        log.info("slangc not found locally — attempting auto-download...");
        try {
            var loader = NativeLibraryLoader.defaultLoader();
            var result = loader.resolve(SlangSpec.spec());
            if (result.isAvailable()) {
                var slangcPath = result.executablePath("slangc");
                if (slangcPath != null && Files.isExecutable(slangcPath)) {
                    log.info("Found slangc via native loader at {}", slangcPath);
                    return new SlangCompiler(slangcPath);
                }
            }
        } catch (Exception e) {
            log.warn("Auto-download of slangc failed: {}", e.getMessage());
        }

        log.warn("slangc not found — Slang compilation unavailable");
        return new SlangCompiler(null);
    }

    public static SlangCompiler at(Path path) {
        return new SlangCompiler(path);
    }

    public boolean isAvailable() { return slangcPath != null; }

    public void addSearchPath(Path path) { searchPaths.add(path); }

    public GlslCompileResult compileToGlsl(String source, String entryPoint, ShaderStageType stage) {
        var cacheKey = source.hashCode() + ":" + entryPoint + ":" + stage.name() + ":glsl";
        return glslCache.computeIfAbsent(cacheKey, k -> doCompileGlsl(source, entryPoint, stage));
    }

    public SpirvCompileResult compileToSpirv(String source, String entryPoint, ShaderStageType stage) {
        var cacheKey = source.hashCode() + ":" + entryPoint + ":" + stage.name() + ":spirv";
        return spirvCache.computeIfAbsent(cacheKey, k -> doCompileSpirv(source, entryPoint, stage));
    }

    public void clearCache() {
        glslCache.clear();
        spirvCache.clear();
    }

    private GlslCompileResult doCompileGlsl(String source, String entryPoint, ShaderStageType stage) {
        try {
            var tmpSource = Files.createTempFile("slang_", ".slang");
            var tmpOutput = Files.createTempFile("slang_", ".glsl");
            try {
                Files.writeString(tmpSource, source);
                var cmd = buildCommand(tmpSource, tmpOutput, entryPoint, stage, "glsl");
                var result = runSlangc(cmd);
                if (result.exitCode() == 0) {
                    return GlslCompileResult.ok(Files.readString(tmpOutput));
                } else {
                    return GlslCompileResult.fail(result.stderr());
                }
            } finally {
                Files.deleteIfExists(tmpSource);
                Files.deleteIfExists(tmpOutput);
            }
        } catch (Exception e) {
            return GlslCompileResult.fail("Compiler error: " + e.getMessage());
        }
    }

    private SpirvCompileResult doCompileSpirv(String source, String entryPoint, ShaderStageType stage) {
        try {
            var tmpSource = Files.createTempFile("slang_", ".slang");
            var tmpOutput = Files.createTempFile("slang_", ".spv");
            try {
                Files.writeString(tmpSource, source);
                var cmd = buildCommand(tmpSource, tmpOutput, entryPoint, stage, "spirv");
                var result = runSlangc(cmd);
                if (result.exitCode() == 0) {
                    return SpirvCompileResult.ok(Files.readAllBytes(tmpOutput));
                } else {
                    return SpirvCompileResult.fail(result.stderr());
                }
            } finally {
                Files.deleteIfExists(tmpSource);
                Files.deleteIfExists(tmpOutput);
            }
        } catch (Exception e) {
            return SpirvCompileResult.fail("Compiler error: " + e.getMessage());
        }
    }

    private List<String> buildCommand(Path source, Path output, String entryPoint, ShaderStageType stage, String target) {
        var cmd = new ArrayList<String>();
        cmd.add(slangcPath.toString());
        cmd.add(source.toString());
        cmd.add("-target"); cmd.add(target);
        cmd.add("-entry"); cmd.add(entryPoint);
        cmd.add("-stage"); cmd.add(stage.slangStage());
        cmd.add("-o"); cmd.add(output.toString());
        for (var sp : searchPaths) {
            cmd.add("-I"); cmd.add(sp.toString());
        }
        return cmd;
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {}

    private ProcessResult runSlangc(List<String> cmd) throws IOException, InterruptedException {
        // Set LD_LIBRARY_PATH to include slangc's directory and parent lib/
        var slangDir = slangcPath.getParent();
        var libDir = slangDir.getParent().resolve("lib");
        var pb = new ProcessBuilder(cmd);
        var env = pb.environment();
        var existingLdPath = env.getOrDefault("LD_LIBRARY_PATH", "");
        var newPath = slangDir + ":" + libDir;
        env.put("LD_LIBRARY_PATH", newPath + (existingLdPath.isEmpty() ? "" : ":" + existingLdPath));

        var proc = pb.start();
        var stdout = new String(proc.getInputStream().readAllBytes());
        var stderr = new String(proc.getErrorStream().readAllBytes());
        int exitCode = proc.waitFor();
        return new ProcessResult(exitCode, stdout, stderr);
    }
}

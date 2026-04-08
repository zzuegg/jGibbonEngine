package dev.engine.providers.slang.graalwasm;

import dev.engine.providers.slang.wasm.SlangWasmBridge;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GraalVM polyglot implementation of {@link SlangWasmBridge}.
 *
 * <p>Loads the Slang WASM compiler via GraalJS (which delegates WASM execution
 * to GraalWasm). The Emscripten-compiled {@code slang-wasm.wasm} binary is
 * pre-read in Java and passed as {@code wasmBinary} to the Emscripten module
 * initialization, avoiding the need for browser {@code fetch} or Node.js {@code fs}.
 *
 * <p>The embind-generated API (createGlobalSession, loadModuleFromSource, etc.)
 * is called through GraalVM's polyglot {@link Value} interop — the same API that
 * TeaVM's {@code @JSBody} calls in the browser, but running on the JVM.
 *
 * <p>Thread safety: all calls are synchronized on the polyglot context, which is
 * single-threaded. The Slang WASM module is not thread-safe.
 *
 * <pre>{@code
 * try (var bridge = new GraalSlangWasmBridge(Path.of("tools/slang-wasm"))) {
 *     var compiler = new SlangWasmCompiler(bridge);
 *     var result = compiler.compile(source, entryPoints, TARGET_WGSL);
 * }
 * }</pre>
 */
public class GraalSlangWasmBridge implements SlangWasmBridge, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(GraalSlangWasmBridge.class);

    private final Context context;
    private final Value compileFn;
    private final Value compileComputeFn;
    private volatile boolean available;

    /**
     * Creates a new bridge, loading the Slang WASM module from the given directory.
     *
     * @param slangWasmDir directory containing {@code slang-wasm.mjs} and {@code slang-wasm.wasm}
     */
    public GraalSlangWasmBridge(Path slangWasmDir) {
        this.context = Context.newBuilder("js")
                .allowAllAccess(true)
                .allowExperimentalOptions(true)
                .allowIO(IOAccess.ALL)
                .option("js.esm-eval-returns-exports", "true")
                .build();

        Value compile = null;
        Value compileCompute = null;
        try {
            initializeModule(slangWasmDir);
            compile = context.eval("js", COMPILE_FN);
            compileCompute = context.eval("js", COMPILE_COMPUTE_FN);
            this.available = true;
            log.info("Slang WASM compiler loaded via GraalWasm from {}", slangWasmDir);
        } catch (Exception e) {
            log.warn("Failed to load Slang WASM compiler from {}: {}", slangWasmDir, e.getMessage());
            this.available = false;
        }
        this.compileFn = compile;
        this.compileComputeFn = compileCompute;
    }

    private void initializeModule(Path dir) throws IOException {
        Path wasmPath = dir.resolve("slang-wasm.wasm");
        if (!Files.exists(wasmPath)) {
            throw new IOException("slang-wasm.wasm not found in " + dir);
        }
        Path mjsPath = dir.resolve("slang-wasm.mjs");
        if (!Files.exists(mjsPath)) {
            throw new IOException("slang-wasm.mjs not found in " + dir);
        }

        // Pre-read the WASM binary in Java and pass it to the JS context.
        // This avoids Emscripten's file loading (which needs browser fetch or Node fs).
        byte[] wasmBytes = Files.readAllBytes(wasmPath);
        context.getBindings("js").putMember("_javaWasmBytes", wasmBytes);

        String mjsUri = mjsPath.toUri().toString();

        // Initialization script (evaluated as ES module for top-level await):
        // 1. Convert Java byte[] to JS ArrayBuffer for Emscripten's wasmBinary option
        // 2. Dynamic-import the Emscripten .mjs glue
        // 3. Initialize with wasmBinary to skip file loading
        String initScript = INIT_TEMPLATE.replace("${MJS_URI}", mjsUri);

        Source initSource = Source.newBuilder("js", initScript, "slang-init.mjs")
                .mimeType("application/javascript+module")
                .build();
        context.eval(initSource);
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public synchronized String[] compile(String source, String vertexEntry, String fragmentEntry) {
        if (!available) throw new IllegalStateException("Slang WASM not loaded");
        Value result = compileFn.execute(source, vertexEntry, fragmentEntry);
        return new String[]{
                result.getArrayElement(0).asString(),
                result.getArrayElement(1).asString()
        };
    }

    @Override
    public synchronized String compileCompute(String source, String entryName) {
        if (!available) throw new IllegalStateException("Slang WASM not loaded");
        return compileComputeFn.execute(source, entryName).asString();
    }

    @Override
    public void close() {
        context.close();
    }

    // =====================================================================
    // JS templates — same compilation logic as TeaVmSlangCompiler, executed
    // through GraalJS + GraalWasm instead of TeaVM JSO.
    // =====================================================================

    private static final String INIT_TEMPLATE = """
            // Convert Java byte[] to ArrayBuffer for Emscripten's wasmBinary option
            var _jBytes = globalThis._javaWasmBytes;
            var _ab = new ArrayBuffer(_jBytes.length);
            var _u8 = new Uint8Array(_ab);
            for (var _i = 0; _i < _jBytes.length; _i++) _u8[_i] = _jBytes[_i] & 0xFF;

            // Import the Emscripten .mjs glue and initialize with pre-loaded WASM binary
            var _mod = await import('${MJS_URI}');
            globalThis._slangModule = await _mod.default({ wasmBinary: _ab });
            """;

    /**
     * Compiles vertex + fragment entry points to WGSL.
     * Returns a JS array of [vertexWGSL, fragmentWGSL].
     */
    private static final String COMPILE_FN = """
            (function(source, vertexEntry, fragmentEntry) {
                var slang = globalThis._slangModule;
                if (!slang) throw new Error('Slang WASM not loaded');

                var globalSession = slang.createGlobalSession();
                if (!globalSession) throw new Error('Failed to create Slang global session');

                // Find WGSL target dynamically
                var targets = slang.getCompileTargets();
                var wgslTarget = 0;
                for (var i = 0; i < targets.length; i++) {
                    if (targets[i].name === 'WGSL') {
                        wgslTarget = targets[i].value;
                        break;
                    }
                }
                if (wgslTarget === 0) throw new Error('WGSL target not found in compile targets');

                var session = globalSession.createSession(wgslTarget);
                if (!session) throw new Error('Failed to create WGSL session');

                var mod = session.loadModuleFromSource('' + source, 'shader', 'shader.slang');
                if (!mod) {
                    var err = slang.getLastError();
                    throw new Error('Slang compile error: ' + (err ? err.message : 'unknown'));
                }

                // Stage: vertex=1, fragment=5
                var vertexEP = mod.findAndCheckEntryPoint(vertexEntry, 1);
                var fragmentEP = mod.findAndCheckEntryPoint(fragmentEntry, 5);
                if (!vertexEP) throw new Error('Vertex entry point not found: ' + vertexEntry);
                if (!fragmentEP) throw new Error('Fragment entry point not found: ' + fragmentEntry);

                var components = session.createCompositeComponentType([mod, vertexEP, fragmentEP]);
                var linked = components.link();
                var vertexWGSL = linked.getEntryPointCode(0, 0);
                var fragmentWGSL = linked.getEntryPointCode(1, 0);

                linked.delete();
                components.delete();
                fragmentEP.delete();
                vertexEP.delete();
                mod.delete();
                session.delete();
                globalSession.delete();

                return [vertexWGSL, fragmentWGSL];
            })
            """;

    /**
     * Compiles a single compute entry point to WGSL.
     */
    private static final String COMPILE_COMPUTE_FN = """
            (function(source, entryName) {
                var slang = globalThis._slangModule;
                if (!slang) throw new Error('Slang WASM not loaded');

                var globalSession = slang.createGlobalSession();
                if (!globalSession) throw new Error('Failed to create Slang global session');

                var targets = slang.getCompileTargets();
                var wgslTarget = 0;
                for (var i = 0; i < targets.length; i++) {
                    if (targets[i].name === 'WGSL') {
                        wgslTarget = targets[i].value;
                        break;
                    }
                }
                if (wgslTarget === 0) throw new Error('WGSL target not found');

                var session = globalSession.createSession(wgslTarget);
                var mod = session.loadModuleFromSource(source, 'shader', 'shader.slang');
                if (!mod) {
                    var err = slang.getLastError();
                    throw new Error('Slang compile error: ' + (err ? err.message : 'unknown'));
                }

                var ep = mod.findEntryPointByName(entryName);
                if (!ep) throw new Error('Entry point not found: ' + entryName);

                var components = session.createCompositeComponentType([mod, ep]);
                var linked = components.link();
                var wgsl = linked.getEntryPointCode(0, 0);

                linked.delete();
                components.delete();
                ep.delete();
                mod.delete();
                session.delete();
                globalSession.delete();

                return wgsl;
            })
            """;
}

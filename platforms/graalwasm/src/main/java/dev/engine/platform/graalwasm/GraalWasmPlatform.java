package dev.engine.platform.graalwasm;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.graphics.webgpu.WebGpuConfig;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.providers.graal.webgpu.GraalWgpuBindings;
import dev.engine.providers.graal.webgpu.GraalWgpuInit;
import dev.engine.providers.graal.windowing.GraalCanvasWindowToolkit;
import dev.engine.providers.slang.graalwasm.GraalSlangWasmBridge;
import dev.engine.providers.slang.wasm.SlangWasmCompiler;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;

import java.nio.file.Path;

/**
 * GraalWasm web platform configuration.
 *
 * <p>Creates and owns a shared GraalJS {@link Context} used by all providers
 * (WebGPU bindings, canvas windowing, Slang WASM compiler). Provides the same
 * platform services as the TeaVM web platform but running on the JVM.
 *
 * <pre>{@code
 * var platform = GraalWasmPlatform.builder()
 *     .slangWasmDir(Path.of("tools/slang-wasm"))
 *     .assetBaseUrl("assets/")
 *     .build();
 *
 * // platform.context()    — shared GraalJS context
 * // platform.toolkit()    — canvas windowing
 * // platform.bindings()   — WebGPU bindings
 * // platform.graphicsConfig() — ready-to-use WebGpuConfig
 * }</pre>
 */
public final class GraalWasmPlatform implements Platform, AutoCloseable {

    private final Context context;
    private final GraalCanvasWindowToolkit toolkit;
    private final GraalWgpuBindings bindings;
    private final ShaderCompiler compiler;
    private final String assetBaseUrl;

    private GraalWasmPlatform(Context context, GraalCanvasWindowToolkit toolkit,
                              GraalWgpuBindings bindings, ShaderCompiler compiler,
                              String assetBaseUrl) {
        this.context = context;
        this.toolkit = toolkit;
        this.bindings = bindings;
        this.compiler = compiler;
        this.assetBaseUrl = assetBaseUrl;
    }

    @Override
    public void configureAssets(AssetManager assets) {
        // Asset loading via fetch — uses GraalJS context to call fetch() API
        assets.addSource(new GraalFetchAssetSource(context, assetBaseUrl));
        assets.registerLoader(new SlangShaderLoader());
    }

    @Override
    public ShaderCompiler shaderCompiler() {
        return compiler;
    }

    /** The shared GraalJS context. All providers operate in this context. */
    public Context context() { return context; }

    /** The canvas windowing toolkit. */
    public WindowToolkit toolkit() { return toolkit; }

    /** The WebGPU bindings. */
    public GraalWgpuBindings bindings() { return bindings; }

    /**
     * Returns a ready-to-use {@link WebGpuConfig} for engine configuration.
     * Equivalent to {@code new WebGpuConfig(toolkit(), bindings())}.
     */
    public WebGpuConfig graphicsConfig() {
        return new WebGpuConfig(toolkit, bindings);
    }

    /**
     * Initializes WebGPU (adapter + device). Must be called before rendering.
     *
     * @return the device handle, or 0 on failure
     */
    public int initWebGpu() {
        return GraalWgpuInit.initAsync(context);
    }

    @Override
    public void close() {
        context.close();
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String assetBaseUrl = "assets/";
        private Path slangWasmDir;

        public Builder assetBaseUrl(String url) { this.assetBaseUrl = url; return this; }
        public Builder slangWasmDir(Path dir) { this.slangWasmDir = dir; return this; }

        public GraalWasmPlatform build() {
            var context = Context.newBuilder("js")
                    .allowAllAccess(true)
                    .allowExperimentalOptions(true)
                    .allowIO(IOAccess.ALL)
                    .option("js.esm-eval-returns-exports", "true")
                    .build();

            var toolkit = new GraalCanvasWindowToolkit(context);
            var bindings = new GraalWgpuBindings(context);

            ShaderCompiler compiler;
            if (slangWasmDir != null) {
                var bridge = new GraalSlangWasmBridge(slangWasmDir);
                compiler = new SlangWasmCompiler(bridge);
            } else {
                // No-op compiler — shaders must be pre-compiled
                compiler = new NoOpShaderCompiler();
            }

            return new GraalWasmPlatform(context, toolkit, bindings, compiler, assetBaseUrl);
        }
    }

    /** Fallback compiler when no Slang WASM directory is configured. */
    private static final class NoOpShaderCompiler implements ShaderCompiler {
        @Override public boolean isAvailable() { return false; }
        @Override public CompileResult compile(String source,
                java.util.List<EntryPointDesc> entryPoints, int target) {
            throw new UnsupportedOperationException("No shader compiler configured");
        }
        @Override public CompileResult compileWithTypeMap(String source,
                java.util.List<EntryPointDesc> entryPoints, int target,
                java.util.Map<String, String> typeMap) {
            throw new UnsupportedOperationException("No shader compiler configured");
        }
    }
}

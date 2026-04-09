package dev.engine.platform.graalwasm;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.graphics.webgpu.WebGpuConfig;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.providers.graal.webgpu.GraalWgpuBindings;
import dev.engine.providers.graal.windowing.GraalCanvasWindowToolkit;
import dev.engine.providers.slang.graalwasm.GraalSlangWasmBridge;
import dev.engine.providers.slang.wasm.SlangWasmCompiler;

/**
 * GraalVM Web Image platform. Wires GraalWasm providers using {@code @JS}
 * interop. Compiled to WASM via {@code native-image --tool:svm-wasm}.
 *
 * <p>All browser API initialization (WebGPU adapter/device, Slang WASM module)
 * must be done by the host HTML page before {@code GraalVM.run()} is called.
 */
public final class GraalWasmPlatform implements Platform {

    private final GraalCanvasWindowToolkit toolkit;
    private final GraalWgpuBindings bindings;
    private final ShaderCompiler compiler;
    private final String assetBaseUrl;

    private GraalWasmPlatform(GraalCanvasWindowToolkit toolkit,
                              GraalWgpuBindings bindings,
                              ShaderCompiler compiler,
                              String assetBaseUrl) {
        this.toolkit = toolkit;
        this.bindings = bindings;
        this.compiler = compiler;
        this.assetBaseUrl = assetBaseUrl;
    }

    @Override
    public void configureAssets(AssetManager assets) {
        assets.addSource(new GraalFetchAssetSource(assetBaseUrl));
        assets.registerLoader(new SlangShaderLoader());
    }

    @Override
    public ShaderCompiler shaderCompiler() { return compiler; }

    public WindowToolkit toolkit() { return toolkit; }
    public GraalWgpuBindings bindings() { return bindings; }
    public WebGpuConfig graphicsConfig() { return new WebGpuConfig(toolkit, bindings); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String assetBaseUrl = "assets/";

        public Builder assetBaseUrl(String url) { this.assetBaseUrl = url; return this; }

        public GraalWasmPlatform build() {
            var toolkit = new GraalCanvasWindowToolkit();
            var bindings = new GraalWgpuBindings();
            var bridge = new GraalSlangWasmBridge();
            ShaderCompiler compiler = new SlangWasmCompiler(bridge);
            return new GraalWasmPlatform(toolkit, bindings, compiler, assetBaseUrl);
        }
    }
}

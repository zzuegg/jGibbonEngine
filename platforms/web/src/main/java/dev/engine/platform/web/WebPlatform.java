package dev.engine.platform.web;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.providers.teavm.webgpu.FetchAssetSource;
import dev.engine.providers.teavm.webgpu.TeaVmShaderCompiler;

/**
 * Web platform configuration for TeaVM-compiled applications.
 *
 * <p>Provides fetch-based asset loading and Slang WASM shader compilation.
 *
 * <pre>{@code
 * var platform = WebPlatform.builder()
 *     .assetBaseUrl("assets/")
 *     .build();
 * }</pre>
 */
public final class WebPlatform implements Platform {

    private final String assetBaseUrl;
    private final ShaderCompiler compiler;

    private WebPlatform(String assetBaseUrl, ShaderCompiler compiler) {
        this.assetBaseUrl = assetBaseUrl;
        this.compiler = compiler;
    }

    @Override
    public void configureAssets(AssetManager assets) {
        assets.addSource(new FetchAssetSource(assetBaseUrl));
        assets.registerLoader(new SlangShaderLoader());
    }

    @Override
    public ShaderCompiler shaderCompiler() {
        return compiler;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String assetBaseUrl = "assets/";
        private ShaderCompiler compiler;

        public Builder assetBaseUrl(String url) {
            this.assetBaseUrl = url;
            return this;
        }

        public Builder shaderCompiler(ShaderCompiler compiler) {
            this.compiler = compiler;
            return this;
        }

        public WebPlatform build() {
            if (compiler == null) {
                compiler = new TeaVmShaderCompiler();
            }
            return new WebPlatform(assetBaseUrl, compiler);
        }
    }
}

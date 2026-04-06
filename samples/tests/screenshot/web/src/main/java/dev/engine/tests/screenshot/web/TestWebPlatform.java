package dev.engine.tests.screenshot.web;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import dev.engine.providers.teavm.webgpu.FetchAssetSource;
import dev.engine.providers.teavm.webgpu.TeaVmShaderCompiler;

/**
 * Minimal web platform for screenshot tests.
 *
 * <p>Equivalent to {@code WebPlatform.builder().build()} but avoids
 * depending on {@code :platforms:web} which also applies the TeaVM plugin
 * and creates a circular Gradle dependency.
 */
final class TestWebPlatform implements Platform {

    private final ShaderCompiler compiler = new TeaVmShaderCompiler();

    @Override
    public void configureAssets(AssetManager assets) {
        assets.addSource(new FetchAssetSource("assets/"));
        assets.registerLoader(new SlangShaderLoader());
    }

    @Override
    public ShaderCompiler shaderCompiler() {
        return compiler;
    }
}

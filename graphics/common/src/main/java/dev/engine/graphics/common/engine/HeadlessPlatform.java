package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.graphics.common.NoOpShaderCompiler;
import dev.engine.graphics.shader.ShaderCompiler;

/**
 * Minimal platform for headless/testing use.
 * No asset sources, no-op shader compiler.
 */
public final class HeadlessPlatform implements Platform {

    public static final HeadlessPlatform INSTANCE = new HeadlessPlatform();

    private HeadlessPlatform() {}

    @Override
    public void configureAssets(AssetManager assets) {
        // No sources or loaders — headless
    }

    @Override
    public ShaderCompiler shaderCompiler() {
        return new NoOpShaderCompiler();
    }
}

package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.graphics.shader.ShaderCompiler;

/**
 * Configures platform-specific services for the engine.
 *
 * <p>A platform provides the infrastructure that differs between deployment targets
 * (desktop, web, mobile) but is independent of the chosen graphics/windowing backend:
 * <ul>
 *   <li>Asset sources — filesystem on desktop, fetch on web</li>
 *   <li>Asset loaders — image/model loaders available on that platform</li>
 *   <li>Shader compiler — native slang on desktop, slang-wasm on web</li>
 * </ul>
 *
 * <p>The graphics backend and windowing toolkit are chosen separately via
 * {@link dev.engine.graphics.GraphicsBackendFactory}.
 */
public interface Platform {

    /**
     * Configures the asset manager with platform-appropriate sources and loaders.
     * Called by the engine during initialization.
     */
    void configureAssets(AssetManager assets);

    /**
     * Returns the shader compiler for this platform.
     */
    ShaderCompiler shaderCompiler();
}

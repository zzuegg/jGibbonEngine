package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.graphics.shader.ShaderCompiler;

import java.util.concurrent.Executor;

/**
 * Configures platform-specific services for the engine.
 *
 * <p>A platform provides the infrastructure that differs between deployment targets
 * (desktop, web, mobile) but is independent of the chosen graphics/windowing backend:
 * <ul>
 *   <li>Asset sources — filesystem on desktop, fetch on web</li>
 *   <li>Asset loaders — image/model loaders available on that platform</li>
 *   <li>Shader compiler — native slang on desktop, slang-wasm on web</li>
 *   <li>Module executor — parallel on desktop, sequential on web</li>
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

    /**
     * Returns the executor used by the {@link dev.engine.core.module.ModuleManager}
     * to run same-level modules.
     *
     * <p>The default is a synchronous executor that runs modules on the caller thread.
     * Platforms that support real threading (e.g., desktop JVM) may override this to
     * return a parallel executor so that independent modules at the same dependency
     * level run concurrently. Web-targeted platforms (TeaVM, GraalWasm) should leave
     * the default, because the browser JS host does not support Java threading.
     *
     * @return an {@link Executor} for running module updates; defaults to
     *         {@code Runnable::run}
     */
    default Executor moduleExecutor() {
        return Runnable::run;
    }
}

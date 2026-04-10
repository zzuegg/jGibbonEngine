package dev.engine.platform.desktop;

import dev.engine.bindings.slang.SlangShaderCompiler;
import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.FileSystemAssetSource;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.graphics.common.engine.Platform;
import dev.engine.graphics.shader.ShaderCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Desktop platform configuration.
 *
 * <p>Provides filesystem-based asset loading, native Slang shader compilation,
 * and platform-specific asset loaders (images, models via Assimp/STB).
 *
 * <pre>{@code
 * var platform = DesktopPlatform.builder()
 *     .assetRoot(Path.of("assets"))
 *     .build();
 *
 * var config = EngineConfig.builder()
 *     .platform(platform)
 *     .graphicsBackend(OpenGlBackend.factory(glBindings))
 *     .build();
 * new MyGame().launch(config);
 * }</pre>
 */
public final class DesktopPlatform implements Platform {

    private static final Logger log = LoggerFactory.getLogger(DesktopPlatform.class);

    /**
     * Spawns each module update on a fresh virtual thread (Java 21+).
     * No persistent pool to shut down — virtual threads are released after the
     * {@code Runnable} completes.
     */
    private static final Executor VIRTUAL_THREAD_EXECUTOR =
            runnable -> Thread.ofVirtual().start(runnable);

    private final List<Path> assetRoots;
    private final ShaderCompiler compiler;

    private DesktopPlatform(List<Path> assetRoots, ShaderCompiler compiler) {
        this.assetRoots = List.copyOf(assetRoots);
        this.compiler = compiler;
    }

    @Override
    public void configureAssets(AssetManager assets) {
        for (var root : assetRoots) {
            assets.addSource(new FileSystemAssetSource(root));
        }

        assets.registerLoader(new SlangShaderLoader());
        registerLoaderIfAvailable(assets, "dev.engine.core.asset.ImageLoader");
        registerLoaderIfAvailable(assets, "dev.engine.core.mesh.ObjLoader");
        registerLoaderIfAvailable(assets, "dev.engine.bindings.assimp.AssimpModelLoader");
        registerLoaderIfAvailable(assets, "dev.engine.bindings.assimp.StbImageLoader");
        registerLoaderIfAvailable(assets, "dev.engine.bindings.assimp.DdsLoader");
    }

    @Override
    public ShaderCompiler shaderCompiler() {
        return compiler;
    }

    @Override
    public Executor moduleExecutor() {
        return VIRTUAL_THREAD_EXECUTOR;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Path> assetRoots = new ArrayList<>();
        private ShaderCompiler compiler;

        public Builder assetRoot(Path root) {
            assetRoots.add(root);
            return this;
        }

        public Builder shaderCompiler(ShaderCompiler compiler) {
            this.compiler = compiler;
            return this;
        }

        public DesktopPlatform build() {
            if (compiler == null) {
                compiler = new SlangShaderCompiler();
            }
            return new DesktopPlatform(assetRoots, compiler);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerLoaderIfAvailable(AssetManager assets, String className) {
        try {
            var loader = (dev.engine.core.asset.AssetLoader<?>) Class.forName(className)
                    .getDeclaredConstructor().newInstance();
            assets.registerLoader(loader);
        } catch (Throwable ignored) {
            // Loader not on classpath
        }
    }
}

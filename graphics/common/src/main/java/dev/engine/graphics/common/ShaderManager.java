package dev.engine.graphics.common;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderSource;
import dev.engine.core.handle.Handle;
import dev.engine.core.shader.ShaderStageType;
import dev.engine.core.shader.SlangCompiler;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.core.material.MaterialType;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shader compilation via Slang and caches pipelines.
 *
 * <p>Loads shaders from:
 * <ol>
 *   <li>AssetManager (if configured) — enables hot-reload of .slang files</li>
 *   <li>Classpath resources (fallback for built-in shaders)</li>
 * </ol>
 *
 * <p>When hot-reload is enabled, changing a .slang file recompiles and swaps the pipeline.
 */
public class ShaderManager {

    private static final Logger log = LoggerFactory.getLogger(ShaderManager.class);

    private final SlangCompiler compiler;
    private final RenderDevice device;
    private final Map<String, Handle<PipelineResource>> pipelineCache = new ConcurrentHashMap<>();
    private AssetManager assetManager;

    public ShaderManager(SlangCompiler compiler, RenderDevice device) {
        this.compiler = compiler;
        this.device = device;
    }

    /** Sets the AssetManager for loading shaders from the filesystem (enables hot-reload). */
    public void setAssetManager(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Gets or compiles the pipeline for a material type.
     * Tries AssetManager first, then classpath resources.
     */
    public Handle<PipelineResource> getPipeline(MaterialType type) {
        return pipelineCache.computeIfAbsent(type.name(), k -> compilePipeline(type));
    }

    public Handle<PipelineResource> compilePipeline(MaterialType type) {
        String source = loadShaderSource(type);
        if (source == null) {
            throw new RuntimeException("No shader source for material type: " + type.name());
        }
        return compileSlangPipeline(source, type.name());
    }

    /** Compiles a shader from source string. */
    public Handle<PipelineResource> compileSlangSource(String source, String name) {
        return compileSlangPipeline(source, name);
    }

    /** Compiles a shader from a file path (via AssetManager or filesystem). */
    public Handle<PipelineResource> compileSlangFile(String path) {
        // Try AssetManager first
        if (assetManager != null) {
            try {
                var shaderSource = assetManager.loadSync(path, SlangShaderSource.class);
                var pipeline = compileSlangPipeline(shaderSource.source(), path);

                // Register for hot-reload
                assetManager.onReload(path, SlangShaderSource.class, reloaded -> {
                    log.info("Hot-reloading shader: {}", path);
                    try {
                        var newPipeline = compileSlangPipeline(reloaded.source(), path);
                        var old = pipelineCache.put(path, newPipeline);
                        if (old != null) device.destroyPipeline(old);
                    } catch (Exception e) {
                        log.warn("Hot-reload compilation failed for {}: {}", path, e.getMessage());
                    }
                });

                pipelineCache.put(path, pipeline);
                return pipeline;
            } catch (Exception e) {
                log.debug("AssetManager couldn't load {}: {}", path, e.getMessage());
            }
        }

        // Fallback to direct filesystem
        try {
            var source = java.nio.file.Files.readString(java.nio.file.Path.of(path));
            return compileSlangPipeline(source, path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader: " + path, e);
        }
    }

    /** Invalidates a cached pipeline, forcing recompilation on next use. */
    public void invalidate(String key) {
        var old = pipelineCache.remove(key);
        if (old != null) {
            device.destroyPipeline(old);
            log.info("Invalidated shader pipeline: {}", key);
        }
    }

    /** Invalidates all cached pipelines. */
    public void invalidateAll() {
        for (var entry : pipelineCache.entrySet()) {
            device.destroyPipeline(entry.getValue());
        }
        pipelineCache.clear();
        compiler.clearCache();
        log.info("Invalidated all shader pipelines");
    }

    private Handle<PipelineResource> compileSlangPipeline(String source, String name) {
        if (!compiler.isAvailable()) {
            throw new RuntimeException("Slang compiler not available — cannot compile shader: " + name);
        }

        log.info("Compiling Slang shader: {}", name);

        var vsResult = compiler.compileToGlsl(source, "vertexMain", ShaderStageType.VERTEX);
        if (!vsResult.success()) {
            throw new RuntimeException("Vertex shader compilation failed for " + name + ": " + vsResult.error());
        }

        var fsResult = compiler.compileToGlsl(source, "fragmentMain", ShaderStageType.FRAGMENT);
        if (!fsResult.success()) {
            throw new RuntimeException("Fragment shader compilation failed for " + name + ": " + fsResult.error());
        }

        log.debug("Slang → GLSL compiled successfully for {}", name);

        return device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vsResult.glsl()),
                new ShaderSource(ShaderStage.FRAGMENT, fsResult.glsl())
        ));
    }

    private String loadShaderSource(MaterialType type) {
        var shaderPath = "shaders/" + type.name().toLowerCase() + ".slang";

        // Try AssetManager first
        if (assetManager != null) {
            try {
                var shader = assetManager.loadSync(shaderPath, SlangShaderSource.class);
                return shader.source();
            } catch (Exception e) {
                // Fall through to classpath
            }
        }

        // Classpath resources (built-in shaders)
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(shaderPath)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load shader resource: {}", shaderPath, e);
        }
        return null;
    }
}

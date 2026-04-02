package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.shader.GlslCompileResult;
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
 * Manages shader compilation via Slang and caches pipelines per material type.
 */
public class ShaderManager {

    private static final Logger log = LoggerFactory.getLogger(ShaderManager.class);

    private final SlangCompiler compiler;
    private final RenderDevice device;
    private final Map<String, Handle<PipelineResource>> pipelineCache = new ConcurrentHashMap<>();

    public ShaderManager(SlangCompiler compiler, RenderDevice device) {
        this.compiler = compiler;
        this.device = device;
    }

    /**
     * Gets or compiles the pipeline for a material type.
     * Built-in types (UNLIT, PBR) load from resources. Custom types load from file path.
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

    public Handle<PipelineResource> compileSlangSource(String source, String name) {
        return compileSlangPipeline(source, name);
    }

    public Handle<PipelineResource> compileSlangFile(String path) {
        try {
            var source = java.nio.file.Files.readString(java.nio.file.Path.of(path));
            return compileSlangPipeline(source, path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read shader: " + path, e);
        }
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
        var resourcePath = "shaders/" + type.name().toLowerCase() + ".slang";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to load shader resource: {}", resourcePath, e);
        }
        return null;
    }
}

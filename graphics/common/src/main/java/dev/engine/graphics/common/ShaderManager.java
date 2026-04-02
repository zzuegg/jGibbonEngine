package dev.engine.graphics.common;

import dev.engine.bindings.slang.SlangCompilerNative;
import dev.engine.bindings.slang.SlangNative;
import dev.engine.bindings.slang.SlangReflection;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shader compilation via Slang (native FFM or process fallback).
 * Produces {@link CompiledShader} with reflection metadata for binding resolution.
 */
public class ShaderManager {

    private static final Logger log = LoggerFactory.getLogger(ShaderManager.class);

    private final SlangCompiler processCompiler; // process-based fallback
    private final RenderDevice device;
    private final Map<String, CompiledShader> shaderCache = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends Record>> materialStructMap = new ConcurrentHashMap<>();
    private AssetManager assetManager;
    private SlangCompilerNative nativeCompiler; // FFM-based, preferred

    public ShaderManager(SlangCompiler processCompiler, RenderDevice device) {
        this.processCompiler = processCompiler;
        this.device = device;

        // Try native compiler first (disabled by default due to glibc heap corruption in COM release)
        // Enable with -Dengine.slang.native=true or by preloading jemalloc
        if (SlangCompilerNative.isAvailable() && "true".equals(System.getProperty("engine.slang.native"))) {
            try {
                this.nativeCompiler = SlangCompilerNative.create();
                log.info("Using native Slang compiler (FFM bindings)");
            } catch (Exception e) {
                log.warn("Native Slang compiler failed, falling back to process: {}", e.getMessage());
            }
        }
    }

    public void setAssetManager(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Registers a material scalar data record type for a shader name.
     * The record's fields are auto-generated into a Slang struct and injected
     * into the shader source at the __MATERIAL_STRUCT__ placeholder.
     */
    public void registerMaterialStruct(String shaderName, Class<? extends Record> scalarDataClass) {
        materialStructMap.put(shaderName, scalarDataClass);
    }

    /**
     * Gets or compiles the shader for a material type.
     * Returns CompiledShader with reflection data.
     */
    public CompiledShader getShader(MaterialType type) {
        return shaderCache.computeIfAbsent(type.name(), k -> compileShader(type));
    }

    /** Legacy: returns just the pipeline handle (no reflection). */
    public Handle<PipelineResource> getPipeline(MaterialType type) {
        return getShader(type).pipeline();
    }

    public CompiledShader compileSlangSource(String source, String name) {
        return compileFromSource(source, name);
    }

    public Handle<PipelineResource> compileSlangFile(String path) {
        var shader = shaderCache.get(path);
        if (shader != null) return shader.pipeline();

        String source = loadShaderFile(path);
        if (source == null) throw new RuntimeException("Failed to read shader: " + path);

        var compiled = compileFromSource(source, path);
        shaderCache.put(path, compiled);
        return compiled.pipeline();
    }

    public void invalidate(String key) {
        var old = shaderCache.remove(key);
        if (old != null) device.destroyPipeline(old.pipeline());
    }

    public void invalidateAll() {
        for (var entry : shaderCache.values()) device.destroyPipeline(entry.pipeline());
        shaderCache.clear();
        if (processCompiler != null) processCompiler.clearCache();
    }

    // --- Internal ---

    private CompiledShader compileShader(MaterialType type) {
        String source = loadShaderSource(type);
        if (source == null) throw new RuntimeException("No shader source for: " + type.name());
        return compileFromSource(source, type.name());
    }

    private CompiledShader compileFromSource(String source, String name) {
        // Inject material struct if registered
        source = injectMaterialStruct(source, name);

        // Try native compiler with reflection
        if (nativeCompiler != null) {
            try {
                return compileNative(source, name);
            } catch (Exception e) {
                log.warn("Native compilation failed for {}, falling back to process: {}", name, e.getMessage());
            }
        }

        // Fallback to process-based (no reflection)
        return compileProcess(source, name);
    }

    private CompiledShader compileNative(String source, String name) {
        log.info("Compiling Slang shader (native): {}", name);

        var entryPoints = List.of(
                new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT));

        try (var result = nativeCompiler.compile(source, entryPoints, SlangNative.SLANG_GLSL)) {
            var vsGlsl = result.code(0);
            var fsGlsl = result.code(1);

            // Build pipeline
            var pipeline = device.createPipeline(PipelineDescriptor.of(
                    new ShaderSource(ShaderStage.VERTEX, vsGlsl),
                    new ShaderSource(ShaderStage.FRAGMENT, fsGlsl)));

            // Extract reflection bindings
            var bindings = extractBindings(result.reflection());

            log.debug("Slang native compiled: {} ({} bindings)", name, bindings.size());
            return new CompiledShader(pipeline, bindings);
        }
    }

    private CompiledShader compileProcess(String source, String name) {
        if (processCompiler == null || !processCompiler.isAvailable()) {
            throw new RuntimeException("No Slang compiler available for: " + name);
        }

        log.info("Compiling Slang shader (process): {}", name);

        var vsResult = processCompiler.compileToGlsl(source, "vertexMain", ShaderStageType.VERTEX);
        if (!vsResult.success()) throw new RuntimeException("VS failed: " + vsResult.error());

        var fsResult = processCompiler.compileToGlsl(source, "fragmentMain", ShaderStageType.FRAGMENT);
        if (!fsResult.success()) throw new RuntimeException("FS failed: " + fsResult.error());

        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, vsResult.glsl()),
                new ShaderSource(ShaderStage.FRAGMENT, fsResult.glsl())));

        // No reflection from process-based compilation — return empty bindings
        return new CompiledShader(pipeline, Map.of());
    }

    private String injectMaterialStruct(String source, String shaderName) {
        if (!source.contains("// __MATERIAL_STRUCT__")) return source;

        var recordClass = materialStructMap.get(shaderName);
        if (recordClass == null) {
            // Remove placeholder, no struct to inject
            return source.replace("// __MATERIAL_STRUCT__", "// (no material struct registered)");
        }

        var structCode = dev.engine.core.shader.SlangStructGenerator.generateCbuffer("MaterialData", recordClass, 1);
        var injected = source.replace("// __MATERIAL_STRUCT__", structCode);
        log.debug("Injecting material struct for {}: {}\n--- Injected source ---\n{}\n--- End ---", shaderName, recordClass.getSimpleName(), injected);
        return injected;
    }

    private Map<String, CompiledShader.ParameterBinding> extractBindings(SlangReflection reflection) {
        var bindings = new HashMap<String, CompiledShader.ParameterBinding>();
        if (reflection == null) return bindings;

        for (var param : reflection.getParameters()) {
            var name = param.name();
            if (name == null) continue;

            int binding = (int) param.binding();
            var type = guessBindingType(name, binding);
            bindings.put(name, new CompiledShader.ParameterBinding(name, binding, type));
        }
        return bindings;
    }

    private CompiledShader.BindingType guessBindingType(String name, int binding) {
        // Heuristic based on name — reflection gives us the slot but not always the type clearly
        var lower = name.toLowerCase();
        if (lower.contains("sampler")) return CompiledShader.BindingType.SAMPLER;
        if (lower.contains("tex") || lower.contains("texture") || lower.contains("map"))
            return CompiledShader.BindingType.TEXTURE;
        if (lower.contains("storage") || lower.contains("buffer"))
            return CompiledShader.BindingType.STORAGE_BUFFER;
        return CompiledShader.BindingType.CONSTANT_BUFFER;
    }

    private String loadShaderSource(MaterialType type) {
        var shaderPath = "shaders/" + type.name().toLowerCase() + ".slang";

        if (assetManager != null) {
            try {
                return assetManager.loadSync(shaderPath, SlangShaderSource.class).source();
            } catch (Exception ignored) {}
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(shaderPath)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to load shader: {}", shaderPath, e);
        }
        return null;
    }

    private String loadShaderFile(String path) {
        if (assetManager != null) {
            try {
                return assetManager.loadSync(path, SlangShaderSource.class).source();
            } catch (Exception ignored) {}
        }
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (IOException e) {
            return null;
        }
    }
}


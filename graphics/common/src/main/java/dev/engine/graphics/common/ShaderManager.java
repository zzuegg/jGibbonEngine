package dev.engine.graphics.common;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderSource;
import dev.engine.graphics.DeviceCapability;
import dev.engine.core.handle.Handle;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.core.property.PropertyKey;
import dev.engine.graphics.shader.GlobalParamsRegistry;
import dev.engine.graphics.shader.SlangParamsBlock;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderBinary;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.shader.ShaderCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages shader compilation via Slang (native FFM or process fallback).
 * Produces {@link CompiledShader} with reflection metadata for binding resolution.
 */
public class ShaderManager {

    private static final Logger log = LoggerFactory.getLogger(ShaderManager.class);

    private final RenderDevice device;
    private final Map<String, CompiledShader> shaderCache = new ConcurrentHashMap<>();
    private final Map<String, CompiledShader> resolvedShaders = new HashMap<>();
    private final Map<dev.engine.core.handle.Handle<?>, CompiledShader> entityShaders = new java.util.WeakHashMap<>();
    private final GlobalParamsRegistry globalParams;
    private final int slangTarget; // ShaderCompiler.TARGET_GLSL / TARGET_SPIRV / TARGET_WGSL
    private AssetManager assetManager;
    private final ShaderCompiler compiler;
    private final int textureBindingOffset;

    /** Returns the texture binding offset for the current backend (e.g. 16 for Vulkan, 0 for OpenGL). */
    public int textureBindingOffset() { return textureBindingOffset; }

    private final GpuResourceManager gpu;

    public ShaderManager(RenderDevice device, GpuResourceManager gpu, GlobalParamsRegistry globalParams, ShaderCompiler compiler) {
        this.device = device;
        this.gpu = gpu;
        this.globalParams = globalParams;
        this.compiler = compiler;

        // Query shader target from backend
        var target = device.queryCapability(DeviceCapability.SHADER_TARGET);
        this.slangTarget = target != null ? target : ShaderCompiler.TARGET_GLSL;

        // Query texture binding offset from backend (Vulkan uses 16, others 0)
        var texOffset = device.queryCapability(DeviceCapability.TEXTURE_BINDING_OFFSET);
        this.textureBindingOffset = texOffset != null ? texOffset : 0;

        if (!compiler.isAvailable()) {
            log.warn("Shader compiler is not available — shader compilation will fail at runtime");
        }
        String targetName = switch (slangTarget) {
            case ShaderCompiler.TARGET_SPIRV -> "SPIRV";
            case ShaderCompiler.TARGET_WGSL -> "WGSL";
            default -> "GLSL";
        };
        log.info("Shader compiler: {} (target: {})", compiler.getClass().getSimpleName(), targetName);
    }

    public void setAssetManager(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    /**
     * Gets or compiles the shader for a shader name (e.g. "PBR", "UNLIT").
     * Returns CompiledShader with reflection data.
     */
    public CompiledShader getShader(String shaderName) {
        return shaderCache.computeIfAbsent(shaderName, k -> compileShader(shaderName));
    }

    /** Returns just the pipeline handle (no reflection). */
    public Handle<PipelineResource> getPipeline(String shaderName) {
        return getShader(shaderName).pipeline();
    }

    public CompiledShader compileSlangSource(String source, String name) {
        return compileFromSource(source, name, null);
    }

    /** Compiles a Slang source with a custom vertex format (null = standard format). */
    public CompiledShader compileSlangSource(String source, String name, VertexFormat vertexFormat) {
        return compileFromSource(source, name, vertexFormat);
    }

    public Handle<PipelineResource> compileSlangFile(String path) {
        var shader = shaderCache.get(path);
        if (shader != null) return shader.pipeline();

        String source = loadShaderFile(path);
        if (source == null) throw new RuntimeException("Failed to read shader: " + path);

        var compiled = compileFromSource(source, path, null);
        shaderCache.put(path, compiled);
        return compiled.pipeline();
    }

    /**
     * Compiles a shader with material param blocks based on the material's keys.
     * Uses generic specialization when native compiler is available, falls back to
     * static global instance for the process-based compiler.
     * Cached by shader hint + key set combination.
     */
    public CompiledShader getShaderWithMaterial(String shaderHint, Set<? extends PropertyKey<?, ?>> materialKeys) {
        var keyNames = materialKeys.stream()
                .map(PropertyKey::name)
                .sorted()
                .toList();
        var cacheKey = shaderHint + "_" + String.join("_", keyNames);

        return shaderCache.computeIfAbsent(cacheKey, k -> {
            String source = loadShaderSource(shaderHint);
            if (source == null) throw new RuntimeException("No shader source for: " + shaderHint);

            source = prependParamBlocks(source, materialKeys, true);
            var materialBlock = SlangParamsBlock.fromKeys("Material", materialKeys);
            return compileWithAutoSpecialize(source, cacheKey, materialBlock.uboTypeName());
        });
    }

    public void invalidate(String key) {
        var old = shaderCache.remove(key);
        if (old != null) gpu.destroyPipeline(old.pipeline());
    }

    /**
     * Resolves a shader for an entity based on its material's shader hint and keys.
     * Returns null if no material or shader hint, or if compilation fails.
     * Caches the result per entity.
     */
    public CompiledShader resolveForEntity(dev.engine.core.handle.Handle<?> entity,
                                            dev.engine.core.material.MaterialData material) {
        if (material == null || material.shaderHint() == null) return null;

        var hint = material.shaderHint();
        var shaderKeys = material.keys().stream()
                .filter(k -> !RenderStateManager.isRenderStateKey(k))
                .collect(java.util.stream.Collectors.toSet());
        var keyNames = shaderKeys.stream()
                .map(dev.engine.core.property.PropertyKey::name)
                .sorted()
                .toList();
        var cacheKey = hint + "_" + String.join("_", keyNames);

        var compiled = resolvedShaders.computeIfAbsent(cacheKey, k -> {
            try {
                if (hint.contains("/") || hint.contains("\\") || hint.contains(".")) {
                    return compileSlangSource(loadShaderFile(hint), hint);
                }
                return getShaderWithMaterial(hint, shaderKeys);
            } catch (Exception e) {
                log.error("Shader compilation FAILED for hint='{}'", hint, e);
                return null;
            }
        });

        if (compiled != null) {
            entityShaders.put(entity, compiled);
        }
        return compiled;
    }

    /** Returns the compiled shader previously resolved for an entity, or null. */
    public CompiledShader getEntityShader(dev.engine.core.handle.Handle<?> entity) {
        return entityShaders.get(entity);
    }

    /** Removes the cached shader for a destroyed entity. */
    public void removeEntityShader(dev.engine.core.handle.Handle<?> entity) {
        entityShaders.remove(entity);
    }

    /**
     * Extracts buffer name → binding slot map from a CompiledShader's reflection.
     * Only includes constant buffer bindings.
     */
    public static Map<String, Integer> extractBufferBindings(CompiledShader compiled) {
        if (compiled.bindings().isEmpty()) return Map.of();
        var result = new java.util.HashMap<String, Integer>();
        for (var entry : compiled.bindings().entrySet()) {
            var binding = entry.getValue();
            if (binding.type() == CompiledShader.BindingType.CONSTANT_BUFFER) {
                result.put(entry.getKey(), binding.binding());
            }
        }
        return result;
    }

    public void invalidateAll() {
        for (var entry : shaderCache.values()) gpu.destroyPipeline(entry.pipeline());
        shaderCache.clear();
        resolvedShaders.clear();
        entityShaders.clear();
    }

    /** Destroys all cached pipelines. Call on shutdown. */
    public void close() {
        invalidateAll();
    }

    // --- Internal ---

    private CompiledShader compileShader(String shaderName) {
        String source = loadShaderSource(shaderName);
        if (source == null) throw new RuntimeException("No shader source for: " + shaderName);
        return compileFromSource(source, shaderName, null);
    }

    private CompiledShader compileFromSource(String source, String name, VertexFormat vertexFormat) {
        return compileNative(source, name, vertexFormat);
    }

    /**
     * Compiles with auto-specialization: scans shader source for generic declarations,
     * matches interface names to concrete types from the registry + material, and specializes.
     */
    private CompiledShader compileWithAutoSpecialize(String source, String name, String materialTypeName) {
        var typeMap = new java.util.LinkedHashMap<String, String>();
        for (var entry : globalParams.entries()) {
            typeMap.put(entry.name(), "Ubo" + entry.name() + "Params");
        }
        typeMap.put("Material", materialTypeName);

        log.info("Compiling Slang shader (auto-specialize): {}", name);

        var entryPoints = List.of(
                new ShaderCompiler.EntryPointDesc("vertexMain", ShaderCompiler.STAGE_VERTEX),
                new ShaderCompiler.EntryPointDesc("fragmentMain", ShaderCompiler.STAGE_FRAGMENT));

        try (var result = compiler.compileWithTypeMap(source, entryPoints, slangTarget, typeMap)) {
            var pipeline = createPipelineFromResult(result, null);
            var bindings = extractBindings(result);
            log.debug("Slang compiled: {} ({} bindings)", name, bindings.size());
            return new CompiledShader(pipeline, bindings);
        }
    }

    private CompiledShader compileNative(String source, String name, VertexFormat vertexFormat) {
        log.info("Compiling Slang shader: {}", name);

        var entryPoints = List.of(
                new ShaderCompiler.EntryPointDesc("vertexMain", ShaderCompiler.STAGE_VERTEX),
                new ShaderCompiler.EntryPointDesc("fragmentMain", ShaderCompiler.STAGE_FRAGMENT));

        try (var result = compiler.compile(source, entryPoints, slangTarget)) {
            var pipeline = createPipelineFromResult(result, vertexFormat);
            var bindings = extractBindings(result);
            log.debug("Slang compiled: {} ({} bindings)", name, bindings.size());
            return new CompiledShader(pipeline, bindings);
        }
    }

    private Handle<PipelineResource> createPipelineFromResult(ShaderCompiler.CompileResult result, VertexFormat vertexFormat) {
        var format = vertexFormat != null ? vertexFormat : dev.engine.graphics.common.mesh.PrimitiveMeshes.STANDARD_FORMAT;
        if (slangTarget == ShaderCompiler.TARGET_SPIRV) {
            return gpu.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(format));
        } else {
            // For WGSL targets, Slang preserves original entry point names (vertexMain/fragmentMain).
            // For GLSL, Slang always renames to "main". Pass the correct names so backends can use them.
            String vsEntry = (slangTarget == ShaderCompiler.TARGET_WGSL) ? "vertexMain" : "main";
            String fsEntry = (slangTarget == ShaderCompiler.TARGET_WGSL) ? "fragmentMain" : "main";
            return gpu.createPipeline(PipelineDescriptor.of(
                    new ShaderSource(ShaderStage.VERTEX, result.code(0), vsEntry),
                    new ShaderSource(ShaderStage.FRAGMENT, result.code(1), fsEntry))
                    .withVertexFormat(format));
        }
    }

    /**
     * Prepends generated param blocks (camera, material, engine) before the shader source.
     * Shaders access data through well-known globals: camera.get_X(), material.get_X(), engine.get_X().
     */
    /**
     * Prepends generated param blocks before the shader source.
     *
     * @param source       the shader source
     * @param materialKeys material property keys
     * @param useGenericSpecialization if true, omits static global instances for all blocks
     *                                 (shader declares dependencies via generic params instead)
     */
    private String prependParamBlocks(String source, Set<? extends PropertyKey<?, ?>> materialKeys,
                                       boolean useGenericSpecialization) {
        boolean includeGlobals = !useGenericSpecialization;

        var sb = new StringBuilder();
        sb.append("// === Generated param blocks ===\n\n");

        // All registered global params (engine, camera, object, user-defined)
        sb.append(globalParams.generateSlang(includeGlobals));

        // Material params — no fixed binding, resolved via reflection
        if (materialKeys != null && !materialKeys.isEmpty()) {
            sb.append(SlangParamsBlock.fromKeys("Material", materialKeys)
                    .generateUbo(includeGlobals));
            sb.append("\n");

            // Generate combined texture+sampler declarations for TextureData keys.
            // Uses Sampler2D (combined type) for cross-backend compatibility.
            // Convention: key "albedoTexture" → Sampler2D albedoTexture
            //
            // Binding strategy differs by target:
            //   - GLSL: Slang auto-assigns sequential layout(binding=N) after UBOs.
            //           The Renderer reads the actual binding from the generated GLSL.
            //   - SPIRV/Vulkan: The Vulkan descriptor set layout has textures at a fixed
            //           offset (TEXTURE_BINDING_OFFSET=16). We annotate [[vk::binding(16+i)]]
            //           so the SPIRV matches the descriptor layout.
            int vkTexOffset = textureBindingOffset;
            int texIndex = 0;

            var sortedTexKeys = materialKeys.stream()
                    .filter(k -> k.type() == dev.engine.graphics.texture.SampledTexture.class)
                    .sorted(java.util.Comparator.comparing(PropertyKey::name))
                    .toList();
            for (var key : sortedTexKeys) {
                String texName = key.name();
                // Only emit vk::binding for SPIRV targets (Vulkan descriptor set layout).
                // For WGSL/GLSL, let Slang auto-assign sequential bindings. Slang applies
                // vk::binding to ALL targets (including WGSL), which would force texture
                // bindings to index 16+ even in WebGPU where there's no such requirement.
                if (slangTarget == ShaderCompiler.TARGET_SPIRV) {
                    sb.append("[[vk::binding(").append(vkTexOffset + texIndex).append(")]]\n");
                }
                sb.append("Sampler2D ").append(texName).append(";\n");
                texIndex++;
            }
            sb.append("\n");
        }

        sb.append("// === End generated param blocks ===\n\n");

        // Strip legacy placeholder if present
        source = source.replace("// __MATERIAL_STRUCT__", "");

        var result = sb + source;
        log.debug("Prepended param blocks:\n--- Full source ---\n{}\n--- End ---", result);
        return result;
    }


    /**
     * Converts compiler-provided parameter info to CompiledShader bindings.
     * The compiler implementations handle reflection and GLSL parsing internally.
     */
    private Map<String, CompiledShader.ParameterBinding> extractBindings(ShaderCompiler.CompileResult result) {
        var bindings = new HashMap<String, CompiledShader.ParameterBinding>();
        for (var entry : result.parameters().entrySet()) {
            var param = entry.getValue();
            var type = switch (param.category()) {
                case TEXTURE -> CompiledShader.BindingType.TEXTURE;
                case SAMPLER -> CompiledShader.BindingType.SAMPLER;
                case SHADER_RESOURCE -> CompiledShader.BindingType.STORAGE_BUFFER;
                default -> CompiledShader.BindingType.CONSTANT_BUFFER;
            };
            bindings.put(entry.getKey(), new CompiledShader.ParameterBinding(param.name(), param.binding(), type));
        }
        return bindings;
    }

    /**
     * Loads a shader source from the asset system or classpath.
     * @param shaderPath the resource path (e.g. "shaders/debug_ui.slang")
     * @return the shader source, or null if not found
     */
    public String loadResource(String shaderPath) {
        if (assetManager != null) {
            try {
                var result = assetManager.loadSync(shaderPath, SlangShaderSource.class);
                if (result != null) return result.source();
            } catch (Exception e) {
                log.debug("AssetManager failed to load {}: {}", shaderPath, e.getMessage());
            }
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(shaderPath)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Classpath failed to load {}: {}", shaderPath, e.getMessage());
        }
        return null;
    }

    private String loadShaderSource(String shaderName) {
        var shaderPath = "shaders/" + shaderName.toLowerCase() + ".slang";

        if (assetManager != null) {
            try {
                log.debug("Loading shader via AssetManager: {}", shaderPath);
                var result = assetManager.loadSync(shaderPath, SlangShaderSource.class);
                if (result != null) {
                    log.debug("Shader loaded via AssetManager: {} ({} chars)", shaderPath, result.source().length());
                    return result.source();
                }
            } catch (Exception e) {
                log.warn("AssetManager failed to load shader {}: {}", shaderPath, e.getMessage());
            }
        } else {
            log.debug("No AssetManager set, trying classpath for: {}", shaderPath);
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
        try (var reader = new java.io.BufferedReader(new java.io.FileReader(path))) {
            var sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}

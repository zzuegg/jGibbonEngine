package dev.engine.graphics.common;

import dev.engine.bindings.slang.SlangCompilerNative;
import dev.engine.bindings.slang.SlangNative;
import dev.engine.bindings.slang.SlangReflection;
import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderSource;
import dev.engine.graphics.DeviceCapability;
import dev.engine.core.handle.Handle;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.shader.GlobalParamsRegistry;
import dev.engine.core.shader.SlangParamsBlock;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderBinary;
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
    private final GlobalParamsRegistry globalParams;
    private final int slangTarget; // SlangNative.SLANG_GLSL or SLANG_SPIRV
    private AssetManager assetManager;
    private final SlangCompilerNative nativeCompiler;

    public ShaderManager(RenderDevice device, GlobalParamsRegistry globalParams) {
        this.device = device;
        this.globalParams = globalParams;

        // Detect target format from backend
        var backend = device.queryCapability(DeviceCapability.BACKEND_NAME);
        this.slangTarget = switch (backend) {
            case "Vulkan" -> SlangNative.SLANG_SPIRV;
            case "WebGPU" -> SlangNative.SLANG_WGSL;
            default -> SlangNative.SLANG_GLSL;
        };

        if (!SlangCompilerNative.isAvailable()) {
            throw new RuntimeException("Slang native library not available — cannot compile shaders");
        }
        this.nativeCompiler = SlangCompilerNative.create();
        String targetName = switch (slangTarget) {
            case SlangNative.SLANG_SPIRV -> "SPIRV";
            case SlangNative.SLANG_WGSL -> "WGSL";
            default -> "GLSL";
        };
        log.info("Using native Slang compiler (FFM bindings, target: {})", targetName);
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

    /**
     * Compiles a shader with material param blocks based on the material's keys.
     * Uses generic specialization when native compiler is available, falls back to
     * static global instance for the process-based compiler.
     * Cached by shader hint + key set combination.
     */
    public CompiledShader getShaderWithMaterial(String shaderHint, Set<PropertyKey<?>> materialKeys) {
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
        if (old != null) device.destroyPipeline(old.pipeline());
    }

    public void invalidateAll() {
        for (var entry : shaderCache.values()) device.destroyPipeline(entry.pipeline());
        shaderCache.clear();
    }

    // --- Internal ---

    private CompiledShader compileShader(String shaderName) {
        String source = loadShaderSource(shaderName);
        if (source == null) throw new RuntimeException("No shader source for: " + shaderName);
        return compileFromSource(source, shaderName);
    }

    private CompiledShader compileFromSource(String source, String name) {
        return compileNative(source, name);
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
                new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT));

        try (var result = nativeCompiler.compileWithTypeMap(source, entryPoints, slangTarget, typeMap)) {
            var pipeline = createPipelineFromResult(result);
            // Pass generated GLSL for binding extraction (more reliable than reflection for GLSL)
            var glslCodes = slangTarget == SlangNative.SLANG_GLSL
                    ? new String[]{ result.code(0), result.code(1) }
                    : null;
            var bindings = extractBindings(result.reflection(), glslCodes);
            log.debug("Slang compiled: {} ({} bindings)", name, bindings.size());
            return new CompiledShader(pipeline, bindings);
        }
    }

    private CompiledShader compileNative(String source, String name) {
        log.info("Compiling Slang shader: {}", name);

        var entryPoints = List.of(
                new SlangCompilerNative.EntryPointDesc("vertexMain", SlangNative.SLANG_STAGE_VERTEX),
                new SlangCompilerNative.EntryPointDesc("fragmentMain", SlangNative.SLANG_STAGE_FRAGMENT));

        try (var result = nativeCompiler.compile(source, entryPoints, slangTarget)) {
            var pipeline = createPipelineFromResult(result);
            var glslCodes = slangTarget == SlangNative.SLANG_GLSL
                    ? new String[]{ result.code(0), result.code(1) }
                    : null;
            var bindings = extractBindings(result.reflection(), glslCodes);
            log.debug("Slang compiled: {} ({} bindings)", name, bindings.size());
            return new CompiledShader(pipeline, bindings);
        }
    }

    private Handle<PipelineResource> createPipelineFromResult(SlangCompilerNative.CompileResult result) {
        // Use standard vertex format (pos + normal + uv) for all shaders
        var standardFormat = dev.engine.graphics.common.mesh.PrimitiveMeshes.STANDARD_FORMAT;
        if (slangTarget == SlangNative.SLANG_SPIRV) {
            return device.createPipeline(PipelineDescriptor.ofSpirv(
                    new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                    new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                    .withVertexFormat(standardFormat));
        } else {
            // For WGSL targets, Slang preserves original entry point names (vertexMain/fragmentMain).
            // For GLSL, Slang always renames to "main". Pass the correct names so backends can use them.
            String vsEntry = (slangTarget == SlangNative.SLANG_WGSL) ? "vertexMain" : "main";
            String fsEntry = (slangTarget == SlangNative.SLANG_WGSL) ? "fragmentMain" : "main";
            return device.createPipeline(PipelineDescriptor.of(
                    new ShaderSource(ShaderStage.VERTEX, result.code(0), vsEntry),
                    new ShaderSource(ShaderStage.FRAGMENT, result.code(1), fsEntry))
                    .withVertexFormat(standardFormat));
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
    private String prependParamBlocks(String source, Set<PropertyKey<?>> materialKeys,
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
            // Convention: key "albedoMap" → Sampler2D albedoTexture
            //
            // Binding strategy differs by target:
            //   - GLSL: Slang auto-assigns sequential layout(binding=N) after UBOs.
            //           The Renderer reads the actual binding from the generated GLSL.
            //   - SPIRV/Vulkan: The Vulkan descriptor set layout has textures at a fixed
            //           offset (TEXTURE_BINDING_OFFSET=16). We annotate [[vk::binding(16+i)]]
            //           so the SPIRV matches the descriptor layout.
            int vkTexOffset = 16; // Must match VkDescriptorManager.TEXTURE_BINDING_OFFSET
            int texIndex = 0;

            var sortedTexKeys = materialKeys.stream()
                    .filter(k -> k.type() == dev.engine.core.asset.TextureData.class)
                    .sorted(java.util.Comparator.comparing(PropertyKey::name))
                    .toList();
            for (var key : sortedTexKeys) {
                String texName = mapTextureKeyName(key.name());
                // Always emit vk::binding for Vulkan; Slang only uses it for SPIRV targets
                sb.append("[[vk::binding(").append(vkTexOffset + texIndex).append(")]]\n");
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
     * Maps a material texture key name to the shader texture parameter name.
     * Convention: "albedoMap" → "albedoTexture", "normalMap" → "normalTexture".
     */
    private static String mapTextureKeyName(String keyName) {
        return switch (keyName) {
            case "albedoMap" -> "albedoTexture";
            case "normalMap" -> "normalTexture";
            case "roughnessMap" -> "roughnessTexture";
            case "metallicMap" -> "metallicTexture";
            case "emissiveMap" -> "emissiveTexture";
            case "aoMap" -> "aoTexture";
            default -> keyName;
        };
    }

    private Map<String, CompiledShader.ParameterBinding> extractBindings(SlangReflection reflection) {
        return extractBindings(reflection, null);
    }

    /**
     * Extracts bindings from Slang reflection, optionally augmenting with GLSL-parsed bindings.
     * Slang reflection can report incorrect binding offsets for GLSL targets (particularly for
     * textures), so we also parse the generated GLSL for {@code layout(binding = N)} annotations.
     */
    private Map<String, CompiledShader.ParameterBinding> extractBindings(SlangReflection reflection,
                                                                         String[] generatedGlsl) {
        var bindings = new HashMap<String, CompiledShader.ParameterBinding>();
        if (reflection == null) return bindings;

        // Parse GLSL for binding annotations if available (more reliable than reflection for GLSL targets)
        var glslBindings = new HashMap<String, Integer>();
        if (generatedGlsl != null) {
            for (var glsl : generatedGlsl) {
                if (glsl != null) parseGlslBindings(glsl, glslBindings);
            }
        }

        for (var param : reflection.getParameters()) {
            var name = param.name();
            if (name == null) continue;

            // Try GLSL-parsed binding first (more reliable), fall back to reflection
            int binding;
            if (glslBindings.containsKey(name)) {
                binding = glslBindings.get(name);
                log.debug("  Reflection: {} → GLSL binding={}", name, binding);
            } else {
                long bindingDTS = param.bindingOffset(dev.engine.bindings.slang.SlangReflection.SLANG_PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT);
                long bindingCB = param.bindingOffset(dev.engine.bindings.slang.SlangReflection.SLANG_PARAMETER_CATEGORY_CONSTANT_BUFFER);
                long bindingSR = param.bindingOffset(dev.engine.bindings.slang.SlangReflection.SLANG_PARAMETER_CATEGORY_SHADER_RESOURCE);
                long bindingSS = param.bindingOffset(dev.engine.bindings.slang.SlangReflection.SLANG_PARAMETER_CATEGORY_SAMPLER_STATE);
                binding = (int) Math.max(bindingDTS, Math.max(bindingCB, Math.max(bindingSR, bindingSS)));
                log.debug("  Reflection: {} → DTS={}, CB={}, SR={}, SS={}, resolved={}", name, bindingDTS, bindingCB, bindingSR, bindingSS, binding);
            }
            var type = guessBindingType(name, binding);
            bindings.put(name, new CompiledShader.ParameterBinding(name, binding, type));
        }
        return bindings;
    }

    /**
     * Parses GLSL source for {@code layout(binding = N)} annotations followed by
     * uniform declarations, extracting the parameter name (Slang-suffixed with _0) back
     * to the original name.
     */
    private void parseGlslBindings(String glsl, Map<String, Integer> bindings) {
        // Match: layout(binding = N) ... uniform ... paramName_0;
        // Slang appends _0 suffix to parameter names in GLSL output
        var pattern = java.util.regex.Pattern.compile(
                "layout\\(binding\\s*=\\s*(\\d+)\\)\\s*\\n?" +
                "(?:layout\\([^)]*\\)\\s*)?uniform\\s+\\w+\\s+(\\w+?)(?:_0)?\\s*\\{");
        var matcher = pattern.matcher(glsl);
        while (matcher.find()) {
            int binding = Integer.parseInt(matcher.group(1));
            String rawName = matcher.group(2);
            // Strip Slang's SLANG_ParameterGroup_ prefix if present
            String name = rawName.replace("block_SLANG_ParameterGroup_", "");
            bindings.put(name, binding);
        }

        // Also match simple uniform declarations (samplers, textures):
        // layout(binding = N)\nuniform sampler2D albedoTexture_0;
        var simplePattern = java.util.regex.Pattern.compile(
                "layout\\(binding\\s*=\\s*(\\d+)\\)\\s*\\n?" +
                "uniform\\s+\\w+\\s+(\\w+?)_0\\s*;");
        var simpleMatcher = simplePattern.matcher(glsl);
        while (simpleMatcher.find()) {
            int binding = Integer.parseInt(simpleMatcher.group(1));
            String name = simpleMatcher.group(2);
            bindings.put(name, binding);
        }
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

    private String loadShaderSource(String shaderName) {
        var shaderPath = "shaders/" + shaderName.toLowerCase() + ".slang";

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

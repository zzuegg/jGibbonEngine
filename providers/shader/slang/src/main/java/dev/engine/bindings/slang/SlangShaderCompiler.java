package dev.engine.bindings.slang;

import dev.engine.graphics.shader.ShaderCompiler;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Desktop {@link ShaderCompiler} implementation that wraps {@link SlangCompilerNative}.
 *
 * <p>Uses FFM (Foreign Function & Memory) bindings to the native Slang library
 * for shader compilation and reflection.
 */
public class SlangShaderCompiler implements ShaderCompiler {

    private final SlangCompilerNative nativeCompiler;

    public SlangShaderCompiler() {
        this.nativeCompiler = SlangCompilerNative.create();
    }

    @Override
    public boolean isAvailable() {
        return SlangCompilerNative.isAvailable();
    }

    @Override
    public CompileResult compile(String source, List<EntryPointDesc> entryPoints, int target) {
        var nativeEps = entryPoints.stream()
                .map(e -> new SlangCompilerNative.EntryPointDesc(e.name(), e.stage()))
                .toList();

        try (var result = nativeCompiler.compile(source, nativeEps, target)) {
            return toCompileResult(result, entryPoints.size(), target);
        }
    }

    @Override
    public CompileResult compileWithTypeMap(String source, List<EntryPointDesc> entryPoints,
                                            int target, Map<String, String> typeMap) {
        var nativeEps = entryPoints.stream()
                .map(e -> new SlangCompilerNative.EntryPointDesc(e.name(), e.stage()))
                .toList();
        var orderedTypeMap = new LinkedHashMap<>(typeMap);

        try (var result = nativeCompiler.compileWithTypeMap(source, nativeEps, target, orderedTypeMap)) {
            return toCompileResult(result, entryPoints.size(), target);
        }
    }

    private CompileResult toCompileResult(SlangCompilerNative.CompileResult result, int count, int target) {
        String[] codes = new String[count];
        byte[][] codeBytes = new byte[count][];
        for (int i = 0; i < count; i++) {
            codes[i] = result.code(i);
            codeBytes[i] = result.codeBytes(i);
        }

        // Extract parameter info from reflection + GLSL parsing
        var parameters = extractParameters(result, target);
        return new CompileResult(codes, codeBytes, count, parameters);
    }

    /**
     * Extracts parameter binding info from Slang reflection and, for GLSL targets,
     * augments with bindings parsed from the generated GLSL source.
     */
    private Map<String, ParameterInfo> extractParameters(SlangCompilerNative.CompileResult result, int target) {
        var params = new HashMap<String, ParameterInfo>();
        var reflection = result.reflection();

        // Parse GLSL for binding annotations if targeting GLSL (more reliable than reflection)
        var glslBindings = new HashMap<String, Integer>();
        if (target == TARGET_GLSL) {
            for (int i = 0; i < result.entryPointCount(); i++) {
                var glsl = result.code(i);
                if (glsl != null) parseGlslBindings(glsl, glslBindings);
            }
        }

        if (reflection != null) {
            for (var param : reflection.getParameters()) {
                var name = param.name();
                if (name == null) continue;

                int binding;
                if (glslBindings.containsKey(name)) {
                    binding = glslBindings.get(name);
                } else {
                    long bindingDTS = param.bindingOffset(SlangReflection.SLANG_PARAMETER_CATEGORY_DESCRIPTOR_TABLE_SLOT);
                    long bindingCB = param.bindingOffset(SlangReflection.SLANG_PARAMETER_CATEGORY_CONSTANT_BUFFER);
                    long bindingSR = param.bindingOffset(SlangReflection.SLANG_PARAMETER_CATEGORY_SHADER_RESOURCE);
                    long bindingSS = param.bindingOffset(SlangReflection.SLANG_PARAMETER_CATEGORY_SAMPLER_STATE);
                    binding = (int) Math.max(bindingDTS, Math.max(bindingCB, Math.max(bindingSR, bindingSS)));
                }

                var category = guessCategory(name);
                params.put(name, new ParameterInfo(name, binding, category));
            }
        }

        return params;
    }

    /**
     * Parses GLSL source for {@code layout(binding = N)} annotations.
     */
    private void parseGlslBindings(String glsl, Map<String, Integer> bindings) {
        // Match: layout(binding = N) ... uniform ... paramName_0 {
        var blockPattern = java.util.regex.Pattern.compile(
                "layout\\(binding\\s*=\\s*(\\d+)\\)\\s*\\n?" +
                "(?:layout\\([^)]*\\)\\s*)?uniform\\s+\\w+\\s+(\\w+?)(?:_0)?\\s*\\{");
        var blockMatcher = blockPattern.matcher(glsl);
        while (blockMatcher.find()) {
            int binding = Integer.parseInt(blockMatcher.group(1));
            String rawName = blockMatcher.group(2);
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

    private ParameterCategory guessCategory(String name) {
        var lower = name.toLowerCase();
        if (lower.contains("sampler")) return ParameterCategory.SAMPLER;
        if (lower.contains("tex") || lower.contains("texture") || lower.contains("map"))
            return ParameterCategory.TEXTURE;
        if (lower.contains("storage") || lower.contains("buffer"))
            return ParameterCategory.SHADER_RESOURCE;
        return ParameterCategory.CONSTANT_BUFFER;
    }
}

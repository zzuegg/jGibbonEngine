package dev.engine.providers.teavm.webgpu;

import dev.engine.graphics.shader.ShaderCompiler;

import java.util.List;
import java.util.Map;

/**
 * Web {@link ShaderCompiler} implementation that wraps the Slang WASM compiler.
 *
 * <p>Compiles Slang source to WGSL via the Slang WASM module loaded in the browser.
 * Entry points are compiled by passing vertex and fragment names to
 * {@link TeaVmSlangCompiler#compile(String, String, String)}.
 */
public class TeaVmShaderCompiler implements ShaderCompiler {

    @Override
    public boolean isAvailable() {
        return TeaVmSlangCompiler.isAvailable();
    }

    @Override
    public CompileResult compile(String source, List<EntryPointDesc> entryPoints, int target) {
        // TeaVmSlangCompiler.compile expects vertex + fragment entry point names
        if (entryPoints.size() == 2) {
            String[] wgsl = TeaVmSlangCompiler.compile(source,
                    entryPoints.get(0).name(), entryPoints.get(1).name());
            return new CompileResult(wgsl, new byte[wgsl.length][], wgsl.length);
        } else if (entryPoints.size() == 1) {
            // Single entry point (compute)
            String wgsl = TeaVmSlangCompiler.compileCompute(source, entryPoints.get(0).name());
            return new CompileResult(new String[]{wgsl}, new byte[1][], 1);
        } else {
            throw new UnsupportedOperationException(
                    "TeaVmShaderCompiler supports 1 or 2 entry points, got " + entryPoints.size());
        }
    }

    @Override
    public CompileResult compileWithTypeMap(String source, List<EntryPointDesc> entryPoints,
                                            int target, Map<String, String> typeMap) {
        // Slang WASM doesn't expose specialize() — do source-level specialization.
        // Replace generic parameters <C : IFoo> with concrete types from typeMap.
        // e.g. <C : ICameraParams, O : IObjectParams> → remove generics, replace C with UboCameraParams
        String specialized = sourceSpecialize(source, typeMap);
        return compile(specialized, entryPoints, target);
    }

    /**
     * Performs source-level generic specialization by replacing generic declarations
     * and type references with concrete types from the type map.
     *
     * Transforms:
     *   vertexMain<C : ICameraParams, O : IObjectParams>(...)
     * Into:
     *   vertexMain(...)
     * And replaces all occurrences of C/O with their concrete types.
     */
    private String sourceSpecialize(String source, Map<String, String> typeMap) {
        // Build interface→concrete type mapping: "ICameraParams" → "UboCameraParams"
        var interfaceToConcreteType = new java.util.HashMap<String, String>();
        for (var entry : typeMap.entrySet()) {
            // typeMap has: "Camera" → "UboCameraParams", "Object" → "UboObjectParams" etc.
            // Interface names are "I" + key + "Params"
            interfaceToConcreteType.put("I" + entry.getKey() + "Params", entry.getValue());
        }

        // Find and process generic declarations: <TypeParam : InterfaceName, ...>
        // Pattern: identifier<T1 : I1, T2 : I2, ...>(
        var result = new StringBuilder();
        int i = 0;
        // Track type parameter → concrete type replacements
        var paramReplacements = new java.util.HashMap<String, String>();

        while (i < source.length()) {
            // Look for generic parameter lists: <...>
            if (source.charAt(i) == '<') {
                int close = source.indexOf('>', i);
                if (close > i) {
                    String genericPart = source.substring(i + 1, close);
                    // Parse: "C : ICameraParams, O : IObjectParams"
                    boolean allResolved = true;
                    String[] params = genericPart.split(",");
                    for (String param : params) {
                        String trimmed = param.trim();
                        String[] parts = trimmed.split("\\s*:\\s*");
                        if (parts.length == 2) {
                            String typeParam = parts[0].trim();
                            String interfaceName = parts[1].trim();
                            String concreteType = interfaceToConcreteType.get(interfaceName);
                            if (concreteType != null) {
                                paramReplacements.put(typeParam, concreteType);
                            } else {
                                allResolved = false;
                            }
                        } else {
                            allResolved = false;
                        }
                    }
                    if (allResolved && !paramReplacements.isEmpty()) {
                        // Remove the generic parameter list entirely
                        i = close + 1;
                        continue;
                    }
                }
            }
            result.append(source.charAt(i));
            i++;
        }

        // Now replace all type parameter references with concrete types
        String specialized = result.toString();
        for (var entry : paramReplacements.entrySet()) {
            // Replace standalone type references: "C camera;" → "UboCameraParams camera;"
            // Use word boundary matching
            specialized = specialized.replaceAll(
                "\\b" + entry.getKey() + "\\b",
                entry.getValue());
        }

        return specialized;
    }
}

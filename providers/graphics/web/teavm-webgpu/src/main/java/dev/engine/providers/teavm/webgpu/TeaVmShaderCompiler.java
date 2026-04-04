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
        // Slang WASM's WGSL backend returns null for generic entry points.
        // Workaround: resolve generics at the source level before compilation.
        // Replace <C : ICameraParams> with concrete types from the type map.
        String specialized = sourceSpecialize(source, typeMap);
        return compile(specialized, entryPoints, target);
    }

    /**
     * Source-level generic specialization. Replaces generic parameter declarations
     * and their usage with concrete types from the type map.
     */
    private String sourceSpecialize(String source, Map<String, String> typeMap) {
        // Build interface→concrete mapping: "ICameraParams" → "UboCameraParams"
        var ifaceToType = new java.util.HashMap<String, String>();
        for (var e : typeMap.entrySet()) {
            ifaceToType.put("I" + e.getKey() + "Params", e.getValue());
        }

        // Parse and remove generic declarations, track replacements
        var result = new StringBuilder();
        var replacements = new java.util.HashMap<String, String>();
        int i = 0;

        while (i < source.length()) {
            if (source.charAt(i) == '<' && i > 0 && Character.isLetterOrDigit(source.charAt(i - 1))) {
                int close = source.indexOf('>', i);
                if (close > i) {
                    String generic = source.substring(i + 1, close);
                    boolean resolved = true;
                    for (String param : generic.split(",")) {
                        String[] parts = param.trim().split("\\s*:\\s*");
                        if (parts.length == 2) {
                            String concrete = ifaceToType.get(parts[1].trim());
                            if (concrete != null) {
                                replacements.put(parts[0].trim(), concrete);
                            } else {
                                resolved = false;
                            }
                        } else {
                            resolved = false;
                        }
                    }
                    if (resolved) {
                        i = close + 1;
                        continue;
                    }
                }
            }
            result.append(source.charAt(i));
            i++;
        }

        String out = result.toString();
        for (var e : replacements.entrySet()) {
            out = out.replaceAll("\\b" + e.getKey() + "\\b", e.getValue());
        }
        return out;
    }
}

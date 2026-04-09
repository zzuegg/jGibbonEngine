package dev.engine.providers.slang.wasm;

import dev.engine.graphics.shader.ShaderCompiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WASM-based Slang shader compiler implementing {@link ShaderCompiler}.
 *
 * <p>Delegates actual WASM calls to a {@link SlangWasmBridge} implementation.
 * The compilation logic — entry point dispatch and source-level generic
 * specialization — is shared across all bridge implementations.
 *
 * <pre>{@code
 * var bridge = new GraalSlangWasmBridge(Path.of("tools/slang-wasm"));
 * var compiler = new SlangWasmCompiler(bridge);
 *
 * var result = compiler.compile(slangSource, List.of(
 *     new EntryPointDesc("vertexMain", STAGE_VERTEX),
 *     new EntryPointDesc("fragmentMain", STAGE_FRAGMENT)
 * ), TARGET_WGSL);
 * }</pre>
 */
public class SlangWasmCompiler implements ShaderCompiler {

    private final SlangWasmBridge bridge;

    public SlangWasmCompiler(SlangWasmBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public boolean isAvailable() {
        return bridge.isAvailable();
    }

    @Override
    public CompileResult compile(String source, List<EntryPointDesc> entryPoints, int target) {
        if (entryPoints.size() == 2) {
            String[] wgsl = bridge.compile(source,
                    entryPoints.get(0).name(), entryPoints.get(1).name());
            return new CompileResult(wgsl, new byte[wgsl.length][], wgsl.length);
        } else if (entryPoints.size() == 1) {
            String wgsl = bridge.compileCompute(source, entryPoints.get(0).name());
            return new CompileResult(new String[]{wgsl}, new byte[1][], 1);
        } else {
            throw new UnsupportedOperationException(
                    "SlangWasmCompiler supports 1 or 2 entry points, got " + entryPoints.size());
        }
    }

    @Override
    public CompileResult compileWithTypeMap(String source, List<EntryPointDesc> entryPoints,
                                            int target, Map<String, String> typeMap) {
        String specialized = sourceSpecialize(source, typeMap);
        return compile(specialized, entryPoints, target);
    }

    /**
     * Source-level generic specialization. Replaces generic parameter declarations
     * and their usage with concrete types from the type map.
     *
     * <p>This is a workaround for Slang WASM's WGSL backend returning null for
     * generic entry points. Generics are resolved at the source level before
     * compilation by replacing {@code <C : ICameraParams>} with concrete types.
     */
    private String sourceSpecialize(String source, Map<String, String> typeMap) {
        var ifaceToType = new HashMap<String, String>();
        for (var e : typeMap.entrySet()) {
            ifaceToType.put("I" + e.getKey() + "Params", e.getValue());
        }

        var result = new StringBuilder();
        var replacements = new HashMap<String, String>();
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

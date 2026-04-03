package dev.engine.graphics.common;

import dev.engine.graphics.shader.ShaderCompiler;

import java.util.List;
import java.util.Map;

/**
 * A no-op {@link ShaderCompiler} for headless testing.
 *
 * <p>Always reports as unavailable and returns empty compilation results.
 * Used by {@link Renderer#createHeadless()} and similar test harnesses
 * where real shader compilation is not needed.
 */
public class NoOpShaderCompiler implements ShaderCompiler {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public CompileResult compile(String source, List<EntryPointDesc> entryPoints, int target) {
        return empty(entryPoints.size());
    }

    @Override
    public CompileResult compileWithTypeMap(String source, List<EntryPointDesc> entryPoints,
                                            int target, Map<String, String> typeMap) {
        return empty(entryPoints.size());
    }

    private CompileResult empty(int count) {
        return new CompileResult(new String[count], new byte[count][], count);
    }
}

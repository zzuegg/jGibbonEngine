package dev.engine.graphics.shader;

import java.util.List;
import java.util.Map;

/**
 * Compiles shader source code to target-specific output (GLSL, SPIRV, WGSL).
 *
 * <p>Desktop: wraps SlangCompilerNative (FFM).
 * Web: wraps Slang WASM compiler.
 *
 * <p>Implementations are injected into {@code ShaderManager} and {@code Renderer}
 * so the same rendering pipeline works on all platforms.
 */
public interface ShaderCompiler {

    /**
     * Compiled shader result with code per entry point and optional binding metadata.
     */
    record CompileResult(
            String[] code,
            byte[][] codeBytes,
            int entryPointCount,
            Map<String, ParameterInfo> parameters
    ) implements AutoCloseable {

        /** Convenience constructor without parameter info. */
        public CompileResult(String[] code, byte[][] codeBytes, int entryPointCount) {
            this(code, codeBytes, entryPointCount, Map.of());
        }

        public String code(int index) { return code[index]; }
        public byte[] codeBytes(int index) { return codeBytes[index]; }

        @Override
        public void close() {
            // no-op for simple results — subclasses may release native resources
        }
    }

    /**
     * Discovered shader parameter with name, binding slot, and category.
     */
    record ParameterInfo(String name, int binding, ParameterCategory category) {}

    /** Broad categories for shader parameters. */
    enum ParameterCategory {
        CONSTANT_BUFFER,
        TEXTURE,
        SAMPLER,
        SHADER_RESOURCE,
        UNKNOWN
    }

    /** Describes an entry point to compile. */
    record EntryPointDesc(String name, int stage) {}

    // Stage constants (matching Slang conventions)
    int STAGE_VERTEX = 1;
    int STAGE_FRAGMENT = 5;
    int STAGE_COMPUTE = 6;

    // Target constants (matching Slang conventions)
    int TARGET_GLSL = 2;
    int TARGET_SPIRV = 6;
    int TARGET_WGSL = 28;

    /** Whether the compiler is available and ready for use. */
    boolean isAvailable();

    /** Compiles shader source with multiple entry points. */
    CompileResult compile(String source, List<EntryPointDesc> entryPoints, int target);

    /** Compiles with generic type specialization (material variants). */
    CompileResult compileWithTypeMap(String source, List<EntryPointDesc> entryPoints,
                                     int target, Map<String, String> typeMap);
}

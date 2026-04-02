package dev.engine.bindings.slang;

import java.util.List;

/**
 * High-level Slang compiler using FFM bindings to the native Slang library.
 *
 * <p>Usage:
 * <pre>{@code
 * var compiler = SlangCompilerNative.create();
 * var result = compiler.compile(source, "vertexMain", SlangNative.SLANG_STAGE_VERTEX, SlangNative.SLANG_GLSL);
 * String glsl = result.code();
 * var reflection = result.reflection();
 * compiler.close();
 * }</pre>
 */
public class SlangCompilerNative implements AutoCloseable {

    private final SlangSession globalSession;

    private SlangCompilerNative(SlangSession globalSession) {
        this.globalSession = globalSession;
    }

    /**
     * Creates a new compiler instance by initializing a Slang global session.
     *
     * @return a new compiler
     * @throws SlangException if the native library is not available
     */
    public static SlangCompilerNative create() {
        return new SlangCompilerNative(SlangNative.createGlobalSession());
    }

    /**
     * Returns true if the Slang native library is available.
     */
    public static boolean isAvailable() {
        return SlangNative.isAvailable();
    }

    /**
     * Compiles a Slang source string, extracting a single entry point.
     *
     * @param source     the Slang source code
     * @param entryPoint the entry point function name
     * @param stage      the shader stage (e.g. {@link SlangNative#SLANG_STAGE_VERTEX})
     * @param target     the compile target (e.g. {@link SlangNative#SLANG_GLSL})
     * @return the compilation result
     */
    public CompileResult compile(String source, String entryPoint, int stage, int target) {
        return compile(source, List.of(new EntryPointDesc(entryPoint, stage)), target);
    }

    /**
     * Compiles a Slang source string with multiple entry points.
     *
     * @param source      the Slang source code
     * @param entryPoints the entry point descriptors
     * @param target      the compile target
     * @return the compilation result
     */
    public CompileResult compile(String source, List<EntryPointDesc> entryPoints, int target) {
        try (var session = globalSession.createSession(target)) {
            var module = session.loadModuleFromSourceString("shader", source);

            // Find all entry points
            var eps = new SlangEntryPoint[entryPoints.size()];
            for (int i = 0; i < entryPoints.size(); i++) {
                var desc = entryPoints.get(i);
                eps[i] = module.findAndCheckEntryPoint(desc.name(), desc.stage());
            }

            // Create composite: module + all entry points
            var components = new ComPtr[1 + eps.length];
            components[0] = module.com();
            for (int i = 0; i < eps.length; i++) {
                components[i + 1] = eps[i].com();
            }

            var composite = session.createCompositeComponentType(components);
            var linked = composite.link();

            // Extract code for each entry point
            var codes = new String[entryPoints.size()];
            var codeBytes = new byte[entryPoints.size()][];
            for (int i = 0; i < entryPoints.size(); i++) {
                try (var blob = linked.getEntryPointCode(i, 0)) {
                    codes[i] = blob.string();
                    codeBytes[i] = blob.data();
                }
            }

            // Get reflection
            SlangReflection reflection = null;
            try {
                reflection = linked.getLayout(0);
            } catch (Exception e) {
                // Reflection may not be available for all targets
            }

            return new CompileResult(codes, codeBytes, reflection, linked, module, eps, composite);
        }
    }

    @Override
    public void close() {
        globalSession.close();
    }

    /**
     * Describes an entry point to compile.
     */
    public record EntryPointDesc(String name, int stage) {}

    /**
     * Result of a compilation, holding compiled code and reflection data.
     */
    public static class CompileResult implements AutoCloseable {
        private final String[] codes;
        private final byte[][] codeBytes;
        private final SlangReflection reflection;
        private final SlangProgram linked;
        private final SlangModule module;
        private final SlangEntryPoint[] entryPoints;
        private final SlangProgram composite;

        CompileResult(String[] codes, byte[][] codeBytes, SlangReflection reflection,
                      SlangProgram linked, SlangModule module, SlangEntryPoint[] entryPoints,
                      SlangProgram composite) {
            this.codes = codes;
            this.codeBytes = codeBytes;
            this.reflection = reflection;
            this.linked = linked;
            this.module = module;
            this.entryPoints = entryPoints;
            this.composite = composite;
        }

        /** Returns the compiled code for entry point 0 as a string. */
        public String code() {
            return codes.length > 0 ? codes[0] : null;
        }

        /** Returns the compiled code for the given entry point index as a string. */
        public String code(int entryPointIndex) {
            return codes[entryPointIndex];
        }

        /** Returns the compiled code for entry point 0 as raw bytes. */
        public byte[] codeBytes() {
            return codeBytes.length > 0 ? codeBytes[0] : null;
        }

        /** Returns the compiled code for the given entry point as raw bytes. */
        public byte[] codeBytes(int entryPointIndex) {
            return codeBytes[entryPointIndex];
        }

        /** Returns the number of compiled entry points. */
        public int entryPointCount() {
            return codes.length;
        }

        /** Returns the reflection data, or null if not available. */
        public SlangReflection reflection() {
            return reflection;
        }

        @Override
        public void close() {
            if (linked != null) linked.close();
            if (composite != null) composite.close();
            if (entryPoints != null) {
                for (var ep : entryPoints) {
                    if (ep != null) ep.close();
                }
            }
            if (module != null) module.close();
        }
    }
}

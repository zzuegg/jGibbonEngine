package dev.engine.providers.slang.wasm;

/**
 * Abstraction over the Slang WASM compiler module.
 *
 * <p>Implementations bridge between Java and the {@code slang-wasm.wasm} module
 * using platform-specific mechanisms (GraalVM polyglot, TeaVM JSO, etc.).
 * All implementations target the same Emscripten-compiled Slang WASM binary
 * and its embind-generated API.
 *
 * <p>The compilation flow mirrors the Slang Playground pattern:
 * <ol>
 *   <li>Create a global session</li>
 *   <li>Find the WGSL compile target</li>
 *   <li>Create a WGSL session</li>
 *   <li>Load source as a module</li>
 *   <li>Find and check entry points</li>
 *   <li>Create composite, link, extract WGSL code</li>
 * </ol>
 */
public interface SlangWasmBridge {

    /**
     * Returns true if the Slang WASM module is loaded and ready.
     */
    boolean isAvailable();

    /**
     * Compiles Slang source to WGSL for vertex and fragment entry points.
     *
     * @param source        the Slang shader source code
     * @param vertexEntry   vertex entry point name (e.g. "vertexMain")
     * @param fragmentEntry fragment entry point name (e.g. "fragmentMain")
     * @return array of [vertexWGSL, fragmentWGSL]
     */
    String[] compile(String source, String vertexEntry, String fragmentEntry);

    /**
     * Compiles a single compute shader entry point to WGSL.
     *
     * @param source    the Slang shader source code
     * @param entryName compute entry point name
     * @return the compiled WGSL code
     */
    String compileCompute(String source, String entryName);
}

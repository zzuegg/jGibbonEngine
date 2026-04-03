package dev.engine.providers.teavm.webgpu;

import org.teavm.jso.JSBody;

/**
 * Compiles Slang shaders to WGSL using the Slang WASM compiler in the browser.
 *
 * <p>Requires {@code slang-wasm.mjs} and {@code slang-wasm.wasm} to be loaded
 * and initialized before use. The index.html bootstrap script stores the
 * initialized module in {@code window._slangModule}.
 */
public class TeaVmSlangCompiler {

    /**
     * Returns {@code true} if the Slang WASM module has been loaded and is
     * available for compilation.
     */
    @JSBody(script = "return !!window._slangModule;")
    public static native boolean isAvailable();

    /**
     * Returns the Slang compiler version string, or {@code null} if not loaded.
     */
    @JSBody(script = """
        var slang = window._slangModule;
        return slang ? slang.getVersionString() : null;
    """)
    public static native String getVersionString();

    /**
     * Compiles Slang source to WGSL for vertex and fragment entry points.
     *
     * @param source        the Slang shader source code
     * @param vertexEntry   vertex entry point name (e.g. "vertexMain")
     * @param fragmentEntry fragment entry point name (e.g. "fragmentMain")
     * @return array of [vertexWGSL, fragmentWGSL]
     */
    @JSBody(params = {"source", "vertexEntry", "fragmentEntry"}, script = """
        var slang = window._slangModule;
        if (!slang) throw new Error('Slang WASM not loaded');

        var globalSession = slang.createGlobalSession();
        if (!globalSession) throw new Error('Failed to create Slang global session');

        var targets = slang.getCompileTargets();
        var wgslTarget = -1;
        var targetNames = Object.keys(targets);
        for (var i = 0; i < targetNames.length; i++) {
            if (targetNames[i] === 'WGSL') {
                wgslTarget = targets[targetNames[i]];
                break;
            }
        }
        if (wgslTarget < 0) {
            for (var i = 0; i < targetNames.length; i++) {
                if (targetNames[i].toLowerCase().indexOf('wgsl') >= 0) {
                    wgslTarget = targets[targetNames[i]];
                    break;
                }
            }
        }
        if (wgslTarget < 0) throw new Error('WGSL target not found in Slang. Available: ' + targetNames.join(', '));

        var session = globalSession.createSession(wgslTarget);
        if (!session) throw new Error('Failed to create Slang session');

        var module = session.loadModuleFromSource('shader', 'shader.slang', source);
        if (!module) {
            var err = slang.getLastError();
            throw new Error('Slang compile error: ' + (err ? err.message : 'unknown'));
        }

        var vertexEP = module.findEntryPointByName(vertexEntry);
        var fragmentEP = module.findEntryPointByName(fragmentEntry);
        if (!vertexEP) throw new Error('Vertex entry point not found: ' + vertexEntry);
        if (!fragmentEP) throw new Error('Fragment entry point not found: ' + fragmentEntry);

        var components = session.createCompositeComponentType([module, vertexEP, fragmentEP]);
        var linked = components.link();

        var vertexWGSL = linked.getEntryPointCode(0, 0);
        var fragmentWGSL = linked.getEntryPointCode(1, 0);

        linked.delete();
        components.delete();
        fragmentEP.delete();
        vertexEP.delete();
        module.delete();
        session.delete();
        globalSession.delete();

        return [vertexWGSL, fragmentWGSL];
    """)
    public static native String[] compile(String source, String vertexEntry, String fragmentEntry);

    /**
     * Compiles a single compute shader entry point to WGSL.
     *
     * @param source    the Slang shader source code
     * @param entryName compute entry point name
     * @return the compiled WGSL code
     */
    @JSBody(params = {"source", "entryName"}, script = """
        var slang = window._slangModule;
        if (!slang) throw new Error('Slang WASM not loaded');

        var globalSession = slang.createGlobalSession();
        if (!globalSession) throw new Error('Failed to create Slang global session');

        var targets = slang.getCompileTargets();
        var wgslTarget = -1;
        var targetNames = Object.keys(targets);
        for (var i = 0; i < targetNames.length; i++) {
            if (targetNames[i] === 'WGSL') {
                wgslTarget = targets[targetNames[i]];
                break;
            }
        }
        if (wgslTarget < 0) {
            for (var i = 0; i < targetNames.length; i++) {
                if (targetNames[i].toLowerCase().indexOf('wgsl') >= 0) {
                    wgslTarget = targets[targetNames[i]];
                    break;
                }
            }
        }
        if (wgslTarget < 0) throw new Error('WGSL target not found in Slang');

        var session = globalSession.createSession(wgslTarget);
        var module = session.loadModuleFromSource('shader', 'shader.slang', source);
        if (!module) {
            var err = slang.getLastError();
            throw new Error('Slang compile error: ' + (err ? err.message : 'unknown'));
        }

        var ep = module.findEntryPointByName(entryName);
        if (!ep) throw new Error('Entry point not found: ' + entryName);

        var components = session.createCompositeComponentType([module, ep]);
        var linked = components.link();
        var wgsl = linked.getEntryPointCode(0, 0);

        linked.delete();
        components.delete();
        ep.delete();
        module.delete();
        session.delete();
        globalSession.delete();

        return wgsl;
    """)
    public static native String compileCompute(String source, String entryName);
}

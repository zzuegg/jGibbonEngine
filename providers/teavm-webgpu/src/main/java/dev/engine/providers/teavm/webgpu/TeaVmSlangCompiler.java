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

        var srcStr = '' + source;

        // Find WGSL target dynamically (same as Slang Playground)
        var targets = slang.getCompileTargets();
        var wgslTarget = 0;
        for (var i = 0; i < targets.length; i++) {
            if (targets[i].name === 'WGSL') {
                wgslTarget = targets[i].value;
                break;
            }
        }
        if (wgslTarget === 0) throw new Error('WGSL target not found in compile targets');
        var session = globalSession.createSession(wgslTarget);
        if (!session) throw new Error('Failed to create Slang WGSL session');

        var module = session.loadModuleFromSource(srcStr, 'shader', 'shader.slang');
        if (!module) {
            var err = slang.getLastError();
            throw new Error('Slang compile error: ' + (err ? err.message : 'unknown'));
        }

        // Use findAndCheckEntryPoint (like Slang Playground) — handles generics automatically
        // Stage constants: vertex=1, fragment=5
        console.log('[SlangWASM] Module loaded, finding entry points...');
        var vertexEP = module.findAndCheckEntryPoint(vertexEntry, 1);
        console.log('[SlangWASM] vertexEP:', vertexEP);
        if (!vertexEP) {
            var err = slang.getLastError();
            console.error('[SlangWASM] findAndCheckEntryPoint failed for vertex:', err);
            throw new Error('Vertex entry point not found: ' + vertexEntry + ' error: ' + (err ? err.message : ''));
        }
        var fragmentEP = module.findAndCheckEntryPoint(fragmentEntry, 5);
        console.log('[SlangWASM] fragmentEP:', fragmentEP);
        if (!fragmentEP) {
            var err = slang.getLastError();
            console.error('[SlangWASM] findAndCheckEntryPoint failed for fragment:', err);
            throw new Error('Fragment entry point not found: ' + fragmentEntry + ' error: ' + (err ? err.message : ''));
        }

        console.log('[SlangWASM] Creating composite...');
        var components = session.createCompositeComponentType([module, vertexEP, fragmentEP]);
        console.log('[SlangWASM] Linking...');
        var linked = components.link();
        console.log('[SlangWASM] Linked, getting code...');

        // Try getTargetCode first (whole program)
        var wholeCode = null;
        try { wholeCode = linked.getTargetCode(0); } catch(e) { console.log('[SlangWASM] getTargetCode(0) error:', e.message); }
        console.log('[SlangWASM] getTargetCode(0):', wholeCode ? wholeCode.length + ' chars' : 'null');
        if (wholeCode) console.log('[SlangWASM] whole code first 300:', wholeCode.substring(0, 300));

        // Try getEntryPointCode with different target indices
        var vertexWGSL = null;
        var fragmentWGSL = null;
        for (var ti = 0; ti < 3; ti++) {
            try {
                var v = linked.getEntryPointCode(0, ti);
                var f = linked.getEntryPointCode(1, ti);
                console.log('[SlangWASM] getEntryPointCode(0,' + ti + '):', v ? v.length + ' chars' : 'null');
                console.log('[SlangWASM] getEntryPointCode(1,' + ti + '):', f ? f.length + ' chars' : 'null');
                if (v && v.length > 0 && !vertexWGSL) { vertexWGSL = v; fragmentWGSL = f; }
            } catch(e) {
                console.log('[SlangWASM] getEntryPointCode target ' + ti + ' error:', e.message);
            }
        }

        // If still null, use the whole program code (split isn't needed for WGSL)
        if (!vertexWGSL && wholeCode) {
            console.log('[SlangWASM] Using whole program code as both vertex and fragment');
            vertexWGSL = wholeCode;
            fragmentWGSL = wholeCode;
        }

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

        var SLANG_WGSL = 26;
        var session = globalSession.createSession(SLANG_WGSL);
        var module = session.loadModuleFromSource(source, 'shader', 'shader.slang');
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

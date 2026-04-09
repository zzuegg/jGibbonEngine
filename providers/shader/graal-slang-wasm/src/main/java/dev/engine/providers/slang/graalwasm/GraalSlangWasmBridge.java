package dev.engine.providers.slang.graalwasm;

import dev.engine.providers.slang.wasm.SlangWasmBridge;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GraalVM Web Image implementation of {@link SlangWasmBridge}.
 *
 * <p>Assumes the Slang WASM module is loaded by the host HTML page
 * and stored in {@code globalThis._slangModule}.
 */
public class GraalSlangWasmBridge implements SlangWasmBridge {

    private static final Logger log = LoggerFactory.getLogger(GraalSlangWasmBridge.class);

    @Override
    public boolean isAvailable() {
        return isSlangAvailableJS().asBoolean();
    }

    @Override
    public String[] compile(String source, String vertexEntry, String fragmentEntry) {
        JSString jsResult = compileJS(JSString.of(source), JSString.of(vertexEntry), JSString.of(fragmentEntry));
        String result = jsResult != null ? jsResult.asString() : null;
        if (result == null || result.startsWith("ERROR:")) {
            log.error("Slang compilation failed: {}", result);
            return new String[]{"", ""};
        }
        String[] parts = result.split("\\|SPLIT\\|", 2);
        return parts.length == 2 ? parts : new String[]{"", ""};
    }

    @Override
    public String compileCompute(String source, String entryName) {
        JSString jsResult2 = compileComputeJS(JSString.of(source), JSString.of(entryName));
        String result = jsResult2 != null ? jsResult2.asString() : null;
        if (result == null || result.startsWith("ERROR:")) {
            log.error("Slang compute compilation failed: {}", result);
            return "";
        }
        return result;
    }

    @JS(value = "return !!globalThis._slangModule;")
    private static native JSBoolean isSlangAvailableJS();

    @JS(args = {"source", "vertexEntry", "fragmentEntry"}, value = """
        try {
            var slang = globalThis._slangModule;
            var globalSession = slang.createGlobalSession();
            if (!globalSession) return 'ERROR: Failed to create global session';

            var srcStr = '' + source;
            var vsEntry = '' + vertexEntry;
            var fsEntry = '' + fragmentEntry;

            var targets = slang.getCompileTargets();
            var wgslTarget = 0;
            for (var i = 0; i < targets.length; i++) {
                if (targets[i].name === 'WGSL') { wgslTarget = targets[i].value; break; }
            }
            if (wgslTarget === 0) return 'ERROR: WGSL target not found';

            var session = globalSession.createSession(wgslTarget);
            if (!session) return 'ERROR: Failed to create session';

            var module = session.loadModuleFromSource(srcStr, 'shader', 'shader.slang');
            if (!module) {
                var err = slang.getLastError();
                return 'ERROR: ' + (err ? err.message : 'Failed to load module');
            }

            var vertexEP = module.findAndCheckEntryPoint(vsEntry, 1);
            var fragEP = module.findAndCheckEntryPoint(fsEntry, 5);
            if (!vertexEP) return 'ERROR: Vertex entry point not found: ' + vsEntry;
            if (!fragEP) return 'ERROR: Fragment entry point not found: ' + fsEntry;

            var components = session.createCompositeComponentType([module, vertexEP, fragEP]);
            var linked = components.link();
            var vertexCode = linked.getEntryPointCode(0, 0);
            var fragCode = linked.getEntryPointCode(1, 0);

            linked.delete();
            components.delete();
            fragEP.delete();
            vertexEP.delete();
            module.delete();
            session.delete();
            globalSession.delete();

            return vertexCode + '|SPLIT|' + fragCode;
        } catch(e) {
            return 'ERROR: ' + e.message;
        }
    """)
    private static native JSString compileJS(JSString source, JSString vertexEntry, JSString fragmentEntry);

    @JS(args = {"source", "entryName"}, value = """
        try {
            var slang = globalThis._slangModule;
            var globalSession = slang.createGlobalSession();
            if (!globalSession) return 'ERROR: Failed to create global session';

            var srcStr = '' + source;
            var epName = '' + entryName;

            var targets = slang.getCompileTargets();
            var wgslTarget = 0;
            for (var i = 0; i < targets.length; i++) {
                if (targets[i].name === 'WGSL') { wgslTarget = targets[i].value; break; }
            }
            if (wgslTarget === 0) return 'ERROR: WGSL target not found';

            var session = globalSession.createSession(wgslTarget);
            var module = session.loadModuleFromSource(srcStr, 'shader', 'shader.slang');
            if (!module) {
                var err = slang.getLastError();
                return 'ERROR: ' + (err ? err.message : 'Failed to load module');
            }

            var ep = module.findEntryPointByName(epName);
            if (!ep) return 'ERROR: Compute entry point not found: ' + epName;

            var comp = session.createCompositeComponentType([module, ep]);
            var linked = comp.link();
            var code = linked.getEntryPointCode(0, 0);

            linked.delete();
            comp.delete();
            ep.delete();
            module.delete();
            session.delete();
            globalSession.delete();

            return code;
        } catch(e) {
            return 'ERROR: ' + e.message;
        }
    """)
    private static native JSString compileComputeJS(JSString source, JSString entryName);
}

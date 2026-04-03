package dev.engine.bindings.slang;

import java.lang.foreign.*;

/**
 * Wraps an ISession COM interface pointer.
 *
 * <p>ISession extends ISlangUnknown:
 * <ul>
 *   <li>[0] queryInterface</li>
 *   <li>[1] addRef</li>
 *   <li>[2] release</li>
 *   <li>[3] getGlobalSession() -> IGlobalSession*</li>
 *   <li>[4] loadModule(name, outDiag) -> IModule*</li>
 *   <li>[5] loadModuleFromSource(name, path, source, outDiag) -> IModule*</li>
 *   <li>[6] createCompositeComponentType(types*, count, out*, outDiag) -> SlangResult</li>
 *   <li>...</li>
 *   <li>[20] loadModuleFromSourceString(name, path, string, outDiag) -> IModule*</li>
 * </ul>
 */
public class SlangCompileSession implements AutoCloseable {

    private final ComPtr com;

    SlangCompileSession(ComPtr com) {
        this.com = com;
    }

    public ComPtr com() {
        return com;
    }

    /**
     * Loads a module by name (uses Slang's module resolution).
     *
     * @param moduleName the module name
     * @return the loaded module
     */
    public SlangModule loadModule(String moduleName) {
        try (var arena = Arena.ofConfined()) {
            var nameStr = arena.allocateFrom(moduleName);
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // loadModule — vtable index 4
            // IModule* loadModule(const char* name, IBlob** outDiag)
            var handle = com.methodHandle(4, FunctionDescriptor.of(
                    ValueLayout.ADDRESS,    // IModule* return
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // name
                    ValueLayout.ADDRESS     // outDiag
            ));

            var modulePtr = (MemorySegment) handle.invoke(com.ptr(), nameStr, outDiag);
            checkDiagnostics(outDiag, "loadModule");

            if (modulePtr.equals(MemorySegment.NULL)) {
                throw new SlangException("loadModule returned null for '" + moduleName + "'");
            }

            return new SlangModule(new ComPtr(modulePtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("loadModule failed", t);
        }
    }

    /**
     * Loads a module from a source string.
     *
     * @param moduleName the module name
     * @param source     the Slang source code
     * @return the loaded module
     */
    public SlangModule loadModuleFromSourceString(String moduleName, String source) {
        try (var arena = Arena.ofConfined()) {
            var nameStr = arena.allocateFrom(moduleName);
            var pathStr = arena.allocateFrom(moduleName + ".slang");
            var sourceStr = arena.allocateFrom(source);
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // loadModuleFromSourceString — vtable index 20
            // IModule* loadModuleFromSourceString(name, path, string, outDiag)
            var handle = com.methodHandle(20, FunctionDescriptor.of(
                    ValueLayout.ADDRESS,    // IModule* return
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // name
                    ValueLayout.ADDRESS,    // path
                    ValueLayout.ADDRESS,    // string
                    ValueLayout.ADDRESS     // outDiag
            ));

            var modulePtr = (MemorySegment) handle.invoke(com.ptr(), nameStr, pathStr, sourceStr, outDiag);
            checkDiagnostics(outDiag, "loadModuleFromSourceString");

            if (modulePtr.equals(MemorySegment.NULL)) {
                throw new SlangException("loadModuleFromSourceString returned null for '" + moduleName + "'");
            }

            return new SlangModule(new ComPtr(modulePtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("loadModuleFromSourceString failed", t);
        }
    }

    /**
     * Creates a composite component type from multiple components.
     *
     * @param components array of IComponentType pointers (as ComPtr)
     * @return a composite component type
     */
    public SlangProgram createCompositeComponentType(ComPtr... components) {
        try (var arena = Arena.ofConfined()) {
            var typesArray = arena.allocate(ValueLayout.ADDRESS, components.length);
            for (int i = 0; i < components.length; i++) {
                typesArray.setAtIndex(ValueLayout.ADDRESS, i, components[i].ptr());
            }

            var outPtr = arena.allocate(ValueLayout.ADDRESS);
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // createCompositeComponentType — vtable index 6
            // SlangResult createCompositeComponentType(IComponentType**, count, IComponentType**, IBlob**)
            var handle = com.methodHandle(6, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // SlangResult
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // components array
                    ValueLayout.JAVA_LONG,  // count (SlangInt = int64)
                    ValueLayout.ADDRESS,    // out
                    ValueLayout.ADDRESS     // outDiag
            ));

            int result = (int) handle.invoke(com.ptr(), typesArray,
                    (long) components.length, outPtr, outDiag);
            checkDiagnostics(outDiag, "createCompositeComponentType");

            if (result < 0) {
                throw new SlangException("createCompositeComponentType failed", result);
            }

            var ptr = outPtr.get(ValueLayout.ADDRESS, 0);
            return new SlangProgram(new ComPtr(ptr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("createCompositeComponentType failed", t);
        }
    }

    private void checkDiagnostics(MemorySegment outDiag, String context) {
        var diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
        if (!diagPtr.equals(MemorySegment.NULL)) {
            var blob = new SlangBlob(diagPtr);
            var msg = blob.string();
            blob.close();
            if (!msg.isBlank()) {
                // Diagnostics may contain warnings — only throw if no result was returned
                // The caller checks the actual return value
                System.err.println("[Slang " + context + " diagnostics] " + msg);
            }
        }
    }

    @Override
    public void close() {
        com.close();
    }
}

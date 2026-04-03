package dev.engine.bindings.slang;

import java.lang.foreign.*;

/**
 * Wraps an IModule COM interface pointer.
 *
 * <p>IModule extends IComponentType extends ISlangUnknown:
 * <ul>
 *   <li>[0-2] ISlangUnknown methods</li>
 *   <li>[3-16] IComponentType methods</li>
 *   <li>[17] findEntryPointByName(name, IEntryPoint**) -> SlangResult</li>
 *   <li>[18] getDefinedEntryPointCount() -> int32</li>
 *   <li>[19] getDefinedEntryPoint(index, IEntryPoint**) -> SlangResult</li>
 *   <li>[20] serialize(...)</li>
 *   <li>[21] writeToFile(...)</li>
 *   <li>[22] getName() -> const char*</li>
 *   <li>[23] getFilePath() -> const char*</li>
 *   <li>[24] getUniqueIdentity() -> const char*</li>
 *   <li>[25] findAndCheckEntryPoint(name, stage, IEntryPoint**, IBlob**) -> SlangResult</li>
 * </ul>
 */
public class SlangModule implements AutoCloseable {

    private final ComPtr com;

    SlangModule(ComPtr com) {
        this.com = com;
    }

    public ComPtr com() {
        return com;
    }

    /**
     * Finds an entry point by name. The function must be annotated with
     * {@code [shader("...")]} in the Slang source.
     *
     * @param name the entry point function name
     * @return the entry point
     */
    public SlangEntryPoint findEntryPointByName(String name) {
        try (var arena = Arena.ofConfined()) {
            var nameStr = arena.allocateFrom(name);
            var outPtr = arena.allocate(ValueLayout.ADDRESS);

            // findEntryPointByName — vtable index 17
            var handle = com.methodHandle(17, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // SlangResult
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // name
                    ValueLayout.ADDRESS     // IEntryPoint** out
            ));

            int result = (int) handle.invoke(com.ptr(), nameStr, outPtr);
            if (result < 0) {
                throw new SlangException("findEntryPointByName failed for '" + name + "'", result);
            }

            var epPtr = outPtr.get(ValueLayout.ADDRESS, 0);
            if (epPtr.equals(MemorySegment.NULL)) {
                throw new SlangException("findEntryPointByName returned null for '" + name + "'");
            }

            return new SlangEntryPoint(new ComPtr(epPtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("findEntryPointByName failed", t);
        }
    }

    /**
     * Finds and checks an entry point by name and stage.
     * Works even if the function is not annotated with {@code [shader("...")]}.
     *
     * @param name  the entry point function name
     * @param stage the shader stage (e.g. {@link SlangNative#SLANG_STAGE_VERTEX})
     * @return the entry point
     */
    public SlangEntryPoint findAndCheckEntryPoint(String name, int stage) {
        try (var arena = Arena.ofConfined()) {
            var nameStr = arena.allocateFrom(name);
            var outEntryPoint = arena.allocate(ValueLayout.ADDRESS);
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // findAndCheckEntryPoint — vtable index 25
            var handle = com.methodHandle(25, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // SlangResult
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // name
                    ValueLayout.JAVA_INT,   // stage
                    ValueLayout.ADDRESS,    // IEntryPoint** out
                    ValueLayout.ADDRESS     // IBlob** outDiag
            ));

            int result = (int) handle.invoke(com.ptr(), nameStr, stage, outEntryPoint, outDiag);

            // Check diagnostics
            var diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
            if (!diagPtr.equals(MemorySegment.NULL)) {
                var blob = new SlangBlob(diagPtr);
                var msg = blob.string();
                blob.close();
                if (!msg.isBlank()) {
                    System.err.println("[Slang findAndCheckEntryPoint diagnostics] " + msg);
                }
            }

            if (result < 0) {
                throw new SlangException("findAndCheckEntryPoint failed for '" + name + "'", result);
            }

            var epPtr = outEntryPoint.get(ValueLayout.ADDRESS, 0);
            if (epPtr.equals(MemorySegment.NULL)) {
                throw new SlangException("findAndCheckEntryPoint returned null for '" + name + "'");
            }

            return new SlangEntryPoint(new ComPtr(epPtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("findAndCheckEntryPoint failed", t);
        }
    }

    @Override
    public void close() {
        com.close();
    }
}

package dev.engine.bindings.slang;

import java.lang.foreign.*;

/**
 * Wraps an IComponentType COM interface pointer representing a linked program.
 *
 * <p>IComponentType extends ISlangUnknown:
 * <ul>
 *   <li>[0-2] ISlangUnknown methods</li>
 *   <li>[3] getSession() -> ISession*</li>
 *   <li>[4] getLayout(targetIndex, outDiag) -> ProgramLayout*</li>
 *   <li>[5] getSpecializationParamCount() -> SlangInt</li>
 *   <li>[6] getEntryPointCode(epIndex, targetIndex, outCode, outDiag) -> SlangResult</li>
 *   <li>[7] getResultAsFileSystem(...)</li>
 *   <li>[8] getEntryPointHash(...)</li>
 *   <li>[9] specialize(...)</li>
 *   <li>[10] link(out, outDiag) -> SlangResult</li>
 * </ul>
 */
public class SlangProgram implements AutoCloseable {

    private final ComPtr com;

    SlangProgram(ComPtr com) {
        this.com = com;
    }

    public ComPtr com() {
        return com;
    }

    /**
     * Links this component type, resolving all dependencies.
     *
     * @return a new linked program
     */
    public SlangProgram link() {
        try (var arena = Arena.ofConfined()) {
            var outPtr = arena.allocate(ValueLayout.ADDRESS);
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // link — vtable index 10
            var handle = com.methodHandle(10, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // SlangResult
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // IComponentType** out
                    ValueLayout.ADDRESS     // IBlob** outDiag
            ));

            int result = (int) handle.invoke(com.ptr(), outPtr, outDiag);

            // Check diagnostics
            var diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
            if (!diagPtr.equals(MemorySegment.NULL)) {
                var blob = new SlangBlob(diagPtr);
                var msg = blob.string();
                blob.close();
                if (!msg.isBlank()) {
                    System.err.println("[Slang link diagnostics] " + msg);
                }
            }

            if (result < 0) {
                throw new SlangException("IComponentType::link failed", result);
            }

            var linkedPtr = outPtr.get(ValueLayout.ADDRESS, 0);
            if (linkedPtr.equals(MemorySegment.NULL)) {
                throw new SlangException("link returned null");
            }

            return new SlangProgram(new ComPtr(linkedPtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("link failed", t);
        }
    }

    /**
     * Gets the reflection layout for this program.
     *
     * @param targetIndex the target index (usually 0)
     * @return the reflection data
     */
    public SlangReflection getLayout(int targetIndex) {
        try (var arena = Arena.ofConfined()) {
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // getLayout — vtable index 4
            var handle = com.methodHandle(4, FunctionDescriptor.of(
                    ValueLayout.ADDRESS,    // ProgramLayout* return
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.JAVA_LONG,  // targetIndex (SlangInt = int64)
                    ValueLayout.ADDRESS     // IBlob** outDiag
            ));

            var layoutPtr = (MemorySegment) handle.invoke(com.ptr(), (long) targetIndex, outDiag);

            if (layoutPtr.equals(MemorySegment.NULL)) {
                throw new SlangException("getLayout returned null");
            }

            return new SlangReflection(layoutPtr);
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("getLayout failed", t);
        }
    }

    /**
     * Gets the reflection layout for target 0.
     */
    public SlangReflection getLayout() {
        return getLayout(0);
    }

    /**
     * Gets the compiled code for an entry point.
     *
     * @param entryPointIndex the entry point index
     * @param targetIndex     the target index (usually 0)
     * @return the compiled code as a blob
     */
    public SlangBlob getEntryPointCode(int entryPointIndex, int targetIndex) {
        try (var arena = Arena.ofConfined()) {
            var outCode = arena.allocate(ValueLayout.ADDRESS);
            var outDiag = arena.allocate(ValueLayout.ADDRESS);
            outDiag.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);

            // getEntryPointCode — vtable index 6
            var handle = com.methodHandle(6, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // SlangResult
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.JAVA_LONG,  // entryPointIndex (SlangInt)
                    ValueLayout.JAVA_LONG,  // targetIndex (SlangInt)
                    ValueLayout.ADDRESS,    // IBlob** outCode
                    ValueLayout.ADDRESS     // IBlob** outDiag
            ));

            int result = (int) handle.invoke(com.ptr(),
                    (long) entryPointIndex, (long) targetIndex, outCode, outDiag);

            // Check diagnostics
            var diagPtr = outDiag.get(ValueLayout.ADDRESS, 0);
            if (!diagPtr.equals(MemorySegment.NULL)) {
                var blob = new SlangBlob(diagPtr);
                var msg = blob.string();
                blob.close();
                if (!msg.isBlank()) {
                    System.err.println("[Slang getEntryPointCode diagnostics] " + msg);
                }
            }

            if (result < 0) {
                throw new SlangException("getEntryPointCode failed", result);
            }

            var codePtr = outCode.get(ValueLayout.ADDRESS, 0);
            if (codePtr.equals(MemorySegment.NULL)) {
                throw new SlangException("getEntryPointCode returned null blob");
            }

            return new SlangBlob(codePtr);
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("getEntryPointCode failed", t);
        }
    }

    @Override
    public void close() {
        com.close();
    }
}

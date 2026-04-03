package dev.engine.bindings.slang;

import java.lang.foreign.*;
import java.util.List;

/**
 * Wraps an IGlobalSession COM interface pointer.
 *
 * <p>IGlobalSession extends ISlangUnknown:
 * <ul>
 *   <li>[0] queryInterface</li>
 *   <li>[1] addRef</li>
 *   <li>[2] release</li>
 *   <li>[3] createSession(SessionDesc*, ISession**) -> SlangResult</li>
 *   <li>[4] findProfile(const char*) -> SlangProfileID</li>
 *   <li>... more methods</li>
 * </ul>
 */
public class SlangSession implements AutoCloseable {

    private final ComPtr com;

    SlangSession(ComPtr com) {
        this.com = com;
    }

    public ComPtr com() {
        return com;
    }

    /**
     * Creates a compile session with the given targets and search paths.
     *
     * @param targets     list of SlangCompileTarget values (e.g. SLANG_GLSL)
     * @param searchPaths list of include search paths (can be empty)
     * @return a new compile session
     */
    public SlangCompileSession createSession(List<Integer> targets, List<String> searchPaths) {
        try (var arena = Arena.ofConfined()) {
            // Build TargetDesc array
            // TargetDesc layout (Linux x86_64):
            //   size_t structureSize (8)
            //   int format (4)
            //   int profile (4)
            //   uint flags (4)
            //   int floatingPointMode (4)
            //   int lineDirectiveMode (4)
            //   bool forceGLSLScalarBufferLayout (1 + 3 padding)
            //   ptr compilerOptionEntries (8)
            //   uint compilerOptionEntryCount (4 + 4 padding)
            //   Total: 48 bytes
            long targetDescSize = 48;
            var targetDescs = arena.allocate(targetDescSize * targets.size());
            for (int i = 0; i < targets.size(); i++) {
                long off = i * targetDescSize;
                targetDescs.set(ValueLayout.JAVA_LONG, off, targetDescSize);  // structureSize
                targetDescs.set(ValueLayout.JAVA_INT, off + 8, targets.get(i)); // format
                // All other fields default to 0
            }

            // Build search paths array
            MemorySegment searchPathsArray = MemorySegment.NULL;
            if (!searchPaths.isEmpty()) {
                searchPathsArray = arena.allocate(ValueLayout.ADDRESS, searchPaths.size());
                for (int i = 0; i < searchPaths.size(); i++) {
                    var str = arena.allocateFrom(searchPaths.get(i));
                    searchPathsArray.setAtIndex(ValueLayout.ADDRESS, i, str);
                }
            }

            // Build SessionDesc
            // SessionDesc layout (Linux x86_64, verified with offsetof):
            //   offset  0: size_t structureSize (8)
            //   offset  8: TargetDesc* targets (8)
            //   offset 16: SlangInt targetCount (8)
            //   offset 24: uint32 flags (4)
            //   offset 28: uint32 defaultMatrixLayoutMode (4)
            //   offset 32: char** searchPaths (8)
            //   offset 40: SlangInt searchPathCount (8)
            //   offset 48: PreprocessorMacroDesc* preprocessorMacros (8)
            //   offset 56: SlangInt preprocessorMacroCount (8)
            //   offset 64: ISlangFileSystem* fileSystem (8)
            //   offset 72: bool enableEffectAnnotations (1)
            //   offset 73: bool allowGLSLSyntax (1)
            //   offset 80: CompilerOptionEntry* compilerOptionEntries (8)
            //   offset 88: uint32 compilerOptionEntryCount (4)
            //   offset 92: bool skipSPIRVValidation (1)
            //   Total: 96 bytes
            long sessionDescSize = 96;
            var sessionDesc = arena.allocate(sessionDescSize);
            sessionDesc.set(ValueLayout.JAVA_LONG, 0, sessionDescSize);           // structureSize
            sessionDesc.set(ValueLayout.ADDRESS, 8, targetDescs);                  // targets
            sessionDesc.set(ValueLayout.JAVA_LONG, 16, targets.size());            // targetCount
            sessionDesc.set(ValueLayout.JAVA_INT, 24, 0);                          // flags
            sessionDesc.set(ValueLayout.JAVA_INT, 28, 1);                          // defaultMatrixLayoutMode (ROW_MAJOR=1)
            if (!searchPaths.isEmpty()) {
                sessionDesc.set(ValueLayout.ADDRESS, 32, searchPathsArray);         // searchPaths
                sessionDesc.set(ValueLayout.JAVA_LONG, 40, searchPaths.size());     // searchPathCount
            }

            // Allocate output pointer
            var outPtr = arena.allocate(ValueLayout.ADDRESS);

            // Call createSession — vtable index 3
            var handle = com.methodHandle(3, FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // SlangResult
                    ValueLayout.ADDRESS,    // this
                    ValueLayout.ADDRESS,    // SessionDesc*
                    ValueLayout.ADDRESS     // ISession** out
            ));

            int result = (int) handle.invoke(com.ptr(), sessionDesc, outPtr);
            if (result < 0) {
                throw new SlangException("IGlobalSession::createSession failed", result);
            }

            var sessionPtr = outPtr.get(ValueLayout.ADDRESS, 0);
            if (sessionPtr.equals(MemorySegment.NULL)) {
                throw new SlangException("createSession returned null");
            }

            return new SlangCompileSession(new ComPtr(sessionPtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("createSession failed", t);
        }
    }

    /**
     * Creates a compile session with a single target and no search paths.
     */
    public SlangCompileSession createSession(int target) {
        return createSession(List.of(target), List.of());
    }

    @Override
    public void close() {
        com.close();
    }
}

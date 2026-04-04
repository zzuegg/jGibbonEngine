package dev.engine.bindings.slang;

import dev.engine.core.native_.NativeLibraryLoader;
import dev.engine.bindings.slang.SlangSpec;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Low-level FFM bindings to the Slang shared library.
 *
 * <p>Loads {@code libslang.so} (or platform equivalent) and binds the
 * {@code slang_createGlobalSession2} C entry point. COM interface methods
 * are accessed through {@link ComPtr}.
 */
public final class SlangNative {

    // Compile targets (SlangCompileTarget enum values)
    public static final int SLANG_GLSL = 2;
    public static final int SLANG_SPIRV = 6;
    public static final int SLANG_WGSL = 28;

    // Shader stages (SlangStage enum values)
    public static final int SLANG_STAGE_NONE = 0;
    public static final int SLANG_STAGE_VERTEX = 1;
    public static final int SLANG_STAGE_FRAGMENT = 5;
    public static final int SLANG_STAGE_COMPUTE = 6;

    private static volatile SymbolLookup library;
    private static volatile MethodHandle createGlobalSession2;

    // Reflection C API handles (from deprecated but stable C API)
    private static volatile MethodHandle spReflection_GetParameterCount;
    private static volatile MethodHandle spReflection_GetParameterByIndex;
    private static volatile MethodHandle spReflectionVariable_GetName;
    private static volatile MethodHandle spReflectionVariableLayout_GetVariable;
    private static volatile MethodHandle spReflectionVariableLayout_GetOffset;
    private static volatile MethodHandle spReflectionVariableLayout_GetSpace;
    private static volatile MethodHandle spReflectionVariableLayout_GetTypeLayout;

    private SlangNative() {}

    /**
     * Attempts to load the Slang shared library. Returns true if available.
     */
    public static boolean isAvailable() {
        try {
            ensureLoaded();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Loads the native library if not already loaded.
     *
     * @throws SlangException if the library cannot be found or loaded
     */
    public static synchronized void ensureLoaded() {
        if (library != null) return;

        Path libPath = findLibraryPath();
        if (libPath == null) {
            throw new SlangException("Slang native library not found");
        }

        // libslang.so is a self-contained copy of libslang-compiler.so.
        // No companion libraries need to be pre-loaded.
        var arena = Arena.global();
        var lookup = SymbolLookup.libraryLookup(libPath, arena);

        library = lookup;
        bindFunctions();
    }

    private static Path findLibraryPath() {
        // 1. Check tools/lib/ relative to working dir (walking up)
        var dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && dir != null; i++) {
            var candidate = dir.resolve("tools/lib/libslang.so");
            if (Files.exists(candidate)) return candidate;
            dir = dir.getParent();
        }

        // 2. Try NativeLibraryLoader
        try {
            var loader = NativeLibraryLoader.defaultLoader();
            var result = loader.resolve(SlangSpec.spec());
            if (result.isAvailable() && result.libraryPath() != null) {
                var candidate = result.libraryPath().resolve("libslang.so");
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static void bindFunctions() {
        var linker = Linker.nativeLinker();

        // SlangResult slang_createGlobalSession2(const SlangGlobalSessionDesc* desc, IGlobalSession** out)
        var createSym = library.find("slang_createGlobalSession2")
                .orElseThrow(() -> new SlangException("Symbol slang_createGlobalSession2 not found"));
        createGlobalSession2 = linker.downcallHandle(createSym, FunctionDescriptor.of(
                ValueLayout.JAVA_INT,   // SlangResult return
                ValueLayout.ADDRESS,    // desc pointer
                ValueLayout.ADDRESS     // out pointer
        ));

        // Reflection C API bindings
        bindReflectionFunctions(linker);
    }

    private static void bindReflectionFunctions(Linker linker) {
        // These are from the deprecated-but-stable C reflection API exported from libslang.so
        library.find("spReflection_GetParameterCount").ifPresent(sym ->
                spReflection_GetParameterCount = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS)));

        library.find("spReflection_GetParameterByIndex").ifPresent(sym ->
                spReflection_GetParameterByIndex = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)));

        library.find("spReflectionVariable_GetName").ifPresent(sym ->
                spReflectionVariable_GetName = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS)));

        library.find("spReflectionVariableLayout_GetVariable").ifPresent(sym ->
                spReflectionVariableLayout_GetVariable = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS)));

        library.find("spReflectionVariableLayout_GetOffset").ifPresent(sym ->
                spReflectionVariableLayout_GetOffset = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)));

        library.find("spReflectionVariableLayout_GetSpace").ifPresent(sym ->
                spReflectionVariableLayout_GetSpace = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT)));

        library.find("spReflectionVariableLayout_GetTypeLayout").ifPresent(sym ->
                spReflectionVariableLayout_GetTypeLayout = linker.downcallHandle(sym, FunctionDescriptor.of(
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS)));
    }

    /**
     * Creates a global session using {@code slang_createGlobalSession2}.
     *
     * @return a new {@link SlangSession} wrapping IGlobalSession
     */
    public static SlangSession createGlobalSession() {
        ensureLoaded();
        try (var arena = Arena.ofConfined()) {
            // Allocate SlangGlobalSessionDesc (zeroed = use defaults)
            // struct: uint32 structureSize, uint32 apiVersion, uint32 minLanguageVersion,
            //         bool enableGLSL, uint32[16] reserved
            // Total: 4+4+4+4+64 = 80 bytes
            var desc = arena.allocate(80);
            desc.set(ValueLayout.JAVA_INT, 0, 80);  // structureSize
            desc.set(ValueLayout.JAVA_INT, 4, 0);    // apiVersion = SLANG_API_VERSION (0)
            desc.set(ValueLayout.JAVA_INT, 8, 2025); // minLanguageVersion = SLANG_LANGUAGE_VERSION_2025
            desc.set(ValueLayout.JAVA_BYTE, 12, (byte) 0); // enableGLSL = false

            // Allocate output pointer
            var outPtr = arena.allocate(ValueLayout.ADDRESS);

            int result = (int) createGlobalSession2.invoke(desc, outPtr);
            if (result < 0) {
                throw new SlangException("slang_createGlobalSession2 failed", result);
            }

            var sessionPtr = outPtr.get(ValueLayout.ADDRESS, 0);
            if (sessionPtr.equals(MemorySegment.NULL)) {
                throw new SlangException("slang_createGlobalSession2 returned null session");
            }

            return new SlangSession(new ComPtr(sessionPtr));
        } catch (SlangException e) {
            throw e;
        } catch (Throwable t) {
            throw new SlangException("Failed to create global session", t);
        }
    }

    // --- Reflection C API accessors ---

    static int reflectionGetParameterCount(MemorySegment programLayout) {
        if (spReflection_GetParameterCount == null) return 0;
        try {
            return (int) spReflection_GetParameterCount.invoke(programLayout);
        } catch (Throwable t) {
            throw new SlangException("spReflection_GetParameterCount failed", t);
        }
    }

    static MemorySegment reflectionGetParameterByIndex(MemorySegment programLayout, int index) {
        if (spReflection_GetParameterByIndex == null) return MemorySegment.NULL;
        try {
            return (MemorySegment) spReflection_GetParameterByIndex.invoke(programLayout, index);
        } catch (Throwable t) {
            throw new SlangException("spReflection_GetParameterByIndex failed", t);
        }
    }

    static String reflectionVariableGetName(MemorySegment variable) {
        if (spReflectionVariable_GetName == null) return null;
        try {
            var namePtr = (MemorySegment) spReflectionVariable_GetName.invoke(variable);
            if (namePtr.equals(MemorySegment.NULL)) return null;
            return namePtr.reinterpret(1024).getString(0);
        } catch (Throwable t) {
            throw new SlangException("spReflectionVariable_GetName failed", t);
        }
    }

    static MemorySegment reflectionVariableLayoutGetVariable(MemorySegment variableLayout) {
        if (spReflectionVariableLayout_GetVariable == null) return MemorySegment.NULL;
        try {
            return (MemorySegment) spReflectionVariableLayout_GetVariable.invoke(variableLayout);
        } catch (Throwable t) {
            throw new SlangException("spReflectionVariableLayout_GetVariable failed", t);
        }
    }

    static long reflectionVariableLayoutGetOffset(MemorySegment variableLayout, int category) {
        if (spReflectionVariableLayout_GetOffset == null) return -1;
        try {
            return (long) spReflectionVariableLayout_GetOffset.invoke(variableLayout, category);
        } catch (Throwable t) {
            throw new SlangException("spReflectionVariableLayout_GetOffset failed", t);
        }
    }

    static long reflectionVariableLayoutGetSpace(MemorySegment variableLayout, int category) {
        if (spReflectionVariableLayout_GetSpace == null) return -1;
        try {
            return (long) spReflectionVariableLayout_GetSpace.invoke(variableLayout, category);
        } catch (Throwable t) {
            throw new SlangException("spReflectionVariableLayout_GetSpace failed", t);
        }
    }
}

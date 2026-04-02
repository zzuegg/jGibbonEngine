package dev.engine.core.native_;

import java.nio.file.Path;

/**
 * Result of resolving a native library: the directory containing
 * extracted binaries/libraries, and helper methods to find them.
 */
public record NativeLibraryResult(boolean isAvailable, Path libraryPath) {

    public static final NativeLibraryResult UNAVAILABLE = new NativeLibraryResult(false, null);

    /** Full path to an executable within the resolved library directory */
    public Path executablePath(String name) {
        if (libraryPath == null) return null;
        var platform = Platform.current();
        return libraryPath.resolve(name + platform.os().executableExtension());
    }

    /** The directory path for LD_LIBRARY_PATH / DYLD_LIBRARY_PATH / PATH */
    public String librarySearchPath() {
        return libraryPath != null ? libraryPath.toString() : null;
    }
}

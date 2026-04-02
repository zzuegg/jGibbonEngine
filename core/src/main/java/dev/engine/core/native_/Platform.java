package dev.engine.core.native_;

/**
 * Detects the current OS and architecture for native library resolution.
 */
public record Platform(OS os, Arch arch) {

    private static final Platform CURRENT = detect();

    public static Platform current() { return CURRENT; }

    /** Platform identifier string, e.g. "linux-x86_64", "windows-x86_64", "macos-aarch64" */
    public String identifier() { return os.id() + "-" + arch.id(); }

    public enum OS {
        LINUX("linux"), WINDOWS("windows"), MACOS("macos"), UNKNOWN("unknown");

        private final String id;
        OS(String id) { this.id = id; }
        public String id() { return id; }

        /** File extension for shared libraries on this OS */
        public String sharedLibExtension() {
            return switch (this) {
                case LINUX -> ".so";
                case WINDOWS -> ".dll";
                case MACOS -> ".dylib";
                case UNKNOWN -> ".so";
            };
        }

        /** File extension for executables on this OS (empty on Unix) */
        public String executableExtension() {
            return this == WINDOWS ? ".exe" : "";
        }

        /** Environment variable for library search path */
        public String libraryPathVar() {
            return switch (this) {
                case LINUX -> "LD_LIBRARY_PATH";
                case MACOS -> "DYLD_LIBRARY_PATH";
                case WINDOWS -> "PATH";
                case UNKNOWN -> "LD_LIBRARY_PATH";
            };
        }
    }

    public enum Arch {
        X86_64("x86_64"), AARCH64("aarch64"), UNKNOWN("unknown");

        private final String id;
        Arch(String id) { this.id = id; }
        public String id() { return id; }
    }

    private static Platform detect() {
        var osName = System.getProperty("os.name", "").toLowerCase();
        var archName = System.getProperty("os.arch", "").toLowerCase();

        OS os;
        if (osName.contains("linux")) os = OS.LINUX;
        else if (osName.contains("windows")) os = OS.WINDOWS;
        else if (osName.contains("mac") || osName.contains("darwin")) os = OS.MACOS;
        else os = OS.UNKNOWN;

        Arch arch;
        if (archName.contains("amd64") || archName.contains("x86_64")) arch = Arch.X86_64;
        else if (archName.contains("aarch64") || archName.contains("arm64")) arch = Arch.AARCH64;
        else arch = Arch.UNKNOWN;

        return new Platform(os, arch);
    }
}

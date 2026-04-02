package dev.engine.core.native_;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Describes a native library/tool to be loaded: name, version, executables,
 * shared libraries, and optional download URLs per platform.
 */
public record NativeLibrarySpec(
        String name,
        String version,
        List<String> executables,
        List<String> libraries,
        Map<String, String> downloadUrls,
        String classpathPrefix
) {

    public static Builder builder(String name) { return new Builder(name); }

    public static class Builder {
        private final String name;
        private String version = "latest";
        private final List<String> executables = new ArrayList<>();
        private final List<String> libraries = new ArrayList<>();
        private final Map<String, String> downloadUrls = new HashMap<>();
        private String classpathPrefix;

        Builder(String name) { this.name = name; }

        public Builder version(String version) { this.version = version; return this; }
        public Builder executable(String exe) { executables.add(exe); return this; }
        public Builder library(String lib) { libraries.add(lib); return this; }
        public Builder downloadUrl(String platformId, String url) { downloadUrls.put(platformId, url); return this; }

        /** Classpath prefix for bundled resources, e.g. "natives/slang/" */
        public Builder classpathResource(String prefix) { this.classpathPrefix = prefix; return this; }

        public NativeLibrarySpec build() {
            return new NativeLibrarySpec(name, version,
                    Collections.unmodifiableList(new ArrayList<>(executables)),
                    Collections.unmodifiableList(new ArrayList<>(libraries)),
                    Collections.unmodifiableMap(new HashMap<>(downloadUrls)),
                    classpathPrefix);
        }
    }
}

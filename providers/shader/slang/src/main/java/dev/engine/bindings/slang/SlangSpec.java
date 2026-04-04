package dev.engine.bindings.slang;

import dev.engine.core.native_.NativeLibrarySpec;

/**
 * Slang compiler native library specification.
 * Knows the version, executables, libraries, and download URLs.
 */
public final class SlangSpec {

    public static final String VERSION = "2026.5.2";

    private static final String BASE_URL = "https://github.com/shader-slang/slang/releases/download/v" + VERSION + "/";

    public static NativeLibrarySpec spec() {
        return NativeLibrarySpec.builder("slang")
                .version(VERSION)
                .executable("slangc")
                .library("libslang-compiler.so")
                .library("libslang-rt.so")
                .library("libslang-glslang-" + VERSION + ".so")
                .library("libslang-glsl-module-" + VERSION + ".so")
                .downloadUrl("linux-x86_64", BASE_URL + "slang-" + VERSION + "-linux-x86_64.tar.gz")
                .downloadUrl("linux-aarch64", BASE_URL + "slang-" + VERSION + "-linux-aarch64.tar.gz")
                .downloadUrl("windows-x86_64", BASE_URL + "slang-" + VERSION + "-windows-x86_64.zip")
                .downloadUrl("macos-x86_64", BASE_URL + "slang-" + VERSION + "-macos-x86_64.tar.gz")
                .downloadUrl("macos-aarch64", BASE_URL + "slang-" + VERSION + "-macos-aarch64.tar.gz")
                .build();
    }

    private SlangSpec() {}
}

package dev.engine.bindings.wgpu;

import dev.engine.core.native_.NativeLibrarySpec;
import dev.engine.core.native_.Platform;

/**
 * wgpu-native library specification.
 * Knows the version, shared library name, and download URLs per platform.
 *
 * <p>Release archives from https://github.com/gfx-rs/wgpu-native/releases
 * contain the shared library directly (no nested directories).
 */
public final class WgpuSpec {

    public static final String VERSION = "24.0.3.1";

    private static final String BASE_URL =
            "https://github.com/gfx-rs/wgpu-native/releases/download/v" + VERSION + "/";

    /**
     * Returns the native library spec for wgpu-native.
     *
     * <p>The shared library name varies by platform:
     * <ul>
     *   <li>Linux: {@code libwgpu_native.so}</li>
     *   <li>macOS: {@code libwgpu_native.dylib}</li>
     *   <li>Windows: {@code wgpu_native.dll}</li>
     * </ul>
     */
    public static NativeLibrarySpec spec() {
        var os = Platform.current().os();
        var libName = switch (os) {
            case LINUX -> "libwgpu_native.so";
            case MACOS -> "libwgpu_native.dylib";
            case WINDOWS -> "wgpu_native.dll";
            case UNKNOWN -> "libwgpu_native.so";
        };

        return NativeLibrarySpec.builder("wgpu-native")
                .version(VERSION)
                .library(libName)
                .downloadUrl("linux-x86_64", BASE_URL + "wgpu-linux-x86_64-release.zip")
                .downloadUrl("linux-aarch64", BASE_URL + "wgpu-linux-aarch64-release.zip")
                .downloadUrl("windows-x86_64", BASE_URL + "wgpu-windows-x86_64-release.zip")
                .downloadUrl("macos-x86_64", BASE_URL + "wgpu-macos-x86_64-release.zip")
                .downloadUrl("macos-aarch64", BASE_URL + "wgpu-macos-aarch64-release.zip")
                .build();
    }

    private WgpuSpec() {}
}

package dev.engine.providers.wgpu;

import dev.engine.core.native_.NativeLibrarySpec;

/**
 * wgpu-native library specification for auto-download and caching.
 */
public final class WgpuSpec {

    public static final String VERSION = "27.0.4.0";

    private static final String BASE_URL =
            "https://github.com/gfx-rs/wgpu-native/releases/download/v" + VERSION + "/";

    public static NativeLibrarySpec spec() {
        return NativeLibrarySpec.builder("wgpu-native")
                .version(VERSION)
                .library("libwgpu_native.so")
                .downloadUrl("linux-x86_64", BASE_URL + "wgpu-linux-x86_64-release.zip")
                .downloadUrl("linux-aarch64", BASE_URL + "wgpu-linux-aarch64-release.zip")
                .downloadUrl("windows-x86_64", BASE_URL + "wgpu-windows-x86_64-msvc-release.zip")
                .downloadUrl("macos-x86_64", BASE_URL + "wgpu-macos-x86_64-release.zip")
                .downloadUrl("macos-aarch64", BASE_URL + "wgpu-macos-aarch64-release.zip")
                .build();
    }

    private WgpuSpec() {}
}

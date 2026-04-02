# Native Library Loading

## Architecture

The `NativeLibraryLoader` is a general-purpose system for resolving native binaries and shared libraries. It's not specific to any one library — Slang, wgpu-native, SDL3, physics engines, or any other native dependency can use it.

## Resolution Order

1. **Cache check** — `~/.engine/natives/{name}/{version}/{os}-{arch}/`
2. **Classpath extraction** — if bundled in the JAR under a configurable prefix
3. **Download** — from a URL specified per platform in the `NativeLibrarySpec`

Once resolved, results are cached permanently. Subsequent calls are instant.

## Platform Detection

`Platform.current()` returns the detected `(OS, Arch)` pair:
- OS: `linux`, `windows`, `macos`
- Arch: `x86_64`, `aarch64`
- Combined: `linux-x86_64`, `macos-aarch64`, etc.

Also provides per-OS helpers:
- `sharedLibExtension()` → `.so` / `.dll` / `.dylib`
- `executableExtension()` → `""` / `.exe`
- `libraryPathVar()` → `LD_LIBRARY_PATH` / `DYLD_LIBRARY_PATH` / `PATH`

## Usage

```java
// Define what you need
var spec = NativeLibrarySpec.builder("slang")
    .version("2026.5.2")
    .executable("slangc")
    .library("libslang-compiler.so")
    .downloadUrl("linux-x86_64", "https://...")
    .build();

// Resolve (downloads if needed)
var loader = NativeLibraryLoader.defaultLoader();
var result = loader.resolve(spec);

if (result.isAvailable()) {
    Path slangc = result.executablePath("slangc");
    String libPath = result.librarySearchPath();
}
```

## Slang Integration

`SlangCompiler.find()` resolves slangc in this order:
1. `tools/bin/slangc` in project tree (walks up from working directory)
2. System PATH
3. **Auto-download** via `NativeLibraryLoader` → `~/.engine/natives/slang/2026.5.2/linux-x86_64/`

The auto-download means Slang works on any system without manual installation.

## Cache Location

Default: `~/.engine/natives/`

Structure:
```
~/.engine/natives/
├── slang/
│   └── 2026.5.2/
│       └── linux-x86_64/
│           ├── slangc
│           ├── libslang-compiler.so
│           └── ...
└── wgpu/
    └── 0.20.0/
        └── linux-x86_64/
            └── libwgpu_native.so
```

## Adding a New Native Library

1. Create a spec:
```java
var spec = NativeLibrarySpec.builder("my-native-lib")
    .version("1.0")
    .executable("my-tool")
    .library("libmylib.so")
    .downloadUrl("linux-x86_64", "https://releases.example.com/mylib-linux.tar.gz")
    .downloadUrl("windows-x86_64", "https://releases.example.com/mylib-windows.zip")
    .build();
```

2. Resolve it:
```java
var result = NativeLibraryLoader.defaultLoader().resolve(spec);
```

3. Use it. The loader handles caching, platform detection, and extraction.

## Gotchas

- **Symlinks on NTFS/exFAT** — `tar xzf` may fail creating symlinks on non-Unix filesystems. The loader copies files instead when needed.
- **Executable permissions** — the loader calls `chmod +x` on extracted executables. On Windows this is a no-op.
- **Archive format** — supports `.tar.gz` and `.zip`. Detection is by URL suffix.
- **strip-components** — tar extraction tries `--strip-components=1` first (for archives with a top-level directory), falls back to plain extraction.

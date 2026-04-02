package dev.engine.core.native_;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeLibraryLoaderTest {

    @Nested
    class PlatformDetection {
        @Test void detectsCurrentPlatform() {
            var platform = Platform.current();
            assertNotNull(platform.os());
            assertNotNull(platform.arch());
            assertNotNull(platform.identifier());
            assertTrue(platform.identifier().contains("-")); // e.g. "linux-x86_64"
        }

        @Test void platformIdentifierIsConsistent() {
            var a = Platform.current();
            var b = Platform.current();
            assertEquals(a.identifier(), b.identifier());
        }
    }

    @Nested
    class LibrarySpec {
        @Test void createSpec() {
            var spec = NativeLibrarySpec.builder("slang")
                    .version("2026.5.2")
                    .executable("slangc")
                    .library("libslang-compiler.so")
                    .library("libslang-rt.so")
                    .downloadUrl("linux-x86_64", "https://example.com/slang-linux.tar.gz")
                    .build();

            assertEquals("slang", spec.name());
            assertEquals("2026.5.2", spec.version());
            assertTrue(spec.executables().contains("slangc"));
            assertEquals(2, spec.libraries().size());
        }
    }

    @Nested
    class Loading {
        @TempDir Path cacheDir;

        @Test void loaderCreatesCacheDirectory() {
            var loader = new NativeLibraryLoader(cacheDir);
            var spec = NativeLibrarySpec.builder("test-lib")
                    .version("1.0")
                    .build();

            // Without bundled resources or download, resolve returns empty
            var result = loader.resolve(spec);
            assertNotNull(result);
            // Cache dir should be created
            assertTrue(Files.isDirectory(cacheDir));
        }

        @Test void extractFromLocalPath() throws IOException {
            // Simulate a pre-installed library
            var libDir = cacheDir.resolve("test-lib").resolve("1.0").resolve(Platform.current().identifier());
            Files.createDirectories(libDir);
            Files.writeString(libDir.resolve("testbin"), "#!/bin/sh\necho hello");
            libDir.resolve("testbin").toFile().setExecutable(true);

            var loader = new NativeLibraryLoader(cacheDir);
            var spec = NativeLibrarySpec.builder("test-lib")
                    .version("1.0")
                    .executable("testbin")
                    .build();

            var result = loader.resolve(spec);
            assertTrue(result.isAvailable());
            assertTrue(Files.isExecutable(result.executablePath("testbin")));
        }

        @Test void libraryPathIncludesNativeDir() throws IOException {
            var libDir = cacheDir.resolve("mylib").resolve("2.0").resolve(Platform.current().identifier());
            Files.createDirectories(libDir);
            Files.writeString(libDir.resolve("libfoo.so"), "fake");

            var loader = new NativeLibraryLoader(cacheDir);
            var spec = NativeLibrarySpec.builder("mylib")
                    .version("2.0")
                    .library("libfoo.so")
                    .build();

            var result = loader.resolve(spec);
            assertTrue(result.isAvailable());
            assertNotNull(result.libraryPath());
            assertTrue(result.libraryPath().toString().contains("mylib"));
        }
    }

    @Nested
    class MultipleLibraries {
        @TempDir Path cacheDir;

        @Test void loadTwoLibrariesIndependently() throws IOException {
            var loader = new NativeLibraryLoader(cacheDir);

            // Lib A
            var dirA = cacheDir.resolve("libA").resolve("1.0").resolve(Platform.current().identifier());
            Files.createDirectories(dirA);
            Files.writeString(dirA.resolve("toolA"), "binary");
            dirA.resolve("toolA").toFile().setExecutable(true);

            // Lib B
            var dirB = cacheDir.resolve("libB").resolve("1.0").resolve(Platform.current().identifier());
            Files.createDirectories(dirB);
            Files.writeString(dirB.resolve("toolB"), "binary");
            dirB.resolve("toolB").toFile().setExecutable(true);

            var specA = NativeLibrarySpec.builder("libA").version("1.0").executable("toolA").build();
            var specB = NativeLibrarySpec.builder("libB").version("1.0").executable("toolB").build();

            var resultA = loader.resolve(specA);
            var resultB = loader.resolve(specB);

            assertTrue(resultA.isAvailable());
            assertTrue(resultB.isAvailable());
            assertNotEquals(resultA.libraryPath(), resultB.libraryPath());
        }
    }
}

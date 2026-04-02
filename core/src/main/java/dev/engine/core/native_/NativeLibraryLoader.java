package dev.engine.core.native_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * General-purpose native library loader.
 *
 * <p>Resolves native libraries/tools for the current platform by:
 * <ol>
 *   <li>Checking the cache directory ({@code ~/.engine/natives/} by default)</li>
 *   <li>Extracting from classpath resources if bundled in the JAR</li>
 *   <li>Downloading from a URL if configured in the spec</li>
 * </ol>
 *
 * <p>Results are cached per name/version/platform — subsequent calls are instant.
 *
 * <p>This loader is not Slang-specific. Any native dependency can use it:
 * wgpu-native, SDL3, physics libraries, etc.
 */
public class NativeLibraryLoader {

    private static final Logger log = LoggerFactory.getLogger(NativeLibraryLoader.class);

    private final Path cacheDir;

    public NativeLibraryLoader(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /** Creates a loader with the default cache at ~/.engine/natives/ */
    public static NativeLibraryLoader defaultLoader() {
        var home = System.getProperty("user.home");
        return new NativeLibraryLoader(Path.of(home, ".engine", "natives"));
    }

    /**
     * Resolves a native library for the current platform.
     * Checks cache → classpath → download, in that order.
     */
    public NativeLibraryResult resolve(NativeLibrarySpec spec) {
        var platform = Platform.current();
        var libDir = cacheDir.resolve(spec.name()).resolve(spec.version()).resolve(platform.identifier());

        // 1. Check cache
        if (isComplete(libDir, spec)) {
            log.debug("Native library {} {} found in cache at {}", spec.name(), spec.version(), libDir);
            return new NativeLibraryResult(true, libDir);
        }

        // 2. Try classpath extraction
        if (spec.classpathPrefix() != null) {
            try {
                extractFromClasspath(spec, platform, libDir);
                if (isComplete(libDir, spec)) {
                    log.info("Extracted native library {} {} from classpath to {}", spec.name(), spec.version(), libDir);
                    return new NativeLibraryResult(true, libDir);
                }
            } catch (IOException e) {
                log.warn("Failed to extract {} from classpath: {}", spec.name(), e.getMessage());
            }
        }

        // 3. Try download
        var downloadUrl = spec.downloadUrls().get(platform.identifier());
        if (downloadUrl != null) {
            try {
                downloadAndExtract(downloadUrl, libDir);
                if (isComplete(libDir, spec)) {
                    log.info("Downloaded native library {} {} to {}", spec.name(), spec.version(), libDir);
                    return new NativeLibraryResult(true, libDir);
                }
            } catch (Exception e) {
                log.warn("Failed to download {} from {}: {}", spec.name(), downloadUrl, e.getMessage());
            }
        }

        // 4. Check if files exist even without all executables (partial install)
        if (Files.isDirectory(libDir)) {
            log.debug("Native library {} {} partially available at {}", spec.name(), spec.version(), libDir);
            return new NativeLibraryResult(true, libDir);
        }

        log.warn("Native library {} {} not available for {}", spec.name(), spec.version(), platform.identifier());
        return NativeLibraryResult.UNAVAILABLE;
    }

    private boolean isComplete(Path dir, NativeLibrarySpec spec) {
        if (!Files.isDirectory(dir)) return false;
        for (var exe : spec.executables()) {
            var path = dir.resolve(exe + Platform.current().os().executableExtension());
            if (!Files.isExecutable(path)) return false;
        }
        // For libraries, check if any file starts with the library name
        // (handles versioned files like libslang-compiler.so.0.2026.5.2)
        for (var lib : spec.libraries()) {
            try (var files = Files.list(dir)) {
                boolean found = files.anyMatch(f -> {
                    var name = f.getFileName().toString();
                    return name.equals(lib) || name.startsWith(lib + ".");
                });
                if (!found) return false;
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private void extractFromClasspath(NativeLibrarySpec spec, Platform platform, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        var prefix = spec.classpathPrefix() + platform.identifier() + "/";

        for (var exe : spec.executables()) {
            extractResource(prefix + exe, targetDir.resolve(exe));
            makeExecutable(targetDir.resolve(exe));
        }
        for (var lib : spec.libraries()) {
            extractResource(prefix + lib, targetDir.resolve(lib));
        }
    }

    private void extractResource(String resourcePath, Path target) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void downloadAndExtract(String url, Path targetDir) throws Exception {
        Files.createDirectories(targetDir);
        log.info("Downloading native library from {}...", url);

        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        var request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Download failed with HTTP " + response.statusCode());
        }

        // Extract to a temp staging dir first
        var staging = Files.createTempDirectory("native_staging_");
        try {
            if (url.endsWith(".tar.gz") || url.endsWith(".tgz")) {
                extractTarGz(response.body(), staging);
            } else if (url.endsWith(".zip")) {
                extractZip(response.body(), staging);
            } else {
                throw new IOException("Unsupported archive format: " + url);
            }

            // Flatten: find bin/ and lib/ dirs and copy contents to targetDir
            flattenToTarget(staging, targetDir);
        } finally {
            deleteRecursive(staging);
        }

        // Make all files in target executable
        try (var stream = Files.walk(targetDir)) {
            stream.filter(Files::isRegularFile).forEach(this::tryMakeExecutable);
        }
    }

    private void flattenToTarget(Path staging, Path targetDir) throws IOException {
        // Find all files recursively and copy executables/libraries to target
        try (var stream = Files.walk(staging)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    var name = file.getFileName().toString();
                    // Copy executables and shared libraries
                    if (name.endsWith(".so") || name.contains(".so.") ||
                            name.endsWith(".dll") || name.endsWith(".dylib") ||
                            name.endsWith(".slang-module") ||
                            !name.contains(".") || // executables without extension
                            name.endsWith(".exe")) {
                        Files.copy(file, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.warn("Failed to copy {}: {}", file, e.getMessage());
                }
            });
        }
    }

    private void deleteRecursive(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.delete(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private void extractTarGz(InputStream is, Path targetDir) throws IOException {
        // Use system tar for extraction (available on Linux/macOS)
        var tarFile = Files.createTempFile("native_", ".tar.gz");
        try {
            Files.copy(is, tarFile, StandardCopyOption.REPLACE_EXISTING);
            var proc = new ProcessBuilder("tar", "xzf", tarFile.toString(),
                    "--strip-components=1", "-C", targetDir.toString())
                    .redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            int exit = proc.waitFor();
            if (exit != 0) {
                // Try without --strip-components (some archives aren't nested)
                var proc2 = new ProcessBuilder("tar", "xzf", tarFile.toString(),
                        "-C", targetDir.toString())
                        .redirectErrorStream(true).start();
                proc2.getInputStream().readAllBytes();
                proc2.waitFor();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            Files.deleteIfExists(tarFile);
        }
    }

    private void extractZip(InputStream is, Path targetDir) throws IOException {
        var zipFile = Files.createTempFile("native_", ".zip");
        try {
            Files.copy(is, zipFile, StandardCopyOption.REPLACE_EXISTING);
            var proc = new ProcessBuilder("unzip", "-o", zipFile.toString(), "-d", targetDir.toString())
                    .redirectErrorStream(true).start();
            try {
                proc.getInputStream().readAllBytes();
                proc.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    private void makeExecutable(Path path) {
        try {
            var perms = Files.getPosixFilePermissions(path);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (Exception ignored) {
            // Windows or permission issue — ignore
        }
    }

    private void tryMakeExecutable(Path path) {
        try { makeExecutable(path); } catch (Exception ignored) {}
    }
}

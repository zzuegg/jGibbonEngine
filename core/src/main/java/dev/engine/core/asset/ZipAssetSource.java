package dev.engine.core.asset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Asset source that reads from a ZIP/archive file.
 * Useful for bundled assets, Sketchfab downloads, mod packages, etc.
 *
 * <p>Paths inside the archive are relative: "textures/albedo.png" matches
 * a ZIP entry "textures/albedo.png" or "model/textures/albedo.png" with
 * an optional strip prefix.
 */
public class ZipAssetSource implements AssetSource, AutoCloseable {

    private final ZipFile zipFile;
    private final String stripPrefix;

    /**
     * @param archivePath path to the .zip file
     * @param stripPrefix optional prefix to strip from entry names (e.g. "model/")
     */
    public ZipAssetSource(Path archivePath, String stripPrefix) {
        try {
            this.zipFile = new ZipFile(archivePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to open archive: " + archivePath, e);
        }
        this.stripPrefix = stripPrefix != null ? stripPrefix : "";
    }

    public ZipAssetSource(Path archivePath) {
        this(archivePath, null);
    }

    @Override
    public AssetData load(String path) {
        var entry = findEntry(path);
        if (entry == null) throw new RuntimeException("Not found in archive: " + path);
        try (var is = zipFile.getInputStream(entry)) {
            return new AssetData(path, is.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read from archive: " + path, e);
        }
    }

    @Override
    public boolean exists(String path) {
        return findEntry(path) != null;
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    private ZipEntry findEntry(String path) {
        // Try exact match with prefix
        var entry = zipFile.getEntry(stripPrefix + path);
        if (entry != null) return entry;

        // Try without prefix
        entry = zipFile.getEntry(path);
        if (entry != null) return entry;

        // Search all entries for a suffix match
        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            var e = entries.nextElement();
            if (e.getName().endsWith("/" + path) || e.getName().equals(path)) {
                return e;
            }
        }
        return null;
    }
}

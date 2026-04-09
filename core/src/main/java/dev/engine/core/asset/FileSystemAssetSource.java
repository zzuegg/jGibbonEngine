package dev.engine.core.asset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemAssetSource implements AssetSource {

    private final Path root;

    public FileSystemAssetSource(Path root) {
        this.root = root.normalize();
    }

    @Override
    public AssetData load(String path) {
        try {
            var resolved = root.resolve(path).normalize();
            if (!resolved.startsWith(root)) {
                throw new SecurityException("Path traversal denied: " + path);
            }
            return new AssetData(path, Files.readAllBytes(resolved));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(String path) {
        var resolved = root.resolve(path).normalize();
        return resolved.startsWith(root) && Files.exists(resolved);
    }
}

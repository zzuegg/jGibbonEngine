package dev.engine.core.asset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemAssetSource implements AssetSource {

    private final Path root;

    public FileSystemAssetSource(Path root) {
        this.root = root;
    }

    @Override
    public AssetData load(String path) {
        try {
            var resolved = root.resolve(path);
            return new AssetData(path, Files.readAllBytes(resolved));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(root.resolve(path));
    }
}

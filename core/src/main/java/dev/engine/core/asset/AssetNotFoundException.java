package dev.engine.core.asset;

public class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String path) {
        super("Asset not found: " + path);
    }
}

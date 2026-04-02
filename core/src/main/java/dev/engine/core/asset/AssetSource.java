package dev.engine.core.asset;

public interface AssetSource {

    record AssetData(String path, byte[] bytes) {}

    AssetData load(String path);

    boolean exists(String path);
}

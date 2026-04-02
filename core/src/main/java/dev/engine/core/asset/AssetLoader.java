package dev.engine.core.asset;

public interface AssetLoader<T> {

    boolean supports(String path);

    T load(AssetSource.AssetData data);

    Class<T> assetType();
}

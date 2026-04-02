package dev.engine.core.asset;

/**
 * Loader for .slang shader source files.
 * Returns the raw source as a ShaderSource record for the ShaderManager to compile.
 */
public class SlangShaderLoader implements AssetLoader<SlangShaderSource> {

    @Override
    public boolean supports(String path) {
        return path.endsWith(".slang");
    }

    @Override
    public SlangShaderSource load(AssetSource.AssetData data) {
        return new SlangShaderSource(data.path(), new String(data.bytes()));
    }

    @Override
    public Class<SlangShaderSource> assetType() {
        return SlangShaderSource.class;
    }
}

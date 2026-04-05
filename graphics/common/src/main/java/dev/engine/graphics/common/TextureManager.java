package dev.engine.graphics.common;

import dev.engine.core.asset.TextureData;
import dev.engine.graphics.texture.SampledTexture;
import dev.engine.core.handle.Handle;
import dev.engine.core.material.MaterialData;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.resource.WeakCache;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages GPU texture resources: upload, identity-based caching, and material texture binding.
 */
public class TextureManager {

    private static final Logger log = LoggerFactory.getLogger(TextureManager.class);

    private final GpuResourceManager gpu;
    private final WeakCache<TextureData, Handle<TextureResource>> textureCache = new WeakCache<>();

    public TextureManager(GpuResourceManager gpu) {
        this.gpu = gpu;
    }

    public Handle<TextureResource> upload(TextureData data) {
        return textureCache.getOrCreate(data, this::doUpload);
    }

    /**
     * Binds all texture properties from a material to the correct texture units.
     * Resolves bindings from shader reflection metadata.
     */
    public void bindMaterialTextures(MaterialData matData, CompiledShader shader,
                                      SamplerManager samplerManager, CommandRecorder draw) {
        var sortedTexKeys = matData.keys().stream()
                .filter(k -> k.type() == SampledTexture.class)
                .sorted(java.util.Comparator.comparing(PropertyKey::name))
                .toList();

        int texUnit = 0;
        for (var key : sortedTexKeys) {
            SampledTexture sampled = (SampledTexture) matData.get(key);
            if (sampled == null || sampled.texture() == null) {
                texUnit++;
                continue;
            }

            var texHandle = upload(sampled.texture());
            String texParamName = key.name();
            var texBinding = shader.findBinding(texParamName);
            int unit = texBinding != null ? texBinding.binding() : texUnit;

            draw.bindTexture(unit, texHandle);
            draw.bindSampler(unit, samplerManager.getOrCreate(sampled.sampler()));
            texUnit++;
        }
    }

    /** Polls for garbage-collected TextureData and destroys associated GPU textures. */
    public void pollStale() {
        textureCache.pollStale(gpu::destroyTexture);
    }

    public void close() {
        textureCache.clear(gpu::destroyTexture);
    }

    private Handle<TextureResource> doUpload(TextureData data) {
        var desc = new TextureDescriptor(data.width(), data.height(), mapFormat(data.format()));
        var handle = gpu.createTexture(desc);
        if (!data.compressed()) {
            gpu.uploadTexture(handle, data.pixels());
        } else {
            log.warn("Compressed texture upload not yet supported, skipping pixel upload for {}x{} {} texture",
                    data.width(), data.height(), data.format());
        }
        return handle;
    }

    static TextureFormat mapFormat(TextureData.PixelFormat format) {
        if (format == TextureData.PixelFormat.RGBA8) return TextureFormat.RGBA8;
        if (format == TextureData.PixelFormat.RGB8) return TextureFormat.RGB8;
        if (format == TextureData.PixelFormat.R8) return TextureFormat.R8;
        if (format == TextureData.PixelFormat.RGBA16F) return TextureFormat.RGBA16F;
        if (format == TextureData.PixelFormat.RGBA32F) return TextureFormat.RGBA32F;
        if (format == TextureData.PixelFormat.R16F) return TextureFormat.R16F;
        if (format == TextureData.PixelFormat.R32F) return TextureFormat.R32F;
        log.warn("Unknown pixel format '{}', falling back to RGBA8", format.name());
        return TextureFormat.RGBA8;
    }
}

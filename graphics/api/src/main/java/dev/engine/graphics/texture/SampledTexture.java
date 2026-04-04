package dev.engine.graphics.texture;

import dev.engine.core.asset.TextureData;
import dev.engine.graphics.sampler.SamplerDescriptor;

/**
 * A texture paired with its sampler configuration.
 * Used as the value type for texture properties in materials.
 *
 * @param texture the texture data
 * @param sampler the sampler configuration (filtering, wrapping, etc.)
 */
public record SampledTexture(TextureData texture, SamplerDescriptor sampler) {

    /** Creates a sampled texture with default linear filtering. */
    public SampledTexture(TextureData texture) {
        this(texture, SamplerDescriptor.linear());
    }
}

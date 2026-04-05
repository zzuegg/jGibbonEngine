package dev.engine.graphics.texture;

import dev.engine.core.material.MaterialData;
import dev.engine.core.property.PropertyKey;

/**
 * Standard texture property keys for materials.
 * Each key maps to a {@link SampledTexture} — texture data paired with sampler configuration.
 *
 * <pre>{@code
 * MaterialData.create("PBR")
 *     .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(albedo))
 *     .set(TextureKeys.NORMAL_TEXTURE, new SampledTexture(normalMap, SamplerDescriptor.nearest()));
 * }</pre>
 */
public final class TextureKeys {

    private TextureKeys() {}

    public static final PropertyKey<MaterialData, SampledTexture> ALBEDO_TEXTURE = PropertyKey.of("albedoTexture", SampledTexture.class);
    public static final PropertyKey<MaterialData, SampledTexture> NORMAL_TEXTURE = PropertyKey.of("normalTexture", SampledTexture.class);
    public static final PropertyKey<MaterialData, SampledTexture> ROUGHNESS_TEXTURE = PropertyKey.of("roughnessTexture", SampledTexture.class);
    public static final PropertyKey<MaterialData, SampledTexture> METALLIC_TEXTURE = PropertyKey.of("metallicTexture", SampledTexture.class);
    public static final PropertyKey<MaterialData, SampledTexture> EMISSIVE_TEXTURE = PropertyKey.of("emissiveTexture", SampledTexture.class);
    public static final PropertyKey<MaterialData, SampledTexture> AO_TEXTURE = PropertyKey.of("aoTexture", SampledTexture.class);
}

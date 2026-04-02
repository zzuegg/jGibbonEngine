package dev.engine.core.material;

import dev.engine.core.asset.TextureData;
import dev.engine.core.math.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * PBR (Physically Based Rendering) material.
 * All fields optional — nulls use defaults (white albedo, 0.5 roughness, 0 metallic).
 */
public record PbrMaterial(
        Vec3 albedoColor,
        float roughness,
        float metallic,
        Vec3 emissive,
        float opacity,
        float normalStrength,
        TextureData albedoMap,
        TextureData normalMap,
        TextureData roughnessMap,
        TextureData metallicMap,
        TextureData emissiveMap,
        TextureData aoMap
) implements MaterialData {

    /** Convenience: just color + roughness + metallic. */
    public static PbrMaterial of(Vec3 albedoColor, float roughness, float metallic) {
        return new PbrMaterial(albedoColor, roughness, metallic, Vec3.ZERO, 1f, 1f,
                null, null, null, null, null, null);
    }

    /** Convenience: textured PBR. */
    public static PbrMaterial textured(TextureData albedoMap) {
        return new PbrMaterial(Vec3.ONE, 0.5f, 0f, Vec3.ZERO, 1f, 1f,
                albedoMap, null, null, null, null, null);
    }

    /** Convenience: full textured PBR. */
    public static PbrMaterial textured(TextureData albedoMap, TextureData normalMap,
                                       TextureData roughnessMap, TextureData metallicMap) {
        return new PbrMaterial(Vec3.ONE, 0.5f, 0f, Vec3.ZERO, 1f, 1f,
                albedoMap, normalMap, roughnessMap, metallicMap, null, null);
    }

    // Immutable with-methods for modifying individual fields
    public PbrMaterial withAlbedoColor(Vec3 c) { return new PbrMaterial(c, roughness, metallic, emissive, opacity, normalStrength, albedoMap, normalMap, roughnessMap, metallicMap, emissiveMap, aoMap); }
    public PbrMaterial withRoughness(float r) { return new PbrMaterial(albedoColor, r, metallic, emissive, opacity, normalStrength, albedoMap, normalMap, roughnessMap, metallicMap, emissiveMap, aoMap); }
    public PbrMaterial withMetallic(float m) { return new PbrMaterial(albedoColor, roughness, m, emissive, opacity, normalStrength, albedoMap, normalMap, roughnessMap, metallicMap, emissiveMap, aoMap); }
    public PbrMaterial withAlbedoMap(TextureData t) { return new PbrMaterial(albedoColor, roughness, metallic, emissive, opacity, normalStrength, t, normalMap, roughnessMap, metallicMap, emissiveMap, aoMap); }
    public PbrMaterial withNormalMap(TextureData t) { return new PbrMaterial(albedoColor, roughness, metallic, emissive, opacity, normalStrength, albedoMap, t, roughnessMap, metallicMap, emissiveMap, aoMap); }

    public boolean hasAlbedoMap() { return albedoMap != null; }
    public boolean hasNormalMap() { return normalMap != null; }

    public record ScalarData(Vec3 albedoColor, float roughness, float metallic, Vec3 emissive, float opacity, float normalStrength) {}

    @Override
    public Record scalarData() {
        return new ScalarData(albedoColor, roughness, metallic, emissive, opacity, normalStrength);
    }

    @Override
    public Map<String, TextureData> textures() {
        var map = new HashMap<String, TextureData>();
        if (albedoMap != null) map.put("albedoMap", albedoMap);
        if (normalMap != null) map.put("normalMap", normalMap);
        if (roughnessMap != null) map.put("roughnessMap", roughnessMap);
        if (metallicMap != null) map.put("metallicMap", metallicMap);
        if (emissiveMap != null) map.put("emissiveMap", emissiveMap);
        if (aoMap != null) map.put("aoMap", aoMap);
        return map;
    }
}

package dev.engine.core.material;

import dev.engine.core.asset.TextureData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Component;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;

import java.util.Set;

/**
 * A material is a typed property bag that drives shader selection and uniform data.
 *
 * <p>Properties include scalar values (roughness, metallic), colors (albedo),
 * and textures (albedo map, normal map). The renderer reads all properties
 * and uploads them to GPU (scalars → UBO, textures → sampler bindings).
 *
 * <p>Standard properties are defined as constants. Users can add custom ones.
 */
public class Material implements Component {

    // --- PBR scalar/color properties ---
    public static final PropertyKey<Vec3> ALBEDO_COLOR = PropertyKey.of("albedoColor", Vec3.class);
    public static final PropertyKey<Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);
    public static final PropertyKey<Float> METALLIC = PropertyKey.of("metallic", Float.class);
    public static final PropertyKey<Vec3> EMISSIVE = PropertyKey.of("emissive", Vec3.class);
    public static final PropertyKey<Float> OPACITY = PropertyKey.of("opacity", Float.class);
    public static final PropertyKey<Float> NORMAL_STRENGTH = PropertyKey.of("normalStrength", Float.class);

    // --- Texture properties ---
    public static final PropertyKey<TextureData> ALBEDO_MAP = PropertyKey.of("albedoMap", TextureData.class);
    public static final PropertyKey<TextureData> NORMAL_MAP = PropertyKey.of("normalMap", TextureData.class);
    public static final PropertyKey<TextureData> ROUGHNESS_MAP = PropertyKey.of("roughnessMap", TextureData.class);
    public static final PropertyKey<TextureData> METALLIC_MAP = PropertyKey.of("metallicMap", TextureData.class);
    public static final PropertyKey<TextureData> EMISSIVE_MAP = PropertyKey.of("emissiveMap", TextureData.class);
    public static final PropertyKey<TextureData> AO_MAP = PropertyKey.of("aoMap", TextureData.class);

    // --- Unlit properties ---
    public static final PropertyKey<Vec3> COLOR = PropertyKey.of("color", Vec3.class);

    private final MaterialType type;
    private final MutablePropertyMap properties = new MutablePropertyMap();
    private String shaderSource;
    private Object dataRecord; // Optional: a record holding all material data
    private Class<?> dataRecordType;

    private Material(MaterialType type) {
        this.type = type;
    }

    public static Material create(MaterialType type) {
        return new Material(type);
    }

    public MaterialType type() { return type; }

    // --- Property bag API (auto-managed materials) ---
    public <T> void set(PropertyKey<T> key, T value) { properties.set(key, value); }
    public <T> T get(PropertyKey<T> key) { return properties.get(key); }
    public boolean has(PropertyKey<?> key) { return properties.contains(key); }

    public Set<PropertyKey<?>> changes() { return properties.getChanges(); }
    public void clearChanges() { properties.clearChanges(); }

    // --- Record-based API (custom materials, single source of truth) ---

    /**
     * Sets a typed record as the material data. The record type
     * auto-generates the Slang struct definition via SlangStructGenerator.
     * Use this for custom shaders where you control the data layout.
     */
    public <T extends Record> void setData(T record) {
        this.dataRecord = record;
        this.dataRecordType = record.getClass();
    }

    @SuppressWarnings("unchecked")
    public <T extends Record> T data() { return (T) dataRecord; }

    public Class<?> dataRecordType() { return dataRecordType; }

    public boolean hasRecordData() { return dataRecord != null; }

    // --- Shader source ---
    public void setShaderSource(String path) { this.shaderSource = path; }
    public String shaderSource() { return shaderSource; }
}

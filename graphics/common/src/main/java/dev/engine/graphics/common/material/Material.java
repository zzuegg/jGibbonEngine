package dev.engine.graphics.common.material;

import dev.engine.core.math.Vec3;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;

import java.util.Set;

/**
 * A material is a typed property bag that drives shader selection and uniform data.
 * Standard properties are defined as constants. Users can add any custom properties.
 */
public class Material {

    // Standard PBR properties
    public static final PropertyKey<Vec3> ALBEDO_COLOR = PropertyKey.of("albedoColor", Vec3.class);
    public static final PropertyKey<Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);
    public static final PropertyKey<Float> METALLIC = PropertyKey.of("metallic", Float.class);
    public static final PropertyKey<Vec3> EMISSIVE = PropertyKey.of("emissive", Vec3.class);
    public static final PropertyKey<Float> OPACITY = PropertyKey.of("opacity", Float.class);

    // Standard unlit properties
    public static final PropertyKey<Vec3> COLOR = PropertyKey.of("color", Vec3.class);

    private final MaterialType type;
    private final MutablePropertyMap properties = new MutablePropertyMap();
    private String shaderSource;

    private Material(MaterialType type) {
        this.type = type;
    }

    public static Material create(MaterialType type) {
        return new Material(type);
    }

    public MaterialType type() { return type; }

    public <T> void set(PropertyKey<T> key, T value) { properties.set(key, value); }
    public <T> T get(PropertyKey<T> key) { return properties.get(key); }
    public boolean has(PropertyKey<?> key) { return properties.contains(key); }

    public Set<PropertyKey<?>> changes() { return properties.getChanges(); }
    public void clearChanges() { properties.clearChanges(); }

    public void setShaderSource(String path) { this.shaderSource = path; }
    public String shaderSource() { return shaderSource; }
}

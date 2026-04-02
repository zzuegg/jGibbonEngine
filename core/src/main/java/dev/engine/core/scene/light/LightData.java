package dev.engine.core.scene.light;

import dev.engine.core.math.Vec3;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;

/**
 * A light is a typed property bag. Standard properties are defined as constants,
 * but users can add any custom properties for custom light types.
 */
public class LightData {

    // Standard light properties
    public static final PropertyKey<Vec3> COLOR = PropertyKey.of("color", Vec3.class);
    public static final PropertyKey<Float> INTENSITY = PropertyKey.of("intensity", Float.class);
    public static final PropertyKey<Vec3> DIRECTION = PropertyKey.of("direction", Vec3.class);
    public static final PropertyKey<Vec3> POSITION = PropertyKey.of("position", Vec3.class);
    public static final PropertyKey<Float> RADIUS = PropertyKey.of("radius", Float.class);
    public static final PropertyKey<Float> INNER_ANGLE = PropertyKey.of("innerAngle", Float.class);
    public static final PropertyKey<Float> OUTER_ANGLE = PropertyKey.of("outerAngle", Float.class);
    public static final PropertyKey<Boolean> CASTS_SHADOWS = PropertyKey.of("castsShadows", Boolean.class);

    private final LightType type;
    private final MutablePropertyMap properties = new MutablePropertyMap();

    public LightData(LightType type) {
        this.type = type;
    }

    public LightType type() { return type; }

    public <T> void set(PropertyKey<T> key, T value) { properties.set(key, value); }
    public <T> T get(PropertyKey<T> key) { return properties.get(key); }
    public boolean has(PropertyKey<?> key) { return properties.contains(key); }
}

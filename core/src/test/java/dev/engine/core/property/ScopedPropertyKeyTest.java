package dev.engine.core.property;

import dev.engine.core.math.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for scoped PropertyKey<O, T> where O is the owner type.
 * PropertyMap<O> only accepts PropertyKey<O, ?> keys at compile time.
 */
class ScopedPropertyKeyTest {

    // Two different owner types
    static class Material {}
    static class Light {}

    // Material properties
    static final PropertyKey<Material, Vec3> ALBEDO = PropertyKey.of("albedo", Vec3.class);
    static final PropertyKey<Material, Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);

    // Light properties
    static final PropertyKey<Light, Float> INTENSITY = PropertyKey.of("intensity", Float.class);
    static final PropertyKey<Light, Vec3> COLOR = PropertyKey.of("color", Vec3.class);

    @Test
    void immutableMapAcceptsOnlyScopedKeys() {
        var map = PropertyMap.<Material>builder()
                .set(ALBEDO, new Vec3(1f, 0f, 0f))
                .set(ROUGHNESS, 0.5f)
                .build();
        assertEquals(new Vec3(1f, 0f, 0f), map.get(ALBEDO));
        assertEquals(0.5f, map.get(ROUGHNESS));
    }

    @Test
    void mutableMapAcceptsOnlyScopedKeys() {
        var map = new MutablePropertyMap<Material>();
        map.set(ALBEDO, Vec3.ONE);
        map.set(ROUGHNESS, 0.8f);
        assertEquals(Vec3.ONE, map.get(ALBEDO));
        assertEquals(0.8f, map.get(ROUGHNESS));
    }

    @Test
    void propertyKeyEquality() {
        var a = PropertyKey.<Material, Float>of("roughness", Float.class);
        var b = PropertyKey.<Material, Float>of("roughness", Float.class);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentNamesMeansDifferentKeys() {
        var roughness = PropertyKey.<Material, Float>of("roughness", Float.class);
        var metallic = PropertyKey.<Material, Float>of("metallic", Float.class);
        assertNotEquals(roughness, metallic);
    }

    @Test
    void snapshotPreservesScope() {
        var map = new MutablePropertyMap<Material>();
        map.set(ROUGHNESS, 0.5f);
        PropertyMap<Material> snapshot = map.snapshot();
        assertEquals(0.5f, snapshot.get(ROUGHNESS));
    }

    @Test
    void changeTracking() {
        var map = new MutablePropertyMap<Light>();
        map.set(INTENSITY, 1.0f);
        map.clearChanges();
        map.set(INTENSITY, 2.0f);
        var changes = map.getChanges();
        assertEquals(1, changes.size());
        assertTrue(changes.contains(INTENSITY));
    }

    // NOTE: The following should NOT compile after the refactor:
    // PropertyMap<Material> map = ...;
    // map.get(INTENSITY);  // INTENSITY is PropertyKey<Light, Float> -- wrong owner
    //
    // This is enforced at compile time by the type system, not at runtime.
}

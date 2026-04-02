package dev.engine.core.property;

import dev.engine.core.math.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyMapTest {

    static final PropertyKey<Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);
    static final PropertyKey<Vec3> ALBEDO = PropertyKey.of("albedo", Vec3.class);
    static final PropertyKey<Boolean> TRANSPARENT = PropertyKey.of("transparent", Boolean.class);

    @Nested
    class ImmutableMap {
        @Test void getReturnsSetValue() {
            var map = PropertyMap.builder()
                    .set(ROUGHNESS, 0.5f)
                    .set(ALBEDO, new Vec3(1f, 0f, 0f))
                    .build();
            assertEquals(0.5f, map.get(ROUGHNESS));
            assertEquals(new Vec3(1f, 0f, 0f), map.get(ALBEDO));
        }

        @Test void getMissingReturnsNull() {
            var map = PropertyMap.builder().build();
            assertNull(map.get(ROUGHNESS));
        }

        @Test void containsKey() {
            var map = PropertyMap.builder().set(ROUGHNESS, 0.5f).build();
            assertTrue(map.contains(ROUGHNESS));
            assertFalse(map.contains(ALBEDO));
        }

        @Test void keys() {
            var map = PropertyMap.builder()
                    .set(ROUGHNESS, 0.5f)
                    .set(ALBEDO, Vec3.ONE)
                    .build();
            var keys = map.keys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains(ROUGHNESS));
            assertTrue(keys.contains(ALBEDO));
        }

        @Test void equalityByContent() {
            var a = PropertyMap.builder().set(ROUGHNESS, 0.5f).build();
            var b = PropertyMap.builder().set(ROUGHNESS, 0.5f).build();
            assertEquals(a, b);
        }
    }

    @Nested
    class MutableMapTests {
        @Test void setAndGet() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            assertEquals(0.5f, map.get(ROUGHNESS));
        }

        @Test void trackChanges() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.clearChanges();
            map.set(ROUGHNESS, 0.8f);
            var changes = map.getChanges();
            assertEquals(1, changes.size());
            assertTrue(changes.contains(ROUGHNESS));
        }

        @Test void noChangeIfSameValue() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.clearChanges();
            map.set(ROUGHNESS, 0.5f);
            assertTrue(map.getChanges().isEmpty());
        }

        @Test void snapshot() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.set(ALBEDO, Vec3.ONE);
            var snapshot = map.snapshot();
            assertEquals(0.5f, snapshot.get(ROUGHNESS));
            assertEquals(Vec3.ONE, snapshot.get(ALBEDO));
        }

        @Test void removeTracksChange() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.clearChanges();
            map.remove(ROUGHNESS);
            assertNull(map.get(ROUGHNESS));
            assertTrue(map.getChanges().contains(ROUGHNESS));
        }
    }
}

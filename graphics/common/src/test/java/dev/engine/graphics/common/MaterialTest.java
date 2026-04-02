package dev.engine.graphics.common;

import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.material.Material;
import dev.engine.core.material.MaterialType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialTest {

    @Nested
    class MaterialCreation {
        @Test void createUnlitMaterial() {
            var mat = Material.create(MaterialType.UNLIT);
            assertEquals(MaterialType.UNLIT, mat.type());
        }

        @Test void createPbrMaterial() {
            var mat = Material.create(MaterialType.PBR);
            assertEquals(MaterialType.PBR, mat.type());
        }

        @Test void customMaterialType() {
            var toon = MaterialType.of("TOON");
            var mat = Material.create(toon);
            assertEquals("TOON", mat.type().name());
        }
    }

    @Nested
    class MaterialProperties {
        @Test void setAndGetColor() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ALBEDO_COLOR, new Vec3(1f, 0f, 0f));
            assertEquals(new Vec3(1f, 0f, 0f), mat.get(Material.ALBEDO_COLOR));
        }

        @Test void setAndGetFloat() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ROUGHNESS, 0.5f);
            assertEquals(0.5f, mat.get(Material.ROUGHNESS));
        }

        @Test void defaultValues() {
            var mat = Material.create(MaterialType.PBR);
            // PBR defaults
            assertNull(mat.get(Material.ROUGHNESS)); // not set until user sets it
        }

        @Test void customProperties() {
            var TINT = PropertyKey.of("tint", Vec3.class);
            var mat = Material.create(MaterialType.UNLIT);
            mat.set(TINT, new Vec3(0f, 1f, 0f));
            assertEquals(new Vec3(0f, 1f, 0f), mat.get(TINT));
        }

        @Test void trackChanges() {
            var mat = Material.create(MaterialType.PBR);
            mat.set(Material.ROUGHNESS, 0.5f);
            mat.clearChanges();
            mat.set(Material.ROUGHNESS, 0.8f);
            assertTrue(mat.changes().contains(Material.ROUGHNESS));
        }
    }

    @Nested
    class CustomShader {
        @Test void assignCustomShaderSource() {
            var mat = Material.create(MaterialType.CUSTOM);
            mat.setShaderSource("my_shader.slang");
            assertEquals("my_shader.slang", mat.shaderSource());
        }
    }
}

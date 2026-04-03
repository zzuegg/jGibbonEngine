package dev.engine.graphics.common;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialTest {

    @Nested
    class MaterialCreation {
        @Test void createEmptyMaterial() {
            var mat = MaterialData.create();
            assertEquals(0, mat.size());
            assertNull(mat.shaderHint());
        }

        @Test void createPbrMaterial() {
            var mat = MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.8f);
            assertEquals("PBR", mat.shaderHint());
            assertEquals(new Vec3(1, 0, 0), mat.get(MaterialData.ALBEDO_COLOR));
            assertEquals(0.5f, mat.get(MaterialData.ROUGHNESS));
            assertEquals(0.8f, mat.get(MaterialData.METALLIC));
        }

        @Test void createUnlitMaterial() {
            var mat = MaterialData.unlit(new Vec3(0, 1, 0));
            assertEquals("UNLIT", mat.shaderHint());
            assertEquals(new Vec3(0, 1, 0), mat.get(MaterialData.COLOR));
        }

        @Test void createWithShaderHint() {
            var mat = MaterialData.create("TOON");
            assertEquals("TOON", mat.shaderHint());
        }
    }

    @Nested
    class MaterialProperties {
        @Test void setAndGetColor() {
            var mat = MaterialData.create("PBR")
                    .set(MaterialData.ALBEDO_COLOR, new Vec3(1f, 0f, 0f));
            assertEquals(new Vec3(1f, 0f, 0f), mat.get(MaterialData.ALBEDO_COLOR));
        }

        @Test void setAndGetFloat() {
            var mat = MaterialData.create("PBR")
                    .set(MaterialData.ROUGHNESS, 0.5f);
            assertEquals(0.5f, mat.get(MaterialData.ROUGHNESS));
        }

        @Test void immutableSet() {
            var mat1 = MaterialData.create("PBR")
                    .set(MaterialData.ROUGHNESS, 0.5f);
            var mat2 = mat1.set(MaterialData.ROUGHNESS, 0.8f);
            assertEquals(0.5f, mat1.get(MaterialData.ROUGHNESS));
            assertEquals(0.8f, mat2.get(MaterialData.ROUGHNESS));
        }

        @Test void customProperties() {
            var TINT = PropertyKey.of("tint", Vec3.class);
            var mat = MaterialData.create("UNLIT")
                    .set(TINT, new Vec3(0f, 1f, 0f));
            assertEquals(new Vec3(0f, 1f, 0f), mat.get(TINT));
        }

        @Test void keysReturned() {
            var mat = MaterialData.pbr(Vec3.ONE, 0.5f, 0.2f);
            assertTrue(mat.keys().contains(MaterialData.ALBEDO_COLOR));
            assertTrue(mat.keys().contains(MaterialData.ROUGHNESS));
            assertTrue(mat.keys().contains(MaterialData.METALLIC));
        }
    }

    @Nested
    class ShaderHint {
        @Test void withShaderOverride() {
            var mat = MaterialData.create("PBR")
                    .withShader("custom/toon.slang");
            assertEquals("custom/toon.slang", mat.shaderHint());
        }
    }
}

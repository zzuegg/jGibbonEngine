package dev.engine.core.material;

import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialDataTest {

    @Nested
    class Creation {
        @Test void createEmpty() {
            var mat = MaterialData.create();
            assertEquals(0, mat.size());
        }

        @Test void createWithShaderHint() {
            var mat = MaterialData.create("PBR");
            assertEquals("PBR", mat.shaderHint());
        }

        @Test void pbrFactory() {
            var mat = MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.2f);
            assertEquals(new Vec3(1, 0, 0), mat.get(MaterialData.ALBEDO_COLOR));
            assertEquals(0.5f, mat.get(MaterialData.ROUGHNESS));
            assertEquals(0.2f, mat.get(MaterialData.METALLIC));
            assertEquals("PBR", mat.shaderHint());
        }

        @Test void unlitFactory() {
            var mat = MaterialData.unlit(new Vec3(0, 1, 0));
            assertEquals(new Vec3(0, 1, 0), mat.get(MaterialData.COLOR));
            assertEquals("UNLIT", mat.shaderHint());
        }
    }

    @Nested
    class Immutability {
        @Test void setReturnsNewInstance() {
            var mat1 = MaterialData.create();
            var mat2 = mat1.set(MaterialData.ROUGHNESS, 0.5f);
            assertNotSame(mat1, mat2);
            assertNull(mat1.get(MaterialData.ROUGHNESS));
            assertEquals(0.5f, mat2.get(MaterialData.ROUGHNESS));
        }

        @Test void chainedSetsAccumulate() {
            var mat = MaterialData.create()
                    .set(MaterialData.ALBEDO_COLOR, new Vec3(1, 0, 0))
                    .set(MaterialData.ROUGHNESS, 0.3f)
                    .set(MaterialData.METALLIC, 0.8f);
            assertEquals(new Vec3(1, 0, 0), mat.get(MaterialData.ALBEDO_COLOR));
            assertEquals(0.3f, mat.get(MaterialData.ROUGHNESS));
            assertEquals(0.8f, mat.get(MaterialData.METALLIC));
            assertEquals(3, mat.size());
        }

        @Test void setPreservesExistingKeys() {
            var mat = MaterialData.create()
                    .set(MaterialData.ROUGHNESS, 0.5f)
                    .set(MaterialData.METALLIC, 0.2f);
            var updated = mat.set(MaterialData.ROUGHNESS, 0.8f);
            assertEquals(0.8f, updated.get(MaterialData.ROUGHNESS));
            assertEquals(0.2f, updated.get(MaterialData.METALLIC)); // preserved
        }

        @Test void withShaderReturnsNewInstance() {
            var mat = MaterialData.create("PBR").set(MaterialData.ROUGHNESS, 0.5f);
            var custom = mat.withShader("custom/toon.slang");
            assertEquals("PBR", mat.shaderHint());
            assertEquals("custom/toon.slang", custom.shaderHint());
            assertEquals(0.5f, custom.get(MaterialData.ROUGHNESS)); // data preserved
        }
    }

    @Nested
    class TypeSafety {
        @Test void differentTypesCoexist() {
            var mat = MaterialData.create()
                    .set(MaterialData.ALBEDO_COLOR, new Vec3(1, 0, 0))
                    .set(MaterialData.ROUGHNESS, 0.5f)
                    .set(MaterialData.OPACITY, 0.8f);
            assertInstanceOf(Vec3.class, mat.get(MaterialData.ALBEDO_COLOR));
            assertInstanceOf(Float.class, mat.get(MaterialData.ROUGHNESS));
        }

        @Test void customKeys() {
            var TINT = PropertyKey.<MaterialData, Vec3>of("tint", Vec3.class);
            var STRENGTH = PropertyKey.<MaterialData, Float>of("strength", Float.class);
            var mat = MaterialData.create()
                    .set(TINT, new Vec3(0, 1, 0))
                    .set(STRENGTH, 2.0f);
            assertEquals(new Vec3(0, 1, 0), mat.get(TINT));
            assertEquals(2.0f, mat.get(STRENGTH));
        }

        @Test void hasAndKeys() {
            var mat = MaterialData.pbr(Vec3.ONE, 0.5f, 0.2f);
            assertTrue(mat.has(MaterialData.ALBEDO_COLOR));
            assertTrue(mat.has(MaterialData.ROUGHNESS));
            assertFalse(mat.has(PropertyKey.<MaterialData, String>of("nonExistent", String.class)));
            assertTrue(mat.keys().size() >= 3);
        }
    }

    @Nested
    class SlotType {
        @Test void allMaterialsShareSlot() {
            var pbr = MaterialData.pbr(Vec3.ONE, 0.5f, 0.2f);
            var unlit = MaterialData.unlit(Vec3.ONE);
            assertEquals(pbr.slotType(), unlit.slotType());
            assertEquals(MaterialData.class, pbr.slotType());
        }
    }
}

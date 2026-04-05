package dev.engine.core.scene.light;

import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LightTest {

    @Nested
    class LightProperties {
        @Test void directionalLightHasDirection() {
            var light = new LightData(LightType.DIRECTIONAL);
            light.set(LightData.DIRECTION, new Vec3(0f, -1f, 0f));
            assertEquals(new Vec3(0f, -1f, 0f), light.get(LightData.DIRECTION));
        }

        @Test void pointLightHasPositionAndRadius() {
            var light = new LightData(LightType.POINT);
            light.set(LightData.POSITION, new Vec3(5f, 3f, 0f));
            light.set(LightData.RADIUS, 10f);
            assertEquals(new Vec3(5f, 3f, 0f), light.get(LightData.POSITION));
            assertEquals(10f, light.get(LightData.RADIUS));
        }

        @Test void spotLightHasAngle() {
            var light = new LightData(LightType.SPOT);
            light.set(LightData.INNER_ANGLE, 30f);
            light.set(LightData.OUTER_ANGLE, 45f);
            assertEquals(30f, light.get(LightData.INNER_ANGLE));
            assertEquals(45f, light.get(LightData.OUTER_ANGLE));
        }

        @Test void commonProperties() {
            var light = new LightData(LightType.POINT);
            light.set(LightData.COLOR, new Vec3(1f, 0.9f, 0.8f));
            light.set(LightData.INTENSITY, 2.5f);
            assertEquals(new Vec3(1f, 0.9f, 0.8f), light.get(LightData.COLOR));
            assertEquals(2.5f, light.get(LightData.INTENSITY));
        }

        @Test void castsShadowFlag() {
            var light = new LightData(LightType.DIRECTIONAL);
            light.set(LightData.CASTS_SHADOWS, true);
            assertTrue(light.get(LightData.CASTS_SHADOWS));
        }
    }

    @Nested
    class LightTypeExtensibility {
        @Test void customLightType() {
            var areaLight = LightType.of("AREA");
            var light = new LightData(areaLight);
            assertEquals("AREA", light.type().name());
            // Can set arbitrary properties
            var AREA_WIDTH = PropertyKey.<LightData, Float>of("areaWidth", Float.class);
            light.set(AREA_WIDTH, 2f);
            assertEquals(2f, light.get(AREA_WIDTH));
        }
    }
}

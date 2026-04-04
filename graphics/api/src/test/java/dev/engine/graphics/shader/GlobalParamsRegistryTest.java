package dev.engine.graphics.shader;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.shader.params.CameraParams;
import dev.engine.graphics.shader.params.EngineParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GlobalParamsRegistryTest {

    record LightParams(Vec3 direction, Vec3 color, float intensity) {}
    record WeatherParams(float windSpeed, Vec2 windDirection, float rainIntensity) {}

    private GlobalParamsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GlobalParamsRegistry();
    }

    @Test
    void registerAndRetrieve() {
        registry.register("Engine", EngineParams.class, 0);
        assertEquals(1, registry.entries().size());
        assertEquals("Engine", registry.entries().get(0).name());
        assertEquals(0, registry.entries().get(0).binding());
    }

    @Test
    void registrationOrder() {
        registry.register("Engine", EngineParams.class, 0);
        registry.register("Camera", CameraParams.class, 1);
        registry.register("Light", LightParams.class, 2);

        var entries = registry.entries();
        assertEquals(3, entries.size());
        assertEquals("Engine", entries.get(0).name());
        assertEquals("Camera", entries.get(1).name());
        assertEquals("Light", entries.get(2).name());
    }

    @Test
    void updateData() {
        registry.register("Engine", EngineParams.class, 0);
        var params = new EngineParams(1.5f, 0.016f, new Vec2(1920, 1080), 42);
        registry.update("Engine", params);

        assertEquals(params, registry.entries().get(0).data());
    }

    @Test
    void updateWrongTypeThrows() {
        registry.register("Engine", EngineParams.class, 0);
        assertThrows(IllegalArgumentException.class,
                () -> registry.update("Engine", "not an EngineParams"));
    }

    @Test
    void updateUnknownNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.update("Unknown", new EngineParams(0, 0, Vec2.ZERO, 0)));
    }

    @Test
    void duplicateNameThrows() {
        registry.register("Engine", EngineParams.class, 0);
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("Engine", EngineParams.class, 1));
    }

    @Test
    void duplicateBindingThrows() {
        registry.register("Engine", EngineParams.class, 0);
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("Camera", CameraParams.class, 0));
    }

    @Test
    void generateSlangBlocks() {
        registry.register("Engine", EngineParams.class, 0);
        registry.register("Camera", CameraParams.class, 1);

        var slang = registry.generateSlang();
        assertTrue(slang.contains("cbuffer EngineBuffer : register(b0)"));
        assertTrue(slang.contains("cbuffer CameraBuffer : register(b1)"));
        assertTrue(slang.contains("static UboEngineParams engine"));
        assertTrue(slang.contains("static UboCameraParams camera"));
    }

    @Test
    void userDefinedParamsGenerateSlang() {
        registry.register("Light", LightParams.class, 2);

        var slang = registry.generateSlang();
        assertTrue(slang.contains("interface ILightParams"));
        assertTrue(slang.contains("float3 direction()"));
        assertTrue(slang.contains("float3 color()"));
        assertTrue(slang.contains("float intensity()"));
        assertTrue(slang.contains("cbuffer LightBuffer : register(b2)"));
        assertTrue(slang.contains("static UboLightParams light"));
    }

}

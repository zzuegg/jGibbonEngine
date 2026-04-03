package dev.engine.graphics.renderer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RenderableTest {

    @Test
    void bindingForReturnsReflectedSlot() {
        var bindings = Map.of(
                "CameraBuffer", 0,
                "EngineBuffer", 1,
                "MaterialBuffer", 2);
        var renderable = new Renderable(null, null, null, null, 0, 0, bindings);

        assertEquals(0, renderable.bindingFor("CameraBuffer", 99));
        assertEquals(1, renderable.bindingFor("EngineBuffer", 99));
        assertEquals(2, renderable.bindingFor("MaterialBuffer", 99));
    }

    @Test
    void bindingForReturnsFallbackWhenUnknown() {
        var renderable = new Renderable(null, null, null, null, 0, 0, Map.of());

        assertEquals(0, renderable.bindingFor("CameraBuffer", 0));
        assertEquals(1, renderable.bindingFor("EngineBuffer", 1));
        assertEquals(2, renderable.bindingFor("MaterialBuffer", 2));
    }

    @Test
    void bindingForReturnsFallbackWithEmptyBindingsConstructor() {
        var renderable = new Renderable(null, null, null, null, 0, 0);

        assertEquals(5, renderable.bindingFor("AnyBuffer", 5));
    }
}

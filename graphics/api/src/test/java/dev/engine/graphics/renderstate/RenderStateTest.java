package dev.engine.graphics.renderstate;

import dev.engine.core.property.PropertyMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderStateTest {
    @Test void keysAreDistinct() {
        assertNotEquals(RenderState.DEPTH_TEST, RenderState.DEPTH_WRITE);
        assertNotEquals(RenderState.BLEND_MODE, RenderState.CULL_MODE);
    }

    @Test void keysHaveCorrectTypes() {
        assertEquals(Boolean.class, RenderState.DEPTH_TEST.type());
        assertEquals(CompareFunc.class, RenderState.DEPTH_FUNC.type());
        assertEquals(BlendMode.class, RenderState.BLEND_MODE.type());
        assertEquals(CullMode.class, RenderState.CULL_MODE.type());
        assertEquals(FrontFace.class, RenderState.FRONT_FACE.type());
    }

    @Test void defaultsProvidesSafeBaseline() {
        PropertyMap defaults = RenderState.defaults();
        assertEquals(true, defaults.get(RenderState.DEPTH_TEST));
        assertEquals(true, defaults.get(RenderState.DEPTH_WRITE));
        assertEquals(CompareFunc.LESS, defaults.get(RenderState.DEPTH_FUNC));
        assertEquals(BlendMode.NONE, defaults.get(RenderState.BLEND_MODE));
        assertEquals(CullMode.BACK, defaults.get(RenderState.CULL_MODE));
        assertEquals(FrontFace.CCW, defaults.get(RenderState.FRONT_FACE));
        assertEquals(false, defaults.get(RenderState.WIREFRAME));
    }

    @Test void stencilKeysExist() {
        assertNotNull(RenderState.STENCIL_TEST);
        assertNotNull(RenderState.STENCIL_FUNC);
        assertNotNull(RenderState.STENCIL_REF);
        assertNotNull(RenderState.STENCIL_FAIL);
        assertEquals(Boolean.class, RenderState.STENCIL_TEST.type());
        assertEquals(CompareFunc.class, RenderState.STENCIL_FUNC.type());
        assertEquals(StencilOp.class, RenderState.STENCIL_FAIL.type());
    }

    @Test void canBuildCustomState() {
        PropertyMap state = PropertyMap.builder()
            .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
            .set(RenderState.DEPTH_WRITE, false)
            .build();
        assertEquals(BlendMode.ALPHA, state.get(RenderState.BLEND_MODE));
        assertEquals(false, state.get(RenderState.DEPTH_WRITE));
    }
}

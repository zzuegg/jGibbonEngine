package dev.engine.graphics.renderstate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderStateValueTypesTest {
    @Test void compareFuncHasExpectedInstances() {
        assertNotNull(CompareFunc.LESS);
        assertNotNull(CompareFunc.LEQUAL);
        assertNotNull(CompareFunc.GREATER);
        assertNotNull(CompareFunc.GEQUAL);
        assertNotNull(CompareFunc.EQUAL);
        assertNotNull(CompareFunc.NOT_EQUAL);
        assertNotNull(CompareFunc.ALWAYS);
        assertNotNull(CompareFunc.NEVER);
        assertNotEquals(CompareFunc.LESS, CompareFunc.GREATER);
    }

    @Test void blendModeHasExpectedInstances() {
        assertNotNull(BlendMode.NONE);
        assertNotNull(BlendMode.ALPHA);
        assertNotNull(BlendMode.ADDITIVE);
        assertNotNull(BlendMode.MULTIPLY);
        assertNotNull(BlendMode.PREMULTIPLIED);
    }

    @Test void cullModeHasExpectedInstances() {
        assertNotNull(CullMode.NONE);
        assertNotNull(CullMode.BACK);
        assertNotNull(CullMode.FRONT);
    }

    @Test void frontFaceHasExpectedInstances() {
        assertNotNull(FrontFace.CCW);
        assertNotNull(FrontFace.CW);
    }
}

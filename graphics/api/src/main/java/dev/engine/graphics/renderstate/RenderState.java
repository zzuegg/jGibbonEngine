package dev.engine.graphics.renderstate;

import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;

public interface RenderState {
    PropertyKey<Boolean>     DEPTH_TEST  = PropertyKey.of("depthTest", Boolean.class);
    PropertyKey<Boolean>     DEPTH_WRITE = PropertyKey.of("depthWrite", Boolean.class);
    PropertyKey<CompareFunc> DEPTH_FUNC  = PropertyKey.of("depthFunc", CompareFunc.class);
    PropertyKey<BlendMode>   BLEND_MODE  = PropertyKey.of("blendMode", BlendMode.class);
    PropertyKey<CullMode>    CULL_MODE   = PropertyKey.of("cullMode", CullMode.class);
    PropertyKey<FrontFace>   FRONT_FACE  = PropertyKey.of("frontFace", FrontFace.class);
    PropertyKey<Boolean>     WIREFRAME     = PropertyKey.of("wireframe", Boolean.class);
    PropertyKey<Float>       LINE_WIDTH    = PropertyKey.of("lineWidth", Float.class);
    PropertyKey<Boolean>     SCISSOR_TEST  = PropertyKey.of("scissorTest", Boolean.class);

    PropertyKey<Boolean>     STENCIL_TEST       = PropertyKey.of("stencilTest", Boolean.class);
    PropertyKey<CompareFunc> STENCIL_FUNC       = PropertyKey.of("stencilFunc", CompareFunc.class);
    PropertyKey<Integer>     STENCIL_REF        = PropertyKey.of("stencilRef", Integer.class);
    PropertyKey<Integer>     STENCIL_MASK        = PropertyKey.of("stencilMask", Integer.class);
    PropertyKey<StencilOp>   STENCIL_FAIL       = PropertyKey.of("stencilFail", StencilOp.class);
    PropertyKey<StencilOp>   STENCIL_DEPTH_FAIL = PropertyKey.of("stencilDepthFail", StencilOp.class);
    PropertyKey<StencilOp>   STENCIL_PASS       = PropertyKey.of("stencilPass", StencilOp.class);

    static PropertyMap defaults() {
        return PropertyMap.builder()
            .set(DEPTH_TEST, true)
            .set(DEPTH_WRITE, true)
            .set(DEPTH_FUNC, CompareFunc.LESS)
            .set(BLEND_MODE, BlendMode.NONE)
            .set(CULL_MODE, CullMode.BACK)
            .set(FRONT_FACE, FrontFace.CCW)
            .set(WIREFRAME, false)
            .build();
    }
}

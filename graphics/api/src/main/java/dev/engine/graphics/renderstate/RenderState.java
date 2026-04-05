package dev.engine.graphics.renderstate;

import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;

public interface RenderState {
    PropertyKey<RenderState, Boolean>     DEPTH_TEST  = PropertyKey.of("depthTest", Boolean.class);
    PropertyKey<RenderState, Boolean>     DEPTH_WRITE = PropertyKey.of("depthWrite", Boolean.class);
    PropertyKey<RenderState, CompareFunc> DEPTH_FUNC  = PropertyKey.of("depthFunc", CompareFunc.class);
    PropertyKey<RenderState, BlendMode>   BLEND_MODE  = PropertyKey.of("blendMode", BlendMode.class);
    PropertyKey<RenderState, CullMode>    CULL_MODE   = PropertyKey.of("cullMode", CullMode.class);
    PropertyKey<RenderState, FrontFace>   FRONT_FACE  = PropertyKey.of("frontFace", FrontFace.class);
    PropertyKey<RenderState, Boolean>     WIREFRAME     = PropertyKey.of("wireframe", Boolean.class);
    PropertyKey<RenderState, Float>       LINE_WIDTH    = PropertyKey.of("lineWidth", Float.class);
    PropertyKey<RenderState, Boolean>     SCISSOR_TEST  = PropertyKey.of("scissorTest", Boolean.class);

    PropertyKey<RenderState, Boolean>     STENCIL_TEST       = PropertyKey.of("stencilTest", Boolean.class);
    PropertyKey<RenderState, CompareFunc> STENCIL_FUNC       = PropertyKey.of("stencilFunc", CompareFunc.class);
    PropertyKey<RenderState, Integer>     STENCIL_REF        = PropertyKey.of("stencilRef", Integer.class);
    PropertyKey<RenderState, Integer>     STENCIL_MASK        = PropertyKey.of("stencilMask", Integer.class);
    PropertyKey<RenderState, StencilOp>   STENCIL_FAIL       = PropertyKey.of("stencilFail", StencilOp.class);
    PropertyKey<RenderState, StencilOp>   STENCIL_DEPTH_FAIL = PropertyKey.of("stencilDepthFail", StencilOp.class);
    PropertyKey<RenderState, StencilOp>   STENCIL_PASS       = PropertyKey.of("stencilPass", StencilOp.class);

    static PropertyMap<RenderState> defaults() {
        return PropertyMap.<RenderState>builder()
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

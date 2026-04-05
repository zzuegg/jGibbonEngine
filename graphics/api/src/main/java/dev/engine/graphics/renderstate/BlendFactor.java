package dev.engine.graphics.renderstate;

/**
 * Blend factor for the blend equation: result = src * srcFactor OP dst * dstFactor.
 *
 * <p>Use with {@link BlendMode#of} to create custom blend configurations.
 * Backends map these to their own factor constants.
 */
public interface BlendFactor {
    String name();

    BlendFactor ZERO                 = () -> "ZERO";
    BlendFactor ONE                  = () -> "ONE";
    BlendFactor SRC_COLOR            = () -> "SRC_COLOR";
    BlendFactor ONE_MINUS_SRC_COLOR  = () -> "ONE_MINUS_SRC_COLOR";
    BlendFactor DST_COLOR            = () -> "DST_COLOR";
    BlendFactor ONE_MINUS_DST_COLOR  = () -> "ONE_MINUS_DST_COLOR";
    BlendFactor SRC_ALPHA            = () -> "SRC_ALPHA";
    BlendFactor ONE_MINUS_SRC_ALPHA  = () -> "ONE_MINUS_SRC_ALPHA";
    BlendFactor DST_ALPHA            = () -> "DST_ALPHA";
    BlendFactor ONE_MINUS_DST_ALPHA  = () -> "ONE_MINUS_DST_ALPHA";
}

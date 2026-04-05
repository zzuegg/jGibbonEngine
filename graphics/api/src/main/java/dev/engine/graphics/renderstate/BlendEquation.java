package dev.engine.graphics.renderstate;

/**
 * Blend equation defining how source and destination values are combined.
 *
 * <p>result = src * srcFactor OP dst * dstFactor
 */
public interface BlendEquation {
    String name();

    BlendEquation ADD              = () -> "ADD";
    BlendEquation SUBTRACT         = () -> "SUBTRACT";
    BlendEquation REVERSE_SUBTRACT = () -> "REVERSE_SUBTRACT";
    BlendEquation MIN              = () -> "MIN";
    BlendEquation MAX              = () -> "MAX";
}

package dev.engine.graphics.renderstate;

public interface StencilOp {
    String name();

    StencilOp KEEP      = () -> "KEEP";
    StencilOp ZERO      = () -> "ZERO";
    StencilOp REPLACE   = () -> "REPLACE";
    StencilOp INCR      = () -> "INCR";
    StencilOp DECR      = () -> "DECR";
    StencilOp INVERT    = () -> "INVERT";
    StencilOp INCR_WRAP = () -> "INCR_WRAP";
    StencilOp DECR_WRAP = () -> "DECR_WRAP";
}

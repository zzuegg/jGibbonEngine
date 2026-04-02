package dev.engine.graphics.renderer;

import dev.engine.core.math.Mat4;

public record DrawCommand(Renderable renderable, Mat4 transform) {}

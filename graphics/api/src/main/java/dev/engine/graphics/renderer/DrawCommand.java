package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.PropertyMap;

public record DrawCommand(Handle<?> entity, Renderable renderable, Mat4 transform, PropertyMap<MaterialData> materialData) {}

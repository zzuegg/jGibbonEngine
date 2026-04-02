package dev.engine.core.transaction;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;

public sealed interface Transaction {

    Handle entity();

    record EntityAdded(Handle entity) implements Transaction {}

    record EntityRemoved(Handle entity) implements Transaction {}

    record TransformChanged(Handle entity, Mat4 transform) implements Transaction {}

    record MaterialPropertyChanged(Handle entity, PropertyKey<?> key, Object value) implements Transaction {}

    record MaterialReplaced(Handle entity, PropertyMap material) implements Transaction {}

    record MeshChanged(Handle entity, Handle newMesh) implements Transaction {}
}

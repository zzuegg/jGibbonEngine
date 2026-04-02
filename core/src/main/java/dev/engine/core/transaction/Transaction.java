package dev.engine.core.transaction;

import dev.engine.core.handle.Handle;
import dev.engine.core.material.Material;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.scene.MaterialTag;
import dev.engine.core.scene.MeshTag;

public sealed interface Transaction {

    Handle<?> entity();

    record EntityAdded(Handle<?> entity) implements Transaction {}
    record EntityRemoved(Handle<?> entity) implements Transaction {}
    record TransformChanged(Handle<?> entity, Mat4 transform) implements Transaction {}
    record MaterialPropertyChanged(Handle<?> entity, PropertyKey<?> key, Object value) implements Transaction {}
    record MaterialReplaced(Handle<?> entity, PropertyMap material) implements Transaction {}
    record MeshChanged(Handle<?> entity, MeshData meshData) implements Transaction {}
    record MaterialChanged(Handle<?> entity, Material material) implements Transaction {}
    record MaterialDataChanged(Handle<?> entity, MaterialData materialData) implements Transaction {}

    // Legacy handle-based (for pre-registered resources)
    record MeshAssigned(Handle<?> entity, Handle<MeshTag> mesh) implements Transaction {}
    record MaterialAssigned(Handle<?> entity, Handle<MaterialTag> material) implements Transaction {}
}

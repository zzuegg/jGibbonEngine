package dev.engine.core.transaction;

import dev.engine.core.handle.Handle;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.scene.Component;
import dev.engine.core.scene.MaterialTag;
import dev.engine.core.scene.MeshTag;

/**
 * A transaction represents a change that occurred in the scene.
 * Transactions are emitted by the scene and consumed by renderers and other subsystems.
 *
 * <p>This interface is open — subsystems can define their own transaction types.
 */
public interface Transaction {

    Handle<?> entity();

    // --- Lifecycle ---
    record EntityAdded(Handle<?> entity) implements Transaction {}
    record EntityRemoved(Handle<?> entity) implements Transaction {}

    // --- Component changes (unified) ---
    record ComponentChanged(Handle<?> entity, Component component) implements Transaction {}

    // --- Legacy transactions (kept for compatibility during migration) ---
    record TransformChanged(Handle<?> entity, Mat4 transform) implements Transaction {}
    record MaterialPropertyChanged(Handle<?> entity, PropertyKey<MaterialData, ?> key, Object value) implements Transaction {}
    record MaterialReplaced(Handle<?> entity, PropertyMap<MaterialData> material) implements Transaction {}
    record MeshChanged(Handle<?> entity, MeshData meshData) implements Transaction {}
    record MaterialDataChanged(Handle<?> entity, MaterialData materialData) implements Transaction {}
    record MeshAssigned(Handle<?> entity, Handle<MeshTag> mesh) implements Transaction {}
    record MaterialAssigned(Handle<?> entity, Handle<MaterialTag> material) implements Transaction {}
}

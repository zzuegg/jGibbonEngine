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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransactionBuffer {

    private final List<Transaction> transactions = new ArrayList<>();

    public void added(Handle<?> entity) { transactions.add(new Transaction.EntityAdded(entity)); }
    public void removed(Handle<?> entity) { transactions.add(new Transaction.EntityRemoved(entity)); }
    public void componentChanged(Handle<?> entity, Component component) { transactions.add(new Transaction.ComponentChanged(entity, component)); }
    public void transformChanged(Handle<?> entity, Mat4 transform) { transactions.add(new Transaction.TransformChanged(entity, transform)); }
    public <T> void materialPropertyChanged(Handle<?> entity, PropertyKey<T> key, T value) { transactions.add(new Transaction.MaterialPropertyChanged(entity, key, value)); }
    public void materialReplaced(Handle<?> entity, PropertyMap material) { transactions.add(new Transaction.MaterialReplaced(entity, material)); }
    public void meshChanged(Handle<?> entity, MeshData meshData) { transactions.add(new Transaction.MeshChanged(entity, meshData)); }
    public void materialDataChanged(Handle<?> entity, MaterialData materialData) { transactions.add(new Transaction.MaterialDataChanged(entity, materialData)); }
    public void meshAssigned(Handle<?> entity, Handle<MeshTag> mesh) { transactions.add(new Transaction.MeshAssigned(entity, mesh)); }
    public void materialAssigned(Handle<?> entity, Handle<MaterialTag> material) { transactions.add(new Transaction.MaterialAssigned(entity, material)); }

    public List<Transaction> drain() {
        var result = Collections.unmodifiableList(new ArrayList<>(transactions));
        transactions.clear();
        return result;
    }
}

package dev.engine.core.transaction;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransactionBuffer {

    private final List<Transaction> transactions = new ArrayList<>();

    public void added(Handle entity) {
        transactions.add(new Transaction.EntityAdded(entity));
    }

    public void removed(Handle entity) {
        transactions.add(new Transaction.EntityRemoved(entity));
    }

    public void transformChanged(Handle entity, Mat4 transform) {
        transactions.add(new Transaction.TransformChanged(entity, transform));
    }

    public <T> void materialPropertyChanged(Handle entity, PropertyKey<T> key, T value) {
        transactions.add(new Transaction.MaterialPropertyChanged(entity, key, value));
    }

    public void materialReplaced(Handle entity, PropertyMap material) {
        transactions.add(new Transaction.MaterialReplaced(entity, material));
    }

    public void meshChanged(Handle entity, Handle newMesh) {
        transactions.add(new Transaction.MeshChanged(entity, newMesh));
    }

    public List<Transaction> drain() {
        var result = Collections.unmodifiableList(new ArrayList<>(transactions));
        transactions.clear();
        return result;
    }
}

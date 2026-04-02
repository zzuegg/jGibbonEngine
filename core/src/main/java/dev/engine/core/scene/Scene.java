package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.transaction.Transaction;
import dev.engine.core.transaction.TransactionBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Scene {

    private final HandlePool<EntityTag> entityPool = new HandlePool<>();
    private final Map<Integer, Mat4> localTransforms = new HashMap<>();
    private final Map<Integer, Handle<EntityTag>> parents = new HashMap<>();
    private final Map<Integer, Set<Handle<EntityTag>>> children = new HashMap<>();
    private final TransactionBuffer transactions = new TransactionBuffer();

    public Handle<EntityTag> createEntity() {
        var entity = entityPool.allocate();
        localTransforms.put(entity.index(), Mat4.IDENTITY);
        children.put(entity.index(), new LinkedHashSet<>());
        transactions.added(entity);
        return entity;
    }

    public void destroyEntity(Handle<EntityTag> entity) {
        // Destroy children first (copy to avoid concurrent modification)
        var childSet = children.getOrDefault(entity.index(), Set.of());
        for (var child : new ArrayList<>(childSet)) {
            destroyEntity(child);
        }

        // Remove from parent's children list
        var parent = parents.remove(entity.index());
        if (parent != null) {
            var parentChildren = children.get(parent.index());
            if (parentChildren != null) parentChildren.remove(entity);
        }

        localTransforms.remove(entity.index());
        children.remove(entity.index());
        transactions.removed(entity);
        entityPool.release(entity);
    }

    public void setLocalTransform(Handle<EntityTag> entity, Mat4 transform) {
        localTransforms.put(entity.index(), transform);
        transactions.transformChanged(entity, getWorldTransform(entity));
    }

    public Mat4 getLocalTransform(Handle<EntityTag> entity) {
        return localTransforms.getOrDefault(entity.index(), Mat4.IDENTITY);
    }

    public Mat4 getWorldTransform(Handle<EntityTag> entity) {
        var parent = parents.get(entity.index());
        var local = getLocalTransform(entity);
        if (parent == null) return local;
        return getWorldTransform(parent).mul(local);
    }

    public void setParent(Handle<EntityTag> child, Handle<EntityTag> parent) {
        // Remove from old parent
        var oldParent = parents.get(child.index());
        if (oldParent != null) {
            var oldChildren = children.get(oldParent.index());
            if (oldChildren != null) oldChildren.remove(child);
        }

        parents.put(child.index(), parent);
        children.computeIfAbsent(parent.index(), k -> new LinkedHashSet<>()).add(child);
    }

    public void removeParent(Handle<EntityTag> child) {
        var oldParent = parents.remove(child.index());
        if (oldParent != null) {
            var oldChildren = children.get(oldParent.index());
            if (oldChildren != null) oldChildren.remove(child);
        }
    }

    public Set<Handle<EntityTag>> getChildren(Handle<EntityTag> entity) {
        return Collections.unmodifiableSet(children.getOrDefault(entity.index(), Set.of()));
    }

    public <T> void setMaterialProperty(Handle<EntityTag> entity, PropertyKey<T> key, T value) {
        transactions.materialPropertyChanged(entity, key, value);
    }

    public void setMaterialProperties(Handle<EntityTag> entity, dev.engine.core.property.PropertyMap properties) {
        transactions.materialReplaced(entity, properties);
    }

    public List<Transaction> drainTransactions() {
        return transactions.drain();
    }
}

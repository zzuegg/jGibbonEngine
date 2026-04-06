package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.math.Mat4;
import dev.engine.core.scene.component.Hierarchy;
import dev.engine.core.scene.component.Transform;
import dev.engine.core.transaction.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

public class HierarchicalScene extends AbstractScene {

    private final HandlePool<EntityTag> pool = new HandlePool<>();

    @Override
    public Entity createEntity() {
        var handle = pool.allocate();
        transactions.emit(new Transaction.EntityAdded(handle));
        var entity = new Entity(handle, this);
        entityMap.put(handle, entity);
        return entity;
    }

    @Override
    public void destroyEntity(Handle<EntityTag> handle) {
        var entity = entityMap.get(handle);
        if (entity != null && entity.has(Hierarchy.class)) {
            var h = entity.get(Hierarchy.class);
            for (var child : new ArrayList<>(h.children())) {
                destroyEntity(child.handle());
            }
        }
        entityMap.remove(handle);
        transactions.emit(new Transaction.EntityRemoved(handle));
        pool.release(handle);
    }

    // --- Hierarchy convenience methods ---

    /** Sets the parent of a child entity. */
    public void setParent(Entity child, Entity parent) {
        child.setParent(parent);
    }

    /** Removes the parent from a child entity. */
    public void removeParent(Entity child) {
        if (child.has(Hierarchy.class)) {
            var h = child.get(Hierarchy.class);
            if (h.parent() != null) {
                var parentH = h.parent().get(Hierarchy.class);
                if (parentH != null) parentH.removeChild(child);
                h.setParent(null);
            }
        }
    }

    /** Gets the children of an entity. */
    public Set<Entity> getChildren(Entity parent) {
        if (parent.has(Hierarchy.class)) {
            return parent.get(Hierarchy.class).children();
        }
        return Collections.emptySet();
    }

    @Override
    public Mat4 getWorldTransform(Handle<EntityTag> handle) {
        var entity = entityMap.get(handle);
        if (entity == null) return Mat4.IDENTITY;

        Mat4 local = entity.has(Transform.class) ? entity.get(Transform.class).toMatrix() : Mat4.IDENTITY;

        if (entity.has(Hierarchy.class)) {
            var parent = entity.get(Hierarchy.class).parent();
            if (parent != null) {
                return getWorldTransform(parent.handle()).mul(local);
            }
        }
        return local;
    }
}

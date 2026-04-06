package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.math.Mat4;
import dev.engine.core.scene.component.Transform;
import dev.engine.core.transaction.Transaction;

public class FlatScene extends AbstractScene {

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
        entityMap.remove(handle);
        transactions.emit(new Transaction.EntityRemoved(handle));
        pool.release(handle);
    }

    @Override
    public Mat4 getWorldTransform(Handle<EntityTag> handle) {
        var entity = entityMap.get(handle);
        if (entity != null && entity.has(Transform.class)) return entity.get(Transform.class).toMatrix();
        return Mat4.IDENTITY;
    }
}

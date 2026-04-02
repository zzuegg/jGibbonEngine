package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.math.Mat4;

import java.util.HashMap;
import java.util.Map;

/**
 * A flat scene with no hierarchy. All entities are independent.
 * World transform equals local transform.
 *
 * <p>Best for: ECS-style games, particle systems, flat object lists.
 */
public class FlatScene extends AbstractScene {

    private final HandlePool<EntityTag> entityPool = new HandlePool<>();
    private final Map<Integer, Mat4> transforms = new HashMap<>();

    @Override
    public Handle<EntityTag> createEntity() {
        var entity = entityPool.allocate();
        transforms.put(entity.index(), Mat4.IDENTITY);
        transactions.added(entity);
        return entity;
    }

    @Override
    public void destroyEntity(Handle<EntityTag> entity) {
        transforms.remove(entity.index());
        transactions.removed(entity);
        entityPool.release(entity);
    }

    @Override
    public void setLocalTransform(Handle<EntityTag> entity, Mat4 transform) {
        transforms.put(entity.index(), transform);
        transactions.transformChanged(entity, transform);
    }

    @Override
    public Mat4 getLocalTransform(Handle<EntityTag> entity) {
        return transforms.getOrDefault(entity.index(), Mat4.IDENTITY);
    }

    @Override
    public Mat4 getWorldTransform(Handle<EntityTag> entity) {
        return getLocalTransform(entity); // No hierarchy — world = local
    }
}

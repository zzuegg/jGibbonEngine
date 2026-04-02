package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.material.Material;
import dev.engine.core.math.Mat4;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.scene.component.Transform;
import dev.engine.core.transaction.Transaction;
import dev.engine.core.transaction.TransactionBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all scene implementations.
 * Manages entities and their components. Emits transactions when components change.
 */
public abstract class AbstractScene {

    protected final TransactionBuffer transactions = new TransactionBuffer();
    protected final Map<Handle<EntityTag>, Entity> entityMap = new HashMap<>();

    // --- Entity lifecycle ---

    public abstract Entity createEntity();
    public abstract void destroyEntity(Handle<EntityTag> entity);

    // --- World transform (scene-type specific) ---

    public abstract Mat4 getWorldTransform(Handle<EntityTag> entity);

    // --- Component change handling (called by Entity.add()) ---

    void componentChanged(Entity entity, Component component) {
        switch (component) {
            case Transform t ->
                transactions.transformChanged(entity.handle(), t.toMatrix());
            case MeshData m ->
                transactions.meshChanged(entity.handle(), m);
            case Material m ->
                transactions.materialChanged(entity.handle(), m);
            default -> {
                // Custom components — no built-in transaction, but could be extended
            }
        }
    }

    // --- Legacy compat (used by some internal code) ---

    public void setLocalTransform(Handle<EntityTag> entity, Mat4 transform) {
        var e = entityMap.get(entity);
        if (e != null) e.add(new Transform(
                new dev.engine.core.math.Vec3(transform.m03(), transform.m13(), transform.m23()),
                dev.engine.core.math.Quat.IDENTITY,
                dev.engine.core.math.Vec3.ONE));
        else transactions.transformChanged(entity, transform);
    }

    /** Convenience overload accepting Entity directly. */
    public void setLocalTransform(Entity entity, Mat4 transform) {
        setLocalTransform(entity.handle(), transform);
    }

    public Mat4 getLocalTransform(Handle<EntityTag> entity) {
        var e = entityMap.get(entity);
        if (e != null && e.has(Transform.class)) return e.get(Transform.class).toMatrix();
        return Mat4.IDENTITY;
    }

    /** Convenience overload accepting Entity directly. */
    public Mat4 getLocalTransform(Entity entity) {
        return getLocalTransform(entity.handle());
    }

    /** Convenience overload accepting Entity directly. */
    public Mat4 getWorldTransform(Entity entity) {
        return getWorldTransform(entity.handle());
    }

    /** Convenience: destroy by Entity reference. */
    public void destroyEntity(Entity entity) {
        destroyEntity(entity.handle());
    }

    public <T> void setMaterialProperty(Handle<EntityTag> entity, PropertyKey<T> key, T value) {
        transactions.materialPropertyChanged(entity, key, value);
    }

    /** Convenience overload accepting Entity directly. */
    public <T> void setMaterialProperty(Entity entity, PropertyKey<T> key, T value) {
        setMaterialProperty(entity.handle(), key, value);
    }

    /** Sets all material properties at once via a PropertyMap. */
    public void setMaterialProperties(Entity entity, PropertyMap props) {
        transactions.materialReplaced(entity.handle(), props);
    }

    /** Legacy: assign a mesh handle to an entity. */
    public void setMesh(Handle<EntityTag> entity, Handle<MeshTag> mesh) {
        transactions.meshAssigned(entity, mesh);
    }

    /** Legacy: assign a mesh handle to an entity by Entity reference. */
    public void setMesh(Entity entity, Handle<MeshTag> mesh) {
        transactions.meshAssigned(entity.handle(), mesh);
    }

    /** Legacy: assign a material handle to an entity. */
    public void setMaterial(Handle<EntityTag> entity, Handle<MaterialTag> material) {
        transactions.materialAssigned(entity, material);
    }

    /** Legacy: assign a material handle to an entity by Entity reference. */
    public void setMaterial(Entity entity, Handle<MaterialTag> material) {
        transactions.materialAssigned(entity.handle(), material);
    }

    // --- Entity lookup ---

    public Entity entity(Handle<EntityTag> handle) { return entityMap.get(handle); }

    // --- Query: find all entities with specific component types ---

    public List<Entity> query(Class<? extends Component>... types) {
        var result = new ArrayList<Entity>();
        for (var entity : entityMap.values()) {
            boolean match = true;
            for (var type : types) {
                if (!entity.has(type)) { match = false; break; }
            }
            if (match) result.add(entity);
        }
        return result;
    }

    // --- Renderer-internal ---

    protected List<Transaction> drainTransactions() { return transactions.drain(); }

    static List<Transaction> drain(AbstractScene scene) { return scene.drainTransactions(); }
}

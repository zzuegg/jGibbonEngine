package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.scene.component.Transform;
import dev.engine.core.transaction.Transaction;
import dev.engine.core.transaction.TransactionBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all scene implementations.
 * Manages entities and their components. Emits transactions when components change.
 *
 * <p>Uses {@link TransactionBus} for thread-safe transaction delivery between
 * the logic thread (which emits) and render thread (which drains).
 */
public abstract class AbstractScene {

    /** Well-known subscriber key for the renderer. */
    public static final Object RENDERER_SUBSCRIBER = new Object();

    protected final TransactionBus transactions = new TransactionBus();
    protected final Map<Handle<EntityTag>, Entity> entityMap = new HashMap<>();

    protected AbstractScene() {
        // Register the renderer as a subscriber that receives all transaction types
        transactions.subscribe(RENDERER_SUBSCRIBER);
    }

    // --- Entity lifecycle ---

    public abstract Entity createEntity();
    public abstract void destroyEntity(Handle<EntityTag> entity);

    // --- World transform (scene-type specific) ---

    public abstract Mat4 getWorldTransform(Handle<EntityTag> entity);

    // --- Component change handling (called by Entity.add()) ---

    void componentChanged(Entity entity, Component component) {
        transactions.emit(new Transaction.ComponentChanged(entity.handle(), component));
    }

    // --- Legacy compat (used by some internal code) ---

    public void setLocalTransform(Handle<EntityTag> entity, Mat4 transform) {
        var e = entityMap.get(entity);
        if (e != null) e.add(new Transform(
                new dev.engine.core.math.Vec3(transform.m03(), transform.m13(), transform.m23()),
                dev.engine.core.math.Quat.IDENTITY,
                dev.engine.core.math.Vec3.ONE));
        else transactions.emit(new Transaction.TransformChanged(entity, transform));
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

    public <T> void setMaterialProperty(Handle<EntityTag> entity, PropertyKey<MaterialData, T> key, T value) {
        transactions.emit(new Transaction.MaterialPropertyChanged(entity, key, value));
    }

    /** Convenience overload accepting Entity directly. */
    public <T> void setMaterialProperty(Entity entity, PropertyKey<MaterialData, T> key, T value) {
        setMaterialProperty(entity.handle(), key, value);
    }

    /** Sets all material properties at once via a PropertyMap. */
    public void setMaterialProperties(Entity entity, PropertyMap<MaterialData> props) {
        transactions.emit(new Transaction.MaterialReplaced(entity.handle(), props));
    }

    /** Legacy: assign a mesh handle to an entity. */
    public void setMesh(Handle<EntityTag> entity, Handle<MeshTag> mesh) {
        transactions.emit(new Transaction.MeshAssigned(entity, mesh));
    }

    /** Legacy: assign a mesh handle to an entity by Entity reference. */
    public void setMesh(Entity entity, Handle<MeshTag> mesh) {
        transactions.emit(new Transaction.MeshAssigned(entity.handle(), mesh));
    }

    /** Legacy: assign a material handle to an entity. */
    public void setMaterial(Handle<EntityTag> entity, Handle<MaterialTag> material) {
        transactions.emit(new Transaction.MaterialAssigned(entity, material));
    }

    /** Legacy: assign a material handle to an entity by Entity reference. */
    public void setMaterial(Entity entity, Handle<MaterialTag> material) {
        transactions.emit(new Transaction.MaterialAssigned(entity.handle(), material));
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

    // --- Transaction bus access ---

    /** Returns the transaction bus for additional subscriber registration. */
    public TransactionBus transactionBus() { return transactions; }

    // --- Renderer-internal ---

    protected List<Transaction> drainTransactions() {
        return transactions.drain(RENDERER_SUBSCRIBER);
    }

    static List<Transaction> drain(AbstractScene scene) { return scene.drainTransactions(); }
}

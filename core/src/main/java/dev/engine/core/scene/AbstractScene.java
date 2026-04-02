package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.transaction.Transaction;
import dev.engine.core.transaction.TransactionBuffer;

import java.util.List;

/**
 * Base class for all scene implementations.
 *
 * <p>Defines the user-facing API (create entities, set transforms, set materials)
 * and hides the transaction internals. The Renderer accesses transactions through
 * {@link #collectTransactions()}, which is package-private to prevent user access.
 *
 * <p>Subclasses implement their own spatial organization:
 * <ul>
 *   <li>{@link HierarchicalScene} — parent-child tree (scene graph)</li>
 *   <li>{@link FlatScene} — flat list, no hierarchy</li>
 *   <li>Custom implementations for octrees, grids, ECS, etc.</li>
 * </ul>
 *
 * <p>All implementations emit the same transactions, so the Renderer works
 * identically regardless of scene type.
 */
public abstract class AbstractScene {

    protected final TransactionBuffer transactions = new TransactionBuffer();

    // --- User-facing API (public) ---

    /** Creates a new entity in the scene. */
    public abstract Handle<EntityTag> createEntity();

    /** Destroys an entity and all its dependents. */
    public abstract void destroyEntity(Handle<EntityTag> entity);

    /** Sets the local transform for an entity. */
    public abstract void setLocalTransform(Handle<EntityTag> entity, Mat4 transform);

    /** Gets the local transform for an entity. */
    public abstract Mat4 getLocalTransform(Handle<EntityTag> entity);

    /** Gets the world transform (accumulated from hierarchy if applicable). */
    public abstract Mat4 getWorldTransform(Handle<EntityTag> entity);

    /** Sets a single material property on an entity. */
    public <T> void setMaterialProperty(Handle<EntityTag> entity, PropertyKey<T> key, T value) {
        transactions.materialPropertyChanged(entity, key, value);
    }

    /** Replaces all material properties on an entity. */
    public void setMaterialProperties(Handle<EntityTag> entity, PropertyMap properties) {
        transactions.materialReplaced(entity, properties);
    }

    /** Assigns a mesh to an entity. The handle is opaque — created by the Renderer. */
    public void setMesh(Handle<EntityTag> entity, Handle<MeshTag> mesh) {
        transactions.meshAssigned(entity, mesh);
    }

    /** Assigns a material to an entity. The handle is opaque — created by the Renderer. */
    public void setMaterial(Handle<EntityTag> entity, Handle<MaterialTag> material) {
        transactions.materialAssigned(entity, material);
    }

    // --- Renderer-internal API (not visible to users) ---

    /**
     * Drains all pending transactions. Called by the Renderer each frame.
     * Not public — the Renderer accesses this through the SceneAccess helper.
     */
    protected List<Transaction> drainTransactions() {
        return transactions.drain();
    }

    /**
     * Accessor for the Renderer to drain transactions without exposing
     * the method publicly. Package-private static helper.
     */
    static List<Transaction> drain(AbstractScene scene) {
        return scene.drainTransactions();
    }
}

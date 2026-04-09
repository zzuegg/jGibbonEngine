package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.scene.component.Hierarchy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * An entity in the scene — just an ID with component storage.
 *
 * <pre>
 * var entity = scene.createEntity();
 * entity.add(Transform.at(1, 0, 0));
 * entity.add(PrimitiveMeshes.cube());    // MeshData IS the component
 * entity.add(myMaterial);                // Material IS the component
 *
 * entity.get(Transform.class);           // → Transform
 * entity.has(MeshData.class);            // → true
 * entity.get(MaterialData.class).shaderHint(); // → "PBR"
 * </pre>
 */
public class Entity {

    private final Handle<EntityTag> handle;
    private final AbstractScene scene;
    private final Map<Class<? extends Component>, Component> components = new HashMap<>();

    Entity(Handle<EntityTag> handle, AbstractScene scene) {
        this.handle = handle;
        this.scene = scene;
    }

    public Handle<EntityTag> handle() { return handle; }

    // --- Component management ---

    /**
     * Adds or replaces a component. Uses {@link Component#slotType()}
     * as the key — families like MaterialData share one slot.
     */
    public <T extends Component> Entity add(T component) {
        components.put(component.slotType(), component);
        scene.componentChanged(this, component);
        return this;
    }

    /** Gets a component by slot type, or null. */
    @SuppressWarnings("unchecked")
    public <T extends Component> T get(Class<T> type) {
        return (T) components.get(type);
    }

    /** Checks if entity has a component in the given slot. */
    public boolean has(Class<? extends Component> type) {
        return components.containsKey(type);
    }

    /** Removes a component by slot type. */
    public <T extends Component> Entity remove(Class<T> type) {
        components.remove(type);
        return this;
    }

    /**
     * Updates a component in place: gets current, applies function, re-adds result.
     * Emits a transaction for the change.
     *
     * <pre>
     * entity.update(Transform.class, t -> t.withPosition(new Vec3(1, 0, 0)));
     * entity.update(Transform.class, t -> t.withRotation(myQuat));
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public <T extends Component> Entity update(Class<T> type, UnaryOperator<T> fn) {
        T current = get(type);
        if (current != null) {
            add(fn.apply(current));
        }
        return this;
    }

    // --- Hierarchy shortcuts ---

    public Entity setParent(Entity parent) {
        if (!has(Hierarchy.class)) add(new Hierarchy());
        if (!parent.has(Hierarchy.class)) parent.add(new Hierarchy());

        var myH = get(Hierarchy.class);
        var parentH = parent.get(Hierarchy.class);

        if (myH.parent() != null) {
            var oldParent = myH.parent();
            var oldH = oldParent.get(Hierarchy.class);
            if (oldH != null) {
                oldH.removeChild(this);
                scene.componentChanged(oldParent, oldH);
            }
        }

        myH.setParent(parent);
        parentH.addChild(this);
        scene.componentChanged(this, myH);
        scene.componentChanged(parent, parentH);
        return this;
    }

    public Entity addChild(Entity child) {
        child.setParent(this);
        return this;
    }

    // --- Lifecycle ---

    public void destroy() { scene.destroyEntity(handle); }

    // --- Identity ---

    @Override public boolean equals(Object o) { return o instanceof Entity e && handle.equals(e.handle); }
    @Override public int hashCode() { return handle.hashCode(); }
    @Override public String toString() { return "Entity[" + handle.index() + "]"; }
}

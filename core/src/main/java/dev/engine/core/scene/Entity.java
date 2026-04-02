package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.scene.component.Hierarchy;

import java.util.HashMap;
import java.util.Map;

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
 * entity.get(Material.class).type();     // → MaterialType
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

    /** Adds or replaces a component. Emits a transaction. */
    public <T extends Component> Entity add(T component) {
        components.put(component.getClass(), component);
        scene.componentChanged(this, component);
        return this;
    }

    /** Gets a component by type, or null. */
    @SuppressWarnings("unchecked")
    public <T extends Component> T get(Class<T> type) {
        return (T) components.get(type);
    }

    /** Checks if entity has a component type. */
    public boolean has(Class<? extends Component> type) {
        return components.containsKey(type);
    }

    /** Removes a component. */
    public <T extends Component> Entity remove(Class<T> type) {
        components.remove(type);
        return this;
    }

    // --- Hierarchy shortcuts ---

    public Entity setParent(Entity parent) {
        if (!has(Hierarchy.class)) add(new Hierarchy());
        if (!parent.has(Hierarchy.class)) parent.add(new Hierarchy());

        var myH = get(Hierarchy.class);
        var parentH = parent.get(Hierarchy.class);

        if (myH.parent() != null) {
            var oldH = myH.parent().get(Hierarchy.class);
            if (oldH != null) oldH.removeChild(this);
        }

        myH.setParent(parent);
        parentH.addChild(this);
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

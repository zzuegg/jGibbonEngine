package dev.engine.core.scene;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Quat;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Hierarchy;
import dev.engine.core.scene.component.Transform;

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

    // --- Transform convenience ---

    /** Sets position. Adds Transform if not present. */
    public Entity setPosition(Vec3 position) {
        var t = getOrCreateTransform();
        return add(t.withPosition(position));
    }

    /** Sets position. */
    public Entity setPosition(float x, float y, float z) {
        return setPosition(new Vec3(x, y, z));
    }

    /** Sets rotation. Adds Transform if not present. */
    public Entity setRotation(Quat rotation) {
        var t = getOrCreateTransform();
        return add(t.withRotation(rotation));
    }

    /** Sets scale uniformly. */
    public Entity setScale(float uniform) {
        var t = getOrCreateTransform();
        return add(t.withScale(uniform));
    }

    /** Sets scale per axis. */
    public Entity setScale(Vec3 scale) {
        var t = getOrCreateTransform();
        return add(t.withScale(scale));
    }

    /** Translates by offset (adds to current position). */
    public Entity move(Vec3 offset) {
        var t = getOrCreateTransform();
        return add(t.withPosition(t.position().add(offset)));
    }

    /** Translates by offset. */
    public Entity move(float dx, float dy, float dz) {
        return move(new Vec3(dx, dy, dz));
    }

    /** Rotates around an axis by radians (multiplies current rotation). */
    public Entity rotate(Vec3 axis, float radians) {
        var t = getOrCreateTransform();
        var delta = Quat.fromAxisAngle(axis, radians);
        return add(t.withRotation(delta.mul(t.rotation())));
    }

    /** Rotates around Y axis. */
    public Entity rotateY(float radians) { return rotate(Vec3.UNIT_Y, radians); }

    /** Rotates around X axis. */
    public Entity rotateX(float radians) { return rotate(Vec3.UNIT_X, radians); }

    /** Rotates around Z axis. */
    public Entity rotateZ(float radians) { return rotate(Vec3.UNIT_Z, radians); }

    /** Looks at a target position from current position. */
    public Entity lookAt(Vec3 target, Vec3 up) {
        var t = getOrCreateTransform();
        var dir = target.sub(t.position()).normalize();
        // Simple lookAt quaternion from direction
        var forward = new Vec3(0, 0, -1);
        var dot = forward.dot(dir);
        if (dot < -0.9999f) {
            return add(t.withRotation(Quat.fromAxisAngle(up, (float) Math.PI)));
        }
        var cross = forward.cross(dir);
        return add(t.withRotation(new Quat(cross.x(), cross.y(), cross.z(), 1 + dot).normalize()));
    }

    /** Gets current position, or Vec3.ZERO if no Transform. */
    public Vec3 position() {
        var t = get(Transform.class);
        return t != null ? t.position() : Vec3.ZERO;
    }

    /** Gets current rotation, or identity if no Transform. */
    public Quat rotation() {
        var t = get(Transform.class);
        return t != null ? t.rotation() : Quat.IDENTITY;
    }

    /** Gets current scale, or Vec3.ONE if no Transform. */
    public Vec3 scale() {
        var t = get(Transform.class);
        return t != null ? t.scale() : Vec3.ONE;
    }

    private Transform getOrCreateTransform() {
        var t = get(Transform.class);
        return t != null ? t : Transform.IDENTITY;
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

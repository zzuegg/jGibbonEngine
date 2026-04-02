package dev.engine.core.scene.component;

import dev.engine.core.scene.Component;
import dev.engine.core.scene.Entity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parent-child hierarchy component. Mutable — parent/children change at runtime.
 */
public final class Hierarchy implements Component {

    private Entity parent;
    private final Set<Entity> children = new LinkedHashSet<>();

    public Entity parent() { return parent; }
    public Set<Entity> children() { return Collections.unmodifiableSet(children); }
    public boolean hasParent() { return parent != null; }

    public void setParent(Entity parent) { this.parent = parent; }
    public void addChild(Entity child) { children.add(child); }
    public void removeChild(Entity child) { children.remove(child); }
}

package dev.engine.core.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MutablePropertyMap {

    private final Map<PropertyKey<?>, Object> values = new LinkedHashMap<>();
    private final Set<PropertyKey<?>> changes = new LinkedHashSet<>();

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyKey<T> key) {
        return (T) values.get(key);
    }

    public <T> void set(PropertyKey<T> key, T value) {
        Object old = values.put(key, value);
        if (!Objects.equals(old, value)) {
            changes.add(key);
        }
    }

    public void remove(PropertyKey<?> key) {
        if (values.remove(key) != null) {
            changes.add(key);
        }
    }

    public boolean contains(PropertyKey<?> key) {
        return values.containsKey(key);
    }

    public Set<PropertyKey<?>> getChanges() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(changes));
    }

    public void clearChanges() { changes.clear(); }

    @SuppressWarnings("unchecked")
    public PropertyMap snapshot() {
        var builder = PropertyMap.builder();
        for (var entry : values.entrySet()) {
            var key = (PropertyKey<Object>) entry.getKey();
            builder.set(key, entry.getValue());
        }
        return builder.build();
    }
}

package dev.engine.core.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MutablePropertyMap<O> {

    private final Map<PropertyKey<O, ?>, Object> values = new LinkedHashMap<>();
    private final Set<PropertyKey<O, ?>> changes = new LinkedHashSet<>();

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyKey<O, T> key) {
        return (T) values.get(key);
    }

    public <T> void set(PropertyKey<O, T> key, T value) {
        Object old = values.put(key, value);
        if (!Objects.equals(old, value)) {
            changes.add(key);
        }
    }

    public void remove(PropertyKey<O, ?> key) {
        if (values.remove(key) != null) {
            changes.add(key);
        }
    }

    public boolean contains(PropertyKey<O, ?> key) {
        return values.containsKey(key);
    }

    public Set<PropertyKey<O, ?>> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public Set<PropertyKey<O, ?>> getChanges() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(changes));
    }

    public void clearChanges() { changes.clear(); }

    @SuppressWarnings("unchecked")
    public PropertyMap<O> snapshot() {
        var builder = PropertyMap.<O>builder();
        for (var entry : values.entrySet()) {
            var key = (PropertyKey<O, Object>) entry.getKey();
            builder.set(key, entry.getValue());
        }
        return builder.build();
    }
}

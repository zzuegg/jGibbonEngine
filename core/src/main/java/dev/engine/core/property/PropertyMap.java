package dev.engine.core.property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PropertyMap {

    private final Map<PropertyKey<?>, Object> values;

    private PropertyMap(Map<PropertyKey<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyKey<T> key) {
        return (T) values.get(key);
    }

    public boolean contains(PropertyKey<?> key) {
        return values.containsKey(key);
    }

    public Set<PropertyKey<?>> keys() {
        return values.keySet();
    }

    public int size() { return values.size(); }

    public static Builder builder() { return new Builder(); }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PropertyMap p && values.equals(p.values));
    }

    @Override
    public int hashCode() { return values.hashCode(); }

    public static class Builder {
        private final Map<PropertyKey<?>, Object> values = new LinkedHashMap<>();

        public <T> Builder set(PropertyKey<T> key, T value) {
            values.put(key, value);
            return this;
        }

        public PropertyMap build() { return new PropertyMap(values); }
    }
}

package dev.engine.core.property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PropertyMap<O> {

    private final Map<PropertyKey<O, ?>, Object> values;

    private PropertyMap(Map<PropertyKey<O, ?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyKey<O, T> key) {
        return (T) values.get(key);
    }

    public boolean contains(PropertyKey<O, ?> key) {
        return values.containsKey(key);
    }

    public Set<PropertyKey<O, ?>> keys() {
        return values.keySet();
    }

    public int size() { return values.size(); }

    public static <O> Builder<O> builder() { return new Builder<>(); }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PropertyMap<?> p && values.equals(p.values));
    }

    @Override
    public int hashCode() { return values.hashCode(); }

    public static class Builder<O> {
        private final Map<PropertyKey<O, ?>, Object> values = new LinkedHashMap<>();

        public <T> Builder<O> set(PropertyKey<O, T> key, T value) {
            values.put(key, value);
            return this;
        }

        public PropertyMap<O> build() { return new PropertyMap<>(values); }
    }
}

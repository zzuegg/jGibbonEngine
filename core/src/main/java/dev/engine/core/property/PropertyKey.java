package dev.engine.core.property;

import java.util.Objects;

public final class PropertyKey<T> {

    private final String name;
    private final Class<T> type;

    private PropertyKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public static <T> PropertyKey<T> of(String name, Class<T> type) {
        return new PropertyKey<>(name, type);
    }

    public String name() { return name; }
    public Class<T> type() { return type; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PropertyKey<?> k && name.equals(k.name) && type.equals(k.type));
    }

    @Override
    public int hashCode() { return Objects.hash(name, type); }

    @Override
    public String toString() { return "PropertyKey[" + name + ":" + type.getSimpleName() + "]"; }
}
